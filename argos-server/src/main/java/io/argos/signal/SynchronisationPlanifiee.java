package io.argos.signal;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Event;
import jakarta.inject.Inject;

import org.jboss.logging.Logger;

import io.argos.ingestion.SourceSynchronizer;
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
 * retentée à chaque échéance (COL-09-S4). Une panne d'accès aux données sur une source est journalisée et n'empêche
 * pas la synchronisation des autres.
 *
 * <p>Le planificateur est désactivé dans le profil de test ; la pile E2E désactive la tâche
 * ({@code ARGOS_INGESTION_PLANIFICATION_CADENCE=off}).
 */
@ApplicationScoped
public class SynchronisationPlanifiee {

    private static final Logger LOG = Logger.getLogger(SynchronisationPlanifiee.class);

    /** Bilan d'une échéance : rapports des sources synchronisées et sources sautées (déjà en cours). */
    public record Bilan(Instant instant, List<SourceSynchronizer.SyncReport> rapports, List<String> sautees,
            List<String> enErreur) {
    }

    @Inject
    SourceSynchronizer synchronizer;

    @Inject
    Event<EcheanceSynchronisation> echeances;

    @Scheduled(identity = "signal-synchronisation-sources", every = "{argos.ingestion.planification.cadence}",
            concurrentExecution = Scheduled.ConcurrentExecution.SKIP)
    void planifier() {
        synchroniserToutes(Instant.now());
    }

    /** Rafraîchit les index d'enrichissement puis synchronise chaque source enregistrée (méthode testable). */
    public Bilan synchroniserToutes(Instant instant) {
        echeances.fire(new EcheanceSynchronisation(instant));
        List<SourceSynchronizer.SyncReport> rapports = new ArrayList<>();
        List<String> sautees = new ArrayList<>();
        List<String> enErreur = new ArrayList<>();
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
            }
        }
        LOG.infof("synchronisation planifiée du %s : %d source(s) synchronisée(s), sautées %s, en erreur %s : %s",
                instant, rapports.size(), sautees, enErreur, rapports);
        return new Bilan(instant, List.copyOf(rapports), List.copyOf(sautees), List.copyOf(enErreur));
    }
}
