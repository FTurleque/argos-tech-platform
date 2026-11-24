package io.argos.chain;

import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import jakarta.inject.Inject;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.argos.ingestion.FeedEntry;
import io.argos.ingestion.SourceSynchronizer;
import io.argos.ingestion.SyncStore;
import io.argos.testsupport.CrashingFeed;
import io.argos.testsupport.Fixtures;
import io.argos.testsupport.Api;
import io.argos.testsupport.RecordingSleeper;
import io.argos.testsupport.StubServer;
import io.argos.testsupport.StubServer.Stub;
import io.argos.testsupport.TestDb;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;

/**
 * Résilience de la collecte (tâche 2.5) : curseur durable (COL-05, QS-07), nouvelles tentatives
 * bornées et {@code Retry-After} (COL-06, QS-03), état {@code DEGRADED} (COL-07). Les pannes
 * (429, 5xx, délai dépassé, 401, arrêt brutal) sont simulées ; les attentes sont enregistrées
 * sans être subies.
 */
@QuarkusTest
@QuarkusTestResource(StubServer.class)
class ResilienceTest {

    private static final String CSV = "/osv-export/Maven/modified_id.csv";

    @Inject
    TestDb baseDeTest;

    @Inject
    SourceSynchronizer synchronizer;

    @Inject
    SyncStore store;

    @Inject
    RecordingSleeper sleeper;

    @Inject
    CrashingFeed crashingFeed;

    @BeforeEach
    void reset() {
        baseDeTest.reset();
    }

    private static String advisory(String id, String modified) {
        return Fixtures.osv(id, modified, "Maven", "org.exemple:" + id.toLowerCase(), "pkg:maven/org.exemple/" + id.toLowerCase(),
                "0", "9.0.0");
    }

    @Test
    void col06S1LimitationDeDebitAvecRetryAfter() {
        Fixtures.stubOsv("GHSA-R1", "2026-11-01T00:00:00Z", advisory("GHSA-R1", "2026-11-01T00:00:00Z"));
        StubServer.programmer(CSV, new Stub(429, "", Map.of("Retry-After", "7")),
                Stub.succes("2026-11-01T00:00:00Z,GHSA-R1"));

        SourceSynchronizer.SyncReport report = synchronizer.synchronize("OSV");

        assertEquals("SUCCEEDED", report.state());
        assertEquals(2, report.attempts());
        List<RecordingSleeper.Wait> waits = sleeper.waits();
        assertEquals(1, waits.size());
        assertEquals(Duration.ofSeconds(7), waits.get(0).duration());
        List<StubServer.Received> requests = StubServer.received(CSV);
        assertEquals(2, requests.size());
        assertFalse(requests.get(1).instant().isBefore(waits.get(0).instant()),
                "la seconde requête ne part qu'après l'attente imposée par Retry-After");
        assertEquals(1, baseDeTest.count("SELECT count(*) FROM sync_attempt WHERE http_status = 429 AND retry_after_seconds = 7"));
    }

    @Test
    void col06S2NombreMaximalDeTentativesAtteintCurseurInchange() {
        Fixtures.stubOsv("GHSA-R2", "2026-11-01T00:00:00Z", advisory("GHSA-R2", "2026-11-01T00:00:00Z"));
        synchronizer.synchronize("OSV");
        String cursor = store.cursor("OSV").orElseThrow();
        StubServer.programmer(CSV, Stub.status(503));

        SourceSynchronizer.SyncReport report = synchronizer.synchronize("OSV");

        assertEquals("FAILED", report.state());
        assertEquals(5, report.attempts());
        assertEquals(1 + 5, StubServer.received(CSV).size());
        assertEquals(4, sleeper.waits().size());
        assertEquals(cursor, store.cursor("OSV").orElseThrow());
        assertEquals("EXHAUSTED", baseDeTest.string("SELECT error_kind FROM sync_run WHERE state = 'FAILED'"));
        assertEquals(5, baseDeTest.count("SELECT count(*) FROM sync_attempt WHERE error_kind = 'TRANSIENT'"));
    }

    @Test
    void delaiDepasseTraiteCommeErreurTransitoire() {
        Fixtures.stubOsv("GHSA-R3", "2026-11-01T00:00:00Z", advisory("GHSA-R3", "2026-11-01T00:00:00Z"));
        StubServer.programmer(CSV, Stub.retenue(200, "2026-11-01T00:00:00Z,GHSA-R3", Map.of()),
                Stub.succes("2026-11-01T00:00:00Z,GHSA-R3"));

        SourceSynchronizer.SyncReport report = synchronizer.synchronize("OSV");

        assertEquals("SUCCEEDED", report.state());
        assertEquals(2, report.attempts());
        assertEquals(1, report.created());
    }

    @Test
    void col06S3ErreurPermanenteSansNouvelleTentative() {
        StubServer.programmer(CSV, Stub.status(401));

        SourceSynchronizer.SyncReport report = synchronizer.synchronize("OSV");

        assertEquals("FAILED", report.state());
        assertEquals(1, report.attempts());
        assertTrue(sleeper.waits().isEmpty());
        assertEquals(1, StubServer.received(CSV).size());
        assertEquals("PERMANENT", baseDeTest.string("SELECT error_kind FROM sync_run"));
        assertEquals(1, baseDeTest.count("SELECT count(*) FROM ops_event WHERE kind = 'SYNC_PERMANENT_ERROR'"));
    }

    @Test
    void col07S1EtS2SourceDegradeePuisRetourALaNormale() {
        StubServer.programmer(CSV, Stub.status(403));
        synchronizer.synchronize("OSV");
        assertEquals("FAILING", baseDeTest.string("SELECT state FROM source WHERE id = 'OSV'"));
        synchronizer.synchronize("OSV");
        assertEquals("FAILING", baseDeTest.string("SELECT state FROM source WHERE id = 'OSV'"));
        synchronizer.synchronize("OSV");
        assertEquals("DEGRADED", baseDeTest.string("SELECT state FROM source WHERE id = 'OSV'"));
        assertEquals(1, baseDeTest.count("SELECT count(*) FROM ops_event WHERE kind = 'SOURCE_DEGRADED' AND occurred_at IS NOT NULL"));

        Api.enTantQue("alice")
                .when().get("/api/v1/ops/sources")
                .then().statusCode(200).body("find { it.sourceId == 'OSV' }.state", equalTo("DEGRADED"))
                .body("find { it.sourceId == 'OSV' }.consecutiveFailures", equalTo(3));
        Api.compte("jules");
        Api.enTantQue("jules").when().get("/api/v1/ops/sources").then().statusCode(403);

        Fixtures.stubOsv("GHSA-R4", "2026-11-01T00:00:00Z", advisory("GHSA-R4", "2026-11-01T00:00:00Z"));
        SourceSynchronizer.SyncReport report = synchronizer.synchronize("OSV");

        assertEquals("SUCCEEDED", report.state());
        assertEquals("NOMINAL", baseDeTest.string("SELECT state FROM source WHERE id = 'OSV'"));
        assertEquals(1, baseDeTest.count("SELECT count(*) FROM ops_event WHERE kind = 'SOURCE_RECOVERED'"));
        assertEquals(1, baseDeTest.count("SELECT count(*) FROM signal WHERE external_id = 'GHSA-R4'"));
    }

    @Test
    void col05S1EchecApresReceptionPartielle() {
        Fixtures.stubOsv("GHSA-P1", "2026-11-01T00:00:00Z", advisory("GHSA-P1", "2026-11-01T00:00:00Z"),
                "GHSA-P2", "2026-11-02T00:00:00Z", advisory("GHSA-P2", "2026-11-02T00:00:00Z"),
                "GHSA-P3", "2026-11-03T00:00:00Z", advisory("GHSA-P3", "2026-11-03T00:00:00Z"));
        StubServer.programmer("/osv-api/v1/vulns/GHSA-P2", Stub.status(500));

        SourceSynchronizer.SyncReport failed = synchronizer.synchronize("OSV");

        assertEquals("FAILED", failed.state());
        assertEquals("2026-11-01T00:00:00Z|GHSA-P1", store.cursor("OSV").orElseThrow());
        assertEquals(1, baseDeTest.count("SELECT count(*) FROM signal"));

        StubServer.programmer("/osv-api/v1/vulns/GHSA-P2", Stub.succes(advisory("GHSA-P2", "2026-11-02T00:00:00Z")));
        SourceSynchronizer.SyncReport resumed = synchronizer.synchronize("OSV");

        assertEquals("SUCCEEDED", resumed.state());
        assertEquals(2, resumed.created());
        assertEquals(3, baseDeTest.count("SELECT count(*) FROM signal"));
        assertEquals(3, baseDeTest.count("SELECT count(*) FROM signal_version"));
        assertEquals(1, StubServer.received("/osv-api/v1/vulns/GHSA-P1").size(), "aucun avis enregistré n'est relu");
        assertEquals("2026-11-03T00:00:00Z|GHSA-P3", store.cursor("OSV").orElseThrow());
    }

    @Test
    void col05S2ArretBrutalDuBackendPuisReprise() {
        baseDeTest.exec("INSERT INTO source(id, label) VALUES ('CRASHTEST', 'source de test')");
        List<FeedEntry> entries = List.of(
                new FeedEntry("GHSA-C1", Instant.parse("2026-11-01T00:00:00Z")),
                new FeedEntry("GHSA-C2", Instant.parse("2026-11-02T00:00:00Z")),
                new FeedEntry("GHSA-C3", Instant.parse("2026-11-03T00:00:00Z")),
                new FeedEntry("GHSA-C4", Instant.parse("2026-11-04T00:00:00Z")));
        crashingFeed.configure(entries, entries.stream().map(entree -> advisory(entree.externalId(), entree.modified().toString()))
                .toList(), 2);

        assertThrows(CrashingFeed.SimulatedCrash.class, () -> synchronizer.synchronize(CrashingFeed.SOURCE_ID));
        assertEquals("RUNNING", baseDeTest.string("SELECT state FROM sync_run WHERE source_id = 'CRASHTEST'"));
        assertEquals(2, baseDeTest.count("SELECT count(*) FROM signal WHERE source = 'CRASHTEST'"));
        String runId = baseDeTest.string("SELECT id::text FROM sync_run WHERE source_id = 'CRASHTEST'");

        // Redémarrage : les traitements RUNNING expirés sont reclassés RETRYABLE.
        assertEquals(1, store.recoverExpired(Duration.ZERO));
        assertEquals("RETRYABLE", baseDeTest.string("SELECT state FROM sync_run WHERE source_id = 'CRASHTEST'"));

        SourceSynchronizer.SyncReport report = synchronizer.synchronize(CrashingFeed.SOURCE_ID);

        assertEquals("SUCCEEDED", report.state());
        assertEquals(runId, baseDeTest.string("SELECT id::text FROM sync_run WHERE source_id = 'CRASHTEST'"));
        assertEquals(4, baseDeTest.count("SELECT count(*) FROM signal WHERE source = 'CRASHTEST'"));
        assertEquals(4, baseDeTest.count(
                "SELECT count(*) FROM signal_version v JOIN signal s ON s.id = v.signal_id WHERE s.source = 'CRASHTEST'"));
        assertEquals(List.of("GHSA-C1", "GHSA-C2", "GHSA-C3", "GHSA-C4"), crashingFeed.fetched());
    }

    /** Une panne d'accès aux données (IllegalStateException) termine le traitement en échec, jamais RUNNING. */
    @Test
    void panneAccesDonneesTermineLeTraitementEnEchec() {
        baseDeTest.exec("INSERT INTO source(id, label) VALUES ('CRASHTEST', 'source de test')");
        List<FeedEntry> entries = List.of(new FeedEntry("GHSA-U1", Instant.parse("2026-11-01T00:00:00Z")));
        crashingFeed.configure(entries, entries.stream().map(entree -> advisory(entree.externalId(), entree.modified().toString()))
                .toList(), -1);
        crashingFeed.failOnceWith(new IllegalStateException("panne simulée d'accès aux données"));

        SourceSynchronizer.SyncReport failed = synchronizer.synchronize(CrashingFeed.SOURCE_ID);

        assertEquals("FAILED", failed.state());
        assertEquals(0, baseDeTest.count("SELECT count(*) FROM sync_run WHERE source_id = 'CRASHTEST' AND state = 'RUNNING'"));
        assertEquals("PERMANENT", baseDeTest.string("SELECT error_kind FROM sync_run WHERE source_id = 'CRASHTEST'"));
        assertEquals("FAILING", baseDeTest.string("SELECT state FROM source WHERE id = 'CRASHTEST'"));
        assertEquals(1, baseDeTest.count("SELECT count(*) FROM sync_attempt a JOIN sync_run r ON r.id = a.run_id "
                + "WHERE r.source_id = 'CRASHTEST' AND a.error_kind = 'PERMANENT'"));

        // La source n'est pas bloquée : un nouveau lancement est accepté et réussit.
        SourceSynchronizer.SyncReport retried = synchronizer.synchronize(CrashingFeed.SOURCE_ID);
        assertEquals("SUCCEEDED", retried.state());
        assertEquals("NOMINAL", baseDeTest.string("SELECT state FROM source WHERE id = 'CRASHTEST'"));
    }

    /**
     * Une erreur imprévue (autre qu'une panne d'accès aux données) termine aussi le traitement en échec permanent,
     * jamais RUNNING, puis est relancée à l'appelant (COL-11, frontière d'erreur d'arc42 §8.5).
     */
    @Test
    void erreurImprevueTermineLeTraitementEnEchecPuisEstRelancee() {
        baseDeTest.exec("INSERT INTO source(id, label) VALUES ('CRASHTEST', 'source de test')");
        List<FeedEntry> entries = List.of(new FeedEntry("GHSA-U2", Instant.parse("2026-11-01T00:00:00Z")));
        crashingFeed.configure(entries, entries.stream().map(entree -> advisory(entree.externalId(), entree.modified().toString()))
                .toList(), -1);
        crashingFeed.failOnceWith(new UnsupportedOperationException("erreur imprévue simulée"));

        assertThrows(UnsupportedOperationException.class, () -> synchronizer.synchronize(CrashingFeed.SOURCE_ID));

        assertEquals(0, baseDeTest.count("SELECT count(*) FROM sync_run WHERE source_id = 'CRASHTEST' AND state = 'RUNNING'"));
        assertEquals("PERMANENT", baseDeTest.string("SELECT error_kind FROM sync_run WHERE source_id = 'CRASHTEST'"));
        assertEquals("FAILING", baseDeTest.string("SELECT state FROM source WHERE id = 'CRASHTEST'"));
        assertTrue(baseDeTest.string("SELECT last_error FROM sync_run WHERE source_id = 'CRASHTEST'")
                .contains("UnsupportedOperationException"), "motif : type de l'erreur");
        assertEquals("SUCCEEDED", synchronizer.synchronize(CrashingFeed.SOURCE_ID).state(), "la source n'est pas bloquée");
    }

    /** Un traitement RUNNING sans activité depuis la durée d'expiration ne bloque pas un nouveau lancement. */
    @Test
    void traitementRunningExpireRepris() {
        baseDeTest.exec("INSERT INTO source(id, label) VALUES ('CRASHTEST', 'source de test')");
        List<FeedEntry> entries = List.of(new FeedEntry("GHSA-E1", Instant.parse("2026-11-01T00:00:00Z")));
        crashingFeed.configure(entries, entries.stream().map(entree -> advisory(entree.externalId(), entree.modified().toString()))
                .toList(), 0);
        assertThrows(CrashingFeed.SimulatedCrash.class, () -> synchronizer.synchronize(CrashingFeed.SOURCE_ID));
        assertEquals("RUNNING", synchronizer.synchronize(CrashingFeed.SOURCE_ID).state(), "traitement récent : toujours en cours");

        baseDeTest.exec("UPDATE sync_run SET heartbeat_at = now() - interval '2 hours' WHERE source_id = 'CRASHTEST'");
        SourceSynchronizer.SyncReport report = synchronizer.synchronize(CrashingFeed.SOURCE_ID);

        assertEquals("SUCCEEDED", report.state());
        assertEquals(1, baseDeTest.count("SELECT count(*) FROM signal WHERE source = 'CRASHTEST'"));
    }

    /** Un Retry-After lointain est borné par le délai maximal (argos.ingestion.backoff-max). */
    @Test
    void retryAfterBorneParLeDelaiMaximal() {
        Fixtures.stubOsv("GHSA-R9", "2026-11-01T00:00:00Z", advisory("GHSA-R9", "2026-11-01T00:00:00Z"));
        StubServer.programmer(CSV, new Stub(429, "", Map.of("Retry-After", "86400")),
                Stub.succes("2026-11-01T00:00:00Z,GHSA-R9"));

        SourceSynchronizer.SyncReport report = synchronizer.synchronize("OSV");

        assertEquals("SUCCEEDED", report.state());
        assertEquals(List.of(Duration.ofMinutes(5)), sleeper.waits().stream().map(RecordingSleeper.Wait::duration).toList());
        assertEquals(1, baseDeTest.count("SELECT count(*) FROM sync_attempt WHERE http_status = 429 AND retry_after_seconds = 86400"));
    }
}
