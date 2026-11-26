package io.argos.ingestion;

import java.time.Instant;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import io.argos.commun.Conversions;
import io.argos.conservation.RegleConservation;
import io.argos.conservation.SuppressionParLots;

/**
 * Règles de conservation des traces de la collecte des avis (ADR-0041, EXP-17) : synchronisations terminées et leurs
 * tentatives, avis mis à l'écart (COL-08), événements d'exploitation (COL-07).
 */
public final class ConservationCollecte {

    /**
     * Synchronisation supprimable : terminée avant la limite et citée par aucun avis mis à l'écart encore conservé.
     * Une synchronisation en cours ou à reprendre ({@code RUNNING}, {@code RETRYABLE}) n'est jamais supprimée.
     */
    private static final String SYNCHRONISATION_ECHUE = "r.state IN ('SUCCEEDED', 'FAILED') AND r.finished_at < ?"
            + " AND NOT EXISTS (SELECT 1 FROM ingestion_failure f WHERE f.run_id = r.id)";

    private ConservationCollecte() {
    }

    /** Synchronisations terminées et leurs tentatives ({@code argos.conservation.synchronisations}). */
    @ApplicationScoped
    public static class Synchronisations implements RegleConservation {

        @Inject
        SuppressionParLots suppression;

        @Override
        public String nom() {
            return "synchronisations";
        }

        /** Les tentatives d'abord (clé étrangère sans cascade), puis les synchronisations sans tentative. */
        @Override
        public long purger(Instant limite) {
            Object horodatage = Conversions.utc(limite);
            long tentatives = suppression.supprimer("DELETE FROM sync_attempt WHERE id IN (SELECT a.id FROM sync_attempt a"
                    + " JOIN sync_run r ON r.id = a.run_id WHERE " + SYNCHRONISATION_ECHUE + " LIMIT ?)", horodatage);
            long synchronisations = suppression.supprimer("DELETE FROM sync_run WHERE id IN (SELECT r.id FROM sync_run r"
                    + " WHERE " + SYNCHRONISATION_ECHUE
                    + " AND NOT EXISTS (SELECT 1 FROM sync_attempt a WHERE a.run_id = r.id) LIMIT ?)", horodatage);
            return tentatives + synchronisations;
        }
    }

    /** Avis mis à l'écart, contenu brut compris ({@code argos.conservation.avis-mis-a-l-ecart}). */
    @ApplicationScoped
    public static class AvisMisALEcart implements RegleConservation {

        @Inject
        SuppressionParLots suppression;

        @Override
        public String nom() {
            return "avis-mis-a-l-ecart";
        }

        @Override
        public long purger(Instant limite) {
            return suppression.supprimer("DELETE FROM ingestion_failure WHERE id IN"
                    + " (SELECT id FROM ingestion_failure WHERE received_at < ? LIMIT ?)", Conversions.utc(limite));
        }
    }

    /** Événements d'exploitation des sources ({@code argos.conservation.evenements-exploitation}). */
    @ApplicationScoped
    public static class EvenementsExploitation implements RegleConservation {

        @Inject
        SuppressionParLots suppression;

        @Override
        public String nom() {
            return "evenements-exploitation";
        }

        @Override
        public long purger(Instant limite) {
            return suppression.supprimer("DELETE FROM ops_event WHERE id IN"
                    + " (SELECT id FROM ops_event WHERE occurred_at < ? LIMIT ?)", Conversions.utc(limite));
        }
    }
}
