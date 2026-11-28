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

import com.fasterxml.jackson.databind.JsonNode;

import io.argos.testsupport.Fixtures;
import io.argos.testsupport.ServeurHttpSimule;
import io.argos.testsupport.ServeurHttpSimule.Stub;

/** Catalogue CISA KEV (COL-04-S1) : index servi par le cache jusqu'à l'invalidation, catalogue absent. */
class KevSourceTest {

    private static final String CVE = "CVE-2026-1234";
    private static final Duration DUREE_CACHE = Duration.ofHours(6);

    private ServeurHttpSimule serveur;
    private KevSource kev;

    @BeforeEach
    void demarrer() {
        serveur = ServeurHttpSimule.demarrer();
        kev = new KevSource();
        kev.http = new EnrichmentHttp(EnrichmentHttpTest.DELAI, EnrichmentHttpTest.TAILLE_MAX);
        kev.url = serveur.base() + "/kev.json";
        kev.horloge = Clock.fixed(Instant.parse("2026-11-20T10:00:00Z"), ZoneOffset.UTC);
        kev.cacheTtl = DUREE_CACHE;
    }

    @AfterEach
    void arreter() {
        serveur.close();
    }

    @Test
    void kevChargeUneFoisPuisSertLeCacheJusquALInvalidation() {
        serveur.programmer("/kev.json", Stub.succes("""
                {"vulnerabilities": [{"cveID": "%s", "dateAdded": "2026-11-01", "requiredAction": "Corriger"}]}
                """.formatted(CVE)));

        assertEquals(1, kev.lookup(CVE).size());
        assertEquals(List.of(), kev.lookup("CVE-2026-9999"), "CVE absente du catalogue");
        assertEquals(1, serveur.received("/kev.json").size(), "catalogue servi par le cache");
        JsonNode donnees = kev.lookup(CVE).get(0).data();
        assertTrue(donnees.path("exploited").asBoolean());
        assertEquals("Corriger", donnees.path("requiredAction").asText());

        kev.invalidate();
        kev.lookup(CVE);
        assertEquals(2, serveur.received("/kev.json").size(), "catalogue rechargé après invalidation");
        assertEquals("KEV", kev.id());
    }

    /** COL-04-S1 sur l'extrait enregistré du catalogue : Log4Shell est signalée comme exploitée. */
    @Test
    void col04S1ExploitationAvereeLueDansLeCatalogue() {
        serveur.programmer("/kev.json", Stub.succes(Fixtures.resource("enrichment/kev-extrait.json")));

        List<EnrichmentSource.Finding> resultats = kev.lookup("CVE-2021-44228");

        assertEquals(1, resultats.size());
        assertEquals("CVE-2021-44228", resultats.get(0).reference());
        assertTrue(resultats.get(0).data().path("exploited").asBoolean());
    }

    @Test
    void kevAbsentDonneUnCatalogueVide() {
        assertEquals(List.of(), kev.lookup(CVE));
    }
}
