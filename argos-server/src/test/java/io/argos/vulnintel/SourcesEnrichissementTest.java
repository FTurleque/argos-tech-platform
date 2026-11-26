package io.argos.vulnintel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import jakarta.inject.Inject;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;

import io.argos.testsupport.DataSourceEnPanne;
import io.argos.testsupport.StubServer;
import io.argos.testsupport.StubServer.Stub;
import io.argos.testsupport.TestDb;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;

/**
 * Sources d'enrichissement (COL-04) face à des réponses limites, servies par le serveur simulé : 404, erreur
 * HTTP, JSON illisible, source injoignable, listes vides, métriques de repli, cache des index KEV et
 * CERT-FR, avis sans CVE exploitable ; base injoignable pour les lectures de l'enrichisseur.
 */
@QuarkusTest
@QuarkusTestResource(StubServer.class)
class SourcesEnrichissementTest {

    private static final String CVE = "CVE-2026-1234";
    private static final String NVD = "/nvd?cveId=" + CVE;
    private static final String EPSS = "/epss?cve=" + CVE;
    /** Port réservé où aucun serveur n'écoute : connexion refusée. */
    private static final String ADRESSE_INJOIGNABLE = "http://localhost:1/injoignable";

    @Inject
    TestDb baseDeTest;

    @Inject
    EnrichmentHttp http;

    @Inject
    NvdSource nvd;

    @Inject
    EpssSource epss;

    @Inject
    KevSource kev;

    @Inject
    CertFrSource certFr;

    @BeforeEach
    void reinitialiser() {
        baseDeTest.reset();
    }

    // ------------------------------------------------------------------ appels HTTP

    @Test
    void reponse404ValantAbsenceEtAutreStatutIndisponibilite() {
        assertEquals(Optional.empty(), http.get(URI.create(StubServer.base() + "/absent")));

        StubServer.programmer("/panne", Stub.status(502));
        EnrichmentUnavailableException panne = assertThrows(EnrichmentUnavailableException.class,
                () -> http.get(URI.create(StubServer.base() + "/panne")));
        assertTrue(panne.getMessage().startsWith("HTTP 502"), panne.getMessage());
    }

    @Test
    void jsonIllisibleEtSourceInjoignableRendentLaSourceIndisponible() {
        StubServer.programmer("/illisible", Stub.succes("{pas du json"));
        assertTrue(assertThrows(EnrichmentUnavailableException.class,
                () -> http.getJson(URI.create(StubServer.base() + "/illisible"))).getMessage().startsWith("réponse illisible"));
        assertTrue(assertThrows(EnrichmentUnavailableException.class,
                () -> http.get(URI.create(ADRESSE_INJOIGNABLE))).getMessage().startsWith("erreur réseau"));
    }

    // ------------------------------------------------------------------ NVD

    @Test
    void nvdSansVulnerabiliteOuInconnueNeDonneRien() {
        assertEquals(List.of(), nvd.lookup(CVE), "404 : CVE inconnue");
        StubServer.programmer(NVD, Stub.succes("{\"vulnerabilities\": []}"));
        assertEquals(List.of(), nvd.lookup(CVE));
        StubServer.programmer(NVD, Stub.succes("{\"vulnerabilities\": {}}"));
        assertEquals(List.of(), nvd.lookup(CVE));
    }

    @Test
    void nvdRetientLaPremiereMetriqueDisponibleEtLaDescriptionAnglaise() {
        StubServer.programmer(NVD, Stub.succes("""
                {"vulnerabilities": [{"cve": {"published": "2026-01-02T00:00:00", "lastModified": "2026-01-03T00:00:00",
                  "descriptions": [{"lang": "es", "value": "descripción"}, {"lang": "en", "value": "description"}],
                  "metrics": {"cvssMetricV40": [], "cvssMetricV2": [{"baseSeverity": "HIGH",
                    "cvssData": {"version": "2.0", "baseScore": 7.5}}]}}}]}
                """));

        List<EnrichmentSource.Finding> resultats = nvd.lookup(CVE);

        assertEquals(1, resultats.size());
        JsonNode donnees = resultats.get(0).data();
        assertEquals("description", donnees.path("description").asText());
        assertEquals("2.0", donnees.path("cvssVersion").asText());
        assertEquals(7.5, donnees.path("baseScore").asDouble());
        assertEquals("HIGH", donnees.path("baseSeverity").asText(), "gravité lue dans la métrique quand cvssData n'en porte pas");
        assertEquals("NVD", nvd.id());
    }

    @Test
    void nvdSansMetriqueNiDescriptionGardeLesDates() {
        StubServer.programmer(NVD, Stub.succes("{\"vulnerabilities\": [{\"cve\": {\"published\": \"2026-01-02T00:00:00\"}}]}"));

        JsonNode donnees = nvd.lookup(CVE).get(0).data();

        assertEquals("2026-01-02T00:00:00", donnees.path("published").asText());
        assertFalse(donnees.has("baseScore"));
        assertFalse(donnees.has("description"));
    }

    @Test
    void nvdEnPanneIndisponible() {
        StubServer.programmer(NVD, Stub.status(503));
        assertThrows(EnrichmentUnavailableException.class, () -> nvd.lookup(CVE));
    }

    // ------------------------------------------------------------------ EPSS

    @Test
    void epssSansDonneeNeDonneRienEtAvecDonneeRetientLaPremiere() {
        assertEquals(List.of(), epss.lookup(CVE), "404 : CVE inconnue");
        StubServer.programmer(EPSS, Stub.succes("{\"data\": []}"));
        assertEquals(List.of(), epss.lookup(CVE));

        StubServer.programmer(EPSS, Stub.succes("""
                {"data": [{"epss": "0.42", "percentile": "0.97", "date": "2026-11-20"}, {"epss": "0.01"}]}
                """));
        JsonNode donnees = epss.lookup(CVE).get(0).data();
        assertEquals(0.42, donnees.path("epss").asDouble());
        assertEquals(0.97, donnees.path("percentile").asDouble());
        assertEquals("2026-11-20", donnees.path("date").asText());
        assertEquals("EPSS", epss.id());
    }

    // ------------------------------------------------------------------ KEV

    @Test
    void kevChargeUneFoisPuisSertLeCacheJusquALInvalidation() {
        StubServer.programmer("/kev.json", Stub.succes("""
                {"vulnerabilities": [{"cveID": "%s", "dateAdded": "2026-11-01", "requiredAction": "Corriger"}]}
                """.formatted(CVE)));

        assertEquals(1, kev.lookup(CVE).size());
        assertEquals(List.of(), kev.lookup("CVE-2026-9999"), "CVE absente du catalogue");
        assertEquals(1, StubServer.received("/kev.json").size(), "catalogue servi par le cache");
        JsonNode donnees = kev.lookup(CVE).get(0).data();
        assertTrue(donnees.path("exploited").asBoolean());
        assertEquals("Corriger", donnees.path("requiredAction").asText());

        kev.invalidate();
        kev.lookup(CVE);
        assertEquals(2, StubServer.received("/kev.json").size(), "catalogue rechargé après invalidation");
        assertEquals("KEV", kev.id());
    }

    @Test
    void kevAbsentDonneUnCatalogueVide() {
        assertEquals(List.of(), kev.lookup(CVE));
    }

    // ------------------------------------------------------------------ CERT-FR

    @Test
    void certFrIndexeLesAvisDuFluxEtIgnoreLesAvisAbsentsOuSansCve() {
        StubServer.programmer("/certfr/feed/", Stub.succes("""
                <rss><item>CERTFR-2026-AVI-0001</item><item>CERTFR-2026-AVI-0001</item>
                <item>CERTFR-2026-AVI-0002</item><item>CERTFR-2026-AVI-0003</item></rss>
                """));
        StubServer.programmer("/certfr/CERTFR-2026-AVI-0001/json/", Stub.succes("""
                {"title": "Vulnérabilité", "cves": [{"name": "%s"}, {"name": "  "}, {}]}
                """.formatted(CVE)));
        StubServer.programmer("/certfr/CERTFR-2026-AVI-0003/json/", Stub.succes("{\"title\": \"Sans CVE\"}"));

        List<EnrichmentSource.Finding> resultats = certFr.lookup(CVE);

        assertEquals(1, resultats.size());
        assertEquals("CERTFR-2026-AVI-0001", resultats.get(0).reference());
        assertTrue(resultats.get(0).data().path("url").asText().endsWith("/certfr/CERTFR-2026-AVI-0001/"));
        assertEquals(List.of(), certFr.lookup("CVE-2026-9999"));
        assertEquals(1, StubServer.received("/certfr/feed/").size(), "index servi par le cache");
        assertEquals(1, StubServer.received("/certfr/CERTFR-2026-AVI-0001/").size(), "avis cité deux fois, lu une fois");
        assertEquals("CERTFR", certFr.id());
    }

    @Test
    void certFrSansFluxDonneUnIndexVide() {
        assertEquals(List.of(), certFr.lookup(CVE));
    }

    // ------------------------------------------------------------------ enrichisseur : base injoignable

    @Test
    void lecturesDeLEnrichisseurEnEchecSurBaseInjoignable() {
        Enricher enrichisseur = new Enricher();
        enrichisseur.dataSource = DataSourceEnPanne.creer();

        assertThrows(IllegalStateException.class, () -> enrichisseur.forSignal(UUID.randomUUID()));
    }
}
