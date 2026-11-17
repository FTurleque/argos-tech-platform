package io.argos.scm;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.eclipse.microprofile.config.inject.ConfigProperty;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Demandes d'évolution d'un outil de suivi externe (REG-08) : tickets et demandes de fusion GitHub et
 * GitLab, reconnus à partir de leur adresse web (Q-3 du changement {@code suivre-veille-reglementaire} :
 * GitHub et GitLab par les adapters {@code scm}, pas de Jira).
 *
 * <p>Seules les adresses des forges configurées sont reconnues : hôte et port exacts de
 * {@code argos.scm.github.web-url} et de {@code argos.scm.gitlab.url}, chemin d'un ticket ou d'une demande
 * de fusion. L'état est lu par l'API de la forge en lecture seule ({@link ForgeHttp} : {@code GET}, aucune
 * redirection, taille bornée, jeton facultatif jamais journalisé).
 */
@ApplicationScoped
public class DemandesEvolution {

    private static final Pattern GITHUB = Pattern.compile("/([\\w.-]+/[\\w.-]+)/(issues|pull)/(\\d{1,9})/?");
    private static final Pattern GITLAB = Pattern.compile("/(.+?)/-/(issues|merge_requests)/(\\d{1,9})/?");
    private static final long TAILLE_MAX = 1024L * 1024;

    /** Forge d'une demande. */
    public enum Forge { GITHUB, GITLAB }

    /** Demande reconnue : forge, dépôt, nature ({@code issues}, {@code pull}, {@code merge_requests}), numéro, adresse. */
    public record Demande(Forge forge, String depot, String nature, int numero, String adresse) {
    }

    private final ForgeHttp http;
    private final URI githubWeb;
    private final String githubApi;
    private final URI gitlab;
    private final Optional<String> githubToken;
    private final Optional<String> gitlabToken;
    private final ObjectMapper json = new ObjectMapper();

    @Inject
    public DemandesEvolution(@ConfigProperty(name = "argos.sources.http-timeout") Duration timeout,
            @ConfigProperty(name = "argos.scm.github.web-url", defaultValue = "https://github.com") String githubWeb,
            @ConfigProperty(name = "argos.scm.github.url") String githubApi,
            @ConfigProperty(name = "argos.scm.gitlab.url") String gitlab,
            @ConfigProperty(name = "argos.scm.github.token") Optional<String> githubToken,
            @ConfigProperty(name = "argos.scm.gitlab.token") Optional<String> gitlabToken) {
        this.http = new ForgeHttp(timeout);
        this.githubWeb = URI.create(sansBarre(githubWeb));
        this.githubApi = sansBarre(githubApi);
        this.gitlab = URI.create(sansBarre(gitlab));
        this.githubToken = githubToken.filter(t -> !t.isBlank());
        this.gitlabToken = gitlabToken.filter(t -> !t.isBlank());
    }

    /** Demande désignée par l'adresse, si elle désigne un ticket ou une demande de fusion d'une forge configurée. */
    public Optional<Demande> reconnaitre(String adresse) {
        if (adresse == null || adresse.length() > 500) {
            return Optional.empty();
        }
        URI uri;
        try {
            uri = new URI(adresse.strip());
        } catch (URISyntaxException e) {
            return Optional.empty();
        }
        if (uri.getRawQuery() != null || uri.getRawFragment() != null || uri.getRawUserInfo() != null
                || uri.getHost() == null || uri.getRawPath() == null) {
            return Optional.empty();
        }
        if (memeOrigine(uri, githubWeb)) {
            Matcher m = GITHUB.matcher(uri.getRawPath());
            if (m.matches() && ForgeHttp.depotGithubValide(m.group(1))) {
                return Optional.of(new Demande(Forge.GITHUB, m.group(1), m.group(2), Integer.parseInt(m.group(3)),
                        origine(githubWeb) + "/" + m.group(1) + "/" + m.group(2) + "/" + m.group(3)));
            }
        }
        if (memeOrigine(uri, gitlab)) {
            String prefixe = gitlab.getRawPath() == null ? "" : gitlab.getRawPath();
            String chemin = uri.getRawPath();
            if (chemin.startsWith(prefixe + "/")) {
                Matcher m = GITLAB.matcher(chemin.substring(prefixe.length()));
                if (m.matches() && m.group(1).matches("[\\w.-]+(/[\\w.-]+)+") && !m.group(1).contains("..")) {
                    return Optional.of(new Demande(Forge.GITLAB, m.group(1), m.group(2), Integer.parseInt(m.group(3)),
                            origine(gitlab) + prefixe + "/" + m.group(1) + "/-/" + m.group(2) + "/" + m.group(3)));
                }
            }
        }
        return Optional.empty();
    }

    /**
     * État de la demande lu par l'API de la forge : {@code open}/{@code closed} (GitHub),
     * {@code opened}/{@code closed}/{@code merged} (GitLab). Vide si la demande n'existe pas.
     *
     * @throws ScmException si la forge est injoignable ou répond en erreur
     */
    public Optional<String> etat(Demande d) {
        URI uri = switch (d.forge()) {
            case GITHUB -> URI.create(githubApi + "/repos/" + d.depot() + "/" + ("pull".equals(d.nature()) ? "pulls" : "issues")
                    + "/" + d.numero());
            case GITLAB -> URI.create(origine(gitlab) + (gitlab.getRawPath() == null ? "" : gitlab.getRawPath())
                    + "/api/v4/projects/" + URLEncoder.encode(d.depot(), StandardCharsets.UTF_8) + "/" + d.nature() + "/"
                    + d.numero());
        };
        ForgeHttp.Reponse r = http.get(uri, d.forge() == Forge.GITHUB ? githubToken : gitlabToken, d.forge() == Forge.GITHUB,
                d.forge() == Forge.GITHUB ? "application/vnd.github+json" : null, TAILLE_MAX);
        if (r.status() == 404) {
            return Optional.empty();
        }
        if (r.status() != 200) {
            throw new ScmException("HTTP " + r.status() + " de " + uri.getHost(), null);
        }
        try {
            JsonNode n = json.readTree(r.corps());
            String etat = n.path("state").asText("");
            if (d.forge() == Forge.GITHUB && n.path("merged").asBoolean(false)) {
                etat = "merged";
            }
            etat = etat.toLowerCase(Locale.ROOT);
            return Optional.of(etat.matches("[a-z_]{1,16}") ? etat : "inconnu");
        } catch (IOException e) {
            throw new ScmException("réponse illisible de " + uri.getHost(), e);
        }
    }

    private static boolean memeOrigine(URI uri, URI attendue) {
        return uri.getScheme() != null && uri.getScheme().equalsIgnoreCase(attendue.getScheme())
                && uri.getHost().equalsIgnoreCase(attendue.getHost()) && port(uri) == port(attendue);
    }

    private static int port(URI u) {
        if (u.getPort() >= 0) {
            return u.getPort();
        }
        return "https".equalsIgnoreCase(u.getScheme()) ? 443 : 80;
    }

    private static String origine(URI u) {
        return u.getScheme() + "://" + u.getRawAuthority();
    }

    private static String sansBarre(String s) {
        return s.replaceAll("/+$", "");
    }
}
