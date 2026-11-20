package io.argos.inventory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import javax.sql.DataSource;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import io.argos.scm.RepositoryRef;

/**
 * Projets suivis, chacun rattaché à un seul espace (table {@code espace}), et leurs dépôts (INV-10, décision J6 du
 * porteur du projet, 2026-11-19) : un projet regroupe zéro, un ou plusieurs dépôts ({@code project_repository}),
 * dont au plus un principal. Le dépôt donné à la création reste aussi décrit par les colonnes {@code scm_*} du
 * projet, lues par {@code projectintel} ([PROPOSITION] P-7 du changement automatiser-chaine-veille).
 */
@ApplicationScoped
public class ProjectDirectory {

    /** Manifestes lus par défaut pour l'inventaire de repli. */
    public static final List<String> DEFAULT_MANIFESTS = List.of("package.json", "requirements.txt");

    /** Projet suivi ; {@code repository} est absent si aucun dépôt n'est rattaché par les colonnes {@code scm_*}. */
    public record Project(UUID id, UUID workspaceId, String name, Optional<RepositoryRef> repository,
            List<String> manifestPaths) {
    }

    /**
     * Dépôt rattaché à un projet.
     *
     * @param ref fournisseur, identifiant, branche et emplacement de la SBOM du dépôt
     * @param manifestPaths manifestes lus pour l'inventaire de repli de ce dépôt
     * @param principal vrai pour le dépôt principal du projet (au plus un)
     */
    public record Depot(UUID id, UUID projectId, RepositoryRef ref, List<String> manifestPaths, boolean principal) {
    }

    @Inject
    DataSource dataSource;

    /**
     * Crée un projet ; un dépôt donné devient son dépôt principal ({@code project_repository}) et reste décrit par
     * les colonnes {@code scm_*} du projet.
     */
    @Transactional
    public UUID create(UUID workspaceId, String name, RepositoryRef repository, List<String> manifestPaths) {
        UUID id = UUID.randomUUID();
        String sql = """
                INSERT INTO project(id, workspace_id, name, scm_provider, scm_repository, scm_branch, sbom_job, sbom_path,
                                    manifest_paths)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;
        try (Connection connexion = dataSource.getConnection(); PreparedStatement requete = connexion.prepareStatement(sql)) {
            requete.setObject(1, id);
            requete.setObject(2, workspaceId);
            requete.setString(3, name);
            requete.setString(4, repository == null ? null : repository.provider());
            requete.setString(5, repository == null ? null : repository.repository());
            requete.setString(6, repository == null ? null : repository.branch());
            requete.setString(7, repository == null ? null : repository.sbomJob());
            requete.setString(8, repository == null ? null : repository.sbomPath());
            requete.setString(9, String.join(",", manifestPaths == null ? DEFAULT_MANIFESTS : manifestPaths));
            requete.executeUpdate();
        } catch (SQLException erreur) {
            if ("23503".equals(erreur.getSQLState())) {
                throw new IllegalArgumentException("espace inconnu : " + workspaceId, erreur);
            }
            throw new IllegalStateException("création du projet impossible", erreur);
        }
        if (repository != null) {
            insererDepot(id, repository, manifestPaths, true);
        }
        return id;
    }

    /**
     * Rattache un dépôt à un projet (INV-10) ; il devient principal si le projet n'en a pas. Aucune API ne l'expose :
     * l'administration des dépôts d'un projet est hors du changement ([PROPOSITION] P-7).
     */
    @Transactional
    public UUID ajouterDepot(UUID projectId, RepositoryRef repository, List<String> manifestPaths) {
        boolean principal = depots(projectId).stream().noneMatch(Depot::principal);
        return insererDepot(projectId, repository, manifestPaths, principal);
    }

    /** Dépôts d'un projet, le principal d'abord, puis par date de rattachement (traitement technique). */
    @Transactional
    public List<Depot> depots(UUID projectId) {
        String sql = """
                SELECT id, project_id, provider, repository, branch, sbom_job, sbom_path, manifest_paths, principal
                FROM project_repository WHERE project_id = ? ORDER BY principal DESC, created_at, id
                """;
        try (Connection connexion = dataSource.getConnection(); PreparedStatement requete = connexion.prepareStatement(sql)) {
            requete.setObject(1, projectId);
            try (ResultSet resultat = requete.executeQuery()) {
                List<Depot> depots = new ArrayList<>();
                while (resultat.next()) {
                    depots.add(new Depot(resultat.getObject(1, UUID.class), resultat.getObject(2, UUID.class),
                            new RepositoryRef(resultat.getString(3), resultat.getString(4), resultat.getString(5), resultat.getString(6),
                                    resultat.getString(7)),
                            chemins(resultat.getString(8)), resultat.getBoolean(9)));
                }
                return depots;
            }
        } catch (SQLException erreur) {
            throw new IllegalStateException("lecture des dépôts du projet impossible", erreur);
        }
    }

    /** Projets ayant au moins un dépôt, par date de création puis identifiant (import planifié, INV-07). */
    @Transactional
    public List<UUID> avecDepots() {
        String sql = """
                SELECT p.id FROM project p WHERE EXISTS (SELECT 1 FROM project_repository r WHERE r.project_id = p.id)
                ORDER BY p.created_at, p.id
                """;
        try (Connection connexion = dataSource.getConnection(); PreparedStatement requete = connexion.prepareStatement(sql);
                ResultSet resultat = requete.executeQuery()) {
            List<UUID> ids = new ArrayList<>();
            while (resultat.next()) {
                ids.add(resultat.getObject(1, UUID.class));
            }
            return ids;
        } catch (SQLException erreur) {
            throw new IllegalStateException("lecture des projets impossible", erreur);
        }
    }

    @Transactional
    public Optional<Project> find(UUID id) {
        String sql = """
                SELECT id, workspace_id, name, scm_provider, scm_repository, scm_branch, sbom_job, sbom_path, manifest_paths
                FROM project WHERE id = ?
                """;
        try (Connection connexion = dataSource.getConnection(); PreparedStatement requete = connexion.prepareStatement(sql)) {
            requete.setObject(1, id);
            try (ResultSet resultat = requete.executeQuery()) {
                return resultat.next() ? Optional.of(project(resultat)) : Optional.empty();
            }
        } catch (SQLException erreur) {
            throw new IllegalStateException("lecture du projet impossible", erreur);
        }
    }

    /** Projets rattachés à un dépôt, pour les traitements techniques (collecte de l'activité). */
    @Transactional
    public List<Project> withRepository() {
        String sql = """
                SELECT id, workspace_id, name, scm_provider, scm_repository, scm_branch, sbom_job, sbom_path, manifest_paths
                FROM project WHERE scm_provider IS NOT NULL AND scm_repository IS NOT NULL ORDER BY created_at, id
                """;
        try (Connection connexion = dataSource.getConnection(); PreparedStatement requete = connexion.prepareStatement(sql);
                ResultSet resultat = requete.executeQuery()) {
            List<Project> projets = new ArrayList<>();
            while (resultat.next()) {
                projets.add(project(resultat));
            }
            return projets;
        } catch (SQLException erreur) {
            throw new IllegalStateException("lecture des projets impossible", erreur);
        }
    }

    private UUID insererDepot(UUID projectId, RepositoryRef repository, List<String> manifestPaths, boolean principal) {
        UUID id = UUID.randomUUID();
        String sql = """
                INSERT INTO project_repository(id, project_id, provider, repository, branch, sbom_job, sbom_path,
                                               manifest_paths, principal)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;
        try (Connection connexion = dataSource.getConnection(); PreparedStatement requete = connexion.prepareStatement(sql)) {
            requete.setObject(1, id);
            requete.setObject(2, projectId);
            requete.setString(3, repository.provider());
            requete.setString(4, repository.repository());
            requete.setString(5, repository.branch());
            requete.setString(6, repository.sbomJob());
            requete.setString(7, repository.sbomPath());
            requete.setString(8, String.join(",", manifestPaths == null ? DEFAULT_MANIFESTS : manifestPaths));
            requete.setBoolean(9, principal);
            requete.executeUpdate();
        } catch (SQLException erreur) {
            if ("23503".equals(erreur.getSQLState())) {
                throw new IllegalArgumentException("projet inconnu : " + projectId, erreur);
            }
            if ("23505".equals(erreur.getSQLState())) {
                throw new IllegalArgumentException("dépôt déjà rattaché au projet : " + repository.repository(), erreur);
            }
            throw new IllegalStateException("rattachement du dépôt impossible", erreur);
        }
        return id;
    }

    private static Project project(ResultSet resultat) throws SQLException {
        Optional<RepositoryRef> repo = resultat.getString(4) == null ? Optional.empty()
                : Optional.of(new RepositoryRef(resultat.getString(4), resultat.getString(5), resultat.getString(6),
                        resultat.getString(7), resultat.getString(8)));
        return new Project(resultat.getObject(1, UUID.class), resultat.getObject(2, UUID.class), resultat.getString(3), repo,
                chemins(resultat.getString(9)));
    }

    private static List<String> chemins(String manifests) {
        return manifests == null || manifests.isBlank() ? List.of()
                : Arrays.stream(manifests.split(",")).map(String::strip).filter(chemin -> !chemin.isEmpty()).toList();
    }
}
