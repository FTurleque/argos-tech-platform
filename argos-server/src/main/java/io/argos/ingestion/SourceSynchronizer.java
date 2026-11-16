package io.argos.ingestion;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;

import org.jboss.logging.Logger;

/**
 * Synchronise une source d'avis (DF-COL-1, DC-3) : lecture des avis postérieurs au curseur,
 * normalisation, enregistrement avis par avis, nouvelles tentatives bornées et état de la source.
 *
 * <p>Chaque tentative reprend depuis le curseur persisté : une panne en cours de lot ne perd ni
 * ne duplique aucun avis (COL-05-S1).
 */
@ApplicationScoped
public class SourceSynchronizer {

    private static final Logger LOG = Logger.getLogger(SourceSynchronizer.class);

    /** Résultat d'une synchronisation. */
    public record SyncReport(String sourceId, String state, int attempts, int created, int updated, int unchanged,
            int rejected, String message) {
    }

    @Inject
    Instance<AdvisoryFeed> feeds;

    @Inject
    SyncStore store;

    @Inject
    AdvisoryPersister persister;

    @Inject
    RetryPolicy policy;

    @Inject
    Sleeper sleeper;

    public SyncReport synchronize(String sourceId) {
        AdvisoryFeed feed = feeds.stream().filter(f -> f.sourceId().equals(sourceId)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("source inconnue : " + sourceId));
        Optional<UUID> started = store.startOrResume(sourceId);
        if (started.isEmpty()) {
            return new SyncReport(sourceId, "RUNNING", 0, 0, 0, 0, 0, "une synchronisation est déjà en cours");
        }
        UUID runId = started.get();
        int attempts = store.attempts(runId);
        Counters counters = new Counters();
        while (true) {
            attempts++;
            try {
                runOnce(feed, runId, counters);
                store.succeed(runId, attempts);
                store.sourceSucceeded(sourceId);
                LOG.infof("synchronisation %s réussie (run %s) : %s", sourceId, runId, counters);
                return counters.report(sourceId, "SUCCEEDED", attempts, null);
            } catch (SourceException e) {
                store.recordAttempt(runId, attempts, e);
                LOG.warnf("synchronisation %s, tentative %d en échec (%s) : %s", sourceId, attempts, e.kind(),
                        e.getMessage());
                if (e.kind() == SourceException.Kind.PERMANENT) {
                    return failed(sourceId, runId, attempts, "PERMANENT", e, counters);
                }
                if (attempts >= policy.maxAttempts()) {
                    return failed(sourceId, runId, attempts, "EXHAUSTED", e, counters);
                }
                Duration delay = policy.delayAfter(attempts, e.retryAfter());
                store.markRetryable(runId, attempts, Instant.now().plus(delay), e.getMessage());
                try {
                    sleeper.sleep(delay);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    return counters.report(sourceId, "RETRYABLE", attempts, "attente interrompue");
                }
                store.resume(runId);
            }
        }
    }

    private SyncReport failed(String sourceId, UUID runId, int attempts, String kind, SourceException e,
            Counters counters) {
        store.fail(runId, attempts, kind, e.getMessage());
        store.sourceFailed(sourceId, e.getMessage(), policy.degradedThreshold(),
                e.kind() == SourceException.Kind.PERMANENT);
        return counters.report(sourceId, "FAILED", attempts, e.getMessage());
    }

    private void runOnce(AdvisoryFeed feed, UUID runId, Counters counters) {
        String position = store.cursor(feed.sourceId()).orElse(null);
        for (FeedEntry entry : feed.changesSince(position)) {
            RawAdvisory raw = feed.fetch(entry);
            NormalizedAdvisory advisory;
            try {
                advisory = feed.normalize(raw);
            } catch (AdvisoryRejectedException rejected) {
                persister.persistFailure(feed.sourceId(), runId, raw, rejected.getMessage(), entry.position());
                counters.rejected++;
                continue;
            }
            switch (persister.persist(feed.sourceId(), runId, advisory, entry.position())) {
                case CREATED -> counters.created++;
                case UPDATED -> counters.updated++;
                case UNCHANGED, IGNORED_OLDER -> counters.unchanged++;
            }
        }
    }

    private static final class Counters {
        int created;
        int updated;
        int unchanged;
        int rejected;

        SyncReport report(String sourceId, String state, int attempts, String message) {
            return new SyncReport(sourceId, state, attempts, created, updated, unchanged, rejected, message);
        }

        @Override
        public String toString() {
            return "créés=" + created + ", modifiés=" + updated + ", inchangés=" + unchanged + ", écartés=" + rejected;
        }
    }
}
