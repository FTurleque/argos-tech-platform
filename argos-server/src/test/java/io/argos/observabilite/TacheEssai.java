package io.argos.observabilite;

import jakarta.enterprise.context.ApplicationScoped;

import org.jboss.logging.Logger;

import io.quarkus.scheduler.Scheduled;

/**
 * Tâche planifiée des tests d'observabilité : désactivée ({@code off}) sauf dans {@link ProfilObservabilite}, qui
 * la déclenche toutes les secondes. Elle journalise une ligne, puis échoue si {@link #echouer} est vrai.
 */
@ApplicationScoped
public class TacheEssai {

    /** Identité de la tâche, nom de son contrôle de santé et valeur de l'étiquette {@code tache}. */
    public static final String IDENTITE = "essai-observabilite";

    /** Message journalisé à chaque exécution. */
    public static final String MESSAGE = "exécution de la tâche d'essai d'observabilité";

    private static final Logger LOG = Logger.getLogger(TacheEssai.class);

    private volatile boolean echouer;

    @TacheObservee
    @Scheduled(identity = IDENTITE, every = "${argos.essai.tache-observee.cadence:off}",
            concurrentExecution = Scheduled.ConcurrentExecution.SKIP)
    void executer() {
        LOG.info(MESSAGE);
        if (echouer) {
            throw new IllegalStateException("échec simulé de la tâche d'essai");
        }
    }

    void echouer(boolean valeur) {
        echouer = valeur;
    }
}
