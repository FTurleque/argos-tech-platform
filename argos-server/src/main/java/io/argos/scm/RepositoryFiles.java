package io.argos.scm;

import static io.argos.scm.AccesForges.enc;
import static io.argos.scm.AccesForges.provider;

import java.io.IOException;
import java.net.URI;
import java.time.Duration;
import java.util.Optional;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.eclipse.microprofile.config.inject.ConfigProperty;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Récupère la SBOM de la branche principale et les manifestes d'un dépôt (INV-01, INV-04).
 * Ne renvoie que des octets, accompagnés pour une SBOM de son format reconnu (CycloneDX ou SPDX,
 * {@link SbomDocument}) : aucun objet propre à GitLab ou GitHub ne sort de ce module
 * (arc42 §5, « scm-connector »).
 *
 * <p>Une réponse 404 sur un fichier d'un dépôt existant signifie « absent ». Un dépôt introuvable
 * lève {@link DepotIntrouvable} ; un jeton refusé (401) ou un accès interdit (403 hors quota)
 * {@link AccesRefuse} ; un quota épuisé (429, ou 403 portant {@code x-ratelimit-remaining: 0},
 * {@code Retry-After} ou un message de limite de débit GitHub, voir {@link ForgeHttp#quota})
 * {@link QuotaEpuise} ; tout autre statut que 200, une redirection (jamais suivie) ou une réponse
 * au-delà de {@code argos.inventory.max-document-bytes} lève {@link ScmException}. Le délai
 * {@code argos.sources.http-timeout} borne l'établissement de la connexion et l'attente des en-têtes
 * de réponse ; la lecture du corps est bornée en taille, pas en durée. Les jetons, lus dans la
 * configuration, ne figurent dans aucun message.
 *
 * <p>Une {@link Lecture} regroupe les appels d'un même rafraîchissement : la vérification d'existence
 * d'un dépôt GitHub y est faite au plus une fois, quel que soit le nombre de fichiers absents.
 *
 * <ul>
 *   <li>GitLab : SBOM = artefact du dernier job réussi de la branche,
 *       {@code GET /api/v4/projects/:id/jobs/artifacts/:ref/raw/:path?job=:job} ;
 *       fichier = {@code GET /api/v4/projects/:id/repository/files/:path/raw?ref=:ref} ;
 *       un 404 dont le corps vaut {@code {"message":"404 Project Not Found"}} désigne un projet
 *       introuvable.</li>
 *   <li>GitHub : SBOM et fichiers lus dans le dépôt,
 *       {@code GET /repos/:owner/:repo/contents/:path?ref=:ref} (média {@code raw}) ; le 404 étant
 *       identique pour un fichier et pour un dépôt absents, un 404 est suivi de
 *       {@code GET /repos/:owner/:repo} pour les distinguer.</li>
 * </ul>
 *
 * <p>Aucun de ces points d'accès n'est paginé : la récupération ne suit ni en-tête {@code Link} ni
 * page suivante.
 */
@ApplicationScoped
public class RepositoryFiles {

    /** Message d'erreur de GitLab pour un projet absent ou invisible avec le jeton (API REST v4). */
    static final String PROJET_GITLAB_INTROUVABLE = "404 Project Not Found";

    private static final ObjectMapper JSON = new ObjectMapper();

    private final AccesForges forges;
    private final long maxBytes;

    @Inject
    public RepositoryFiles(@ConfigProperty(name = "argos.sources.http-timeout") Duration timeout,
            @ConfigProperty(name = "argos.scm.gitlab.url") String gitlabUrl,
            @ConfigProperty(name = "argos.scm.github.url") String githubUrl,
            @ConfigProperty(name = "argos.scm.gitlab.token") Optional<String> gitlabToken,
            @ConfigProperty(name = "argos.scm.github.token") Optional<String> githubToken,
            @ConfigProperty(name = "argos.inventory.max-document-bytes") long maxBytes) {
        this.forges = AccesForges.configure(timeout, gitlabUrl, githubUrl, gitlabToken.orElse(null),
                githubToken.orElse(null));
        this.maxBytes = maxBytes;
    }

    /** Lecture d'un dépôt pour un rafraîchissement : la vérification du dépôt y est mémorisée. */
    public Lecture lecture(RepositoryRef ref) {
        return new Lecture(ref);
    }

    /** Dernière SBOM produite pour la branche principale, avec son format ; vide si aucune n'est disponible. */
    public Optional<SbomDocument> latestSbom(RepositoryRef ref) {
        return lecture(ref).latestSbom();
    }

    /** Fichier du dépôt sur la branche principale ; vide s'il n'existe pas dans un dépôt existant. */
    public Optional<byte[]> file(RepositoryRef ref, String filePath) {
        return lecture(ref).file(filePath);
    }

    /**
     * Appels successifs sur un même dépôt (SBOM puis manifestes) : après un 404 GitHub, le dépôt
     * n'est interrogé qu'une fois, et son existence vaut pour les fichiers absents suivants.
     * Instance à usage unique, non partagée entre fils d'exécution.
     */
    public final class Lecture {

        private final RepositoryRef ref;
        private boolean depotVerifie;

        private Lecture(RepositoryRef ref) {
            this.ref = ref;
        }

        /** Dernière SBOM produite pour la branche principale, avec son format ; vide si aucune n'est disponible. */
        public Optional<SbomDocument> latestSbom() {
            if (ref.sbomPath() == null || ref.sbomPath().isBlank()) {
                return Optional.empty();
            }
            return switch (provider(ref)) {
                case "GITLAB" -> {
                    if (ref.sbomJob() == null || ref.sbomJob().isBlank()) {
                        yield Optional.empty();
                    }
                    yield fetch(gitlab(ref, "/jobs/artifacts/" + enc(ref.branch()) + "/raw/" + path(ref.sbomPath())
                            + "?job=" + enc(ref.sbomJob())), false).map(SbomDocument::de);
                }
                case "GITHUB" -> file(ref.sbomPath()).map(SbomDocument::de);
                default -> throw new ScmException("forge non prise en charge : " + ref.provider(), null);
            };
        }

        /** Fichier du dépôt sur la branche principale ; vide s'il n'existe pas dans un dépôt existant. */
        public Optional<byte[]> file(String filePath) {
            return switch (provider(ref)) {
                case "GITLAB" -> fetch(gitlab(ref, "/repository/files/" + enc(filePath) + "/raw?ref=" + enc(ref.branch())),
                        false);
                case "GITHUB" -> fetch(URI.create(forges.githubUrl() + "/repos/" + githubRepository(ref) + "/contents/"
                        + path(filePath) + "?ref=" + enc(ref.branch())), true);
                default -> throw new ScmException("forge non prise en charge : " + ref.provider(), null);
            };
        }

        private Optional<byte[]> fetch(URI uri, boolean github) {
            ForgeHttp.Reponse reponse = forges.http().get(uri, github ? forges.githubToken() : forges.gitlabToken(), github,
                    github ? "application/vnd.github.raw+json" : null, maxBytes);
            if (reponse.status() == 404) {
                verifierDepot(reponse, github);
                return Optional.empty();
            }
            verifierStatut(reponse.status(), uri);
            return Optional.of(reponse.corps());
        }

        /**
         * Distingue, après un 404, un fichier absent d'un dépôt introuvable : message d'erreur pour
         * GitLab, interrogation du dépôt (une fois par lecture) pour GitHub.
         *
         * @throws DepotIntrouvable si le dépôt n'existe pas ou n'est pas visible avec le jeton configuré
         */
        private void verifierDepot(ForgeHttp.Reponse reponse404, boolean github) {
            if (!github) {
                if (PROJET_GITLAB_INTROUVABLE.equals(message(reponse404.corps()))) {
                    throw new DepotIntrouvable("GITLAB", ref.repository());
                }
                return;
            }
            if (depotVerifie) {
                return;
            }
            URI uri = URI.create(forges.githubUrl() + "/repos/" + githubRepository(ref));
            ForgeHttp.Reponse reponse = forges.http().get(uri, forges.githubToken(), true, "application/vnd.github+json", maxBytes);
            if (reponse.status() == 404) {
                throw new DepotIntrouvable("GITHUB", ref.repository());
            }
            verifierStatut(reponse.status(), uri);
            depotVerifie = true;
        }
    }

    private URI gitlab(RepositoryRef ref, String suffix) {
        return URI.create(forges.gitlabUrl() + "/api/v4/projects/" + enc(ref.repository()) + suffix);
    }

    private static void verifierStatut(int status, URI uri) {
        if (status == 401 || status == 403) {
            throw new AccesRefuse(uri.getHost(), status);
        }
        if (status != 200) {
            throw new ScmException("HTTP " + status + " de " + uri.getHost(), null);
        }
    }

    /** Champ {@code message} d'un corps d'erreur JSON ; chaîne vide si le corps n'en contient pas. */
    static String message(byte[] corps) {
        if (corps.length == 0) {
            return "";
        }
        try {
            JsonNode noeud = JSON.readTree(corps);
            return noeud != null && noeud.path("message").isTextual() ? noeud.path("message").asText() : "";
        } catch (IOException illisible) {
            return "";
        }
    }

    private static String githubRepository(RepositoryRef ref) {
        if (!ForgeHttp.depotGithubValide(ref.repository())) {
            throw new ScmException("dépôt GitHub invalide : attendu owner/repo", null);
        }
        return ref.repository();
    }

    /** Encode chaque segment d'un chemin en conservant les « / ». */
    private static String path(String value) {
        StringBuilder cheminEncode = new StringBuilder();
        for (String segment : value.split("/")) {
            if (segment.isEmpty() || "..".equals(segment) || ".".equals(segment)) {
                continue;
            }
            if (cheminEncode.length() > 0) {
                cheminEncode.append('/');
            }
            cheminEncode.append(enc(segment));
        }
        return cheminEncode.toString();
    }
}
