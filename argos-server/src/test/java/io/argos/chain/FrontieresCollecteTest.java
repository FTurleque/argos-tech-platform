package io.argos.chain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.List;

import jakarta.inject.Inject;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.argos.ingestion.FeedEntry;
import io.argos.ingestion.SourceSynchronizer;
import io.argos.signal.SynchronisationPlanifiee;
import io.argos.testsupport.CrashingFeed;
import io.argos.testsupport.Fixtures;
import io.argos.testsupport.StubServer;
import io.argos.testsupport.TestDb;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;

/**
 * Frontière d'erreur de la collecte des avis (COL-11, AUD-QUA-20, arc42 §8.5) : deux sources dans le même passage,
 * {@code CRASHTEST} (première dans l'ordre des identifiants) et {@code OSV} (servie par la source simulée). Une erreur
 * de la première n'empêche pas la synchronisation de la seconde ; une erreur imprévue termine la première en échec
 * permanent puis est relancée par l'échéance.
 */
@QuarkusTest
@QuarkusTestResource(StubServer.class)
class FrontieresCollecteTest {

    private static final Instant ECHEANCE = Instant.parse("2026-11-24T08:00:00Z");
    private static final String MODIFIE = "2026-11-20T00:00:00Z";

    @Inject
    TestDb baseDeTest;

    @Inject
    SynchronisationPlanifiee planification;

    @Inject
    SourceSynchronizer synchroniseur;

    @Inject
    CrashingFeed sourceDeTest;

    @BeforeEach
    void reinitialiser() {
        baseDeTest.reset();
        baseDeTest.exec("INSERT INTO source(id, label) VALUES ('" + CrashingFeed.SOURCE_ID + "', 'source de test')");
        Fixtures.stubOsv("GHSA-FR-OSV", MODIFIE, Fixtures.osv("GHSA-FR-OSV", MODIFIE, "Maven", "org.exemple:lib",
                "pkg:maven/org.exemple/lib", "0", "1.3.0"));
    }

    private void servirSourceDeTest(String identifiant, String purl) {
        List<FeedEntry> entrees = List.of(new FeedEntry(identifiant, Instant.parse(MODIFIE)));
        sourceDeTest.configure(entrees, List.of(Fixtures.osv(identifiant, MODIFIE, "npm", "zz", purl, "0", "1.1.0")), -1);
    }

    /** COL-11-S1 : un avis au PURL mal encodé est enregistré par écosystème et nom ; la seconde source est synchronisée. */
    @Test
    void col11S1AvisAuPurlMalEncode() {
        servirSourceDeTest("GHSA-FR-ZZ", "pkg:npm/%zz@1.0.0");

        SynchronisationPlanifiee.Bilan bilan = planification.synchroniserToutes(ECHEANCE);

        assertEquals(List.of(CrashingFeed.SOURCE_ID, "OSV"),
                bilan.rapports().stream().map(SourceSynchronizer.SyncReport::sourceId).toList());
        assertTrue(bilan.rapports().stream().allMatch(rapport -> "SUCCEEDED".equals(rapport.state())), bilan.toString());
        assertEquals(0, baseDeTest.count("SELECT count(*) FROM sync_run WHERE state = 'RUNNING'"));
        assertEquals(1, baseDeTest.count("SELECT count(*) FROM signal WHERE external_id = 'GHSA-FR-OSV'"));
        assertEquals("pkg:npm/zz", baseDeTest.string("SELECT p.package_key FROM signal_package p JOIN signal s "
                + "ON s.id = p.signal_id WHERE s.external_id = 'GHSA-FR-ZZ'"), "avis indexé par écosystème et nom");
        assertTrue(bilan.erreursImprevues().isEmpty());
    }

    /** COL-11-S2 : une erreur imprévue termine la première source en échec permanent, la seconde est synchronisée. */
    @Test
    void col11S2ErreurImprevuePendantLaSynchronisation() {
        servirSourceDeTest("GHSA-FR-IMP", "pkg:npm/zz@1.0.0");
        IllegalArgumentException imprevue = new IllegalArgumentException("erreur imprévue simulée");
        sourceDeTest.failOnceWith(imprevue);

        SynchronisationPlanifiee.Bilan bilan = planification.synchroniserToutes(ECHEANCE);

        assertEquals(List.of(CrashingFeed.SOURCE_ID), bilan.enErreur());
        assertEquals(List.of("OSV"), bilan.rapports().stream().map(SourceSynchronizer.SyncReport::sourceId).toList());
        assertEquals("SUCCEEDED", bilan.rapports().get(0).state());
        assertEquals("FAILED|PERMANENT", baseDeTest.string("SELECT state || '|' || error_kind FROM sync_run WHERE source_id = '"
                + CrashingFeed.SOURCE_ID + "'"));
        assertEquals(0, baseDeTest.count("SELECT count(*) FROM sync_cursor WHERE source_id = '" + CrashingFeed.SOURCE_ID + "'"),
                "curseur inchangé");
        assertEquals("FAILING", baseDeTest.string("SELECT state FROM source WHERE id = '" + CrashingFeed.SOURCE_ID + "'"));
        assertEquals(List.of(imprevue), bilan.erreursImprevues());
        assertSame(imprevue, assertThrows(IllegalArgumentException.class, bilan::relancerErreurImprevue),
                "l'échéance relance l'erreur imprévue");
    }

    /** COL-11-S3 : des erreurs imprévues répétées jusqu'au seuil font passer la source en DEGRADED. */
    @Test
    void col11S3SeuilAtteintParDesErreursImprevues() {
        servirSourceDeTest("GHSA-FR-SEUIL", "pkg:npm/zz@1.0.0");
        for (int essai = 0; essai < 3; essai++) {
            sourceDeTest.failOnceWith(new IllegalArgumentException("erreur imprévue simulée " + essai));
            assertThrows(IllegalArgumentException.class, () -> synchroniseur.synchronize(CrashingFeed.SOURCE_ID));
        }

        assertEquals("DEGRADED", baseDeTest.string("SELECT state FROM source WHERE id = '" + CrashingFeed.SOURCE_ID + "'"));
        assertEquals(1, baseDeTest.count("SELECT count(*) FROM ops_event WHERE kind = 'SOURCE_DEGRADED' AND source_id = '"
                + CrashingFeed.SOURCE_ID + "'"));
        assertEquals(0, baseDeTest.count("SELECT count(*) FROM sync_run WHERE state = 'RUNNING'"));
    }
}
