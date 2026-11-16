package io.argos.ingestion;

import java.util.UUID;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

/**
 * Enregistre un avis, ou sa mise à l'écart, et avance le curseur dans la même transaction :
 * le curseur ne dépasse jamais le dernier avis enregistré durablement (COL-05, INV-D5).
 */
@ApplicationScoped
public class AdvisoryPersister {

    @Inject
    AdvisorySink sink;

    @Inject
    SyncStore store;

    @Transactional
    public AdvisorySink.Outcome persist(String sourceId, UUID runId, NormalizedAdvisory advisory, String position) {
        AdvisorySink.Outcome outcome = sink.record(sourceId, advisory);
        store.advanceCursor(sourceId, position, runId);
        return outcome;
    }

    @Transactional
    public void persistFailure(String sourceId, UUID runId, RawAdvisory raw, String reason, String position) {
        store.recordFailure(sourceId, runId, raw.externalId(), reason, raw.content());
        store.advanceCursor(sourceId, position, runId);
    }
}
