package io.argos.api.security;

import java.net.URI;
import java.util.List;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;

import org.eclipse.microprofile.config.inject.ConfigProperty;

import io.quarkus.vertx.http.runtime.filters.Filters;
import io.vertx.ext.web.RoutingContext;

/**
 * Pose les en-têtes de sécurité de l'IHM servie et de l'API (ACC-12, design.md D3) : politique de sécurité du
 * contenu alignée sur celle du client desktop avec l'origine de l'émetteur public configuré, interdiction
 * d'affichage en cadre, type de contenu non deviné et politique de référent. Un filtre Vert.x couvre les
 * ressources statiques, que ni un filtre JAX-RS ni une balise {@code meta} ne couvrent (la balise ignore
 * {@code frame-ancestors} et l'émetteur n'est connu qu'à l'exécution).
 */
@ApplicationScoped
public class EnTetesSecurite {

    static final String CONTENT_SECURITY_POLICY = "Content-Security-Policy";
    static final String X_CONTENT_TYPE_OPTIONS = "X-Content-Type-Options";
    static final String REFERRER_POLICY = "Referrer-Policy";
    static final String VALEUR_NOSNIFF = "nosniff";
    static final String VALEUR_SANS_REFERENT = "no-referrer";

    /** Priorité du filtre : avant les filtres de sécurité de Quarkus, pour couvrir aussi les refus 401. */
    static final int PRIORITE = 9_000;

    /** Pages techniques du mode développement, dont les scripts en ligne exigent une politique plus large. */
    private static final List<String> CHEMINS_OUTILS_DEVELOPPEMENT = List.of("/q/dev", "/q/swagger-ui");

    private final String politiqueContenu;

    EnTetesSecurite(@ConfigProperty(name = "argos.ui.oidc.emetteur") String emetteurPublic) {
        this.politiqueContenu = politiqueContenu(emetteurPublic);
    }

    void enregistrer(@Observes Filters filtres) {
        filtres.register(this::poser, PRIORITE);
    }

    void poser(RoutingContext contexte) {
        if (!estOutilDeDeveloppement(contexte.normalizedPath())) {
            contexte.response()
                    .putHeader(CONTENT_SECURITY_POLICY, politiqueContenu)
                    .putHeader(X_CONTENT_TYPE_OPTIONS, VALEUR_NOSNIFF)
                    .putHeader(REFERRER_POLICY, VALEUR_SANS_REFERENT);
        }
        contexte.next();
    }

    private static boolean estOutilDeDeveloppement(String chemin) {
        return CHEMINS_OUTILS_DEVELOPPEMENT.stream().anyMatch(chemin::startsWith);
    }

    /**
     * Politique de sécurité du contenu de l'IHM : contenu limité à son origine, connexions limitées à son origine
     * et à celle de l'émetteur, ni objet ni cadre ni base d'adresse relative. Reprend celle du client desktop
     * ({@code argos-desktop/src/ui-protocol.js}), dont {@code style-src 'unsafe-inline'}.
     *
     * @throws IllegalArgumentException si l'émetteur n'est pas une adresse absolue
     */
    static String politiqueContenu(String emetteurPublic) {
        return String.join("; ",
                "default-src 'self'",
                "script-src 'self'",
                "style-src 'self' 'unsafe-inline'",
                "img-src 'self' data:",
                "font-src 'self'",
                "connect-src 'self' " + origine(emetteurPublic),
                "object-src 'none'",
                "base-uri 'none'",
                "frame-ancestors 'none'");
    }

    /** Origine (schéma, hôte et port) d'une adresse absolue. */
    static String origine(String adresse) {
        URI uri = URI.create(adresse);
        if (uri.getScheme() == null || uri.getHost() == null) {
            throw new IllegalArgumentException("Émetteur OIDC public : adresse absolue attendue, reçu « " + adresse + " »");
        }
        String port = uri.getPort() < 0 ? "" : ":" + uri.getPort();
        return uri.getScheme() + "://" + uri.getHost() + port;
    }
}
