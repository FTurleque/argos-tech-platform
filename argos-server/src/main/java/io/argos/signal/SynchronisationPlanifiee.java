package io.argos.signal;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Event;
import jakarta.inject.Inject;

import org.eclipse.microprofile.health.Readiness;
import org.jboss.logging.Logger;

import io.argos.ingestion.SanteSources;
import io.argos.ingestion.SourceSynchronizer;
import io.argos.observabilite.ErreursImprevues;
import io.argos.observabilite.TacheObservee;
import io.quarkus.scheduler.Scheduled;

/**
 * Synchronisation planifiée des sources d'avis (COL-09, décision J1 du porteur du projet, 2026-11-19 ;
 * ADR-0031). À chaque échéance, toutes les {@code argos.ingestion.planification.cadence} (une heure par
 * défaut), les index d'enrichissement sont rafraîchis puis chaque source enregistrée est synchronisée
 * depuis son curseur persisté, comme un lancement par l'exploitant (COL-05).
 *
 * <p>Une source déjà en cours de synchronisation (lancée à la demande ou par une échéance précédente) est
 * sautée : l'exclusion repose sur l'index unique {@code sync_run_one_running} et le battement de cœur
 * ({@code argos.ingestion.running-expiry}), pas sur le planificateur (COL-09-S2). Une source dégradée est
 * retentée à chaque échéance (COL-09-S4). Une panne d'accès aux données ou une erreur imprévue sur une source est
 * journalisée avec sa cause, la source est comptée en erreur et les suivantes sont synchronisées (COL-11) ; l'échéance
 * relance ensuite la première erreur imprévue (frontière d'erreur, arc42 §8.5). Le passage relève le nombre de sources par état
 * ({@link SanteSources#actualiser()}, EXP-14).
 *
 * <p>Le planificateur est désactivé dans le profil de test ; la pile E2E désactive la tâche
 * ({@code ARGOS_INGESTION_PLANIFICATION_CADENCE=off}).
 */
@ApplicationScoped
public class SynchronisationPlanifiee {

    private static final Logger LOG = Logger.getLogger(SynchronisationPlanifiee.class);

    /**
     * Bilan d'une échéance : rapports des sources synchronisées, sources sautées (déjà en cours), sources en erreur
     * et erreurs imprévues retenues, que {@link #relancerErreurImprevue()} relance.
     */
    public record Bilan(Instant instant, List<SourceSynchronizer.SyncReport> rapports, List<String> sautees,
            List<String> enErreur, List<RuntimeException> erreursImprevues) {

        /** Relance la première erreur imprévue du passage ; sans effet s'il n'y en a pas. */
        public void relancerErreurImprevue() {
            ErreursImprevues.relancer(erreursImprevues);
        }
    }

    @Inject
    Clock horloge;

    @Inject
    SourceSynchronizer synchronizer;

    @Inject
    Event<EcheanceSynchronisation> echeances;

    @Inject
    @Readiness
    SanteSources santeSources;

    @TacheObservee
    @Scheduled(identity = "signal-synchronisation-sources", every = "{argos.ingestion.planification.cadence}",
            concurrentExecution = Scheduled.ConcurrentExecution.SKIP)
    void planifier() {
        synchroniserToutes(horloge.instant()).relancerErreurImprevue();
    }

    /** Rafraîchit les index d'enrichissement puis synchronise chaque source enregistrée (méthode testable). */
    public Bilan synchroniserToutes(Instant instant) {
        echeances.fire(new EcheanceSynchronisation(instant));
        List<SourceSynchronizer.SyncReport> rapports = new ArrayList<>();
        List<String> sautees = new ArrayList<>();
        List<String> enErreur = new ArrayList<>();
        ErreursImprevues imprevues = new ErreursImprevues();
        for (String source : synchronizer.synchronisables()) {
            try {
                SourceSynchronizer.SyncReport rapport = synchronizer.synchronize(source);
                if ("RUNNING".equals(rapport.state())) {
                    sautees.add(source);
                } else {
                    rapports.add(rapport);
                }
            } catch (IllegalStateException erreur) {
                enErreur.add(source);
                LOG.errorf(erreur, "synchronisation planifiée de la source %s interrompue", source);
            } catch (RuntimeException imprevue) {
                enErreur.add(source);
                imprevues.retenir(imprevue);
                LOG.errorf(imprevue, "synchronisation planifiée de la source %s interrompue par une erreur imprévue", source);
            }
        }
        santeSources.actualiser();
        LOG.infof("synchronisation planifiée du %s : %d source(s) synchronisée(s), sautées %s, en erreur %s : %s",
                instant, rapports.size(), sautees, enErreur, rapports);
        return new Bilan(instant, List.copyOf(rapports), List.copyOf(sautees), List.copyOf(enErreur), imprevues.retenues());
    }
}
