package io.argos.scm;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.Locale;
import java.util.Optional;

/**
 * Accès commun aux forges GitLab et GitHub ({@code argos.scm.*}) : client HTTP sans redirection
 * ({@link ForgeHttp}), adresses de base sans « / » final et jetons facultatifs, un jeton vide ou blanc valant
 * absence de jeton. Partagé par {@link RepositoryFiles} et {@link RepositoryActivity}.
 *
 * <p>Classe et non record : les jetons d'accès restent des champs privés, hors de {@link #toString()},
 * {@code equals} et {@code hashCode}, pour qu'aucun journal ne puisse les exposer.
 */
final class AccesForges {

    private final ForgeHttp http;
    private final String gitlabUrl;
    private final String githubUrl;
    private final String gitlabToken;
    private final String githubToken;

    /**
     * @param http client HTTP en lecture seule, qui ne suit aucune redirection
     * @param gitlabUrl adresse de base de GitLab ; un « / » final est retiré
     * @param githubUrl adresse de base de l'API GitHub ; un « / » final est retiré
     * @param gitlabToken jeton GitLab, {@code null}, vide ou blanc s'il n'est pas configuré
     * @param githubToken jeton GitHub, {@code null}, vide ou blanc s'il n'est pas configuré
     */
    AccesForges(ForgeHttp http, String gitlabUrl, String githubUrl, String gitlabToken, String githubToken) {
        this.http = http;
        this.gitlabUrl = sansBarreFinale(gitlabUrl);
        this.githubUrl = sansBarreFinale(githubUrl);
        this.gitlabToken = renseigne(gitlabToken);
        this.githubToken = renseigne(githubToken);
    }

    /** Accès construit depuis la configuration : le délai borne la connexion et l'attente des en-têtes. */
    static AccesForges configure(ParametresForges parametres, Clock horloge) {
        // Aucune redirection suivie : le jeton d'accès ne doit pas partir vers un autre hôte (ForgeHttp).
        return new AccesForges(new ForgeHttp(parametres.timeout(), horloge), parametres.gitlabUrl(), parametres.githubUrl(),
                parametres.gitlabToken().orElse(null), parametres.githubToken().orElse(null));
    }

    /** Client HTTP en lecture seule, qui ne suit aucune redirection. */
    ForgeHttp http() {
        return http;
    }

    /** Adresse de base de GitLab, sans « / » final. */
    String gitlabUrl() {
        return gitlabUrl;
    }

    /** Adresse de base de l'API GitHub, sans « / » final. */
    String githubUrl() {
        return githubUrl;
    }

    /** Jeton GitLab, absent s'il n'est pas configuré ou blanc. */
    Optional<String> gitlabToken() {
        return Optional.ofNullable(gitlabToken);
    }

    /** Jeton GitHub, absent s'il n'est pas configuré ou blanc. */
    Optional<String> githubToken() {
        return Optional.ofNullable(githubToken);
    }

    /** Forge d'un dépôt en majuscules ({@code GITLAB}, {@code GITHUB}) ; chaîne vide si elle n'est pas renseignée. */
    static String provider(RepositoryRef ref) {
        return ref.provider() == null ? "" : ref.provider().toUpperCase(Locale.ROOT);
    }

    /** Encodage d'un paramètre ou d'un segment d'URL, l'espace étant codé {@code %20}. */
    static String enc(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }

    /** Description sans les jetons, dont seule la présence est indiquée. */
    @Override
    public String toString() {
        return "AccesForges[gitlabUrl=" + gitlabUrl + ", githubUrl=" + githubUrl
                + ", jetonGitlab=" + (gitlabToken != null) + ", jetonGithub=" + (githubToken != null) + "]";
    }

    private static String sansBarreFinale(String url) {
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }

    private static String renseigne(String jeton) {
        return jeton == null || jeton.isBlank() ? null : jeton;
    }
}
