package io.argos.scm;

import java.time.Duration;
import java.util.Optional;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.Dependent;
import jakarta.enterprise.inject.Produces;

import org.eclipse.microprofile.config.inject.ConfigProperty;

/**
 * Accès configuré aux forges GitLab et GitHub, commun aux lecteurs de dépôts, d'activité et de demandes d'évolution.
 *
 * @param timeout délai de connexion et d'attente des en-têtes ({@code argos.sources.http-timeout})
 * @param gitlabUrl adresse de base de GitLab ({@code argos.scm.gitlab.url})
 * @param githubUrl adresse de base de l'API GitHub ({@code argos.scm.github.url})
 * @param gitlabToken jeton GitLab, absent s'il n'est pas configuré ({@code argos.scm.gitlab.token})
 * @param githubToken jeton GitHub, absent s'il n'est pas configuré ({@code argos.scm.github.token})
 */
public record ParametresForges(Duration timeout, String gitlabUrl, String githubUrl, Optional<String> gitlabToken,
        Optional<String> githubToken) {

    /** Produit les paramètres des forges depuis la configuration. */
    @ApplicationScoped
    static class Producteur {

        @ConfigProperty(name = "argos.sources.http-timeout")
        Duration timeout;

        @ConfigProperty(name = "argos.scm.gitlab.url")
        String gitlabUrl;

        @ConfigProperty(name = "argos.scm.github.url")
        String githubUrl;

        @ConfigProperty(name = "argos.scm.gitlab.token")
        Optional<String> gitlabToken;

        @ConfigProperty(name = "argos.scm.github.token")
        Optional<String> githubToken;

        @Produces
        @Dependent
        ParametresForges parametres() {
            return new ParametresForges(timeout, gitlabUrl, githubUrl, gitlabToken, githubToken);
        }
    }
}
