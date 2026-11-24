package io.argos.ingestion;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;

import org.jboss.logging.Logger;

/**
 * Synchronise une source d'avis (DF-COL-1, DC-3) : lecture des avis postérieurs au curseur,
 * normalisation, enregistrement avis par avis, nouvelles tentatives bornées et état de la source.
 *
 * <p>Chaque tentative reprend depuis le curseur persisté : une panne en cours de lot ne perd ni
 * ne duplique aucun avis (COL-05-S1). Une panne d'accès aux données ou une erreur imprévue termine le traitement
 * {@code FAILED} avec le motif {@code PERMANENT} ; l'erreur imprévue est ensuite relancée (COL-11).
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

    /**
     * Sources enregistrées dans la table {@code source} et servies par un connecteur, dans l'ordre de leur
     * identifiant : celles que la synchronisation planifiée parcourt (COL-09).
     */
    public List<String> synchronisables() {
        Set<String> connues = feeds.stream().map(AdvisoryFeed::sourceId).collect(Collectors.toSet());
        return store.sourceStates().stream().map(SyncStore.SourceState::sourceId).filter(connues::contains).toList();
    }

    public SyncReport synchronize(String sourceId) {
        AdvisoryFeed feed = feeds.stream().filter(candidate -> candidate.sourceId().equals(sourceId)).findFirst()
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
            } catch (SourceException error) {
                store.recordAttempt(runId, attempts, error);
                LOG.warnf(error, "synchronisation %s, tentative %d en échec (%s) : %s", sourceId, attempts, error.kind(),
                        error.getMessage());
                if (error.kind() == SourceException.Kind.PERMANENT) {
                    return failed(sourceId, runId, attempts, "PERMANENT", error, counters);
                }
                if (attempts >= policy.maxAttempts()) {
                    return failed(sourceId, runId, attempts, "EXHAUSTED", error, counters);
                }
                Duration delay = policy.delayAfter(attempts, error.retryAfter());
                store.markRetryable(runId, attempts, Instant.now().plus(delay), error.getMessage());
                try {
                    sleeper.sleep(delay);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    return counters.report(sourceId, "RETRYABLE", attempts, "attente interrompue");
                }
                store.resume(runId);
            } catch (IllegalStateException panne) {
                // Une panne d'accès aux données (curseur, enregistrement d'un avis) termine le traitement en échec :
                // il ne reste pas RUNNING et la source change d'état (COL-07).
                SourceException error = new SourceException(SourceException.Kind.PERMANENT, null, null,
                        "panne d'accès aux données : " + panne.getClass().getSimpleName(), panne);
                LOG.errorf(panne, "synchronisation %s, tentative %d interrompue par une panne d'accès aux données",
                        sourceId, attempts);
                enregistrerTentativeOuJournaliser(sourceId, runId, attempts, error);
                return failed(sourceId, runId, attempts, "PERMANENT", error, counters);
            } catch (RuntimeException imprevue) {
                // Frontière d'erreur (arc42 §8.5) : une erreur imprévue termine le traitement en échec permanent, compté
                // pour l'état DEGRADED (COL-11), puis est relancée à l'appelant ; elle n'est pas rejouée.
                SourceException error = new SourceException(SourceException.Kind.PERMANENT, null, null,
                        "erreur imprévue : " + imprevue.getClass().getSimpleName(), imprevue);
                LOG.errorf(imprevue, "synchronisation %s, tentative %d interrompue par une erreur imprévue", sourceId, attempts);
                enregistrerTentativeOuJournaliser(sourceId, runId, attempts, error);
                failed(sourceId, runId, attempts, "PERMANENT", error, counters);
                throw imprevue;
            }
        }
    }

    /** Enregistre la tentative ; une nouvelle panne d'accès aux données est journalisée avec sa cause. */
    private void enregistrerTentativeOuJournaliser(String sourceId, UUID runId, int attempts, SourceException error) {
        try {
            store.recordAttempt(runId, attempts, error);
        } catch (IllegalStateException panne) {
            LOG.warnf(panne, "tentative %d de la synchronisation %s non enregistrée", attempts, sourceId);
        }
    }

    private SyncReport failed(String sourceId, UUID runId, int attempts, String kind, SourceException error,
            Counters counters) {
        store.fail(runId, attempts, kind, error.getMessage());
        store.sourceFailed(sourceId, error.getMessage(), policy.degradedThreshold(),
                error.kind() == SourceException.Kind.PERMANENT);
        return counters.report(sourceId, "FAILED", attempts, error.getMessage());
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
