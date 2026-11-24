package io.argos.scm;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
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
    public DemandesEvolution(ParametresForges parametres,
            @ConfigProperty(name = "argos.scm.github.web-url", defaultValue = "https://github.com") String githubWeb,
            Clock horloge) {
        this.http = new ForgeHttp(parametres.timeout(), horloge);
        this.githubWeb = URI.create(sansBarre(githubWeb));
        this.githubApi = sansBarre(parametres.githubUrl());
        this.gitlab = URI.create(sansBarre(parametres.gitlabUrl()));
        this.githubToken = parametres.githubToken().filter(jeton -> !jeton.isBlank());
        this.gitlabToken = parametres.gitlabToken().filter(jeton -> !jeton.isBlank());
    }

    /** Demande désignée par l'adresse, si elle désigne un ticket ou une demande de fusion d'une forge configurée. */
    public Optional<Demande> reconnaitre(String adresse) {
        if (adresse == null || adresse.length() > 500) {
            return Optional.empty();
        }
        URI uri;
        try {
            uri = new URI(adresse.strip());
        } catch (URISyntaxException malFormee) {
            return Optional.empty();
        }
        if (uri.getRawQuery() != null || uri.getRawFragment() != null || uri.getRawUserInfo() != null
                || uri.getHost() == null || uri.getRawPath() == null) {
            return Optional.empty();
        }
        if (memeOrigine(uri, githubWeb)) {
            Matcher correspondance = GITHUB.matcher(uri.getRawPath());
            if (correspondance.matches() && ForgeHttp.depotGithubValide(correspondance.group(1))) {
                return Optional.of(new Demande(Forge.GITHUB, correspondance.group(1), correspondance.group(2),
                        Integer.parseInt(correspondance.group(3)), origine(githubWeb) + "/" + correspondance.group(1) + "/"
                        + correspondance.group(2) + "/" + correspondance.group(3)));
            }
        }
        if (memeOrigine(uri, gitlab)) {
            String prefixe = gitlab.getRawPath() == null ? "" : gitlab.getRawPath();
            String chemin = uri.getRawPath();
            if (chemin.startsWith(prefixe + "/")) {
                Matcher correspondance = GITLAB.matcher(chemin.substring(prefixe.length()));
                if (correspondance.matches() && correspondance.group(1).matches("[\\w.-]+(/[\\w.-]+)+")
                        && !correspondance.group(1).contains("..")) {
                    return Optional.of(new Demande(Forge.GITLAB, correspondance.group(1), correspondance.group(2),
                            Integer.parseInt(correspondance.group(3)), origine(gitlab) + prefixe + "/" + correspondance.group(1)
                            + "/-/" + correspondance.group(2) + "/" + correspondance.group(3)));
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
    public Optional<String> etat(Demande demande) {
        URI uri = switch (demande.forge()) {
            case GITHUB -> URI.create(githubApi + "/repos/" + demande.depot() + "/" + ("pull".equals(demande.nature()) ? "pulls" : "issues")
                    + "/" + demande.numero());
            case GITLAB -> URI.create(origine(gitlab) + (gitlab.getRawPath() == null ? "" : gitlab.getRawPath())
                    + "/api/v4/projects/" + URLEncoder.encode(demande.depot(), StandardCharsets.UTF_8) + "/" + demande.nature() + "/"
                    + demande.numero());
        };
        ForgeHttp.Reponse reponse = http.get(uri, demande.forge() == Forge.GITHUB ? githubToken : gitlabToken,
                demande.forge() == Forge.GITHUB, demande.forge() == Forge.GITHUB ? "application/vnd.github+json" : null, TAILLE_MAX);
        if (reponse.status() == 404) {
            return Optional.empty();
        }
        if (reponse.status() != 200) {
            throw new ScmException("HTTP " + reponse.status() + " de " + uri.getHost(), null);
        }
        try {
            JsonNode noeud = json.readTree(reponse.corps());
            String etat = noeud.path("state").asText("");
            if (demande.forge() == Forge.GITHUB && noeud.path("merged").asBoolean(false)) {
                etat = "merged";
            }
            etat = etat.toLowerCase(Locale.ROOT);
            return Optional.of(etat.matches("[a-z_]{1,16}") ? etat : "inconnu");
        } catch (IOException erreur) {
            throw new ScmException("réponse illisible de " + uri.getHost(), erreur);
        }
    }

    private static boolean memeOrigine(URI uri, URI attendue) {
        return uri.getScheme() != null && uri.getScheme().equalsIgnoreCase(attendue.getScheme())
                && uri.getHost().equalsIgnoreCase(attendue.getHost()) && port(uri) == port(attendue);
    }

    private static int port(URI uri) {
        if (uri.getPort() >= 0) {
            return uri.getPort();
        }
        return "https".equalsIgnoreCase(uri.getScheme()) ? 443 : 80;
    }

    private static String origine(URI uri) {
        return uri.getScheme() + "://" + uri.getRawAuthority();
    }

    private static String sansBarre(String valeur) {
        return valeur.replaceAll("/+$", "");
    }
}
