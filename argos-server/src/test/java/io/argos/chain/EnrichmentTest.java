package io.argos.chain;

import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.Duration;
import java.util.UUID;

import jakarta.inject.Inject;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.argos.ingestion.SourceSynchronizer;
import io.argos.testsupport.Fixtures;
import io.argos.testsupport.Api;
import io.argos.testsupport.StubServer;
import io.argos.testsupport.StubServer.Stub;
import io.argos.testsupport.TestDb;
import io.argos.vulnintel.Enricher;
import io.argos.vulnintel.TraitementEnrichissements;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;

/**
 * Enrichissement par NVD, CISA KEV, FIRST EPSS et CERT-FR (tâche 2.4, COL-04), sur réponses
 * enregistrées de ces sources servies par le serveur simulé.
 */
@QuarkusTest
@QuarkusTestResource(StubServer.class)
class EnrichmentTest {

    private static final String LOG4SHELL = "GHSA-jfh8-c2jp-5v3q";

    @Inject
    TestDb baseDeTest;

    @Inject
    SourceSynchronizer synchronizer;

    @Inject
    Enricher enricher;

    @Inject
    TraitementEnrichissements enrichissements;

    @Inject
    Clock horloge;

    @BeforeEach
    void reset() {
        baseDeTest.reset();
    }

    private UUID collectLog4Shell() {
        Fixtures.stubOsv(LOG4SHELL, "2025-10-22T19:37:02.616807Z", Fixtures.resource("osv/GHSA-jfh8-c2jp-5v3q.json"));
        synchronizer.synchronize("OSV");
        enrichissements.enrichirSignauxEchus(horloge.instant());
        return baseDeTest.uuid("SELECT id FROM signal WHERE external_id = '" + LOG4SHELL + "'");
    }

    @Test
    void col04S1ExploitationAvereeSignaleeParKev() {
        StubServer.programmer("/kev.json", Stub.succes(Fixtures.resource("enrichment/kev-extrait.json")));

        UUID signal = collectLog4Shell();

        assertTrue(enricher.exploited(signal));
        Api.enTantQue("alice").when().get("/api/v1/signals/" + signal)
                .then().statusCode(200)
                .body("exploited", equalTo(true))
                .body("enrichments.find { it.source == 'KEV' }.reference", equalTo("CVE-2021-44228"));
    }

    @Test
    void col04S2NvdRattacheAuSignalExistant() {
        StubServer.programmer("/nvd?cveId=CVE-2021-44228", Stub.succes(Fixtures.resource("enrichment/nvd-CVE-2021-44228.json")));

        UUID signal = collectLog4Shell();

        assertEquals(1, baseDeTest.count("SELECT count(*) FROM signal"));
        assertEquals(1, baseDeTest.count("SELECT count(*) FROM enrichment WHERE source = 'NVD' AND signal_id = '" + signal + "'"));
        String data = baseDeTest.string("SELECT data::text FROM enrichment WHERE source = 'NVD'");
        assertTrue(data.contains("\"baseScore\": 10.0") || data.contains("\"baseScore\":10.0"), data);
    }

    @Test
    void col04S3SourceSecondaireIndisponible() {
        StubServer.programmer("/epss?cve=CVE-2021-44228", Stub.status(503));

        UUID signal = collectLog4Shell();

        assertEquals(1, baseDeTest.count("SELECT count(*) FROM signal"));
        assertEquals(0, baseDeTest.count("SELECT count(*) FROM enrichment WHERE source = 'EPSS'"));
        Api.enTantQue("alice").when().get("/api/v1/signals/" + signal)
                .then().statusCode(200).body("signal.externalId", equalTo(LOG4SHELL));

        assertEquals(1, baseDeTest.count("SELECT tentatives FROM enrichissement_attente WHERE signal_id = ?", signal));

        // La source répond de nouveau : le passage suivant de la file rattache les données EPSS, sans action manuelle.
        StubServer.programmer("/epss?cve=CVE-2021-44228", Stub.succes(Fixtures.resource("enrichment/epss-CVE-2021-44228.json")));
        TraitementEnrichissements.Bilan bilan = enrichissements.enrichirSignauxEchus(horloge.instant().plus(Duration.ofHours(1)));

        assertEquals(1, bilan.enrichis());
        assertEquals(1, baseDeTest.count("SELECT count(*) FROM enrichment WHERE source = 'EPSS'"));
        assertEquals(0, baseDeTest.count("SELECT count(*) FROM enrichissement_attente"));
    }

    /** COL-04-S4 : la synchronisation n'interroge aucune source secondaire ; le signal attend dans la file. */
    @Test
    void col04S4EnregistrementSansAttenteDesSourcesSecondaires() {
        Fixtures.stubOsv(LOG4SHELL, "2025-10-22T19:37:02.616807Z", Fixtures.resource("osv/GHSA-jfh8-c2jp-5v3q.json"));

        SourceSynchronizer.SyncReport rapport = synchronizer.synchronize("OSV");

        assertEquals("SUCCEEDED", rapport.state());
        assertTrue(StubServer.received("/nvd").isEmpty(), "aucun appel NVD pendant la synchronisation");
        assertTrue(StubServer.received("/epss").isEmpty(), "aucun appel EPSS pendant la synchronisation");
        assertEquals(1, baseDeTest.count("SELECT count(*) FROM enrichissement_attente"));
    }

    /** Le rattrapage replace en file un signal à CVE resté sans aucun enrichissement. */
    @Test
    void leRattrapageReplaceEnFileUnSignalSansEnrichissement() {
        Fixtures.stubOsv(LOG4SHELL, "2025-10-22T19:37:02.616807Z", Fixtures.resource("osv/GHSA-jfh8-c2jp-5v3q.json"));
        synchronizer.synchronize("OSV");
        baseDeTest.exec("DELETE FROM enrichissement_attente");

        assertEquals(1, enricher.retryMissing(10));
        assertEquals(0, enricher.retryMissing(10), "déjà en file");
        assertEquals(1, baseDeTest.count("SELECT count(*) FROM enrichissement_attente"));
    }
}
