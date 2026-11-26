package io.argos.signal;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import io.argos.commun.RegleViolee;
import io.argos.ingestion.LancementSynchronisation;
import io.argos.ingestion.SourceSynchronizer;
import io.argos.ingestion.SyncStore;

/**
 * Façade d'exploitation des sources d'avis pour l'API : état des sources (COL-07-S1), zone
 * d'échec (COL-08) et lancement d'une synchronisation en tâche de fond (COL-12). Le module {@code api} ne dépend pas
 * directement de {@code source-ingestion} (arc42 §5, {@code ModuleBoundariesTest}).
 */
@ApplicationScoped
public class SourceOperations {

    /** État d'une source (DF-COL-3 : NOMINAL, FAILING ou DEGRADED). */
    public record SourceStatus(String sourceId, String label, String state, int consecutiveFailures,
            Instant lastSuccessAt, Instant lastFailureAt, String lastError, String cursor) {
    }

    /** Avis mis à l'écart, sans son contenu brut. */
    public record RejectedAdvisory(UUID id, String externalId, String reason, Instant receivedAt) {
    }

    /** Événement d'exploitation. */
    public record OperationEvent(String kind, String detail, Instant occurredAt) {
    }

    /** Synchronisation acceptée : source et identifiant du traitement poursuivi en tâche de fond (COL-12-S1). */
    public record SyncLaunch(String sourceId, UUID runId) {
    }

    @Inject
    SyncStore store;

    @Inject
    SourceSynchronizer synchronizer;

    @Inject
    LancementSynchronisation lancement;

    public List<SourceStatus> statuses() {
        return store.sourceStates().stream().map(state -> new SourceStatus(state.sourceId(), state.label(), state.state(),
                state.consecutiveFailures(), state.lastSuccessAt(), state.lastFailureAt(), state.lastError(), state.cursor())).toList();
    }

    public List<RejectedAdvisory> rejected(String sourceId) {
        return store.failures(sourceId).stream()
                .map(failure -> new RejectedAdvisory(failure.id(), failure.externalId(), failure.reason(), failure.receivedAt())).toList();
    }

    public List<OperationEvent> events(String sourceId) {
        return store.events(sourceId).stream().map(event -> new OperationEvent(event.kind(), event.detail(), event.occurredAt()))
                .toList();
    }

    /**
     * Démarre un traitement de la source et le poursuit en tâche de fond (COL-12-S1). Refusé si un traitement est en
     * cours, attente de reprise comprise (COL-12-S2, COL-12-S3), ou si la file des synchronisations est pleine : le
     * traitement reste alors {@code RETRYABLE}, repris à la prochaine échéance planifiée.
     *
     * @throws RegleViolee {@code SYNCHRONISATION_EN_COURS} ou {@code LANCEMENT_SATURE}
     */
    public SyncLaunch lancer(String sourceId) {
        UUID runId = synchronizer.demarrer(sourceId).orElseThrow(() -> new RegleViolee("SYNCHRONISATION_EN_COURS",
                "Une synchronisation de la source est déjà en cours."));
        if (!lancement.poursuivreEnFond(sourceId, runId)) {
            store.abandonner(runId, store.attempts(runId), "lancement refusé : file des synchronisations pleine");
            throw new RegleViolee("LANCEMENT_SATURE",
                    "Trop de synchronisations en attente : la source sera synchronisée à la prochaine échéance.");
        }
        return new SyncLaunch(sourceId, runId);
    }
}
