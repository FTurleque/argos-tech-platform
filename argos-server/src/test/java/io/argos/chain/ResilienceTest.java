package io.argos.chain;

import static io.restassured.RestAssured.given;
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
import io.argos.testsupport.HeaderCallerResolver;
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
    TestDb db;

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
        db.reset();
    }

    private static String advisory(String id, String modified) {
        return Fixtures.osv(id, modified, "Maven", "org.exemple:" + id.toLowerCase(), "pkg:maven/org.exemple/" + id.toLowerCase(),
                "0", "9.0.0");
    }

    @Test
    void col06S1LimitationDeDebitAvecRetryAfter() {
        Fixtures.stubOsv("GHSA-R1", "2026-11-01T00:00:00Z", advisory("GHSA-R1", "2026-11-01T00:00:00Z"));
        StubServer.on(CSV, new Stub(429, "", Map.of("Retry-After", "7"), 0),
                Stub.ok("2026-11-01T00:00:00Z,GHSA-R1"));

        SourceSynchronizer.SyncReport report = synchronizer.synchronize("OSV");

        assertEquals("SUCCEEDED", report.state());
        assertEquals(2, report.attempts());
        List<RecordingSleeper.Wait> waits = sleeper.waits();
        assertEquals(1, waits.size());
        assertEquals(Duration.ofSeconds(7), waits.get(0).duration());
        List<StubServer.Received> requests = StubServer.received(CSV);
        assertEquals(2, requests.size());
        assertFalse(requests.get(1).at().isBefore(waits.get(0).at()),
                "la seconde requête ne part qu'après l'attente imposée par Retry-After");
        assertEquals(1, db.count("SELECT count(*) FROM sync_attempt WHERE http_status = 429 AND retry_after_seconds = 7"));
    }

    @Test
    void col06S2NombreMaximalDeTentativesAtteintCurseurInchange() {
        Fixtures.stubOsv("GHSA-R2", "2026-11-01T00:00:00Z", advisory("GHSA-R2", "2026-11-01T00:00:00Z"));
        synchronizer.synchronize("OSV");
        String cursor = store.cursor("OSV").orElseThrow();
        StubServer.on(CSV, Stub.status(503));

        SourceSynchronizer.SyncReport report = synchronizer.synchronize("OSV");

        assertEquals("FAILED", report.state());
        assertEquals(5, report.attempts());
        assertEquals(1 + 5, StubServer.received(CSV).size());
        assertEquals(4, sleeper.waits().size());
        assertEquals(cursor, store.cursor("OSV").orElseThrow());
        assertEquals("EXHAUSTED", db.string("SELECT error_kind FROM sync_run WHERE state = 'FAILED'"));
        assertEquals(5, db.count("SELECT count(*) FROM sync_attempt WHERE error_kind = 'TRANSIENT'"));
    }

    @Test
    void delaiDepasseTraiteCommeErreurTransitoire() {
        Fixtures.stubOsv("GHSA-R3", "2026-11-01T00:00:00Z", advisory("GHSA-R3", "2026-11-01T00:00:00Z"));
        StubServer.on(CSV, new Stub(200, "2026-11-01T00:00:00Z,GHSA-R3", Map.of(), 2_000),
                Stub.ok("2026-11-01T00:00:00Z,GHSA-R3"));

        SourceSynchronizer.SyncReport report = synchronizer.synchronize("OSV");

        assertEquals("SUCCEEDED", report.state());
        assertEquals(2, report.attempts());
        assertEquals(1, report.created());
    }

    @Test
    void col06S3ErreurPermanenteSansNouvelleTentative() {
        StubServer.on(CSV, Stub.status(401));

        SourceSynchronizer.SyncReport report = synchronizer.synchronize("OSV");

        assertEquals("FAILED", report.state());
        assertEquals(1, report.attempts());
        assertTrue(sleeper.waits().isEmpty());
        assertEquals(1, StubServer.received(CSV).size());
        assertEquals("PERMANENT", db.string("SELECT error_kind FROM sync_run"));
        assertEquals(1, db.count("SELECT count(*) FROM ops_event WHERE kind = 'SYNC_PERMANENT_ERROR'"));
    }

    @Test
    void col07S1EtS2SourceDegradeePuisRetourALaNormale() {
        StubServer.on(CSV, Stub.status(403));
        synchronizer.synchronize("OSV");
        assertEquals("FAILING", db.string("SELECT state FROM source WHERE id = 'OSV'"));
        synchronizer.synchronize("OSV");
        assertEquals("FAILING", db.string("SELECT state FROM source WHERE id = 'OSV'"));
        synchronizer.synchronize("OSV");
        assertEquals("DEGRADED", db.string("SELECT state FROM source WHERE id = 'OSV'"));
        assertEquals(1, db.count("SELECT count(*) FROM ops_event WHERE kind = 'SOURCE_DEGRADED' AND occurred_at IS NOT NULL"));

        given().header(HeaderCallerResolver.USER, "ops").header(HeaderCallerResolver.OPERATOR, "true")
                .when().get("/api/v1/ops/sources")
                .then().statusCode(200).body("find { it.sourceId == 'OSV' }.state", equalTo("DEGRADED"))
                .body("find { it.sourceId == 'OSV' }.consecutiveFailures", equalTo(3));
        given().header(HeaderCallerResolver.USER, "alice").when().get("/api/v1/ops/sources").then().statusCode(403);

        Fixtures.stubOsv("GHSA-R4", "2026-11-01T00:00:00Z", advisory("GHSA-R4", "2026-11-01T00:00:00Z"));
        SourceSynchronizer.SyncReport report = synchronizer.synchronize("OSV");

        assertEquals("SUCCEEDED", report.state());
        assertEquals("NOMINAL", db.string("SELECT state FROM source WHERE id = 'OSV'"));
        assertEquals(1, db.count("SELECT count(*) FROM ops_event WHERE kind = 'SOURCE_RECOVERED'"));
        assertEquals(1, db.count("SELECT count(*) FROM signal WHERE external_id = 'GHSA-R4'"));
    }

    @Test
    void col05S1EchecApresReceptionPartielle() {
        Fixtures.stubOsv("GHSA-P1", "2026-11-01T00:00:00Z", advisory("GHSA-P1", "2026-11-01T00:00:00Z"),
                "GHSA-P2", "2026-11-02T00:00:00Z", advisory("GHSA-P2", "2026-11-02T00:00:00Z"),
                "GHSA-P3", "2026-11-03T00:00:00Z", advisory("GHSA-P3", "2026-11-03T00:00:00Z"));
        StubServer.on("/osv-api/v1/vulns/GHSA-P2", Stub.status(500));

        SourceSynchronizer.SyncReport failed = synchronizer.synchronize("OSV");

        assertEquals("FAILED", failed.state());
        assertEquals("2026-11-01T00:00:00Z|GHSA-P1", store.cursor("OSV").orElseThrow());
        assertEquals(1, db.count("SELECT count(*) FROM signal"));

        StubServer.on("/osv-api/v1/vulns/GHSA-P2", Stub.ok(advisory("GHSA-P2", "2026-11-02T00:00:00Z")));
        SourceSynchronizer.SyncReport resumed = synchronizer.synchronize("OSV");

        assertEquals("SUCCEEDED", resumed.state());
        assertEquals(2, resumed.created());
        assertEquals(3, db.count("SELECT count(*) FROM signal"));
        assertEquals(3, db.count("SELECT count(*) FROM signal_version"));
        assertEquals(1, StubServer.received("/osv-api/v1/vulns/GHSA-P1").size(), "aucun avis enregistré n'est relu");
        assertEquals("2026-11-03T00:00:00Z|GHSA-P3", store.cursor("OSV").orElseThrow());
    }

    @Test
    void col05S2ArretBrutalDuBackendPuisReprise() {
        db.exec("INSERT INTO source(id, label) VALUES ('CRASHTEST', 'source de test')");
        List<FeedEntry> entries = List.of(
                new FeedEntry("GHSA-C1", Instant.parse("2026-11-01T00:00:00Z")),
                new FeedEntry("GHSA-C2", Instant.parse("2026-11-02T00:00:00Z")),
                new FeedEntry("GHSA-C3", Instant.parse("2026-11-03T00:00:00Z")),
                new FeedEntry("GHSA-C4", Instant.parse("2026-11-04T00:00:00Z")));
        crashingFeed.configure(entries, entries.stream().map(e -> advisory(e.externalId(), e.modified().toString())).toList(), 2);

        assertThrows(CrashingFeed.SimulatedCrash.class, () -> synchronizer.synchronize(CrashingFeed.SOURCE_ID));
        assertEquals("RUNNING", db.string("SELECT state FROM sync_run WHERE source_id = 'CRASHTEST'"));
        assertEquals(2, db.count("SELECT count(*) FROM signal WHERE source = 'CRASHTEST'"));
        String runId = db.string("SELECT id::text FROM sync_run WHERE source_id = 'CRASHTEST'");

        // Redémarrage : les traitements RUNNING expirés sont reclassés RETRYABLE.
        assertEquals(1, store.recoverExpired(Duration.ZERO));
        assertEquals("RETRYABLE", db.string("SELECT state FROM sync_run WHERE source_id = 'CRASHTEST'"));

        SourceSynchronizer.SyncReport report = synchronizer.synchronize(CrashingFeed.SOURCE_ID);

        assertEquals("SUCCEEDED", report.state());
        assertEquals(runId, db.string("SELECT id::text FROM sync_run WHERE source_id = 'CRASHTEST'"));
        assertEquals(4, db.count("SELECT count(*) FROM signal WHERE source = 'CRASHTEST'"));
        assertEquals(4, db.count("SELECT count(*) FROM signal_version v JOIN signal s ON s.id = v.signal_id WHERE s.source = 'CRASHTEST'"));
        assertEquals(List.of("GHSA-C1", "GHSA-C2", "GHSA-C3", "GHSA-C4"), crashingFeed.fetched());
    }
}
