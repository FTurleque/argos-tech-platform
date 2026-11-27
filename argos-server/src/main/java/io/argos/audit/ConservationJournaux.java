package io.argos.audit;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import io.argos.conservation.RegleConservation;
import io.argos.conservation.RegleParHorodatage;
import io.argos.conservation.SuppressionParLots;

/**
 * Règles de conservation des journaux du module {@code audit} (ADR-0041, EXP-17) : refus d'accès, appels MCP et
 * appels de la passerelle IA, supprimés au-delà de leur durée réglée, d'après leur colonne {@code horodatage}.
 */
@ApplicationScoped
public class ConservationJournaux {

    private static final String COLONNE_HORODATAGE = "horodatage";

    @Inject
    SuppressionParLots suppression;

    /** Journal des refus d'accès ({@code argos.conservation.refus-acces}). */
    @Produces
    @Singleton
    RegleConservation refusAcces() {
        return regle("refus-acces", "refus_acces");
    }

    /** Journal des appels d'outils MCP ({@code argos.conservation.appels-mcp}). */
    @Produces
    @Singleton
    RegleConservation appelsMcp() {
        return regle("appels-mcp", "mcp_appel");
    }

    /** Journal des appels de la passerelle IA ({@code argos.conservation.appels-ia}). */
    @Produces
    @Singleton
    RegleConservation appelsIa() {
        return regle("appels-ia", "ia_appel");
    }

    private RegleConservation regle(String nomRegle, String table) {
        return RegleParHorodatage.produite(suppression, nomRegle, table, COLONNE_HORODATAGE);
    }
}
