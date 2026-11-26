package io.argos.ingestion;

import java.time.Instant;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import io.argos.commun.Conversions;
import io.argos.conservation.RegleConservation;
import io.argos.conservation.RegleParHorodatage;
import io.argos.conservation.SuppressionParLots;

/**
 * Règles de conservation des traces de la collecte des avis (ADR-0041, EXP-17) : synchronisations terminées et leurs
 * tentatives, avis mis à l'écart (COL-08), événements d'exploitation (COL-07).
 */
public final class ConservationCollecte {

    /**
     * Tentatives d'une synchronisation supprimable : terminée avant la limite et citée par aucun avis mis à l'écart
     * encore conservé. Une synchronisation en cours ou à reprendre ({@code RUNNING}, {@code RETRYABLE}) n'est jamais
     * concernée.
     */
    private static final String SUPPRESSION_TENTATIVES = """
            DELETE FROM sync_attempt WHERE id IN (SELECT a.id FROM sync_attempt a JOIN sync_run r ON r.id = a.run_id
              WHERE r.state IN ('SUCCEEDED', 'FAILED') AND r.finished_at < ?
                AND NOT EXISTS (SELECT 1 FROM ingestion_failure f WHERE f.run_id = r.id)
              LIMIT ?)
            """;

    /** Synchronisations supprimables, mêmes conditions, une fois leurs tentatives supprimées. */
    private static final String SUPPRESSION_SYNCHRONISATIONS = """
            DELETE FROM sync_run WHERE id IN (SELECT r.id FROM sync_run r
              WHERE r.state IN ('SUCCEEDED', 'FAILED') AND r.finished_at < ?
                AND NOT EXISTS (SELECT 1 FROM ingestion_failure f WHERE f.run_id = r.id)
                AND NOT EXISTS (SELECT 1 FROM sync_attempt a WHERE a.run_id = r.id)
              LIMIT ?)
            """;

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
            long tentatives = suppression.supprimer(SUPPRESSION_TENTATIVES, horodatage);
            long synchronisations = suppression.supprimer(SUPPRESSION_SYNCHRONISATIONS, horodatage);
            return tentatives + synchronisations;
        }
    }

    /** Avis mis à l'écart, contenu brut compris ({@code argos.conservation.avis-mis-a-l-ecart}). */
    @ApplicationScoped
    public static class AvisMisALEcart extends RegleParHorodatage {

        public AvisMisALEcart() {
            super("avis-mis-a-l-ecart", "DELETE FROM ingestion_failure WHERE id IN"
                    + " (SELECT id FROM ingestion_failure WHERE received_at < ? LIMIT ?)");
        }
    }

    /** Événements d'exploitation des sources ({@code argos.conservation.evenements-exploitation}). */
    @ApplicationScoped
    public static class EvenementsExploitation extends RegleParHorodatage {

        public EvenementsExploitation() {
            super("evenements-exploitation", "DELETE FROM ops_event WHERE id IN (SELECT id FROM ops_event WHERE occurred_at < ? LIMIT ?)");
        }
    }
}
