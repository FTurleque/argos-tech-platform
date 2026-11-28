package io.argos.signal;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.annotation.Annotation;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.Function;

import jakarta.enterprise.event.Event;
import jakarta.enterprise.event.NotificationOptions;
import jakarta.enterprise.util.TypeLiteral;

import org.junit.jupiter.api.Test;

import io.argos.ingestion.SanteSources;
import io.argos.ingestion.SourceSynchronizer;
import io.argos.ingestion.SourceSynchronizer.SyncReport;
import io.argos.testsupport.ProprietesApplication;
import io.quarkus.scheduler.Scheduled;

/**
 * Échéance de la synchronisation planifiée (COL-09, COL-11) avec un synchroniseur simulé : sources sautées, en erreur
 * et synchronisées, publication de l'échéance et relevé de l'état des sources, sans base ni serveur.
 */
class SynchronisationPlanifieeTest {

    private static final Instant ECHEANCE = Instant.parse("2026-11-24T08:00:00Z");
    private static final String SOURCE_DE_TEST = "CRASHTEST";
    private static final String OSV = "OSV";

    /** Synchroniseur simulé : chaque source répond par le résultat programmé (rapport ou erreur levée). */
    private static final class SynchroniseurSimule extends SourceSynchronizer {

        private final Map<String, Function<String, SyncReport>> reponses;
        private final List<String> synchronisees = new ArrayList<>();

        SynchroniseurSimule(Map<String, Function<String, SyncReport>> reponses) {
            this.reponses = reponses;
        }

        @Override
        public List<String> synchronisables() {
            return reponses.keySet().stream().sorted().toList();
        }

        @Override
        public SyncReport synchronize(String sourceId) {
            synchronisees.add(sourceId);
            return reponses.get(sourceId).apply(sourceId);
        }
    }

    /** Relevé de l'état des sources compté, sans base ni registre de métriques. */
    private static final class SanteComptee extends SanteSources {

        private int releves;

        @Override
        public void actualiser() {
            releves++;
        }
    }

    /** Événement CDI qui retient les échéances publiées. */
    private static final class EcheancesRetenues implements Event<EcheanceSynchronisation> {

        private final List<EcheanceSynchronisation> publiees = new ArrayList<>();

        @Override
        public void fire(EcheanceSynchronisation echeance) {
            publiees.add(echeance);
        }

        @Override
        public <U extends EcheanceSynchronisation> CompletionStage<U> fireAsync(U echeance) {
            return CompletableFuture.completedFuture(echeance);
        }

        @Override
        public <U extends EcheanceSynchronisation> CompletionStage<U> fireAsync(U echeance, NotificationOptions options) {
            return CompletableFuture.completedFuture(echeance);
        }

        @Override
        public Event<EcheanceSynchronisation> select(Annotation... qualificatifs) {
            return this;
        }

        @Override
        public <U extends EcheanceSynchronisation> Event<U> select(Class<U> sousType, Annotation... qualificatifs) {
            throw new UnsupportedOperationException("sélection non simulée");
        }

        @Override
        public <U extends EcheanceSynchronisation> Event<U> select(TypeLiteral<U> sousType, Annotation... qualificatifs) {
            throw new UnsupportedOperationException("sélection non simulée");
        }
    }

    private static SyncReport rapport(String source, String etat) {
        return new SyncReport(source, etat, 1, 0, 0, 0, 0, null);
    }

    private static SynchronisationPlanifiee planification(SynchroniseurSimule synchroniseur, SanteComptee sante,
            EcheancesRetenues echeances) {
        SynchronisationPlanifiee planification = new SynchronisationPlanifiee();
        planification.synchronizer = synchroniseur;
        planification.santeSources = sante;
        planification.echeances = echeances;
        return planification;
    }

    /** COL-09-S2 : une source déjà en cours de synchronisation est sautée, sans rapport ni erreur. */
    @Test
    void col09S2SourceDejaEnCoursDeSynchronisationSautee() {
        SynchroniseurSimule synchroniseur = new SynchroniseurSimule(Map.of(OSV, source -> rapport(source, "RUNNING")));
        SanteComptee sante = new SanteComptee();
        EcheancesRetenues echeances = new EcheancesRetenues();

        SynchronisationPlanifiee.Bilan bilan = planification(synchroniseur, sante, echeances).synchroniserToutes(ECHEANCE);

        assertEquals(List.of(OSV), bilan.sautees());
        assertTrue(bilan.rapports().isEmpty());
        assertTrue(bilan.enErreur().isEmpty());
        assertEquals(ECHEANCE, bilan.instant());
        assertEquals(List.of(new EcheanceSynchronisation(ECHEANCE)), echeances.publiees, "index rafraîchis à l'échéance");
        assertEquals(1, sante.releves, "état des sources relevé (EXP-14)");
        assertDoesNotThrow(bilan::relancerErreurImprevue);
    }

    /**
     * COL-11-S2 : une erreur imprévue sur la première source la compte en erreur, la seconde est synchronisée, puis
     * l'échéance relance l'erreur retenue.
     */
    @Test
    void col11S2ErreurImprevuePendantLaSynchronisation() {
        IllegalArgumentException imprevue = new IllegalArgumentException("erreur imprévue simulée");
        SynchroniseurSimule synchroniseur = new SynchroniseurSimule(Map.of(
                SOURCE_DE_TEST, source -> {
                    throw imprevue;
                },
                OSV, source -> rapport(source, "SUCCEEDED")));
        SanteComptee sante = new SanteComptee();

        SynchronisationPlanifiee.Bilan bilan = planification(synchroniseur, sante, new EcheancesRetenues())
                .synchroniserToutes(ECHEANCE);

        assertEquals(List.of(SOURCE_DE_TEST, OSV), synchroniseur.synchronisees);
        assertEquals(List.of(SOURCE_DE_TEST), bilan.enErreur());
        assertEquals(List.of(OSV), bilan.rapports().stream().map(SyncReport::sourceId).toList());
        assertEquals("SUCCEEDED", bilan.rapports().get(0).state());
        assertEquals(List.of(imprevue), bilan.erreursImprevues());
        assertEquals(1, sante.releves);
        assertSame(imprevue, assertThrows(IllegalArgumentException.class, bilan::relancerErreurImprevue),
                "l'échéance relance l'erreur imprévue");
    }

    /** Une panne d'accès aux données compte la source en erreur sans être relancée par l'échéance. */
    @Test
    void panneDAccesAuxDonneesCompteeEnErreurSansRelance() {
        SynchroniseurSimule synchroniseur = new SynchroniseurSimule(Map.of(
                SOURCE_DE_TEST, source -> {
                    throw new IllegalStateException("base injoignable");
                },
                OSV, source -> rapport(source, "SUCCEEDED")));

        SynchronisationPlanifiee.Bilan bilan = planification(synchroniseur, new SanteComptee(), new EcheancesRetenues())
                .synchroniserToutes(ECHEANCE);

        assertEquals(List.of(SOURCE_DE_TEST), bilan.enErreur());
        assertEquals(List.of(OSV), bilan.rapports().stream().map(SyncReport::sourceId).toList());
        assertTrue(bilan.erreursImprevues().isEmpty());
        assertDoesNotThrow(bilan::relancerErreurImprevue);
    }

    /** COL-09-S3 : cadence lue dans la configuration, remplaçable sans recompilation ; planificateur coupé en test. */
    @Test
    void col09S3CadenceConfigurableSansRecompilation() throws NoSuchMethodException {
        Scheduled planifiee = SynchronisationPlanifiee.class.getDeclaredMethod("planifier").getAnnotation(Scheduled.class);

        assertEquals("signal-synchronisation-sources", planifiee.identity());
        assertEquals("{argos.ingestion.planification.cadence}", planifiee.every(), "cadence lue dans la configuration");
        assertEquals(Scheduled.ConcurrentExecution.SKIP, planifiee.concurrentExecution());
        // Valeur par défaut : une heure (décision J1), remplaçable par ARGOS_INGESTION_PLANIFICATION_CADENCE.
        assertEquals("1h", ProprietesApplication.parDefaut("argos.ingestion.planification.cadence"));
        assertEquals("${ARGOS_INGESTION_PLANIFICATION_CADENCE:1h}",
                ProprietesApplication.brute("argos.ingestion.planification.cadence"));
        // Le planificateur est désactivé dans le profil de test (ADR-0031).
        assertEquals("false", ProprietesApplication.brute("%test.quarkus.scheduler.enabled"));
    }
}
