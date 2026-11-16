package io.argos.chain;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.UUID;

import jakarta.inject.Inject;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.argos.ingestion.SourceSynchronizer;
import io.argos.testsupport.Fixtures;
import io.argos.testsupport.HeaderCallerResolver;
import io.argos.testsupport.StubServer;
import io.argos.testsupport.StubServer.Stub;
import io.argos.testsupport.TestDb;
import io.argos.vulnintel.Enricher;
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
    TestDb db;

    @Inject
    SourceSynchronizer synchronizer;

    @Inject
    Enricher enricher;

    @BeforeEach
    void reset() {
        db.reset();
    }

    private UUID collectLog4Shell() {
        Fixtures.stubOsv(LOG4SHELL, "2025-10-22T19:37:02.616807Z", Fixtures.resource("osv/GHSA-jfh8-c2jp-5v3q.json"));
        synchronizer.synchronize("OSV");
        return db.uuid("SELECT id FROM signal WHERE external_id = '" + LOG4SHELL + "'");
    }

    @Test
    void col04S1ExploitationAvereeSignaleeParKev() {
        StubServer.on("/kev.json", Stub.ok(Fixtures.resource("enrichment/kev-extrait.json")));

        UUID signal = collectLog4Shell();

        assertTrue(enricher.exploited(signal));
        given().header(HeaderCallerResolver.USER, "alice").when().get("/api/v1/signals/" + signal)
                .then().statusCode(200)
                .body("exploited", equalTo(true))
                .body("enrichments.find { it.source == 'KEV' }.reference", equalTo("CVE-2021-44228"));
    }

    @Test
    void col04S2NvdRattacheAuSignalExistant() {
        StubServer.on("/nvd?cveId=CVE-2021-44228", Stub.ok(Fixtures.resource("enrichment/nvd-CVE-2021-44228.json")));

        UUID signal = collectLog4Shell();

        assertEquals(1, db.count("SELECT count(*) FROM signal"));
        assertEquals(1, db.count("SELECT count(*) FROM enrichment WHERE source = 'NVD' AND signal_id = '" + signal + "'"));
        String data = db.string("SELECT data::text FROM enrichment WHERE source = 'NVD'");
        assertTrue(data.contains("\"baseScore\": 10.0") || data.contains("\"baseScore\":10.0"), data);
    }

    @Test
    void col04S3SourceSecondaireIndisponible() {
        StubServer.on("/epss?cve=CVE-2021-44228", Stub.status(503));

        UUID signal = collectLog4Shell();

        assertEquals(1, db.count("SELECT count(*) FROM signal"));
        assertEquals(0, db.count("SELECT count(*) FROM enrichment WHERE source = 'EPSS'"));
        given().header(HeaderCallerResolver.USER, "alice").when().get("/api/v1/signals/" + signal)
                .then().statusCode(200).body("signal.externalId", equalTo(LOG4SHELL));

        // Au cycle suivant, la source répond : l'enrichissement manquant est rattrapé.
        StubServer.on("/epss?cve=CVE-2021-44228", Stub.ok(Fixtures.resource("enrichment/epss-CVE-2021-44228.json")));
        assertEquals(1, enricher.retryMissing(10));
        assertEquals(1, db.count("SELECT count(*) FROM enrichment WHERE source = 'EPSS'"));
    }

    @Test
    void enrichissementCertFrDepuisLeFluxEtLeContenuJson() {
        StubServer.on("/certfr/feed/", Stub.ok(Fixtures.resource("enrichment/certfr-feed.xml")));
        StubServer.on("/certfr/CERTFR-2026-AVI-1500/json/", Stub.ok(Fixtures.resource("enrichment/CERTFR-2026-AVI-1500.json")));
        String json = Fixtures.osv("GHSA-HTTPD", "2026-11-12T00:00:00Z", "Maven", "org.exemple:httpd-wrapper",
                "pkg:maven/org.exemple/httpd-wrapper", "0", "2.0.0", "CVE-2026-109163");
        Fixtures.stubOsv("GHSA-HTTPD", "2026-11-12T00:00:00Z", json);
        StubServer.on("/certfr/feed/", Stub.ok(Fixtures.resource("enrichment/certfr-feed.xml")));

        synchronizer.synchronize("OSV");

        UUID signal = db.uuid("SELECT id FROM signal WHERE external_id = 'GHSA-HTTPD'");
        List<Enricher.EnrichmentView> views = enricher.forSignal(signal);
        assertTrue(views.stream().anyMatch(v -> v.source().equals("CERTFR") && v.reference().equals("CERTFR-2026-AVI-1500")),
                views.toString());
        // Seules des adresses construites à partir des références sont appelées (R-12).
        assertTrue(StubServer.received("/certfr/").stream()
                .allMatch(r -> r.pathAndQuery().equals("/certfr/feed/") || r.pathAndQuery().matches("/certfr/CERTFR-\\d{4}-AVI-\\d+/json/")));
        given().header(HeaderCallerResolver.USER, "alice").when().get("/api/v1/signals/" + signal)
                .then().statusCode(200).body("enrichments.source", hasItem("CERTFR"));
    }
}
