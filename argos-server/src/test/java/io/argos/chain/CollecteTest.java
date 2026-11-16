package io.argos.chain;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import jakarta.inject.Inject;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.argos.ingestion.AdvisorySink;
import io.argos.ingestion.RawAdvisory;
import io.argos.ingestion.SourceSynchronizer;
import io.argos.ingestion.osv.OsvNormalizer;
import io.argos.signal.SignalQueries;
import io.argos.signal.SignalRecorder;
import io.argos.testsupport.Fixtures;
import io.argos.testsupport.Api;
import io.argos.testsupport.StubServer;
import io.argos.testsupport.TestDb;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;

/**
 * Collecte des avis OSV contre une source simulée et PostgreSQL réel (tâches 2.1, 2.2, 2.3, 2.6) :
 * COL-01, COL-02, COL-03, COL-08.
 */
@QuarkusTest
@QuarkusTestResource(StubServer.class)
class CollecteTest {

    @Inject
    TestDb db;

    @Inject
    SourceSynchronizer synchronizer;

    @Inject
    SignalQueries signals;

    @Inject
    SignalRecorder recorder;

    @BeforeEach
    void reset() {
        db.reset();
    }

    @Test
    void migrationImposeLUniciteSourceEtIdentifiantExterne() {
        String insert = """
                INSERT INTO signal(id, source, external_id, modified_at, collected_at, updated_at, content_hash, affected,
                                   current_version_id)
                VALUES ('%s', 'OSV', 'GHSA-dup', now(), now(), now(), repeat('a', 64), '[]', '%s')
                """;
        String versions = """
                INSERT INTO signal_version(id, signal_id, version_no, content_hash, source_modified_at, collected_at, raw_content)
                VALUES ('%s', '%s', 1, repeat('a', 64), now(), now(), '{}')
                """;
        UUID s1 = UUID.randomUUID();
        UUID v1 = UUID.randomUUID();
        db.exec("BEGIN; " + insert.formatted(s1, v1) + "; " + versions.formatted(v1, s1) + "; COMMIT;");
        UUID s2 = UUID.randomUUID();
        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> db.exec(insert.formatted(s2, UUID.randomUUID())));
        assertTrue(e.getCause().getMessage().contains("signal_source_external_uk"), e.getCause().getMessage());
    }

    @Test
    void col01S1EtCol02S1NouvelAvisCollecteEtProvenanceConsultable() {
        String raw = Fixtures.resource("osv/GHSA-jfh8-c2jp-5v3q.json");
        Fixtures.stubOsv("GHSA-jfh8-c2jp-5v3q", "2025-10-22T19:37:02.616807Z", raw);

        SourceSynchronizer.SyncReport report = synchronizer.synchronize("OSV");

        assertEquals("SUCCEEDED", report.state());
        assertEquals(1, report.created());
        SignalQueries.SignalView signal = signals.findByExternalId("OSV", "GHSA-jfh8-c2jp-5v3q").orElseThrow();
        assertTrue(signal.affected().stream()
                .anyMatch(p -> p.packageKey().equals("pkg:maven/org.apache.logging.log4j/log4j-core")
                        && p.ranges().stream().anyMatch(r -> "2.15.0".equals(r.fixed()))));

        Api.en("alice")
                .when().get("/api/v1/signals/" + signal.id())
                .then().statusCode(200)
                .body("signal.source", equalTo("OSV"))
                .body("signal.externalId", equalTo("GHSA-jfh8-c2jp-5v3q"))
                .body("signal.collectedAt", notNullValue())
                .body("signal.currentVersionId", equalTo(signal.currentVersionId().toString()))
                .body("versions.versionNo", hasItem(1));
        String body = Api.en("alice")
                .when().get("/api/v1/signals/" + signal.id() + "/versions/" + signal.currentVersionId() + "/raw")
                .then().statusCode(200).extract().asString();
        assertEquals(raw, body);
        given().when().get("/api/v1/signals/" + signal.id()).then().statusCode(401);
    }

    @Test
    void col01S2AucunNouvelAvis() {
        String json = Fixtures.osv("GHSA-0001", "2026-11-01T00:00:00Z", "Maven", "org.exemple:lib",
                "pkg:maven/org.exemple/lib", "0", "1.3.0");
        Fixtures.stubOsv("GHSA-0001", "2026-11-01T00:00:00Z", json);
        synchronizer.synchronize("OSV");
        String updatedAt = db.string("SELECT updated_at::text FROM signal");

        SourceSynchronizer.SyncReport second = synchronizer.synchronize("OSV");

        assertEquals("SUCCEEDED", second.state());
        assertEquals(0, second.created() + second.updated() + second.unchanged());
        assertEquals(1, db.count("SELECT count(*) FROM signal"));
        assertEquals(updatedAt, db.string("SELECT updated_at::text FROM signal"));
        assertEquals(1, StubServer.received("/osv-api/v1/vulns/GHSA-0001").size());
    }

    @Test
    void col03S1EtS2RejeuIdempotentEtModificationHistorisee() {
        String v1 = Fixtures.osv("GHSA-0002", "2026-11-01T00:00:00Z", "Maven", "org.exemple:lib",
                "pkg:maven/org.exemple/lib", "0", "1.3.0");
        AdvisorySink.Outcome first = record(v1);
        AdvisorySink.Outcome replay = record(v1);
        assertEquals(AdvisorySink.Outcome.CREATED, first);
        assertEquals(AdvisorySink.Outcome.UNCHANGED, replay);
        assertEquals(1, db.count("SELECT count(*) FROM signal_version"));

        String v2 = Fixtures.osv("GHSA-0002", "2026-11-05T00:00:00Z", "Maven", "org.exemple:lib",
                "pkg:maven/org.exemple/lib", "0", "1.4.0");
        Fixtures.stubOsv("GHSA-0002", "2026-11-05T00:00:00Z", v2);
        SourceSynchronizer.SyncReport report = synchronizer.synchronize("OSV");

        assertEquals(1, report.updated());
        assertEquals(1, db.count("SELECT count(*) FROM signal"));
        SignalQueries.SignalView signal = signals.findByExternalId("OSV", "GHSA-0002").orElseThrow();
        assertEquals("1.4.0", signal.affected().get(0).ranges().get(0).fixed());
        List<SignalQueries.SignalVersionView> history = signals.versions(signal.id());
        assertEquals(2, history.size());
        assertTrue(signals.rawContent(signal.id(), history.get(0).id()).orElseThrow().contains("1.3.0"));

        String older = Fixtures.osv("GHSA-0002", "2026-10-01T00:00:00Z", "Maven", "org.exemple:lib",
                "pkg:maven/org.exemple/lib", "0", "1.0.0");
        assertEquals(AdvisorySink.Outcome.IGNORED_OLDER, record(older));
        assertEquals("1.4.0", signals.find(signal.id()).orElseThrow().affected().get(0).ranges().get(0).fixed());
    }

    @Test
    void col03S3CollectesConcurrentesDuMemeAvis() throws Exception {
        String json = Fixtures.osv("GHSA-0003", "2026-11-01T00:00:00Z", "Maven", "org.exemple:lib",
                "pkg:maven/org.exemple/lib", "0", "1.3.0");
        int threads = 4;
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        try {
            List<Future<AdvisorySink.Outcome>> results = new java.util.ArrayList<>();
            for (int i = 0; i < threads; i++) {
                results.add(pool.submit(() -> {
                    start.await();
                    return record(json);
                }));
            }
            start.countDown();
            long created = 0;
            for (Future<AdvisorySink.Outcome> r : results) {
                if (r.get(30, TimeUnit.SECONDS) == AdvisorySink.Outcome.CREATED) {
                    created++;
                }
            }
            assertEquals(1, created);
        } finally {
            pool.shutdownNow();
        }
        assertEquals(1, db.count("SELECT count(*) FROM signal WHERE external_id = 'GHSA-0003'"));
        assertEquals(1, db.count("SELECT count(*) FROM signal_version"));
    }

    @Test
    void col08S1EtCol02S2AvisMalformeMisALEcartSansBloquerLeLot() {
        String a = Fixtures.osv("GHSA-000A", "2026-11-01T00:00:00Z", "Maven", "org.exemple:a", "pkg:maven/org.exemple/a",
                "0", "1.0.0");
        String b = "{\"summary\":\"avis sans identifiant\",\"modified\":\"2026-11-02T00:00:00Z\"}";
        String c = Fixtures.osv("GHSA-000C", "2026-11-03T00:00:00Z", "Maven", "org.exemple:c", "pkg:maven/org.exemple/c",
                "0", "1.0.0");
        Fixtures.stubOsv("GHSA-000A", "2026-11-01T00:00:00Z", a, "GHSA-000B", "2026-11-02T00:00:00Z", b,
                "GHSA-000C", "2026-11-03T00:00:00Z", c);

        SourceSynchronizer.SyncReport report = synchronizer.synchronize("OSV");

        assertEquals("SUCCEEDED", report.state());
        assertEquals(2, report.created());
        assertEquals(1, report.rejected());
        assertEquals(0, db.count("SELECT count(*) FROM signal WHERE external_id = 'GHSA-000B'"));
        assertEquals("identifiant externe absent", db.string("SELECT reason FROM ingestion_failure WHERE external_id = 'GHSA-000B'"));
        assertEquals(b, db.string("SELECT raw_content FROM ingestion_failure"));
        assertEquals("OSV", db.string("SELECT source_id FROM ingestion_failure"));
        Api.en("alice")
                .when().get("/api/v1/ops/sources/OSV/rejected")
                .then().statusCode(200).body("[0].externalId", equalTo("GHSA-000B"))
                .body("[0].reason", equalTo("identifiant externe absent"));
    }

    private AdvisorySink.Outcome record(String json) {
        return QuarkusTransaction.requiringNew().call(
                () -> recorder.record("OSV", OsvNormalizer.normalize(new RawAdvisory("x", json))));
    }
}
