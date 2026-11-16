package io.argos.inventory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import javax.sql.DataSource;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import org.eclipse.microprofile.config.inject.ConfigProperty;

import io.argos.workspace.ContexteBase;

/**
 * Consultation des inventaires (INV-02, INV-03) et recherche des composants par clé de paquet
 * pour la corrélation. Le seuil d'obsolescence d'une SBOM vaut 30 jours après sa date de
 * génération (décision du 2026-11-16, Q-3) ; une SBOM sans date de génération est traitée
 * comme obsolète, faute de pouvoir établir sa fraîcheur ([PROPOSITION], fiche inventaire-sbom).
 *
 * <p>La consultation par un utilisateur s'exécute sous le rôle {@code argos_app} avec son compte :
 * la RLS des tables d'inventaire sert de garde-fou (ADR-0016). La recherche de composants pour la
 * corrélation est un traitement technique.
 */
@ApplicationScoped
public class InventoryQueries {

    /** Composant consultable. {@code transitive} : {@code null} si inconnu. */
    public record ComponentView(UUID id, String purl, String packageKey, String version, Boolean transitive) {
    }

    /** Inventaire de référence d'un projet, avec son origine et son âge. */
    public record InventoryView(UUID projectId, String origin, Instant sourceDate, Long ageDays, boolean obsolete,
            String specVersion, Instant importedAt, List<ComponentView> components) {
    }

    /** Import refusé (INV-05). */
    public record ImportFailure(UUID id, String reason, String sourceRef, Instant receivedAt) {
    }

    /** Composant candidat à la corrélation, avec le projet, l'espace et l'inventaire d'origine. */
    public record ComponentMatch(UUID componentId, UUID projectId, UUID workspaceId, String packageKey, String purl,
            String version, Boolean transitive, String inventoryOrigin, Instant inventoryDate, boolean inventoryObsolete) {
    }

    @Inject
    DataSource dataSource;

    @ConfigProperty(name = "argos.inventory.sbom-max-age", defaultValue = "P30D")
    Duration sbomMaxAge;

    @Transactional
    public Optional<InventoryView> inventory(UUID projectId, UUID accountId) {
        String sql = "SELECT origin, source_date, spec_version, imported_at FROM inventory WHERE project_id = ?";
        try (Connection c = dataSource.getConnection()) {
            ContexteBase.appliquer(c, new ContexteBase.Contexte(accountId, null, null));
            try (PreparedStatement ps = c.prepareStatement(sql)) {
                ps.setObject(1, projectId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) {
                        return Optional.empty();
                    }
                    String origin = rs.getString(1);
                    Instant sourceDate = instant(rs, 2);
                    Long age = sourceDate == null ? null : Duration.between(sourceDate, Instant.now()).toDays();
                    return Optional.of(new InventoryView(projectId, origin, sourceDate, age, sbomObsolete(origin, sourceDate),
                            rs.getString(3), instant(rs, 4), components(c, projectId)));
                }
            }
        } catch (SQLException e) {
            throw new IllegalStateException("lecture de l'inventaire impossible", e);
        }
    }

    @Transactional
    public List<ImportFailure> failures(UUID projectId, UUID accountId) {
        List<ImportFailure> failures = new ArrayList<>();
        String sql = "SELECT id, reason, source_ref, received_at FROM inventory_import_failure WHERE project_id = ? ORDER BY received_at";
        try (Connection c = dataSource.getConnection()) {
            ContexteBase.appliquer(c, new ContexteBase.Contexte(accountId, null, null));
            try (PreparedStatement ps = c.prepareStatement(sql)) {
                ps.setObject(1, projectId);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        failures.add(new ImportFailure(rs.getObject(1, UUID.class), rs.getString(2), rs.getString(3),
                                instant(rs, 4)));
                    }
                }
            }
        } catch (SQLException e) {
            throw new IllegalStateException("lecture des imports refusés impossible", e);
        }
        return failures;
    }

    /** Composants dont la clé de paquet figure parmi celles données, éventuellement limités à un projet. */
    @Transactional
    public List<ComponentMatch> componentsMatching(Collection<String> packageKeys, Optional<UUID> projectId) {
        List<ComponentMatch> matches = new ArrayList<>();
        if (packageKeys.isEmpty()) {
            return matches;
        }
        String sql = """
                SELECT c.id, c.project_id, p.workspace_id, c.package_key, c.purl, c.version, c.transitive,
                       i.origin, i.source_date
                FROM component c JOIN inventory i ON i.project_id = c.project_id JOIN project p ON p.id = c.project_id
                WHERE c.package_key = ANY (?) AND (?::uuid IS NULL OR c.project_id = ?::uuid)
                """;
        try (Connection c = dataSource.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setArray(1, c.createArrayOf("varchar", packageKeys.toArray()));
            ps.setObject(2, projectId.orElse(null));
            ps.setObject(3, projectId.orElse(null));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String origin = rs.getString(8);
                    Instant date = instant(rs, 9);
                    matches.add(new ComponentMatch(rs.getObject(1, UUID.class), rs.getObject(2, UUID.class),
                            rs.getObject(3, UUID.class), rs.getString(4), rs.getString(5), rs.getString(6),
                            (Boolean) rs.getObject(7), origin, date, !"SBOM".equals(origin) || sbomObsolete(origin, date)));
                }
            }
        } catch (SQLException e) {
            throw new IllegalStateException("recherche des composants impossible", e);
        }
        return matches;
    }

    /** Clés de paquet de l'inventaire d'un projet. */
    @Transactional
    public List<String> packageKeys(UUID projectId) {
        List<String> keys = new ArrayList<>();
        try (Connection c = dataSource.getConnection();
                PreparedStatement ps = c.prepareStatement("SELECT DISTINCT package_key FROM component WHERE project_id = ?")) {
            ps.setObject(1, projectId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    keys.add(rs.getString(1));
                }
            }
        } catch (SQLException e) {
            throw new IllegalStateException("lecture des paquets du projet impossible", e);
        }
        return keys;
    }

    /** Origine de l'inventaire de référence du projet (traitement technique), s'il existe. */
    @Transactional
    public Optional<String> origin(UUID projectId) {
        try (Connection c = dataSource.getConnection();
                PreparedStatement ps = c.prepareStatement("SELECT origin FROM inventory WHERE project_id = ?")) {
            ps.setObject(1, projectId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(rs.getString(1)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new IllegalStateException("lecture de l'inventaire impossible", e);
        }
    }

    /** Espace du projet, s'il existe. */
    @Transactional
    public Optional<UUID> workspaceOf(UUID projectId) {
        try (Connection c = dataSource.getConnection();
                PreparedStatement ps = c.prepareStatement("SELECT workspace_id FROM project WHERE id = ?")) {
            ps.setObject(1, projectId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(rs.getObject(1, UUID.class)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new IllegalStateException("lecture du projet impossible", e);
        }
    }

    /**
     * SBOM obsolète : plus de 30 jours après sa génération, ou sans date de génération. Un inventaire
     * de repli (manifestes) n'est pas une SBOM : l'indicateur restitué vaut faux, mais la corrélation
     * ne le juge jamais assez fiable pour le niveau « exact » (COR-06).
     */
    private boolean sbomObsolete(String origin, Instant sourceDate) {
        return "SBOM".equals(origin) && (sourceDate == null || sourceDate.plus(sbomMaxAge).isBefore(Instant.now()));
    }

    private static List<ComponentView> components(Connection c, UUID projectId) throws SQLException {
        List<ComponentView> components = new ArrayList<>();
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT id, purl, package_key, version, transitive FROM component WHERE project_id = ? ORDER BY package_key, version")) {
            ps.setObject(1, projectId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    components.add(new ComponentView(rs.getObject(1, UUID.class), rs.getString(2), rs.getString(3),
                            rs.getString(4), (Boolean) rs.getObject(5)));
                }
            }
        }
        return components;
    }

    private static Instant instant(ResultSet rs, int column) throws SQLException {
        OffsetDateTime value = rs.getObject(column, OffsetDateTime.class);
        return value == null ? null : value.toInstant();
    }
}
