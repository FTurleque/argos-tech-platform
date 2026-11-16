package io.argos.inventory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import javax.sql.DataSource;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import io.argos.scm.RepositoryRef;

/** Projets suivis, chacun rattaché à un seul espace (table {@code espace}) et à au plus un dépôt. */
@ApplicationScoped
public class ProjectDirectory {

    /** Manifestes lus par défaut pour l'inventaire de repli. */
    public static final List<String> DEFAULT_MANIFESTS = List.of("package.json", "requirements.txt");

    /** Projet suivi ; {@code repository} est absent si aucun dépôt n'est rattaché. */
    public record Project(UUID id, UUID workspaceId, String name, Optional<RepositoryRef> repository,
            List<String> manifestPaths) {
    }

    @Inject
    DataSource dataSource;

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
        return id;
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
                if (!rs.next()) {
                    return Optional.empty();
                }
                Optional<RepositoryRef> repo = rs.getString(4) == null ? Optional.empty()
                        : Optional.of(new RepositoryRef(rs.getString(4), rs.getString(5), rs.getString(6),
                                rs.getString(7), rs.getString(8)));
                String manifests = rs.getString(9);
                List<String> paths = manifests == null || manifests.isBlank() ? List.of()
                        : Arrays.stream(manifests.split(",")).map(String::strip).filter(s -> !s.isEmpty()).toList();
                return Optional.of(new Project(rs.getObject(1, UUID.class), rs.getObject(2, UUID.class),
                        rs.getString(3), repo, paths));
            }
        } catch (SQLException e) {
            throw new IllegalStateException("lecture du projet impossible", e);
        }
    }
}
