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

    /** Longueur maximale d'une adresse de demande. */
    private static final int ADRESSE_MAX = 500;
    private static final int HTTP_OK = 200;
    private static final int HTTP_NON_TROUVE = 404;
    private static final Pattern DEPOT_GITLAB = Pattern.compile("[\\w.-]+(/[\\w.-]+)+");
    private static final Pattern ETAT_ADMIS = Pattern.compile("[a-z_]{1,16}");
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
        Optional<URI> admise = adresseAdmise(adresse);
        if (admise.isEmpty()) {
            return Optional.empty();
        }
        URI uri = admise.get();
        Optional<Demande> github = memeOrigine(uri, githubWeb) ? demandeGithub(uri) : Optional.empty();
        return github.isPresent() || !memeOrigine(uri, gitlab) ? github : demandeGitlab(uri);
    }

    /** Adresse absolue analysable, d'au plus {@value #ADRESSE_MAX} caractères, sans requête, fragment ni identifiants. */
    private static Optional<URI> adresseAdmise(String adresse) {
        if (adresse == null || adresse.length() > ADRESSE_MAX) {
            return Optional.empty();
        }
        URI uri;
        try {
            uri = new URI(adresse.strip());
        } catch (URISyntaxException malFormee) {
            return Optional.empty();
        }
        boolean superflu = uri.getRawQuery() != null || uri.getRawFragment() != null || uri.getRawUserInfo() != null;
        return superflu || uri.getHost() == null || uri.getRawPath() == null ? Optional.empty() : Optional.of(uri);
    }

    /** Ticket ou demande de fusion GitHub désigné par le chemin de l'adresse. */
    private Optional<Demande> demandeGithub(URI uri) {
        Matcher correspondance = GITHUB.matcher(uri.getRawPath());
        if (!correspondance.matches() || !ForgeHttp.depotGithubValide(correspondance.group(1))) {
            return Optional.empty();
        }
        return Optional.of(new Demande(Forge.GITHUB, correspondance.group(1), correspondance.group(2),
                Integer.parseInt(correspondance.group(3)), origine(githubWeb) + "/" + correspondance.group(1) + "/"
                + correspondance.group(2) + "/" + correspondance.group(3)));
    }

    /** Ticket ou demande de fusion GitLab désigné par le chemin de l'adresse, sous le préfixe de l'instance. */
    private Optional<Demande> demandeGitlab(URI uri) {
        String prefixe = gitlab.getRawPath() == null ? "" : gitlab.getRawPath();
        String chemin = uri.getRawPath();
        if (!chemin.startsWith(prefixe + "/")) {
            return Optional.empty();
        }
        Matcher correspondance = GITLAB.matcher(chemin.substring(prefixe.length()));
        boolean valide = correspondance.matches() && depotGitlabValide(correspondance.group(1));
        if (!valide) {
            return Optional.empty();
        }
        return Optional.of(new Demande(Forge.GITLAB, correspondance.group(1), correspondance.group(2),
                Integer.parseInt(correspondance.group(3)), origine(gitlab) + prefixe + "/" + correspondance.group(1)
                + "/-/" + correspondance.group(2) + "/" + correspondance.group(3)));
    }

    /** Chemin de projet GitLab : groupes et projet de caractères sûrs, sans remontée. */
    private static boolean depotGitlabValide(String depot) {
        return DEPOT_GITLAB.matcher(depot).matches() && !depot.contains("..");
    }

    /**
     * État de la demande lu par l'API de la forge : {@code open}/{@code closed} (GitHub),
     * {@code opened}/{@code closed}/{@code merged} (GitLab). Vide si la demande n'existe pas.
     *
     * @throws ScmException si la forge est injoignable ou répond en erreur
     */
    public Optional<String> etat(Demande demande) {
        URI uri = adresseApi(demande);
        boolean github = demande.forge() == Forge.GITHUB;
        ForgeHttp.Reponse reponse = http.get(uri, github ? githubToken : gitlabToken, github,
                github ? "application/vnd.github+json" : null, TAILLE_MAX);
        if (reponse.status() == HTTP_NON_TROUVE) {
            return Optional.empty();
        }
        if (reponse.status() != HTTP_OK) {
            throw new ScmException("HTTP " + reponse.status() + " de " + uri.getHost(), null);
        }
        try {
            return Optional.of(etatLu(json.readTree(reponse.corps()), github));
        } catch (IOException erreur) {
            throw new ScmException("réponse illisible de " + uri.getHost(), erreur);
        }
    }

    /** Adresse de la demande dans l'API de sa forge. */
    private URI adresseApi(Demande demande) {
        return switch (demande.forge()) {
            case GITHUB -> URI.create(githubApi + "/repos/" + demande.depot() + "/"
                    + ("pull".equals(demande.nature()) ? "pulls" : "issues") + "/" + demande.numero());
            case GITLAB -> URI.create(origine(gitlab) + (gitlab.getRawPath() == null ? "" : gitlab.getRawPath())
                    + "/api/v4/projects/" + URLEncoder.encode(demande.depot(), StandardCharsets.UTF_8) + "/" + demande.nature()
                    + "/" + demande.numero());
        };
    }

    /** État normalisé en minuscules ; une demande GitHub fusionnée vaut {@code merged} ; une valeur inattendue, {@code inconnu}. */
    private static String etatLu(JsonNode noeud, boolean github) {
        String lu = github && noeud.path("merged").asBoolean(false) ? "merged" : noeud.path("state").asText("");
        String normalise = lu.toLowerCase(Locale.ROOT);
        return ETAT_ADMIS.matcher(normalise).matches() ? normalise : "inconnu";
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
