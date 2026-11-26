package io.argos.ingestion;

import java.time.Clock;
import java.time.Duration;
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
 * normalisation, enregistrement avis par avis, nouvelles tentatives bornées et état de la source. Un traitement est
 * démarré ({@link #demarrer(String)}) puis poursuivi ({@link #poursuivre(String, UUID)}) : sur le fil du
 * planificateur pour la synchronisation planifiée, en tâche de fond pour un lancement par l'exploitant (COL-12) ;
 * pendant l'attente d'une nouvelle tentative, le traitement reste {@code RUNNING} et donc exclusif.
 *
 * <p>Chaque tentative reprend depuis le curseur persisté : une panne en cours de lot ne perd ni
 * ne duplique aucun avis (COL-05-S1). Une panne d'accès aux données ou une erreur imprévue termine le traitement
 * {@code FAILED} avec le motif {@code PERMANENT} ; l'erreur imprévue est ensuite relancée (COL-11).
 */
@ApplicationScoped
public class SourceSynchronizer {

    private static final Logger LOG = Logger.getLogger(SourceSynchronizer.class);

    /** Message d'un lancement refusé parce qu'un traitement de la source est en cours. */
    static final String DEJA_EN_COURS = "une synchronisation est déjà en cours";

    private static final String ATTENTE_INTERROMPUE = "attente interrompue";

    /** Résultat d'une synchronisation. */
    public record SyncReport(String sourceId, String state, int attempts, int created, int updated, int unchanged,
            int rejected, String message) {
    }

    @Inject
    Clock horloge;

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

    /**
     * Démarre un traitement pour la source, ou reprend son traitement {@code RETRYABLE} (DC-3) ; vide si un traitement
     * est déjà en cours, y compris en attente d'une nouvelle tentative (COL-12-S2, COL-12-S3).
     *
     * @throws IllegalArgumentException si aucun connecteur ne sert la source
     */
    public Optional<UUID> demarrer(String sourceId) {
        feed(sourceId);
        return store.startOrResume(sourceId);
    }

    /** Démarre puis poursuit la synchronisation de la source sur le fil appelant (synchronisation planifiée, COL-09). */
    public SyncReport synchronize(String sourceId) {
        Optional<UUID> started = demarrer(sourceId);
        if (started.isEmpty()) {
            return new SyncReport(sourceId, "RUNNING", 0, 0, 0, 0, 0, DEJA_EN_COURS);
        }
        return poursuivre(sourceId, started.get());
    }

    /**
     * Poursuit le traitement démarré par {@link #demarrer(String)} jusqu'à son issue : tentatives bornées, attente
     * entre deux tentatives sous l'exclusion du traitement (COL-06, COL-12-S3).
     */
    public SyncReport poursuivre(String sourceId, UUID runId) {
        AdvisoryFeed feed = feed(sourceId);
        SynchronisationSource synchronisation = new SynchronisationSource(sourceId, runId);
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
                Optional<SyncReport> issue = apresErreurDeSource(synchronisation, attempts, error, counters);
                if (issue.isPresent()) {
                    return issue.get();
                }
            } catch (IllegalStateException panne) {
                // Une panne d'accès aux données (curseur, enregistrement d'un avis) termine le traitement en échec :
                // il ne reste pas RUNNING et la source change d'état (COL-07).
                return echecPermanent(synchronisation, attempts, counters, panne, "panne d'accès aux données");
            } catch (RuntimeException imprevue) {
                // Frontière d'erreur (arc42 §8.5) : une erreur imprévue termine le traitement en échec permanent, compté
                // pour l'état DEGRADED (COL-11), puis est relancée à l'appelant ; elle n'est pas rejouée.
                echecPermanent(synchronisation, attempts, counters, imprevue, "erreur imprévue");
                throw imprevue;
            }
        }
    }

    /**
     * Après une erreur de la source : issue du traitement (échec permanent, tentatives épuisées, attente interrompue,
     * traitement repris par un autre lancement), ou vide pour une nouvelle tentative après l'attente.
     */
    private Optional<SyncReport> apresErreurDeSource(SynchronisationSource synchronisation, int attempts, SourceException error,
            Counters counters) {
        String sourceId = synchronisation.sourceId();
        UUID runId = synchronisation.runId();
        store.recordAttempt(runId, attempts, error);
        LOG.warnf(error, "synchronisation %s, tentative %d en échec (%s) : %s", sourceId, attempts, error.kind(), error.getMessage());
        if (error.kind() == SourceException.Kind.PERMANENT) {
            return Optional.of(failed(synchronisation, attempts, "PERMANENT", error, counters));
        }
        if (attempts >= policy.maxAttempts()) {
            return Optional.of(failed(synchronisation, attempts, "EXHAUSTED", error, counters));
        }
        Duration delay = policy.delayAfter(attempts, error.retryAfter());
        store.attendreReprise(runId, attempts, horloge.instant().plus(delay), error.getMessage());
        try {
            sleeper.sleep(delay);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            store.abandonner(runId, attempts, ATTENTE_INTERROMPUE);
            return Optional.of(counters.report(sourceId, "RETRYABLE", attempts, ATTENTE_INTERROMPUE));
        }
        if (!store.reprendre(runId)) {
            LOG.warnf("synchronisation %s (run %s) : traitement expiré et repris par un autre lancement", sourceId, runId);
            return Optional.of(counters.report(sourceId, "RUNNING", attempts, DEJA_EN_COURS));
        }
        return Optional.empty();
    }

    private AdvisoryFeed feed(String sourceId) {
        return feeds.stream().filter(candidate -> candidate.sourceId().equals(sourceId)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("source inconnue : " + sourceId));
    }

    /**
     * Termine le traitement en échec permanent, motif {@code <nature> : <type d'erreur>} : erreur journalisée avec sa
     * cause, tentative enregistrée, traitement {@code FAILED} et source en échec.
     */
    private SyncReport echecPermanent(SynchronisationSource synchronisation, int attempts, Counters counters, RuntimeException cause,
            String nature) {
        LOG.errorf(cause, "synchronisation %s, tentative %d interrompue (%s)", synchronisation.sourceId(), attempts, nature);
        SourceException error = new SourceException(SourceException.Kind.PERMANENT, null, null,
                nature + " : " + cause.getClass().getSimpleName(), cause);
        enregistrerTentativeOuJournaliser(synchronisation.sourceId(), synchronisation.runId(), attempts, error);
        return failed(synchronisation, attempts, "PERMANENT", error, counters);
    }

    /** Synchronisation en cours : source et identifiant de son traitement ({@code sync_run}). */
    private record SynchronisationSource(String sourceId, UUID runId) {
    }

    /** Enregistre la tentative ; une nouvelle panne d'accès aux données est journalisée avec sa cause. */
    private void enregistrerTentativeOuJournaliser(String sourceId, UUID runId, int attempts, SourceException error) {
        try {
            store.recordAttempt(runId, attempts, error);
        } catch (IllegalStateException panne) {
            LOG.warnf(panne, "tentative %d de la synchronisation %s non enregistrée", attempts, sourceId);
        }
    }

    private SyncReport failed(SynchronisationSource synchronisation, int attempts, String kind, SourceException error, Counters counters) {
        String sourceId = synchronisation.sourceId();
        UUID runId = synchronisation.runId();
        store.fail(runId, attempts, kind, error.getMessage());
        store.sourceFailed(sourceId, error.getMessage(), policy.degradedThreshold(),
                error.kind() == SourceException.Kind.PERMANENT);
        return counters.report(sourceId, "FAILED", attempts, error.getMessage());
    }

    private void runOnce(AdvisoryFeed feed, UUID runId, Counters counters) {
        String position = store.cursor(feed.sourceId()).orElse(null);
        try (AdvisoryFeed.Lecture lecture = feed.lire(position)) {
            for (FeedEntry entry : lecture.entrees()) {
                enregistrer(feed, runId, entry, lecture.avis(entry), counters);
            }
        }
    }

    private void enregistrer(AdvisoryFeed feed, UUID runId, FeedEntry entry, RawAdvisory raw, Counters counters) {
        NormalizedAdvisory advisory;
        try {
            advisory = feed.normalize(raw);
        } catch (AdvisoryRejectedException rejected) {
            persister.persistFailure(feed.sourceId(), runId, raw, rejected.getMessage(), entry.position());
            counters.rejected++;
            return;
        }
        switch (persister.persist(feed.sourceId(), runId, advisory, entry.position())) {
            case CREATED -> counters.created++;
            case UPDATED -> counters.updated++;
            case UNCHANGED, IGNORED_OLDER -> counters.unchanged++;
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
