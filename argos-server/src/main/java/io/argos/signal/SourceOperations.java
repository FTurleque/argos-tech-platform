package io.argos.signal;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import io.argos.ingestion.SourceSynchronizer;
import io.argos.ingestion.SyncStore;

/**
 * Façade d'exploitation des sources d'avis pour l'API : état des sources (COL-07-S1), zone
 * d'échec (COL-08) et lancement d'une synchronisation. Le module {@code api} ne dépend pas
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

    /** Résultat d'une synchronisation. */
    public record SyncResult(String state, int attempts, int created, int updated, int unchanged, int rejected,
            String message) {
    }

    @Inject
    SyncStore store;

    @Inject
    SourceSynchronizer synchronizer;

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

    public SyncResult synchronize(String sourceId) {
        SourceSynchronizer.SyncReport report = synchronizer.synchronize(sourceId);
        return new SyncResult(report.state(), report.attempts(), report.created(), report.updated(), report.unchanged(), report.rejected(),
                report.message());
    }
}
