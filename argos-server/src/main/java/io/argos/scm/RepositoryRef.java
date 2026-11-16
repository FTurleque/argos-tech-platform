package io.argos.scm;

/**
 * Dépôt d'un projet suivi.
 *
 * @param provider {@code GITLAB} ou {@code GITHUB}
 * @param repository identifiant du dépôt : chemin ou identifiant numérique GitLab, {@code owner/repo} GitHub
 * @param branch branche principale
 * @param sbomJob job CI GitLab qui publie la SBOM en artefact (ignoré pour GitHub)
 * @param sbomPath chemin de la SBOM : dans les artefacts du job (GitLab) ou dans le dépôt (GitHub)
 */
public record RepositoryRef(String provider, String repository, String branch, String sbomJob, String sbomPath) {
}
