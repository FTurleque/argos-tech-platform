package io.argos.observabilite;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

/**
 * État d'une tâche planifiée active pour le contrôle de disponibilité (EXP-15, design.md D5). Le retard toléré vaut
 * {@code facteur} intervalles du déclencheur, intervalle mesuré entre son échéance précédente et la suivante (décision
 * Q-2 du porteur du projet, 2026-11-24 : deux échéances manquées par défaut). La référence est la fin de la dernière
 * exécution, ou le démarrage du serveur si la tâche ne s'est pas encore exécutée.
 *
 * @param etat état publié dans le contrôle de santé
 * @param retardTolere retard toléré, vide tant que le déclencheur n'a pas d'échéance précédente
 */
public record EvaluationTache(Etat etat, Optional<Duration> retardTolere) {

    /** État d'une tâche ; aucun ne rend le serveur indisponible. */
    public enum Etat {
        /** Dernière exécution réussie, dans le retard toléré. */
        A_L_HEURE("a-l-heure"),
        /** Aucune exécution depuis le démarrage, retard toléré non dépassé. */
        JAMAIS_EXECUTEE("jamais-executee"),
        /** Dernière exécution terminée par une erreur, dans le retard toléré. */
        EN_ECHEC("en-echec"),
        /** Référence plus ancienne que le retard toléré. */
        EN_RETARD("en-retard");

        private final String libelle;

        Etat(String libelle) {
            this.libelle = libelle;
        }

        public String libelle() {
            return libelle;
        }

        /** Vrai pour un état publié comme dégradé. */
        boolean degrade() {
            return this == EN_ECHEC || this == EN_RETARD;
        }
    }

    /**
     * Échéances du déclencheur d'une tâche.
     *
     * @param precedente échéance précédente, vide avant la première
     * @param suivante échéance suivante, vide si le déclencheur n'en a plus
     */
    public record Echeances(Optional<Instant> precedente, Optional<Instant> suivante) {

        /** Intervalle entre les deux échéances, vide s'il en manque une. */
        Optional<Duration> intervalle() {
            return precedente.flatMap(debut -> suivante.map(fin -> Duration.between(debut, fin)));
        }
    }

    /**
     * Évalue une tâche à l'instant {@code maintenant}.
     *
     * @param echeances échéances du déclencheur
     * @param derniere dernière exécution terminée depuis le démarrage
     * @param demarrage instant du démarrage du serveur
     * @param facteur nombre d'intervalles tolérés
     */
    public static EvaluationTache evaluer(Echeances echeances, Optional<EtatTaches.Execution> derniere, Instant demarrage,
            long facteur, Instant maintenant) {
        Optional<Duration> retardTolere = echeances.intervalle().map(intervalle -> intervalle.multipliedBy(facteur));
        Instant reference = derniere.map(EtatTaches.Execution::fin).orElse(demarrage);
        if (retardTolere.isPresent() && Duration.between(reference, maintenant).compareTo(retardTolere.get()) > 0) {
            return new EvaluationTache(Etat.EN_RETARD, retardTolere);
        }
        if (derniere.isEmpty()) {
            return new EvaluationTache(Etat.JAMAIS_EXECUTEE, retardTolere);
        }
        return new EvaluationTache(derniere.get().reussie() ? Etat.A_L_HEURE : Etat.EN_ECHEC, retardTolere);
    }
}
