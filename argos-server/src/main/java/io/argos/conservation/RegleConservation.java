package io.argos.conservation;

import java.time.Instant;

/**
 * Règle de conservation d'une table ou d'un groupe de tables liées (ADR-0041, EXP-17), déclarée par le module
 * propriétaire de ces tables sous forme de bean CDI. La durée de conservation se règle par la propriété
 * {@code argos.conservation.<nom>} (durée ISO-8601), lue par {@link PurgesConservation}.
 */
public interface RegleConservation {

    /** Préfixe des propriétés de durée de conservation. */
    String PREFIXE_PROPRIETE = "argos.conservation.";

    /** Nom stable de la règle, en kebab-case : suffixe de sa propriété de durée et étiquette de ses métriques. */
    String nom();

    /**
     * Supprime les lignes antérieures à {@code limite} que la règle couvre, en épargnant celles qui restent utiles
     * (traitement en cours, version courante, ligne citée).
     *
     * @param limite instant avant lequel une ligne est échue
     * @return nombre de lignes supprimées
     * @throws IllegalStateException si la base refuse la suppression
     */
    long purger(Instant limite);
}
