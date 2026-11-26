package io.argos.chain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Method;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import jakarta.inject.Inject;

import org.eclipse.microprofile.config.ConfigProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.argos.ingestion.SourceSynchronizer;
import io.argos.signal.SynchronisationPlanifiee;
import io.argos.testsupport.Fixtures;
import io.argos.testsupport.StubServer;
import io.argos.testsupport.StubServer.Stub;
import io.argos.testsupport.TestDb;
import io.argos.vulnintel.CertFrSource;
import io.argos.vulnintel.Enricher;
import io.argos.vulnintel.TraitementEnrichissements;
import io.quarkus.scheduler.Scheduled;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;

/**
 * Synchronisation planifiée des sources d'avis (changement {@code automatiser-chaine-veille}, tâches 1.2 et
 * 1.3) : COL-09-S1 à COL-09-S5. Le planificateur est désactivé dans le profil de test (ADR-0031) : chaque
 * échéance est simulée par un appel à {@link SynchronisationPlanifiee#synchroniserToutes(Instant)} avec une
 * horloge simulée ; OSV et CERT-FR sont simulés par le serveur local et les réponses enregistrées.
 */
@QuarkusTest
@QuarkusTestResource(StubServer.class)
class SynchronisationPlanifieeTest {

    private static final Instant INSTANT_INITIAL = Instant.parse("2026-11-19T08:00:00Z");

    @Inject
    TestDb baseDeTest;

    @Inject
    SynchronisationPlanifiee planification;

    @Inject
    SourceSynchronizer synchronizer;

    @Inject
    CertFrSource certFr;

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

    private static String avis(String id, String modifie, String... alias) {
        return Fixtures.osv(id, modifie, "Maven", "org.exemple:lib", "pkg:maven/org.exemple/lib", "0", "1.3.0", alias);
    }

    @Test
    void col09S1CadenceAtteinteSynchronisationDepuisLeCurseur() {
        Fixtures.stubOsv("GHSA-PL01", "2026-11-18T10:00:00Z", avis("GHSA-PL01", "2026-11-18T10:00:00Z"));

        SynchronisationPlanifiee.Bilan premier = planification.synchroniserToutes(INSTANT_INITIAL);

        assertEquals(List.of("OSV"), premier.rapports().stream().map(SourceSynchronizer.SyncReport::sourceId).toList());
        assertEquals("SUCCEEDED", premier.rapports().get(0).state());
        assertEquals(1, premier.rapports().get(0).created());
        assertEquals(1, baseDeTest.count("SELECT count(*) FROM signal WHERE external_id = 'GHSA-PL01'"));
        assertEquals("2026-11-18T10:00:00Z|GHSA-PL01", baseDeTest.string("SELECT position FROM sync_cursor WHERE source_id = 'OSV'"));

        // Une heure plus tard : seul l'avis postérieur au curseur est demandé.
        Fixtures.stubOsv("GHSA-PL01", "2026-11-18T10:00:00Z", avis("GHSA-PL01", "2026-11-18T10:00:00Z"),
                "GHSA-PL02", "2026-11-19T08:30:00Z", avis("GHSA-PL02", "2026-11-19T08:30:00Z"));
        SynchronisationPlanifiee.Bilan second = planification.synchroniserToutes(INSTANT_INITIAL.plus(Duration.ofHours(1)));

        assertEquals(1, second.rapports().get(0).created());
        assertEquals(0, second.rapports().get(0).unchanged());
        assertEquals(1, StubServer.received("/osv-api/v1/vulns/GHSA-PL01").size(), "avis antérieur au curseur non relu");
        assertEquals(1, StubServer.received("/osv-api/v1/vulns/GHSA-PL02").size());
        assertEquals("2026-11-19T08:30:00Z|GHSA-PL02", baseDeTest.string("SELECT position FROM sync_cursor WHERE source_id = 'OSV'"));
        assertEquals(0, baseDeTest.count("SELECT count(*) FROM sync_run WHERE state = 'RUNNING'"));
    }

    @Test
    void col09S2SourceDejaEnCoursDeSynchronisationSautee() {
        // Synchronisation lancée par l'exploitant et toujours en cours (battement de cœur récent).
        UUID enCours = UUID.randomUUID();
        baseDeTest.exec("INSERT INTO sync_run(id, source_id, state, attempts, started_at, heartbeat_at) VALUES ('" + enCours
                + "', 'OSV', 'RUNNING', 1, now(), now())");
        Fixtures.stubOsv("GHSA-PL03", "2026-11-18T10:00:00Z", avis("GHSA-PL03", "2026-11-18T10:00:00Z"));

        SynchronisationPlanifiee.Bilan bilan = planification.synchroniserToutes(INSTANT_INITIAL);

        assertEquals(List.of("OSV"), bilan.sautees());
        assertTrue(bilan.rapports().isEmpty());
        assertEquals(1, baseDeTest.count("SELECT count(*) FROM sync_run WHERE source_id = 'OSV'"));
        assertEquals(enCours.toString(), baseDeTest.string("SELECT id::text FROM sync_run WHERE state = 'RUNNING'"));
        assertEquals(0, baseDeTest.count("SELECT count(*) FROM signal"));
        assertTrue(StubServer.received("/osv-").isEmpty(), "aucune lecture de la source");
    }

    @Test
    void col09S3CadenceConfigurableSansRecompilation() throws Exception {
        Method tache = SynchronisationPlanifiee.class.getDeclaredMethod("planifier");
        Scheduled planifiee = tache.getAnnotation(Scheduled.class);

        assertEquals("signal-synchronisation-sources", planifiee.identity());
        assertEquals("{argos.ingestion.planification.cadence}", planifiee.every(), "cadence lue dans la configuration");
        assertEquals(Scheduled.ConcurrentExecution.SKIP, planifiee.concurrentExecution());
        // Valeur par défaut : une heure (décision J1), remplaçable par ARGOS_INGESTION_PLANIFICATION_CADENCE.
        assertEquals("1h", ConfigProvider.getConfig().getValue("argos.ingestion.planification.cadence", String.class));
        // Le planificateur est désactivé dans le profil de test (ADR-0031).
        assertEquals(Boolean.FALSE, ConfigProvider.getConfig().getValue("quarkus.scheduler.enabled", Boolean.class));
    }

    @Test
    void col09S4SourceDegradeeRetenteeEtRetablie() {
        baseDeTest.exec("UPDATE source SET state = 'DEGRADED', consecutive_failures = 3, last_error = 'HTTP 503' WHERE id = 'OSV'");
        Fixtures.stubOsv("GHSA-PL04", "2026-11-18T10:00:00Z", avis("GHSA-PL04", "2026-11-18T10:00:00Z"));

        SynchronisationPlanifiee.Bilan bilan = planification.synchroniserToutes(INSTANT_INITIAL);

        assertEquals("SUCCEEDED", bilan.rapports().get(0).state());
        assertEquals("NOMINAL", baseDeTest.string("SELECT state FROM source WHERE id = 'OSV'"));
        assertEquals(0, baseDeTest.count("SELECT consecutive_failures FROM source WHERE id = 'OSV'"));
        assertEquals(1, baseDeTest.count("SELECT count(*) FROM ops_event WHERE kind = 'SOURCE_RECOVERED'"));
    }

    @Test
    void col09S5IndexCertFrRafraichiAChaqueEcheanceSansBloquer() {
        // Index CERT-FR chargé plus tôt, alors qu'aucun avis ne citait la CVE : mis en cache (6 h).
        StubServer.programmer("/certfr/feed/", Stub.succes("<rss version=\"2.0\"><channel></channel></rss>"));
        assertTrue(certFr.lookup("CVE-2026-109163").isEmpty());

        // Le CERT-FR publie ensuite l'avis : l'échéance relit l'index avant l'enrichissement.
        StubServer.programmer("/certfr/feed/", Stub.succes(Fixtures.resource("enrichment/certfr-feed.xml")));
        StubServer.programmer("/certfr/CERTFR-2026-AVI-1500/json/", Stub.succes(Fixtures.resource("enrichment/CERTFR-2026-AVI-1500.json")));
        Fixtures.stubOsv("GHSA-PL05", "2026-11-18T10:00:00Z", avis("GHSA-PL05", "2026-11-18T10:00:00Z", "CVE-2026-109163"));

        planification.synchroniserToutes(INSTANT_INITIAL);
        enrichissements.traiter(horloge.instant());

        UUID signal = baseDeTest.uuid("SELECT id FROM signal WHERE external_id = 'GHSA-PL05'");
        assertTrue(enricher.forSignal(signal).stream()
                .anyMatch(vue -> vue.source().equals("CERTFR") && vue.reference().equals("CERTFR-2026-AVI-1500")),
                "index CERT-FR relu à l'échéance");

        // CERT-FR indisponible : les signaux de l'échéance suivante sont enregistrés quand même (COL-04-S3).
        StubServer.programmer("/certfr/feed/", Stub.status(503));
        Fixtures.stubOsv("GHSA-PL05", "2026-11-18T10:00:00Z", avis("GHSA-PL05", "2026-11-18T10:00:00Z", "CVE-2026-109163"),
                "GHSA-PL06", "2026-11-19T08:30:00Z", avis("GHSA-PL06", "2026-11-19T08:30:00Z", "CVE-2026-109163"));

        SynchronisationPlanifiee.Bilan bilan = planification.synchroniserToutes(INSTANT_INITIAL.plus(Duration.ofHours(1)));
        enrichissements.traiter(horloge.instant());

        assertEquals("SUCCEEDED", bilan.rapports().get(0).state());
        assertEquals(1, bilan.rapports().get(0).created());
        assertEquals(1, baseDeTest.count("SELECT count(*) FROM signal WHERE external_id = 'GHSA-PL06'"));
        UUID suivant = baseDeTest.uuid("SELECT id FROM signal WHERE external_id = 'GHSA-PL06'");
        assertTrue(enricher.forSignal(suivant).stream().noneMatch(vue -> vue.source().equals("CERTFR")),
                "CERT-FR indisponible : aucun enrichissement inventé");
    }
}
