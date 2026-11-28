package io.argos.inventory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import javax.sql.DataSource;

import com.fasterxml.jackson.annotation.JsonProperty;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import org.eclipse.microprofile.config.inject.ConfigProperty;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.argos.commun.ContexteBase;
import io.argos.commun.Jdbc;
import io.argos.commun.Page;
import io.argos.commun.Pagination;

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
            String specVersion, Instant importedAt, Page<ComponentView> components, int ignoredCount,
            List<ParsedInventory.IgnoredComponent> ignoredComponents) {
    }

    /**
     * Synthèse de l'inventaire de référence d'un projet, sans la liste des composants (décision G5 du porteur du
     * projet, 2026-11-19) : mêmes champs que {@link InventoryView}, {@code components} remplacé par
     * {@code componentCount}, nombre de composants inventoriés. Pour un projet à plusieurs dépôts (INV-10),
     * l'origine et les dates sont celles du dépôt principal (compatibilité), {@code ignoredCount} la somme des
     * dépôts, et {@code repositories} décrit chaque dépôt rattaché, le principal d'abord (vide sans dépôt).
     */
    public record InventorySummary(UUID projectId, String origin, Instant sourceDate, Long ageDays, boolean obsolete,
            String specVersion, Instant importedAt, long componentCount, int ignoredCount,
            List<ParsedInventory.IgnoredComponent> ignoredComponents, List<RepositorySummary> repositories) {
    }

    /**
     * Dépôt rattaché au projet et son dernier import réussi (INV-10-S1) : {@code origin}, {@code sourceDate} et
     * {@code importedAt} sont nuls tant qu'aucun import n'a réussi ; {@code lastFailure} est le dernier échec
     * d'import postérieur au dernier import réussi, nul s'il n'y en a pas.
     */
    public record RepositorySummary(UUID id, String provider, String repository, String branch, boolean principal,
            String origin, Instant sourceDate, Instant importedAt, RepositoryFailure lastFailure) {
    }

    /** Échec d'import d'un dépôt : motif (sans jeton) et date. */
    public record RepositoryFailure(String reason, Instant receivedAt) {
    }

    /** Dépôt dans lequel figure un composant. */
    public record ComponentRepository(UUID id, String provider, String repository) {
    }

    /** Projet listé pour un compte : identifiant, espace (identifiant et nom) et nom du projet. */
    public record ProjectItem(UUID id, @JsonProperty("workspaceId") UUID espaceId,
            @JsonProperty("workspaceName") String nomEspace, String name) {
    }

    /** Page des projets lisibles par un compte (arc42 §8.10). {@code total} : nombre de projets lisibles. */
    public record ProjectPage(List<ProjectItem> items, int page, int size, long total) {
    }

    /**
     * Composant inventorié d'une page. {@code name} : clé du paquet sans le préfixe {@code pkg:<type>/}
     * (espace de noms et nom) ; {@code transitive} : {@code null} si inconnu ; {@code repositories} : dépôts du
     * projet qui contiennent le composant (INV-10-S1), vide pour un composant importé sans dépôt.
     */
    public record ComponentItem(UUID id, String name, String version, String purl, String packageKey, Boolean transitive,
            List<ComponentRepository> repositories) {
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
    public record ComponentMatch(UUID componentId, UUID projectId, UUID espaceId, String packageKey, String purl,
            String version, Boolean transitive, String inventoryOrigin, Instant inventoryDate, boolean inventoryObsolete) {
    }

    @Inject
    Clock horloge;

    @Inject
    DataSource dataSource;

    @ConfigProperty(name = "argos.inventory.sbom-max-age", defaultValue = "P30D")
    Duration sbomMaxAge;

    @Transactional
    public Optional<InventoryView> inventory(UUID projectId, UUID accountId, Pagination pagination) {
        return inventaire(projectId, accountId, pagination.taille(), pagination.decalage());
    }

    /**
     * Clés de paquet distinctes des composants d'un projet ; vide pour un projet hors des espaces du compte (RLS).
     * Lecture interne du serveur MCP, qui n'en restitue que les échéances.
     */
    @Transactional
    public Set<String> clesDePaquets(UUID projectId, UUID accountId) {
        try (Connection connexion = dataSource.getConnection()) {
            ContexteBase.appliquer(connexion, ContexteBase.Contexte.duCompte(accountId));
            return new LinkedHashSet<>(Jdbc.lister(connexion,
                    "SELECT DISTINCT package_key FROM component WHERE project_id = ? ORDER BY package_key",
                    Jdbc.parametres(projectId), lignes -> lignes.getString(1)));
        } catch (SQLException erreur) {
            throw new IllegalStateException("lecture des clés de paquet impossible", erreur);
        }
    }

    /**
     * Inventaire d'un projet avec ses {@code limite} premiers composants, triés par clé de paquet, version puis
     * identifiant ; {@code components.total} compte tous les composants (contexte d'un projet du serveur MCP).
     */
    @Transactional
    public Optional<InventoryView> premiersComposants(UUID projectId, UUID accountId, int limite) {
        return inventaire(projectId, accountId, limite, 0);
    }

    private Optional<InventoryView> inventaire(UUID projectId, UUID accountId, int taille, long decalage) {
        try (Connection connexion = dataSource.getConnection()) {
            ContexteBase.appliquer(connexion, ContexteBase.Contexte.duCompte(accountId));
            Optional<LigneInventaire> ligne = ligne(connexion, projectId);
            if (ligne.isEmpty()) {
                return Optional.empty();
            }
            LigneInventaire entete = ligne.get();
            return Optional.of(new InventoryView(projectId, entete.origin(), entete.sourceDate(), entete.ageDays(),
                    entete.obsolete(), entete.specVersion(), entete.importedAt(), components(connexion, projectId, taille, decalage),
                    entete.ignoredCount(),
                    entete.ignoredComponents()));
        } catch (SQLException erreur) {
            throw new IllegalStateException("lecture de l'inventaire impossible", erreur);
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
            ContexteBase.appliquer(connexion, ContexteBase.Contexte.duCompte(accountId));
            return summary(connexion, projectId);
        } catch (SQLException erreur) {
            throw new IllegalStateException("lecture de l'inventaire impossible", erreur);
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
                entete.ignoredComponents(), repositories(connexion, projectId)));
    }

    /** Dépôts du projet et leur dernier import, lus sous le contexte de la connexion (RLS de V19). */
    private static List<RepositorySummary> repositories(Connection connexion, UUID projectId) throws SQLException {
        String sql = """
                SELECT r.id, r.provider, r.repository, r.branch, r.principal, ri.origin, ri.source_date, ri.imported_at,
                       f.reason, f.received_at
                FROM project_repository r
                LEFT JOIN repository_inventory ri ON ri.repository_id = r.id
                LEFT JOIN LATERAL (SELECT reason, received_at FROM inventory_import_failure x
                                   WHERE x.repository_id = r.id AND (ri.imported_at IS NULL OR x.received_at > ri.imported_at)
                                   ORDER BY x.received_at DESC LIMIT 1) f ON true
                WHERE r.project_id = ? ORDER BY r.principal DESC, r.created_at, r.id
                """;
        List<RepositorySummary> depots = new ArrayList<>();
        try (PreparedStatement requete = connexion.prepareStatement(sql)) {
            requete.setObject(1, projectId);
            try (ResultSet lignes = requete.executeQuery()) {
                while (lignes.next()) {
                    String motif = lignes.getString(9);
                    depots.add(new RepositorySummary(lignes.getObject(1, UUID.class), lignes.getString(2), lignes.getString(3),
                            lignes.getString(4), lignes.getBoolean(5), lignes.getString(6), instant(lignes, 7),
                            instant(lignes, 8), motif == null ? null : new RepositoryFailure(motif, instant(lignes, 10))));
                }
            }
        }
        return depots;
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
                Long age = sourceDate == null ? null : Duration.between(sourceDate, horloge.instant()).toDays();
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
            ContexteBase.appliquer(connexion, ContexteBase.Contexte.duCompte(accountId));
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
        } catch (SQLException erreur) {
            throw new IllegalStateException("lecture des projets impossible", erreur);
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
            ContexteBase.appliquer(connexion, ContexteBase.Contexte.duCompte(accountId));
            long total = count(connexion, "SELECT count(*) FROM component WHERE project_id = ?", projectId);
            List<ComponentItem> lus = new ArrayList<>();
            try (PreparedStatement requete = connexion.prepareStatement(sql)) {
                requete.setObject(1, projectId);
                requete.setInt(2, appliedSize);
                requete.setLong(3, (long) appliedPage * appliedSize);
                try (ResultSet lignes = requete.executeQuery()) {
                    while (lignes.next()) {
                        lus.add(new ComponentItem(lignes.getObject(1, UUID.class), lignes.getString(2), lignes.getString(3),
                                lignes.getString(4), lignes.getString(5), (Boolean) lignes.getObject(6), List.of()));
                    }
                }
            }
            Map<UUID, List<ComponentRepository>> depots = depotsDesComposants(connexion,
                    lus.stream().map(ComponentItem::id).toList());
            List<ComponentItem> items = lus.stream().map(composant -> new ComponentItem(composant.id(), composant.name(),
                    composant.version(), composant.purl(), composant.packageKey(), composant.transitive(),
                    depots.getOrDefault(composant.id(), List.of()))).toList();
            return new ComponentPage(projectId, items, appliedPage, appliedSize, total);
        } catch (SQLException erreur) {
            throw new IllegalStateException("lecture des composants impossible", erreur);
        }
    }

    /**
     * Page des imports refusés ou en erreur d'un projet, du plus récent au plus ancien (INV-05, ADR-0040) ; page vide
     * pour un projet hors des espaces du compte (RLS).
     */
    @Transactional
    public Page<ImportFailure> failures(UUID projectId, UUID accountId, Pagination pagination) {
        try (Connection connexion = dataSource.getConnection()) {
            ContexteBase.appliquer(connexion, ContexteBase.Contexte.duCompte(accountId));
            long total = count(connexion, "SELECT count(*) FROM inventory_import_failure WHERE project_id = ?", projectId);
            List<ImportFailure> failures = Jdbc.lister(connexion, "SELECT id, reason, source_ref, received_at"
                    + " FROM inventory_import_failure WHERE project_id = ? ORDER BY received_at DESC, id DESC LIMIT ? OFFSET ?",
                    Jdbc.parametres(projectId, pagination.taille(), pagination.decalage()),
                    lignes -> new ImportFailure(lignes.getObject(1, UUID.class), lignes.getString(2), lignes.getString(3),
                            instant(lignes, 4)));
            return Page.depuis(failures, pagination, total);
        } catch (SQLException erreur) {
            throw new IllegalStateException("lecture des imports refusés impossible", erreur);
        }
    }

    /**
     * Composants dont la clé de paquet figure parmi celles données, éventuellement limités à un projet. Pour un
     * composant présent dans plusieurs dépôts (INV-10), l'origine et la date retenues sont celles de sa meilleure
     * occurrence : SBOM non obsolète, puis SBOM obsolète, puis manifestes, et à rang égal la source la plus récente
     * ([PROPOSITION] P-7). Une occurrence sans dépôt, ou un composant sans occurrence, prend l'origine de la ligne
     * de synthèse {@code inventory}.
     */
    @Transactional
    public List<ComponentMatch> componentsMatching(Collection<String> packageKeys, Optional<UUID> projectId) {
        return componentsMatching(packageKeys, projectId, Optional.empty());
    }

    /**
     * Composants dont la clé de paquet figure parmi celles données, éventuellement limités à un projet et à un espace :
     * le filtre d'espace s'applique en base (radar, RAD-10), jamais après lecture des composants de tous les espaces.
     * Origine et date retenues comme pour {@link #componentsMatching(Collection, Optional)}.
     */
    @Transactional
    public List<ComponentMatch> componentsMatching(Collection<String> packageKeys, Optional<UUID> projectId,
            Optional<UUID> espaceId) {
        if (packageKeys.isEmpty()) {
            return new ArrayList<>();
        }
        String sql = """
                SELECT c.id, c.project_id, p.workspace_id, c.package_key, c.purl, c.version, c.transitive,
                       coalesce(ri.origin, i.origin), CASE WHEN ri.repository_id IS NULL THEN i.source_date ELSE ri.source_date END
                FROM component c JOIN inventory i ON i.project_id = c.project_id JOIN project p ON p.id = c.project_id
                LEFT JOIN component_occurrence o ON o.component_id = c.id
                LEFT JOIN repository_inventory ri ON ri.repository_id = o.repository_id
                WHERE c.package_key = ANY (?) AND (?::uuid IS NULL OR c.project_id = ?::uuid)
                  AND (?::uuid IS NULL OR p.workspace_id = ?::uuid)
                ORDER BY c.project_id, c.package_key, c.version, c.id
                """;
        Map<UUID, ComponentMatch> meilleures = new LinkedHashMap<>();
        try (Connection connexion = dataSource.getConnection(); PreparedStatement requete = connexion.prepareStatement(sql)) {
            requete.setArray(1, connexion.createArrayOf("varchar", packageKeys.toArray()));
            requete.setObject(2, projectId.orElse(null));
            requete.setObject(3, projectId.orElse(null));
            requete.setObject(4, espaceId.orElse(null));
            requete.setObject(5, espaceId.orElse(null));
            try (ResultSet lignes = requete.executeQuery()) {
                while (lignes.next()) {
                    String origin = lignes.getString(8);
                    Instant date = instant(lignes, 9);
                    ComponentMatch candidat = new ComponentMatch(lignes.getObject(1, UUID.class), lignes.getObject(2, UUID.class),
                            lignes.getObject(3, UUID.class), lignes.getString(4), lignes.getString(5), lignes.getString(6),
                            (Boolean) lignes.getObject(7), origin, date, !"SBOM".equals(origin) || sbomObsolete(origin, date));
                    meilleures.merge(candidat.componentId(), candidat, InventoryQueries::meilleure);
                }
            }
        } catch (SQLException erreur) {
            throw new IllegalStateException("recherche des composants impossible", erreur);
        }
        return new ArrayList<>(meilleures.values());
    }

    /** Meilleure de deux occurrences d'un même composant (P-7). */
    private static ComponentMatch meilleure(ComponentMatch gauche, ComponentMatch droite) {
        int rangA = rang(gauche);
        int rangB = rang(droite);
        if (rangA != rangB) {
            return rangA < rangB ? gauche : droite;
        }
        if (gauche.inventoryDate() == null) {
            return droite.inventoryDate() == null ? gauche : droite;
        }
        return droite.inventoryDate() != null && droite.inventoryDate().isAfter(gauche.inventoryDate()) ? droite : gauche;
    }

    /** 0 : SBOM non obsolète ; 1 : SBOM obsolète ; 2 : manifestes ou origine inconnue. */
    private static int rang(ComponentMatch correspondance) {
        if (!"SBOM".equals(correspondance.inventoryOrigin())) {
            return 2;
        }
        return correspondance.inventoryObsolete() ? 1 : 0;
    }

    /** Origine du dernier import réussi d'un dépôt (traitement technique), s'il existe. */
    @Transactional
    public Optional<String> originDepot(UUID repositoryId) {
        try (Connection connexion = dataSource.getConnection(); PreparedStatement requete =
                connexion.prepareStatement("SELECT origin FROM repository_inventory WHERE repository_id = ?")) {
            requete.setObject(1, repositoryId);
            try (ResultSet lignes = requete.executeQuery()) {
                return lignes.next() ? Optional.of(lignes.getString(1)) : Optional.empty();
            }
        } catch (SQLException erreur) {
            throw new IllegalStateException("lecture de l'inventaire du dépôt impossible", erreur);
        }
    }

    /** Projets dont la recorrélation est restée en attente (marqueur posé, INV-09-S4), traitement technique. */
    @Transactional
    public List<UUID> projetsARecorreler() {
        List<UUID> projets = new ArrayList<>();
        try (Connection connexion = dataSource.getConnection(); PreparedStatement requete = connexion.prepareStatement(
                "SELECT project_id FROM inventory WHERE recorrelation_pending ORDER BY imported_at, project_id");
                ResultSet lignes = requete.executeQuery()) {
            while (lignes.next()) {
                projets.add(lignes.getObject(1, UUID.class));
            }
        } catch (SQLException erreur) {
            throw new IllegalStateException("lecture des recorrélations en attente impossible", erreur);
        }
        return projets;
    }

    /**
     * Identifiant de l'état courant de l'inventaire d'un projet (renouvelé à chaque import ou reconstitution), lu au
     * début d'une recorrélation pour n'effacer le marqueur que si l'inventaire n'a pas changé entre-temps.
     */
    @Transactional
    public Optional<UUID> importCourant(UUID projectId) {
        return identifiantDuProjet("SELECT import_id FROM inventory WHERE project_id = ?", projectId,
                "lecture de l'inventaire impossible");
    }

    /** Dépôts de chacun des composants donnés, le principal d'abord, lus sous le contexte de la connexion. */
    private static Map<UUID, List<ComponentRepository>> depotsDesComposants(Connection connexion, List<UUID> composants)
            throws SQLException {
        Map<UUID, List<ComponentRepository>> depots = new HashMap<>();
        if (composants.isEmpty()) {
            return depots;
        }
        String sql = """
                SELECT o.component_id, r.id, r.provider, r.repository FROM component_occurrence o
                JOIN project_repository r ON r.id = o.repository_id
                WHERE o.component_id = ANY (?) ORDER BY r.principal DESC, r.created_at, r.id
                """;
        try (PreparedStatement requete = connexion.prepareStatement(sql)) {
            requete.setArray(1, connexion.createArrayOf("uuid", composants.toArray()));
            try (ResultSet lignes = requete.executeQuery()) {
                while (lignes.next()) {
                    depots.computeIfAbsent(lignes.getObject(1, UUID.class), cle -> new ArrayList<>())
                            .add(new ComponentRepository(lignes.getObject(2, UUID.class), lignes.getString(3), lignes.getString(4)));
                }
            }
        }
        return depots;
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
        } catch (SQLException erreur) {
            throw new IllegalStateException("lecture des paquets du projet impossible", erreur);
        }
        return keys;
    }

    /** Espace du projet, s'il existe. */
    @Transactional
    public Optional<UUID> espaceDe(UUID projectId) {
        return identifiantDuProjet("SELECT workspace_id FROM project WHERE id = ?", projectId, "lecture du projet impossible");
    }

    /** Identifiant lu par une requête dont le seul paramètre est le projet, vide si aucune ligne ne correspond. */
    private Optional<UUID> identifiantDuProjet(String sql, UUID projectId, String echec) {
        try (Connection connexion = dataSource.getConnection()) {
            return Jdbc.unique(connexion, sql, Jdbc.parametres(projectId), lignes -> lignes.getObject(1, UUID.class));
        } catch (SQLException erreur) {
            throw new IllegalStateException(echec, erreur);
        }
    }

    /**
     * SBOM obsolète : plus de 30 jours après sa génération, ou sans date de génération. Un inventaire
     * de repli (manifestes) n'est pas une SBOM : l'indicateur restitué vaut faux, mais la corrélation
     * ne le juge jamais assez fiable pour le niveau « exact » (COR-06).
     */
    private boolean sbomObsolete(String origin, Instant sourceDate) {
        return "SBOM".equals(origin) && (sourceDate == null || sourceDate.plus(sbomMaxAge).isBefore(horloge.instant()));
    }

    /** Page des composants d'un projet, triés par clé de paquet, version puis identifiant (tri stable). */
    private static Page<ComponentView> components(Connection connexion, UUID projectId, int taille, long decalage)
            throws SQLException {
        long total = count(connexion, "SELECT count(*) FROM component WHERE project_id = ?", projectId);
        List<ComponentView> components = Jdbc.lister(connexion, "SELECT id, purl, package_key, version, transitive FROM component"
                + " WHERE project_id = ? ORDER BY package_key, version NULLS LAST, id LIMIT ? OFFSET ?",
                Jdbc.parametres(projectId, taille, decalage),
                lignes -> new ComponentView(lignes.getObject(1, UUID.class), lignes.getString(2), lignes.getString(3),
                        lignes.getString(4), (Boolean) lignes.getObject(5)));
        return new Page<>(components, Math.toIntExact(decalage / Math.max(taille, 1)), taille, total);
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
        } catch (JsonProcessingException erreur) {
            throw new IllegalStateException("lecture des composants ignorés impossible", erreur);
        }
    }

    private static Instant instant(ResultSet lignes, int column) throws SQLException {
        OffsetDateTime value = lignes.getObject(column, OffsetDateTime.class);
        return value == null ? null : value.toInstant();
    }
}
