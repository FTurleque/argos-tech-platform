package io.argos.inventory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import javax.sql.DataSource;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Event;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Écriture des inventaires : remplacement en bloc de l'inventaire d'un dépôt (D2, INV-01-S2) en conservant
 * l'identité des composants inchangés, union des dépôts d'un projet (INV-10, décision D6 du changement
 * automatiser-chaine-veille), composants ignorés faute de PURL signalés avec l'inventaire (INV-01, décision D6 du
 * 2026-11-18), journal des imports refusés ou en erreur (INV-05, INV-07-S2) et marqueur de recorrélation
 * (INV-09-S4, [PROPOSITION] P-10).
 *
 * <p>Chaque écriture prend le verrou consultatif transactionnel du projet
 * ({@code pg_advisory_xact_lock(hashtext('inventory:' || projet))}) : imports manuels et planifiés d'un même
 * projet s'exécutent l'un après l'autre, et l'inventaire de référence résulte toujours d'imports complets
 * (INV-08-S2). {@link InventoryReplaced} est publié dans la transaction et observé après sa validation.
 */
@ApplicationScoped
public class InventoryStore {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /**
     * Inventaire lu pour un emplacement du projet : un dépôt, ou l'emplacement sans dépôt ({@code repositoryId}
     * nul) d'un import manuel pour un projet sans dépôt principal ([PROPOSITION] P-B1).
     */
    public record Remplacement(UUID repositoryId, String origin, ParsedInventory parsed, String sourceRef) {
    }

    @Inject
    DataSource dataSource;

    @Inject
    Event<InventoryReplaced> replaced;

    /** Remplace l'inventaire d'un seul emplacement du projet (import manuel, INV-01, INV-08-S1). */
    @Transactional
    public void replace(UUID projectId, Remplacement remplacement) {
        remplacer(projectId, List.of(remplacement));
    }

    /**
     * Remplace, dans une seule transaction et sous le verrou du projet, les occurrences des emplacements donnés,
     * puis recalcule l'union : un composant sans occurrence est retiré (INV-B1), le caractère transitif d'un
     * composant présent dans plusieurs dépôts vaut « direct » dès qu'un dépôt l'utilise directement. Pose le
     * marqueur de recorrélation et publie {@link InventoryReplaced}. Les emplacements absents de la liste gardent
     * leur dernier import réussi (INV-10-S2).
     */
    @Transactional
    public void remplacer(UUID projectId, List<Remplacement> remplacements) {
        if (remplacements.isEmpty()) {
            return;
        }
        String origin = remplacements.get(remplacements.size() - 1).origin();
        try (Connection c = dataSource.getConnection()) {
            verrouiller(c, projectId);
            for (Remplacement r : remplacements) {
                UUID importId = UUID.randomUUID();
                ecrireEntete(c, projectId, r, importId);
                ecrireOccurrences(c, projectId, r, importId);
            }
            reconstituer(c, projectId);
        } catch (SQLException e) {
            throw new IllegalStateException("enregistrement de l'inventaire impossible", e);
        }
        replaced.fire(new InventoryReplaced(projectId, origin));
    }

    /**
     * Détache un dépôt de son projet puis reconstitue l'inventaire du projet (INV-10-S3) : les composants qui ne
     * figuraient que dans ce dépôt sont retirés et la recorrélation suit.
     *
     * @return vrai si le dépôt appartenait au projet
     */
    @Transactional
    public boolean detacherDepot(UUID projectId, UUID repositoryId) {
        boolean retire;
        try (Connection c = dataSource.getConnection()) {
            verrouiller(c, projectId);
            // Les occurrences et le dernier import du dépôt disparaissent avec lui (ON DELETE CASCADE, V19).
            try (PreparedStatement ps = c.prepareStatement("DELETE FROM project_repository WHERE id = ? AND project_id = ?")) {
                ps.setObject(1, repositoryId);
                ps.setObject(2, projectId);
                retire = ps.executeUpdate() == 1;
            }
            if (retire && exists(c, "SELECT 1 FROM inventory WHERE project_id = ?", projectId)) {
                reconstituer(c, projectId);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("retrait du dépôt impossible", e);
        }
        if (retire) {
            replaced.fire(new InventoryReplaced(projectId, null));
        }
        return retire;
    }

    /**
     * Relance la recorrélation d'un projet dont le marqueur est resté posé (INV-09-S4) : publie de nouveau
     * {@link InventoryReplaced}, observé après la validation de cette transaction.
     */
    @Transactional
    public void relancerRecorrelation(UUID projectId) {
        replaced.fire(new InventoryReplaced(projectId, null));
    }

    /**
     * Efface le marqueur de recorrélation si l'inventaire n'a pas changé depuis le début de la recorrélation
     * ({@code importId} lu à son début) : un import survenu entre-temps garde son marqueur et sa recorrélation.
     */
    @Transactional
    public void recorrelationTerminee(UUID projectId, UUID importId) {
        try (Connection c = dataSource.getConnection(); PreparedStatement ps = c.prepareStatement(
                "UPDATE inventory SET recorrelation_pending = false WHERE project_id = ? AND import_id = ?")) {
            ps.setObject(1, projectId);
            ps.setObject(2, importId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("effacement du marqueur de recorrélation impossible", e);
        }
    }

    /** Enregistre un import refusé pour un import manuel (sans dépôt). */
    @Transactional(Transactional.TxType.REQUIRES_NEW)
    public void recordFailure(UUID projectId, String reason, String sourceRef) {
        recordFailure(projectId, null, reason, sourceRef);
    }

    /** Enregistre un import refusé ou en erreur, rattaché au dépôt concerné s'il est connu (INV-05, INV-07-S2). */
    @Transactional(Transactional.TxType.REQUIRES_NEW)
    public void recordFailure(UUID projectId, UUID repositoryId, String reason, String sourceRef) {
        try (Connection c = dataSource.getConnection(); PreparedStatement ps = c.prepareStatement("""
                INSERT INTO inventory_import_failure(id, project_id, repository_id, reason, source_ref, received_at)
                VALUES (?, ?, ?, ?, ?, ?)
                """)) {
            ps.setObject(1, UUID.randomUUID());
            ps.setObject(2, projectId);
            ps.setObject(3, repositoryId);
            ps.setString(4, reason.length() > 1000 ? reason.substring(0, 1000) : reason);
            ps.setString(5, cut(sourceRef));
            ps.setObject(6, Instant.now().atOffset(ZoneOffset.UTC));
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("enregistrement de l'échec d'import impossible", e);
        }
    }

    private static void verrouiller(Connection c, UUID projectId) throws SQLException {
        try (PreparedStatement lock = c.prepareStatement("SELECT pg_advisory_xact_lock(hashtext(?))")) {
            lock.setString(1, "inventory:" + projectId);
            lock.execute();
        }
    }

    /**
     * Ligne de synthèse {@code inventory} (créée si besoin : les composants la référencent) et dernier import du
     * dépôt ({@code repository_inventory}).
     */
    private static void ecrireEntete(Connection c, UUID projectId, Remplacement r, UUID importId) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("""
                INSERT INTO inventory(project_id, origin, source_date, spec_version, source_ref, import_id, imported_at,
                    ignored_count, ignored_components)
                VALUES (?, ?, ?, ?, ?, ?, now(), ?, ?::jsonb)
                ON CONFLICT (project_id) DO UPDATE SET origin = excluded.origin, source_date = excluded.source_date,
                    spec_version = excluded.spec_version, source_ref = excluded.source_ref,
                    import_id = excluded.import_id, imported_at = excluded.imported_at,
                    ignored_count = excluded.ignored_count, ignored_components = excluded.ignored_components
                """)) {
            setEntete(ps, projectId, r, importId);
            ps.executeUpdate();
        }
        if (r.repositoryId() == null) {
            return;
        }
        try (PreparedStatement ps = c.prepareStatement("""
                INSERT INTO repository_inventory(project_id, origin, source_date, spec_version, source_ref, import_id,
                    imported_at, ignored_count, ignored_components, repository_id)
                VALUES (?, ?, ?, ?, ?, ?, now(), ?, ?::jsonb, ?)
                ON CONFLICT (repository_id) DO UPDATE SET origin = excluded.origin, source_date = excluded.source_date,
                    spec_version = excluded.spec_version, source_ref = excluded.source_ref,
                    import_id = excluded.import_id, imported_at = excluded.imported_at,
                    ignored_count = excluded.ignored_count, ignored_components = excluded.ignored_components
                """)) {
            setEntete(ps, projectId, r, importId);
            ps.setObject(9, r.repositoryId());
            ps.executeUpdate();
        }
    }

    private static void setEntete(PreparedStatement ps, UUID projectId, Remplacement r, UUID importId) throws SQLException {
        ParsedInventory parsed = r.parsed();
        ps.setObject(1, projectId);
        ps.setString(2, r.origin());
        ps.setObject(3, parsed.sourceDate() == null ? null : parsed.sourceDate().atOffset(ZoneOffset.UTC));
        ps.setString(4, parsed.specVersion());
        ps.setString(5, cut(r.sourceRef()));
        ps.setObject(6, importId);
        ps.setInt(7, parsed.ignoredCount());
        ps.setString(8, ignoredJson(parsed));
    }

    /**
     * Remplace les occurrences de l'emplacement : composants créés ou retrouvés par leur identité (projet, paquet,
     * version), anciennes occurrences de l'emplacement supprimées. Un import dans un dépôt supprime aussi les
     * occurrences sans dépôt, antérieures au rattachement d'un dépôt ([PROPOSITION] P-B1).
     */
    private static void ecrireOccurrences(Connection c, UUID projectId, Remplacement r, UUID importId) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(r.repositoryId() == null
                ? "DELETE FROM component_occurrence WHERE project_id = ? AND repository_id IS NULL"
                : "DELETE FROM component_occurrence WHERE project_id = ? AND (repository_id = ? OR repository_id IS NULL)")) {
            ps.setObject(1, projectId);
            if (r.repositoryId() != null) {
                ps.setObject(2, r.repositoryId());
            }
            ps.executeUpdate();
        }
        try (PreparedStatement composant = c.prepareStatement("""
                INSERT INTO component(id, project_id, package_key, purl, version, transitive, import_id)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (project_id, package_key, (coalesce(version, '')))
                DO UPDATE SET purl = excluded.purl, import_id = excluded.import_id
                RETURNING id
                """);
                PreparedStatement occurrence = c.prepareStatement("""
                        INSERT INTO component_occurrence(component_id, repository_id, project_id, transitive, import_id)
                        VALUES (?, ?, ?, ?, ?)
                        ON CONFLICT (component_id, (coalesce(repository_id, '00000000-0000-0000-0000-000000000000'::uuid)))
                        DO UPDATE SET transitive = CASE
                            WHEN component_occurrence.transitive = false OR excluded.transitive = false THEN false
                            ELSE coalesce(component_occurrence.transitive, excluded.transitive) END
                        """)) {
            for (ParsedInventory.Item item : r.parsed().components()) {
                composant.setObject(1, UUID.randomUUID());
                composant.setObject(2, projectId);
                composant.setString(3, item.packageKey());
                composant.setString(4, item.purl());
                composant.setString(5, item.version());
                setTransitive(composant, 6, item.transitive());
                composant.setObject(7, importId);
                UUID componentId;
                try (ResultSet rs = composant.executeQuery()) {
                    rs.next();
                    componentId = rs.getObject(1, UUID.class);
                }
                occurrence.setObject(1, componentId);
                occurrence.setObject(2, r.repositoryId());
                occurrence.setObject(3, projectId);
                setTransitive(occurrence, 4, item.transitive());
                occurrence.setObject(5, importId);
                occurrence.addBatch();
            }
            occurrence.executeBatch();
        }
    }

    /**
     * Recalcule l'union des dépôts (DC-6) : retire les composants sans occurrence, met à jour leur caractère
     * transitif, recopie dans la ligne de synthèse l'origine et les dates du dépôt principal (ou, sans dépôt
     * principal importé, du dernier dépôt importé) et la somme des composants ignorés, puis pose le marqueur de
     * recorrélation avec un nouvel identifiant d'import.
     */
    private static void reconstituer(Connection c, UUID projectId) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("""
                DELETE FROM component c WHERE c.project_id = ?
                  AND NOT EXISTS (SELECT 1 FROM component_occurrence o WHERE o.component_id = c.id)
                """)) {
            ps.setObject(1, projectId);
            ps.executeUpdate();
        }
        try (PreparedStatement ps = c.prepareStatement("""
                UPDATE component c SET transitive = agg.transitive
                FROM (SELECT component_id, CASE WHEN bool_or(transitive = false) THEN false
                                                WHEN bool_or(transitive) THEN true END AS transitive
                      FROM component_occurrence WHERE project_id = ? GROUP BY component_id) agg
                WHERE c.id = agg.component_id AND c.transitive IS DISTINCT FROM agg.transitive
                """)) {
            ps.setObject(1, projectId);
            ps.executeUpdate();
        }
        Optional<UUID> reference = depotDeReference(c, projectId);
        if (reference.isPresent()) {
            try (PreparedStatement ps = c.prepareStatement("""
                    UPDATE inventory i SET origin = ri.origin, source_date = ri.source_date, spec_version = ri.spec_version,
                        source_ref = ri.source_ref, imported_at = ri.imported_at,
                        ignored_count = (SELECT coalesce(sum(t.ignored_count), 0) FROM repository_inventory t
                                         WHERE t.project_id = i.project_id),
                        ignored_components = (SELECT coalesce(jsonb_agg(e.valeur ORDER BY e.rang_depot, e.rang), '[]'::jsonb)
                            FROM (SELECT x.valeur, x.rang, CASE WHEN t.repository_id = ri.repository_id THEN 0 ELSE 1 END
                                         AS rang_depot
                                  FROM repository_inventory t
                                  CROSS JOIN LATERAL jsonb_array_elements(t.ignored_components) WITH ORDINALITY AS x(valeur, rang)
                                  WHERE t.project_id = i.project_id
                                  ORDER BY rang_depot, t.imported_at DESC, x.rang LIMIT 100) e)
                    FROM repository_inventory ri WHERE i.project_id = ? AND ri.repository_id = ?
                    """)) {
                ps.setObject(1, projectId);
                ps.setObject(2, reference.get());
                ps.executeUpdate();
            }
        }
        try (PreparedStatement ps = c.prepareStatement(
                "UPDATE inventory SET import_id = ?, recorrelation_pending = true WHERE project_id = ?")) {
            ps.setObject(1, UUID.randomUUID());
            ps.setObject(2, projectId);
            ps.executeUpdate();
        }
    }

    /** Dépôt dont la synthèse reprend l'origine : le principal s'il a un import, sinon le dernier importé. */
    private static Optional<UUID> depotDeReference(Connection c, UUID projectId) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("""
                SELECT ri.repository_id FROM repository_inventory ri JOIN project_repository r ON r.id = ri.repository_id
                WHERE ri.project_id = ? ORDER BY r.principal DESC, ri.imported_at DESC, ri.repository_id LIMIT 1
                """)) {
            ps.setObject(1, projectId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(rs.getObject(1, UUID.class)) : Optional.empty();
            }
        }
    }

    private static boolean exists(Connection c, String sql, UUID projectId) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setObject(1, projectId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    private static void setTransitive(PreparedStatement ps, int index, Boolean transitive) throws SQLException {
        if (transitive == null) {
            ps.setNull(index, Types.BOOLEAN);
        } else {
            ps.setBoolean(index, transitive);
        }
    }

    /** Composants ignorés de l'import (au plus {@value ParsedInventory#MAX_SIGNALED}), en tableau JSON. */
    private static String ignoredJson(ParsedInventory parsed) {
        List<ParsedInventory.IgnoredComponent> ignored = parsed.ignoredComponents() == null ? List.of() : parsed.ignoredComponents();
        try {
            return MAPPER.writeValueAsString(ignored.subList(0, Math.min(ignored.size(), ParsedInventory.MAX_SIGNALED)));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("sérialisation des composants ignorés impossible", e);
        }
    }

    private static String cut(String value) {
        return value == null || value.length() <= 500 ? value : value.substring(0, 500);
    }
}
