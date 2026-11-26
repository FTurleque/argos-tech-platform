package io.argos.inventory;

import java.util.List;

import io.argos.scm.RepositoryRef;

/**
 * Dépôt saisi pour un projet par l'administrateur de son espace (INV-12), déjà validé par l'API.
 *
 * @param forge {@code GITHUB} ou {@code GITLAB}
 * @param depot identifiant du dépôt sur la forge
 * @param branche branche principale
 * @param jobSbom job CI GitLab qui publie la SBOM, ou {@code null}
 * @param cheminSbom chemin de la SBOM, ou {@code null}
 * @param manifestes manifestes de l'inventaire de repli ; vide pour {@link ProjectDirectory#DEFAULT_MANIFESTS}
 */
public record DepotProjet(String forge, String depot, String branche, String jobSbom, String cheminSbom, List<String> manifestes) {

    /** Vrai si l'identifiant du dépôt est lisible par sa forge (forme {@code owner/repo} pour GitHub). */
    public boolean identifiantValide() {
        return reference().identifiantValide();
    }

    /** Manifestes déclarés, ou les manifestes par défaut si aucun ne l'est. */
    List<String> manifestesOuDefaut() {
        return manifestes == null || manifestes.isEmpty() ? ProjectDirectory.DEFAULT_MANIFESTS : manifestes;
    }

    RepositoryRef reference() {
        return new RepositoryRef(forge, depot, branche, jobSbom, cheminSbom);
    }
}
