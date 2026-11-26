package io.argos.execution;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;

import org.eclipse.microprofile.config.Config;
import org.eclipse.microprofile.config.ConfigProvider;
import org.jboss.logging.Logger;

import io.argos.observabilite.Correlation;

/**
 * Exécuteur borné possédé par un bean CDI (AUD-QUA-11, design.md D3 et DC-1 du changement
 * {@code injecter-horloge-et-executeurs}, arc42 §8.5) : créé à l'initialisation du bean avec les tailles de sa
 * configuration, arrêté à l'arrêt de l'application ({@code shutdownNow}, tâches en cours interrompues). Il porte le
 * motif « soumettre, attendre avec délai, annuler » : {@link #executer} rend l'issue d'une tâche sans jamais attendre
 * au-delà du délai, et annule par interruption une tâche expirée. La tâche reçoit la corrélation du fil appelant.
 *
 * <p>Chaque usage est un bean {@code @ApplicationScoped} qui étend cette classe en nommant ses fils et le préfixe de sa
 * configuration.
 */
public abstract class ExecuteurBorne {

    private static final Logger LOG = Logger.getLogger(ExecuteurBorne.class);

    /** Durée d'inactivité au-delà de laquelle un fil au-delà du nombre minimal s'arrête. */
    private static final long INACTIVITE_SECONDES = 60;

    /** Issue d'une tâche soumise. */
    public enum Etat {
        /** Résultat obtenu dans le délai. */
        OBTENU,
        /** Tâche refusée : file pleine ou exécuteur arrêté. */
        REFUSEE,
        /** Délai dépassé : la tâche est annulée par interruption. */
        DELAI_DEPASSE,
        /** La tâche a levé une erreur, disponible dans {@link Issue#cause()}. */
        ECHEC,
        /** Attente interrompue ou tâche annulée par l'arrêt de l'exécuteur. */
        INTERROMPUE
    }

    /**
     * Issue d'une tâche.
     *
     * @param etat état de l'issue
     * @param valeur résultat, présent seulement pour {@link Etat#OBTENU}
     * @param cause erreur levée par la tâche ou par la soumission, absente pour {@link Etat#OBTENU}
     */
    public record Issue<T>(Etat etat, Optional<T> valeur, Optional<Throwable> cause) {

        static <T> Issue<T> obtenue(T valeur) {
            return new Issue<>(Etat.OBTENU, Optional.ofNullable(valeur), Optional.empty());
        }

        static <T> Issue<T> manquee(Etat etat, Throwable cause) {
            return new Issue<>(etat, Optional.empty(), Optional.of(cause));
        }
    }

    /**
     * Tailles de l'exécuteur.
     *
     * @param fils nombre de fils permanents
     * @param filsMax nombre maximal de fils
     * @param file capacité de la file d'attente
     */
    public record Tailles(int fils, int filsMax, int file) {
    }

    private final String nomFils;
    private final String prefixeConfiguration;
    private final AtomicInteger enCours = new AtomicInteger();
    private ThreadPoolExecutor executeur;

    /**
     * Exécuteur dont les fils portent le nom {@code nomFils} et dont les tailles sont lues dans
     * {@code <prefixeConfiguration>.fils}, {@code .fils-max} et {@code .file}.
     */
    protected ExecuteurBorne(String nomFils, String prefixeConfiguration) {
        this.nomFils = nomFils;
        this.prefixeConfiguration = prefixeConfiguration;
    }

    /** Tailles de l'exécuteur, lues dans la configuration à son démarrage. */
    protected Tailles tailles() {
        Config configuration = ConfigProvider.getConfig();
        return new Tailles(configuration.getValue(prefixeConfiguration + ".fils", Integer.class),
                configuration.getValue(prefixeConfiguration + ".fils-max", Integer.class),
                configuration.getValue(prefixeConfiguration + ".file", Integer.class));
    }

    @PostConstruct
    protected void demarrer() {
        Tailles tailles = tailles();
        executeur = new ThreadPoolExecutor(tailles.fils(), Math.max(tailles.fils(), tailles.filsMax()), INACTIVITE_SECONDES,
                TimeUnit.SECONDS, new ArrayBlockingQueue<>(tailles.file()), tache -> {
                    Thread fil = new Thread(tache, nomFils);
                    fil.setDaemon(true);
                    return fil;
                }, new ThreadPoolExecutor.AbortPolicy());
    }

    @PreDestroy
    protected void arreter() {
        executeur.shutdownNow();
    }

    /** Vrai une fois l'exécuteur arrêté. */
    public boolean arrete() {
        return executeur.isShutdown();
    }

    /** Vrai si aucune tâche soumise n'est en cours ni en file. */
    public boolean inactif() {
        return enCours.get() == 0;
    }

    /**
     * Soumet la tâche sans attendre son issue ; faux si elle est refusée (file pleine ou exécuteur arrêté). La tâche
     * reçoit la corrélation du fil appelant et traite elle-même ses erreurs.
     */
    public boolean lancer(Runnable tache) {
        Runnable suivie = Correlation.propagerAction(tache);
        enCours.incrementAndGet();
        try {
            executeur.execute(() -> {
                try {
                    suivie.run();
                } finally {
                    enCours.decrementAndGet();
                }
            });
            return true;
        } catch (RejectedExecutionException refus) {
            enCours.decrementAndGet();
            LOG.debugf(refus, "tâche refusée par l'exécuteur %s", nomFils);
            return false;
        }
    }

    /** Soumet la tâche sans attendre ; refusée, elle s'exécute sur le fil appelant : aucune tâche n'est perdue. */
    public void lancerOuExecuter(Runnable tache) {
        if (!lancer(tache)) {
            tache.run();
        }
    }

    /** Exécute la tâche et attend son résultat au plus {@code delai}. */
    public <T> Issue<T> executer(Callable<T> tache, Duration delai) {
        Optional<Future<T>> demande = soumettre(tache);
        if (demande.isEmpty()) {
            return Issue.manquee(Etat.REFUSEE, new RejectedExecutionException("tâche refusée par " + nomFils));
        }
        return attendre(demande.get(), delai.toNanos());
    }

    /**
     * Soumet toutes les tâches puis attend leurs issues, dans l'ordre, au plus {@code delai} pour l'ensemble : une
     * tâche encore en cours à l'échéance est annulée par interruption ; une tâche refusée a l'issue {@link Etat#REFUSEE}.
     */
    public <T> List<Issue<T>> executerTous(List<Callable<T>> taches, Duration delai) {
        long echeance = System.nanoTime() + delai.toNanos();
        List<Optional<Future<T>>> demandes = taches.stream().map(this::soumettre).toList();
        List<Issue<T>> issues = new ArrayList<>();
        for (Optional<Future<T>> demande : demandes) {
            if (demande.isEmpty()) {
                issues.add(Issue.manquee(Etat.REFUSEE, new RejectedExecutionException("tâche refusée par " + nomFils)));
            } else {
                issues.add(attendre(demande.get(), Math.max(0, echeance - System.nanoTime())));
            }
        }
        return issues;
    }

    private <T> Optional<Future<T>> soumettre(Callable<T> tache) {
        // done() s'exécute une seule fois, à la fin de la tâche comme à son annulation, démarrée ou non.
        FutureTask<T> suivie = new FutureTask<>(Correlation.propager(tache)) {
            @Override
            protected void done() {
                enCours.decrementAndGet();
            }
        };
        enCours.incrementAndGet();
        try {
            executeur.execute(suivie);
            return Optional.of(suivie);
        } catch (RejectedExecutionException refus) {
            enCours.decrementAndGet();
            LOG.debugf(refus, "tâche refusée par l'exécuteur %s", nomFils);
            return Optional.empty();
        }
    }

    private <T> Issue<T> attendre(Future<T> demande, long delaiNanos) {
        try {
            return Issue.obtenue(demande.get(delaiNanos, TimeUnit.NANOSECONDS));
        } catch (InterruptedException interruption) {
            demande.cancel(true);
            Thread.currentThread().interrupt();
            LOG.debugf(interruption, "attente d'une tâche de l'exécuteur %s interrompue", nomFils);
            return Issue.manquee(Etat.INTERROMPUE, interruption);
        } catch (ExecutionException echec) {
            LOG.debugf(echec, "tâche de l'exécuteur %s en erreur", nomFils);
            return Issue.manquee(Etat.ECHEC, echec.getCause());
        } catch (TimeoutException depassement) {
            demande.cancel(true);
            LOG.debugf(depassement, "tâche de l'exécuteur %s annulée au délai", nomFils);
            return Issue.manquee(Etat.DELAI_DEPASSE, depassement);
        } catch (CancellationException annulation) {
            LOG.debugf(annulation, "tâche de l'exécuteur %s annulée", nomFils);
            return Issue.manquee(Etat.INTERROMPUE, annulation);
        }
    }
}
