package io.argos.observabilite;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;

import org.jboss.logging.MDC;

import io.quarkus.vertx.http.runtime.filters.Filters;
import io.vertx.ext.web.RoutingContext;

/**
 * Pose l'identifiant de corrélation de chaque requête HTTP (API REST, serveur MCP, points d'exploitation) avant
 * l'authentification et le traitement, et le restitue dans l'en-tête {@value Correlation#EN_TETE_REPONSE}
 * (EXP-13-S1, design.md D2). Le MDC de Quarkus suit le contexte Vert.x de la requête : l'identifiant reste visible
 * sur le fil de travail qui exécute une ressource bloquante.
 */
@ApplicationScoped
public class FiltreCorrelation {

    /** Priorité du filtre : avant les filtres de sécurité et de journal d'accès de Quarkus. */
    static final int PRIORITE = 10_000;

    void enregistrer(@Observes Filters filtres) {
        filtres.register(FiltreCorrelation::correler, PRIORITE);
    }

    static void correler(RoutingContext contexte) {
        String identifiant = Correlation.nouvelIdentifiant();
        MDC.put(Correlation.CLE_CORRELATION, identifiant);
        contexte.response().putHeader(Correlation.EN_TETE_REPONSE, identifiant);
        contexte.addEndHandler(fin -> MDC.remove(Correlation.CLE_CORRELATION));
        contexte.next();
    }
}
