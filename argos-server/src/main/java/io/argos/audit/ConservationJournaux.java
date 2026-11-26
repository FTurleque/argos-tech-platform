package io.argos.audit;

import jakarta.enterprise.context.ApplicationScoped;

import io.argos.conservation.RegleParHorodatage;

/**
 * Règles de conservation des journaux du module {@code audit} (ADR-0041, EXP-17) : refus d'accès, appels MCP et
 * appels de la passerelle IA, supprimés au-delà de leur durée réglée, d'après leur horodatage.
 */
public final class ConservationJournaux {

    private ConservationJournaux() {
    }

    /** Journal des refus d'accès ({@code argos.conservation.refus-acces}). */
    @ApplicationScoped
    public static class RefusAcces extends RegleParHorodatage {

        public RefusAcces() {
            super("refus-acces", "DELETE FROM refus_acces WHERE id IN (SELECT id FROM refus_acces WHERE horodatage < ? LIMIT ?)");
        }
    }

    /** Journal des appels d'outils MCP ({@code argos.conservation.appels-mcp}). */
    @ApplicationScoped
    public static class AppelsMcp extends RegleParHorodatage {

        public AppelsMcp() {
            super("appels-mcp", "DELETE FROM mcp_appel WHERE id IN (SELECT id FROM mcp_appel WHERE horodatage < ? LIMIT ?)");
        }
    }

    /** Journal des appels de la passerelle IA ({@code argos.conservation.appels-ia}). */
    @ApplicationScoped
    public static class AppelsIa extends RegleParHorodatage {

        public AppelsIa() {
            super("appels-ia", "DELETE FROM ia_appel WHERE id IN (SELECT id FROM ia_appel WHERE horodatage < ? LIMIT ?)");
        }
    }
}
