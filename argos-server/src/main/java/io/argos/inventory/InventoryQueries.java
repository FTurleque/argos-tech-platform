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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

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

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** Composant consultable. {@code transitive} : {@code null} si inconnu. */
    public record ComponentView(UUID id, String purl, String packageKey, String version, Boolean transitive) {
    }

    /**
     * Inventaire de référence d'un projet, avec son origine et son âge. {@code ignoredCount} compte les
     * composants du dernier import écartés faute de PURL exploitable ; {@code ignoredComponents} les décrit
     * (nom, groupe, version, motif), au plus {@value ParsedInventory#MAX_SIGNALED} (INV-01, décision D6
     * du 2026-11-18).
     */
    public record InventoryView(UUID projectId, String origin, Instant sourceDate, Long ageDays, boolean obsolete,
            String specVersion, Instant importedAt, List<ComponentView> components, int ignoredCount,
            List<ParsedInventory.IgnoredComponent> ignoredComponents) {
    }

    /**
     * Synthèse de l'inventaire de référence d'un projet, sans la liste des composants (décision G5 du porteur du
     * projet, 2026-11-19) : mêmes champs que {@link InventoryView}, {@code components} remplacé par
     * {@code componentCount}, nombre de composants inventoriés.
     */
    public record InventorySummary(UUID projectId, String origin, Instant sourceDate, Long ageDays, boolean obsolete,
            String specVersion, Instant importedAt, long componentCount, int ignoredCount,
            List<ParsedInventory.IgnoredComponent> ignoredComponents) {
    }

    /** Projet listé pour un compte : identifiant, espace (identifiant et nom) et nom du projet. */
    public record ProjectItem(UUID id, UUID workspaceId, String workspaceName, String name) {
    }

    /** Page des projets lisibles par un compte (arc42 §8.10). {@code total} : nombre de projets lisibles. */
    public record ProjectPage(List<ProjectItem> items, int page, int size, long total) {
    }

    /**
     * Composant inventorié d'une page. {@code name} : clé du paquet sans le préfixe {@code pkg:<type>/}
     * (espace de noms et nom) ; {@code transitive} : {@code null} si inconnu.
     */
    public record ComponentItem(UUID id, String name, String version, String purl, String packageKey, Boolean transitive) {
    }

    /** Page des composants inventoriés d'un projet (arc42 §8.10), triés par nom, version puis identifiant. */
    public record ComponentPage(UUID projectId, List<ComponentItem> items, int page, int size, long total) {
    }

    /** Taille de page par défaut des listes paginées de l'inventaire. */
    public static final int DEFAULT_PAGE_SIZE = 50;

    /** Taille de page maximale des listes paginées de l'inventaire ; une taille supérieure est ramenée à cette borne. */
    public static final int MAX_PAGE_SIZE = 200;

    /** Nom affiché d'un composant : clé du paquet sans le préfixe {@code pkg:<type>/}. */
    private static final String COMPONENT_NAME = "coalesce(substring(package_key from '^pkg:[^/]+/(.+)$'), package_key)";

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
        try (Connection connexion = dataSource.getConnection()) {
            ContexteBase.appliquer(connexion, new ContexteBase.Contexte(accountId, null, null));
            Optional<LigneInventaire> ligne = ligne(connexion, projectId);
            if (ligne.isEmpty()) {
                return Optional.empty();
            }
            LigneInventaire entete = ligne.get();
            return Optional.of(new InventoryView(projectId, entete.origin(), entete.sourceDate(), entete.ageDays(),
                    entete.obsolete(), entete.specVersion(), entete.importedAt(), components(connexion, projectId), entete.ignoredCount(),
                    entete.ignoredComponents()));
        } catch (SQLException e) {
            throw new IllegalStateException("lecture de l'inventaire impossible", e);
        }
    }

    /**
     * Synthèse de l'inventaire de référence d'un projet, sans la liste des composants (décision G5 du porteur du
     * projet, 2026-11-19) : origine, dates, âge, obsolescence, nombre de composants inventoriés et composants
     * ignorés faute de PURL. Lecture sous {@code argos_app} avec le compte : vide si le projet n'a pas
     * d'inventaire ou s'il n'est pas lisible par le compte (RLS de {@code inventory}).
     */
    @Transactional
    public Optional<InventorySummary> summary(UUID projectId, UUID accountId) {
        try (Connection connexion = dataSource.getConnection()) {
            ContexteBase.appliquer(connexion, new ContexteBase.Contexte(accountId, null, null));
            return summary(connexion, projectId);
        } catch (SQLException e) {
            throw new IllegalStateException("lecture de l'inventaire impossible", e);
        }
    }

    private Optional<InventorySummary> summary(Connection connexion, UUID projectId) throws SQLException {
        Optional<LigneInventaire> ligne = ligne(connexion, projectId);
        if (ligne.isEmpty()) {
            return Optional.empty();
        }
        LigneInventaire entete = ligne.get();
        long componentCount = count(connexion, "SELECT count(*) FROM component WHERE project_id = ?", projectId);
        return Optional.of(new InventorySummary(projectId, entete.origin(), entete.sourceDate(), entete.ageDays(),
                entete.obsolete(), entete.specVersion(), entete.importedAt(), componentCount, entete.ignoredCount(),
                entete.ignoredComponents()));
    }

    /** Ligne {@code inventory} d'un projet, commune à l'inventaire complet et à sa synthèse, sans compter les composants. */
    private record LigneInventaire(String origin, Instant sourceDate, Long ageDays, boolean obsolete, String specVersion,
            Instant importedAt, int ignoredCount, List<ParsedInventory.IgnoredComponent> ignoredComponents) {
    }

    private Optional<LigneInventaire> ligne(Connection connexion, UUID projectId) throws SQLException {
        String sql = "SELECT origin, source_date, spec_version, imported_at, ignored_count, ignored_components::text"
                + " FROM inventory WHERE project_id = ?";
        try (PreparedStatement requete = connexion.prepareStatement(sql)) {
            requete.setObject(1, projectId);
            try (ResultSet lignes = requete.executeQuery()) {
                if (!lignes.next()) {
                    return Optional.empty();
                }
                String origin = lignes.getString(1);
                Instant sourceDate = instant(lignes, 2);
                Long age = sourceDate == null ? null : Duration.between(sourceDate, Instant.now()).toDays();
                return Optional.of(new LigneInventaire(origin, sourceDate, age, sbomObsolete(origin, sourceDate),
                        lignes.getString(3), instant(lignes, 4), lignes.getInt(5), ignored(lignes.getString(6))));
            }
        }
    }

    /**
     * Page des projets lisibles par le compte, triés par nom de projet, nom d'espace puis identifiant (tri
     * stable). Aucun filtre applicatif : la RLS de {@code project} sous {@code argos_app} limite la liste aux
     * espaces dont le compte est membre (ADR-0016). Page négative ramenée à 0, taille bornée à
     * [1, {@value #MAX_PAGE_SIZE}]. Seule requête de liste des projets visibles : l'API REST et les outils MCP
     * la partagent (décision G6 du porteur du projet, 2026-11-19).
     */
    @Transactional
    public ProjectPage projects(UUID accountId, int page, int size) {
        int appliedPage = Math.max(page, 0);
        int appliedSize = boundedSize(size);
        String sql = "SELECT p.id, p.workspace_id, e.nom, p.name FROM project p LEFT JOIN espace e ON e.id = p.workspace_id"
                + " ORDER BY p.name, e.nom, p.id LIMIT ? OFFSET ?";
        try (Connection connexion = dataSource.getConnection()) {
            ContexteBase.appliquer(connexion, new ContexteBase.Contexte(accountId, null, null));
            long total = count(connexion, "SELECT count(*) FROM project", null);
            List<ProjectItem> items = new ArrayList<>();
            try (PreparedStatement requete = connexion.prepareStatement(sql)) {
                requete.setInt(1, appliedSize);
                requete.setLong(2, (long) appliedPage * appliedSize);
                try (ResultSet lignes = requete.executeQuery()) {
                    while (lignes.next()) {
                        items.add(new ProjectItem(lignes.getObject(1, UUID.class), lignes.getObject(2, UUID.class), lignes.getString(3),
                                lignes.getString(4)));
                    }
                }
            }
            return new ProjectPage(items, appliedPage, appliedSize, total);
        } catch (SQLException e) {
            throw new IllegalStateException("lecture des projets impossible", e);
        }
    }

    /**
     * Page des composants inventoriés d'un projet, triés par nom, version puis identifiant (tri stable). La
     * lecture s'exécute sous {@code argos_app} avec le compte : un projet hors de ses espaces donne une page
     * vide (RLS de {@code component}). Page négative ramenée à 0, taille bornée à [1, {@value #MAX_PAGE_SIZE}].
     */
    @Transactional
    public ComponentPage components(UUID projectId, UUID accountId, int page, int size) {
        int appliedPage = Math.max(page, 0);
        int appliedSize = boundedSize(size);
        String sql = "SELECT id, " + COMPONENT_NAME + " AS name, version, purl, package_key, transitive FROM component"
                + " WHERE project_id = ? ORDER BY name, version NULLS FIRST, id LIMIT ? OFFSET ?";
        try (Connection connexion = dataSource.getConnection()) {
            ContexteBase.appliquer(connexion, new ContexteBase.Contexte(accountId, null, null));
            long total = count(connexion, "SELECT count(*) FROM component WHERE project_id = ?", projectId);
            List<ComponentItem> items = new ArrayList<>();
            try (PreparedStatement requete = connexion.prepareStatement(sql)) {
                requete.setObject(1, projectId);
                requete.setInt(2, appliedSize);
                requete.setLong(3, (long) appliedPage * appliedSize);
                try (ResultSet lignes = requete.executeQuery()) {
                    while (lignes.next()) {
                        items.add(new ComponentItem(lignes.getObject(1, UUID.class), lignes.getString(2), lignes.getString(3),
                                lignes.getString(4), lignes.getString(5), (Boolean) lignes.getObject(6)));
                    }
                }
            }
            return new ComponentPage(projectId, items, appliedPage, appliedSize, total);
        } catch (SQLException e) {
            throw new IllegalStateException("lecture des composants impossible", e);
        }
    }

    @Transactional
    public List<ImportFailure> failures(UUID projectId, UUID accountId) {
        List<ImportFailure> failures = new ArrayList<>();
        String sql = "SELECT id, reason, source_ref, received_at FROM inventory_import_failure WHERE project_id = ? ORDER BY received_at";
        try (Connection connexion = dataSource.getConnection()) {
            ContexteBase.appliquer(connexion, new ContexteBase.Contexte(accountId, null, null));
            try (PreparedStatement requete = connexion.prepareStatement(sql)) {
                requete.setObject(1, projectId);
                try (ResultSet lignes = requete.executeQuery()) {
                    while (lignes.next()) {
                        failures.add(new ImportFailure(lignes.getObject(1, UUID.class), lignes.getString(2), lignes.getString(3),
                                instant(lignes, 4)));
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
        try (Connection connexion = dataSource.getConnection(); PreparedStatement requete = connexion.prepareStatement(sql)) {
            requete.setArray(1, connexion.createArrayOf("varchar", packageKeys.toArray()));
            requete.setObject(2, projectId.orElse(null));
            requete.setObject(3, projectId.orElse(null));
            try (ResultSet lignes = requete.executeQuery()) {
                while (lignes.next()) {
                    String origin = lignes.getString(8);
                    Instant date = instant(lignes, 9);
                    matches.add(new ComponentMatch(lignes.getObject(1, UUID.class), lignes.getObject(2, UUID.class),
                            lignes.getObject(3, UUID.class), lignes.getString(4), lignes.getString(5), lignes.getString(6),
                            (Boolean) lignes.getObject(7), origin, date, !"SBOM".equals(origin) || sbomObsolete(origin, date)));
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
        try (Connection connexion = dataSource.getConnection();
                PreparedStatement requete = connexion.prepareStatement("SELECT DISTINCT package_key FROM component WHERE project_id = ?")) {
            requete.setObject(1, projectId);
            try (ResultSet lignes = requete.executeQuery()) {
                while (lignes.next()) {
                    keys.add(lignes.getString(1));
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
        try (Connection connexion = dataSource.getConnection();
                PreparedStatement requete = connexion.prepareStatement("SELECT origin FROM inventory WHERE project_id = ?")) {
            requete.setObject(1, projectId);
            try (ResultSet lignes = requete.executeQuery()) {
                return lignes.next() ? Optional.of(lignes.getString(1)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new IllegalStateException("lecture de l'inventaire impossible", e);
        }
    }

    /** Espace du projet, s'il existe. */
    @Transactional
    public Optional<UUID> workspaceOf(UUID projectId) {
        try (Connection connexion = dataSource.getConnection();
                PreparedStatement requete = connexion.prepareStatement("SELECT workspace_id FROM project WHERE id = ?")) {
            requete.setObject(1, projectId);
            try (ResultSet lignes = requete.executeQuery()) {
                return lignes.next() ? Optional.of(lignes.getObject(1, UUID.class)) : Optional.empty();
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

    private static List<ComponentView> components(Connection connexion, UUID projectId) throws SQLException {
        List<ComponentView> components = new ArrayList<>();
        try (PreparedStatement requete = connexion.prepareStatement(
                "SELECT id, purl, package_key, version, transitive FROM component WHERE project_id = ? ORDER BY package_key, version")) {
            requete.setObject(1, projectId);
            try (ResultSet lignes = requete.executeQuery()) {
                while (lignes.next()) {
                    components.add(new ComponentView(lignes.getObject(1, UUID.class), lignes.getString(2), lignes.getString(3),
                            lignes.getString(4), (Boolean) lignes.getObject(5)));
                }
            }
        }
        return components;
    }

    private static int boundedSize(int size) {
        return Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
    }

    private static long count(Connection connexion, String sql, UUID projectId) throws SQLException {
        try (PreparedStatement requete = connexion.prepareStatement(sql)) {
            if (projectId != null) {
                requete.setObject(1, projectId);
            }
            try (ResultSet lignes = requete.executeQuery()) {
                lignes.next();
                return lignes.getLong(1);
            }
        }
    }

    private static List<ParsedInventory.IgnoredComponent> ignored(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return List.of(MAPPER.readValue(json, ParsedInventory.IgnoredComponent[].class));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("lecture des composants ignorés impossible", e);
        }
    }

    private static Instant instant(ResultSet lignes, int column) throws SQLException {
        OffsetDateTime value = lignes.getObject(column, OffsetDateTime.class);
        return value == null ? null : value.toInstant();
    }
}
