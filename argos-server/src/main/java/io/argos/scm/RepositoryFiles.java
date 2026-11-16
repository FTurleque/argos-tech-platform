package io.argos.scm;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Locale;
import java.util.Optional;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.eclipse.microprofile.config.inject.ConfigProperty;

/**
 * Récupère la SBOM de la branche principale et les manifestes d'un dépôt (INV-01, INV-04).
 * Ne renvoie que des octets : aucun objet propre à GitLab ou GitHub ne sort de ce module
 * (arc42 §5, « scm-connector »).
 *
 * <ul>
 *   <li>GitLab : SBOM = artefact du dernier job réussi de la branche,
 *       {@code GET /api/v4/projects/:id/jobs/artifacts/:ref/raw/:path?job=:job} ;
 *       fichier = {@code GET /api/v4/projects/:id/repository/files/:path/raw?ref=:ref}.</li>
 *   <li>GitHub : SBOM et fichiers lus dans le dépôt,
 *       {@code GET /repos/:owner/:repo/contents/:path?ref=:ref} (média {@code raw}).</li>
 * </ul>
 */
@ApplicationScoped
public class RepositoryFiles {

    private final ForgeHttp http;
    private final String gitlabUrl;
    private final String githubUrl;
    private final Optional<String> gitlabToken;
    private final Optional<String> githubToken;
    private final long maxBytes;

    @Inject
    public RepositoryFiles(@ConfigProperty(name = "argos.sources.http-timeout") Duration timeout,
            @ConfigProperty(name = "argos.scm.gitlab.url") String gitlabUrl,
            @ConfigProperty(name = "argos.scm.github.url") String githubUrl,
            @ConfigProperty(name = "argos.scm.gitlab.token") Optional<String> gitlabToken,
            @ConfigProperty(name = "argos.scm.github.token") Optional<String> githubToken,
            @ConfigProperty(name = "argos.inventory.max-document-bytes") long maxBytes) {
        // Aucune redirection suivie : le jeton d'accès ne doit pas partir vers un autre hôte (ForgeHttp).
        this.http = new ForgeHttp(timeout);
        this.gitlabUrl = trim(gitlabUrl);
        this.githubUrl = trim(githubUrl);
        this.gitlabToken = gitlabToken.filter(t -> !t.isBlank());
        this.githubToken = githubToken.filter(t -> !t.isBlank());
        this.maxBytes = maxBytes;
    }

    /** Dernière SBOM produite pour la branche principale ; vide si aucune n'est disponible. */
    public Optional<byte[]> latestSbom(RepositoryRef ref) {
        if (ref.sbomPath() == null || ref.sbomPath().isBlank()) {
            return Optional.empty();
        }
        return switch (provider(ref)) {
            case "GITLAB" -> {
                if (ref.sbomJob() == null || ref.sbomJob().isBlank()) {
                    yield Optional.empty();
                }
                yield fetch(gitlab(ref, "/jobs/artifacts/" + enc(ref.branch()) + "/raw/" + path(ref.sbomPath())
                        + "?job=" + enc(ref.sbomJob())), gitlabToken, false);
            }
            case "GITHUB" -> file(ref, ref.sbomPath());
            default -> throw new ScmException("forge non prise en charge : " + ref.provider(), null);
        };
    }

    /** Fichier du dépôt sur la branche principale ; vide s'il n'existe pas. */
    public Optional<byte[]> file(RepositoryRef ref, String filePath) {
        return switch (provider(ref)) {
            case "GITLAB" -> fetch(gitlab(ref, "/repository/files/" + enc(filePath) + "/raw?ref=" + enc(ref.branch())),
                    gitlabToken, false);
            case "GITHUB" -> fetch(URI.create(githubUrl + "/repos/" + githubRepository(ref) + "/contents/" + path(filePath)
                    + "?ref=" + enc(ref.branch())), githubToken, true);
            default -> throw new ScmException("forge non prise en charge : " + ref.provider(), null);
        };
    }

    private URI gitlab(RepositoryRef ref, String suffix) {
        return URI.create(gitlabUrl + "/api/v4/projects/" + enc(ref.repository()) + suffix);
    }

    private Optional<byte[]> fetch(URI uri, Optional<String> token, boolean github) {
        ForgeHttp.Reponse r = http.get(uri, token, github, github ? "application/vnd.github.raw+json" : null, maxBytes);
        if (r.status() == 404) {
            return Optional.empty();
        }
        if (r.status() != 200) {
            throw new ScmException("HTTP " + r.status() + " de " + uri.getHost(), null);
        }
        return Optional.of(r.corps());
    }

    private static String githubRepository(RepositoryRef ref) {
        if (ref.repository() == null || !ref.repository().matches("[\\w.-]+/[\\w.-]+")) {
            throw new ScmException("dépôt GitHub invalide : attendu owner/repo", null);
        }
        return ref.repository();
    }

    private static String provider(RepositoryRef ref) {
        return ref.provider() == null ? "" : ref.provider().toUpperCase(Locale.ROOT);
    }

    private static String enc(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }

    /** Encode chaque segment d'un chemin en conservant les « / ». */
    private static String path(String value) {
        StringBuilder sb = new StringBuilder();
        for (String segment : value.split("/")) {
            if (segment.isEmpty() || "..".equals(segment) || ".".equals(segment)) {
                continue;
            }
            if (sb.length() > 0) {
                sb.append('/');
            }
            sb.append(enc(segment));
        }
        return sb.toString();
    }

    private static String trim(String url) {
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }
}
