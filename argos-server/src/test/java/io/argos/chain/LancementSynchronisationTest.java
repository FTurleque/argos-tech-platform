package io.argos.chain;

import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import jakarta.inject.Inject;

import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.argos.ingestion.SourceSynchronizer;
import io.argos.testsupport.Api;
import io.argos.testsupport.Fixtures;
import io.argos.testsupport.RecordingSleeper;
import io.argos.testsupport.StubServer;
import io.argos.testsupport.StubServer.Stub;
import io.argos.testsupport.TestDb;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;

/**
 * Lancement d'une synchronisation par l'exploitant (COL-12, changement {@code rendre-ingestion-asynchrone}) : réponse
 * 202 avant la fin du traitement, refus d'un second lancement, exclusivité pendant l'attente d'une nouvelle tentative.
 * Un serveur lent est simulé par une réponse retenue ; aucune attente réelle hors des attentes bornées par Awaitility.
 */
@QuarkusTest
@QuarkusTestResource(StubServer.class)
class LancementSynchronisationTest {

    private static final String CSV = "/osv-export/Maven/modified_id.csv";
    private static final String LANCEMENT = "/api/v1/ops/sources/OSV/synchronizations";
    private static final String MODIFIE = "2026-11-20T00:00:00Z";
    private static final Duration ATTENTE_MAX = Duration.ofSeconds(30);

    @Inject
    TestDb baseDeTest;

    @Inject
    SourceSynchronizer synchronizer;

    @Inject
    RecordingSleeper sleeper;

    @BeforeEach
    void reset() {
        baseDeTest.reset();
    }

    @AfterEach
    void libererLeServeurSimule() {
        StubServer.liberer();
        Awaitility.await().atMost(ATTENTE_MAX).until(() -> baseDeTest.count(
                "SELECT count(*) FROM sync_run WHERE state = 'RUNNING'") == 0);
    }

    private static String avis(String id) {
        return Fixtures.osv(id, MODIFIE, "Maven", "org.exemple:lancement", "pkg:maven/org.exemple/lancement", "0", "2.0.0");
    }

    /** COL-12-S1 : 202 et identifiant du traitement pendant que la source tarde ; le traitement se termine en fond. */
    @Test
    void col12S1LancementAccepteAvantLaFinDuTraitement() {
        Fixtures.stubOsv("GHSA-L1", MODIFIE, avis("GHSA-L1"));
        StubServer.programmer(CSV, Stub.retenue(200, MODIFIE + ",GHSA-L1", Map.of()));

        String runId = Api.enTantQue("alice").when().post(LANCEMENT)
                .then().statusCode(202).body("sourceId", equalTo("OSV")).body("runId", notNullValue())
                .extract().path("runId");

        assertEquals("RUNNING", baseDeTest.string("SELECT state FROM sync_run WHERE id = ?::uuid", runId));
        StubServer.liberer();
        Awaitility.await().atMost(ATTENTE_MAX)
                .until(() -> "SUCCEEDED".equals(baseDeTest.string("SELECT state FROM sync_run WHERE id = ?::uuid", runId)));
        assertEquals(1, baseDeTest.count("SELECT count(*) FROM signal WHERE external_id = 'GHSA-L1'"));
        Api.enTantQue("alice").when().get("/api/v1/ops/sources")
                .then().statusCode(200).body("find { it.sourceId == 'OSV' }.state", equalTo("NOMINAL"));
    }

    /** COL-12-S2 : un second lancement pendant le traitement est refusé (409) sans créer de traitement. */
    @Test
    void col12S2SecondLancementRefusePendantLeTraitement() {
        Fixtures.stubOsv("GHSA-L2", MODIFIE, avis("GHSA-L2"));
        StubServer.programmer(CSV, Stub.retenue(200, MODIFIE + ",GHSA-L2", Map.of()));
        Api.enTantQue("alice").when().post(LANCEMENT).then().statusCode(202);

        Api.enTantQue("alice").when().post(LANCEMENT)
                .then().statusCode(409).body("code", equalTo("SYNCHRONISATION_EN_COURS"));

        assertEquals(1, baseDeTest.count("SELECT count(*) FROM sync_run WHERE source_id = 'OSV'"));
    }

    /**
     * COL-12-S3 (AUD-PER-27) : pendant l'attente d'une nouvelle tentative, la synchronisation planifiée et le lancement
     * manuel trouvent le traitement en cours ; un seul traitement {@code RUNNING} existe, puis la tentative reprend.
     */
    @Test
    void col12S3AttenteDeRepriseExclusive() {
        Fixtures.stubOsv("GHSA-L3", MODIFIE, avis("GHSA-L3"));
        StubServer.programmer(CSV, Stub.status(503), Stub.succes(MODIFIE + ",GHSA-L3"));
        List<String> etatsPlanifies = new ArrayList<>();
        List<Integer> statutsManuels = new ArrayList<>();
        List<Long> traitementsEnCours = new ArrayList<>();
        sleeper.pendantAttente(() -> {
            etatsPlanifies.add(synchronizer.synchronize("OSV").state());
            statutsManuels.add(Api.enTantQue("alice").when().post(LANCEMENT).then().extract().statusCode());
            traitementsEnCours.add(baseDeTest.count("SELECT count(*) FROM sync_run WHERE source_id = 'OSV' AND state = 'RUNNING'"));
        });

        SourceSynchronizer.SyncReport rapport = synchronizer.synchronize("OSV");

        assertEquals(List.of("RUNNING"), etatsPlanifies, "synchronisation planifiée sautée pendant l'attente");
        assertEquals(List.of(409), statutsManuels, "lancement manuel refusé pendant l'attente");
        assertEquals(List.of(1L), traitementsEnCours);
        assertEquals("SUCCEEDED", rapport.state());
        assertEquals(2, rapport.attempts());
        assertEquals(1, baseDeTest.count("SELECT count(*) FROM sync_run WHERE source_id = 'OSV'"));
    }
}
