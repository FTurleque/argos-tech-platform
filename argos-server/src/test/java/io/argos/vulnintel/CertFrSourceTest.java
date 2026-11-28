package io.argos.vulnintel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.argos.testsupport.Fixtures;
import io.argos.testsupport.ServeurHttpSimule;
import io.argos.testsupport.ServeurHttpSimule.Stub;

/** Avis du CERT-FR (COL-04) : index des avis du flux, cache, avis absents ou sans CVE, flux absent. */
class CertFrSourceTest {

    private static final String CVE = "CVE-2026-1234";
    private static final Duration DUREE_CACHE = Duration.ofHours(6);

    private ServeurHttpSimule serveur;
    private CertFrSource certFr;

    @BeforeEach
    void demarrer() {
        serveur = ServeurHttpSimule.demarrer();
        certFr = new CertFrSource();
        certFr.http = new EnrichmentHttp(EnrichmentHttpTest.DELAI, EnrichmentHttpTest.TAILLE_MAX);
        certFr.feedUrl = serveur.base() + "/certfr/feed/";
        certFr.baseUrl = serveur.base() + "/certfr";
        certFr.horloge = Clock.fixed(Instant.parse("2026-11-20T10:00:00Z"), ZoneOffset.UTC);
        certFr.cacheTtl = DUREE_CACHE;
    }

    @AfterEach
    void arreter() {
        serveur.close();
    }

    @Test
    void certFrIndexeLesAvisDuFluxEtIgnoreLesAvisAbsentsOuSansCve() {
        serveur.programmer("/certfr/feed/", Stub.succes("""
                <rss><item>CERTFR-2026-AVI-0001</item><item>CERTFR-2026-AVI-0001</item>
                <item>CERTFR-2026-AVI-0002</item><item>CERTFR-2026-AVI-0003</item></rss>
                """));
        serveur.programmer("/certfr/CERTFR-2026-AVI-0001/json/", Stub.succes("""
                {"title": "Vulnérabilité", "cves": [{"name": "%s"}, {"name": "  "}, {}]}
                """.formatted(CVE)));
        serveur.programmer("/certfr/CERTFR-2026-AVI-0003/json/", Stub.succes("{\"title\": \"Sans CVE\"}"));

        List<EnrichmentSource.Finding> resultats = certFr.lookup(CVE);

        assertEquals(1, resultats.size());
        assertEquals("CERTFR-2026-AVI-0001", resultats.get(0).reference());
        assertTrue(resultats.get(0).data().path("url").asText().endsWith("/certfr/CERTFR-2026-AVI-0001/"));
        assertEquals(List.of(), certFr.lookup("CVE-2026-9999"));
        assertEquals(1, serveur.received("/certfr/feed/").size(), "index servi par le cache");
        assertEquals(1, serveur.received("/certfr/CERTFR-2026-AVI-0001/").size(), "avis cité deux fois, lu une fois");
        assertEquals("CERTFR", certFr.id());
    }

    /**
     * COL-04 sur réponses enregistrées du CERT-FR : l'avis du flux qui cite la CVE est retenu, et seules des adresses
     * construites à partir des références sont appelées (R-12).
     */
    @Test
    void enrichissementCertFrDepuisLeFluxEtLeContenuJson() {
        serveur.programmer("/certfr/feed/", Stub.succes(Fixtures.resource("enrichment/certfr-feed.xml")));
        serveur.programmer("/certfr/CERTFR-2026-AVI-1500/json/", Stub.succes(Fixtures.resource("enrichment/CERTFR-2026-AVI-1500.json")));

        List<EnrichmentSource.Finding> resultats = certFr.lookup("CVE-2026-109163");

        assertTrue(resultats.stream().anyMatch(resultat -> resultat.reference().equals("CERTFR-2026-AVI-1500")),
                resultats.toString());
        assertTrue(serveur.received("/certfr/").stream()
                .allMatch(requete -> requete.pathAndQuery().equals("/certfr/feed/")
                        || requete.pathAndQuery().matches("/certfr/CERTFR-\\d{4}-AVI-\\d+/json/")));
    }

    @Test
    void certFrSansFluxDonneUnIndexVide() {
        assertEquals(List.of(), certFr.lookup(CVE));
    }
}
