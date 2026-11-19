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
        try (Connection c = dataSource.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setObject(1, id);
            ps.setObject(2, workspaceId);
            ps.setString(3, name);
            ps.setString(4, repository == null ? null : repository.provider());
            ps.setString(5, repository == null ? null : repository.repository());
            ps.setString(6, repository == null ? null : repository.branch());
            ps.setString(7, repository == null ? null : repository.sbomJob());
            ps.setString(8, repository == null ? null : repository.sbomPath());
            ps.setString(9, String.join(",", manifestPaths == null ? DEFAULT_MANIFESTS : manifestPaths));
            ps.executeUpdate();
        } catch (SQLException e) {
            if ("23503".equals(e.getSQLState())) {
                throw new IllegalArgumentException("espace inconnu : " + workspaceId, e);
            }
            throw new IllegalStateException("création du projet impossible", e);
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
        try (Connection c = dataSource.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setObject(1, projectId);
            try (ResultSet rs = ps.executeQuery()) {
                List<Depot> depots = new ArrayList<>();
                while (rs.next()) {
                    depots.add(new Depot(rs.getObject(1, UUID.class), rs.getObject(2, UUID.class),
                            new RepositoryRef(rs.getString(3), rs.getString(4), rs.getString(5), rs.getString(6),
                                    rs.getString(7)),
                            chemins(rs.getString(8)), rs.getBoolean(9)));
                }
                return depots;
            }
        } catch (SQLException e) {
            throw new IllegalStateException("lecture des dépôts du projet impossible", e);
        }
    }

    /** Projets ayant au moins un dépôt, par date de création puis identifiant (import planifié, INV-07). */
    @Transactional
    public List<UUID> avecDepots() {
        String sql = """
                SELECT p.id FROM project p WHERE EXISTS (SELECT 1 FROM project_repository r WHERE r.project_id = p.id)
                ORDER BY p.created_at, p.id
                """;
        try (Connection c = dataSource.getConnection(); PreparedStatement ps = c.prepareStatement(sql);
                ResultSet rs = ps.executeQuery()) {
            List<UUID> ids = new ArrayList<>();
            while (rs.next()) {
                ids.add(rs.getObject(1, UUID.class));
            }
            return ids;
        } catch (SQLException e) {
            throw new IllegalStateException("lecture des projets impossible", e);
        }
    }

    @Transactional
    public Optional<Project> find(UUID id) {
        String sql = """
                SELECT id, workspace_id, name, scm_provider, scm_repository, scm_branch, sbom_job, sbom_path, manifest_paths
                FROM project WHERE id = ?
                """;
        try (Connection c = dataSource.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setObject(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(project(rs)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new IllegalStateException("lecture du projet impossible", e);
        }
    }

    /** Projets rattachés à un dépôt, pour les traitements techniques (collecte de l'activité). */
    @Transactional
    public List<Project> withRepository() {
        String sql = """
                SELECT id, workspace_id, name, scm_provider, scm_repository, scm_branch, sbom_job, sbom_path, manifest_paths
                FROM project WHERE scm_provider IS NOT NULL AND scm_repository IS NOT NULL ORDER BY created_at, id
                """;
        try (Connection c = dataSource.getConnection(); PreparedStatement ps = c.prepareStatement(sql);
                ResultSet rs = ps.executeQuery()) {
            List<Project> projets = new ArrayList<>();
            while (rs.next()) {
                projets.add(project(rs));
            }
            return projets;
        } catch (SQLException e) {
            throw new IllegalStateException("lecture des projets impossible", e);
        }
    }

    private UUID insererDepot(UUID projectId, RepositoryRef repository, List<String> manifestPaths, boolean principal) {
        UUID id = UUID.randomUUID();
        String sql = """
                INSERT INTO project_repository(id, project_id, provider, repository, branch, sbom_job, sbom_path,
                                               manifest_paths, principal)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;
        try (Connection c = dataSource.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setObject(1, id);
            ps.setObject(2, projectId);
            ps.setString(3, repository.provider());
            ps.setString(4, repository.repository());
            ps.setString(5, repository.branch());
            ps.setString(6, repository.sbomJob());
            ps.setString(7, repository.sbomPath());
            ps.setString(8, String.join(",", manifestPaths == null ? DEFAULT_MANIFESTS : manifestPaths));
            ps.setBoolean(9, principal);
            ps.executeUpdate();
        } catch (SQLException e) {
            if ("23503".equals(e.getSQLState())) {
                throw new IllegalArgumentException("projet inconnu : " + projectId, e);
            }
            if ("23505".equals(e.getSQLState())) {
                throw new IllegalArgumentException("dépôt déjà rattaché au projet : " + repository.repository(), e);
            }
            throw new IllegalStateException("rattachement du dépôt impossible", e);
        }
        return id;
    }

    private static Project project(ResultSet rs) throws SQLException {
        Optional<RepositoryRef> repo = rs.getString(4) == null ? Optional.empty()
                : Optional.of(new RepositoryRef(rs.getString(4), rs.getString(5), rs.getString(6),
                        rs.getString(7), rs.getString(8)));
        return new Project(rs.getObject(1, UUID.class), rs.getObject(2, UUID.class), rs.getString(3), repo,
                chemins(rs.getString(9)));
    }

    private static List<String> chemins(String manifests) {
        return manifests == null || manifests.isBlank() ? List.of()
                : Arrays.stream(manifests.split(",")).map(String::strip).filter(s -> !s.isEmpty()).toList();
    }
}
