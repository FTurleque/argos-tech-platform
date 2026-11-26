package io.argos.audit;

import java.time.Instant;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import io.argos.commun.Conversions;
import io.argos.conservation.RegleConservation;
import io.argos.conservation.SuppressionParLots;

/**
 * Règles de conservation des journaux du module {@code audit} (ADR-0041, EXP-17) : refus d'accès, appels MCP et
 * appels de la passerelle IA, supprimés au-delà de leur durée réglée, d'après leur horodatage.
 */
public final class ConservationJournaux {

    private ConservationJournaux() {
    }

    /** Journal des refus d'accès ({@code argos.conservation.refus-acces}). */
    @ApplicationScoped
    public static class RefusAcces implements RegleConservation {

        @Inject
        SuppressionParLots suppression;

        @Override
        public String nom() {
            return "refus-acces";
        }

        @Override
        public long purger(Instant limite) {
            return suppression.supprimer("DELETE FROM refus_acces WHERE id IN"
                    + " (SELECT id FROM refus_acces WHERE horodatage < ? LIMIT ?)", Conversions.utc(limite));
        }
    }

    /** Journal des appels d'outils MCP ({@code argos.conservation.appels-mcp}). */
    @ApplicationScoped
    public static class AppelsMcp implements RegleConservation {

        @Inject
        SuppressionParLots suppression;

        @Override
        public String nom() {
            return "appels-mcp";
        }

        @Override
        public long purger(Instant limite) {
            return suppression.supprimer("DELETE FROM mcp_appel WHERE id IN"
                    + " (SELECT id FROM mcp_appel WHERE horodatage < ? LIMIT ?)", Conversions.utc(limite));
        }
    }

    /** Journal des appels de la passerelle IA ({@code argos.conservation.appels-ia}). */
    @ApplicationScoped
    public static class AppelsIa implements RegleConservation {

        @Inject
        SuppressionParLots suppression;

        @Override
        public String nom() {
            return "appels-ia";
        }

        @Override
        public long purger(Instant limite) {
            return suppression.supprimer("DELETE FROM ia_appel WHERE id IN"
                    + " (SELECT id FROM ia_appel WHERE horodatage < ? LIMIT ?)", Conversions.utc(limite));
        }
    }
}
