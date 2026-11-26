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
 * @param manifestes manifestes de l'inventaire de repli, ou {@code null} pour {@link ProjectDirectory#DEFAULT_MANIFESTS}
 */
public record DepotProjet(String forge, String depot, String branche, String jobSbom, String cheminSbom, List<String> manifestes) {

    RepositoryRef reference() {
        return new RepositoryRef(forge, depot, branche, jobSbom, cheminSbom);
    }
}
