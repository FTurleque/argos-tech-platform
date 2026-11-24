package io.argos.architecture.exemples;

import io.quarkus.scheduler.Scheduled;

/**
 * Tâche planifiée sans {@code @TacheObservee}, contre laquelle {@code TachesObserveesTest} vérifie que sa règle la
 * refuse. Désactivée ({@code off}) : jamais exécutée.
 */
public final class TacheNonObservee {

    @Scheduled(identity = "exemple-tache-non-observee", every = "${argos.essai.tache-non-observee.cadence:off}")
    void executer() {
        // Exemple de règle d'architecture : aucun traitement.
    }
}
