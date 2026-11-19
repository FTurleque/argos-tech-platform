package io.argos.inventory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Types;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import javax.sql.DataSource;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Event;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Écriture des inventaires : remplacement en bloc de l'inventaire de référence (D2, INV-01-S2)
 * en conservant l'identité des composants inchangés, composants ignorés faute de PURL signalés avec
 * l'inventaire (INV-01, décision D6 du 2026-11-18), et journal des imports refusés (INV-05).
 */
@ApplicationScoped
public class InventoryStore {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Inject
    DataSource dataSource;

    @Inject
    Event<InventoryReplaced> replaced;

    @Transactional
    public void replace(UUID projectId, String origin, ParsedInventory parsed, String sourceRef) {
        UUID importId = UUID.randomUUID();
        try (Connection c = dataSource.getConnection()) {
            try (PreparedStatement ps = c.prepareStatement("""
                    INSERT INTO inventory(project_id, origin, source_date, spec_version, source_ref, import_id, imported_at,
                        ignored_count, ignored_components)
                    VALUES (?, ?, ?, ?, ?, ?, now(), ?, ?::jsonb)
                    ON CONFLICT (project_id) DO UPDATE SET origin = excluded.origin, source_date = excluded.source_date,
                        spec_version = excluded.spec_version, source_ref = excluded.source_ref,
                        import_id = excluded.import_id, imported_at = excluded.imported_at,
                        ignored_count = excluded.ignored_count, ignored_components = excluded.ignored_components
                    """)) {
                ps.setObject(1, projectId);
                ps.setString(2, origin);
                ps.setObject(3, parsed.sourceDate() == null ? null : parsed.sourceDate().atOffset(ZoneOffset.UTC));
                ps.setString(4, parsed.specVersion());
                ps.setString(5, cut(sourceRef));
                ps.setObject(6, importId);
                ps.setInt(7, parsed.ignoredCount());
                ps.setString(8, ignoredJson(parsed));
                ps.executeUpdate();
            }
            try (PreparedStatement ps = c.prepareStatement("""
                    INSERT INTO component(id, project_id, package_key, purl, version, transitive, import_id)
                    VALUES (?, ?, ?, ?, ?, ?, ?)
                    ON CONFLICT (project_id, package_key, (coalesce(version, '')))
                    DO UPDATE SET purl = excluded.purl, transitive = excluded.transitive, import_id = excluded.import_id
                    """)) {
                for (ParsedInventory.Item item : parsed.components()) {
                    ps.setObject(1, UUID.randomUUID());
                    ps.setObject(2, projectId);
                    ps.setString(3, item.packageKey());
                    ps.setString(4, item.purl());
                    ps.setString(5, item.version());
                    if (item.transitive() == null) {
                        ps.setNull(6, Types.BOOLEAN);
                    } else {
                        ps.setBoolean(6, item.transitive());
                    }
                    ps.setObject(7, importId);
                    ps.addBatch();
                }
                ps.executeBatch();
            }
            try (PreparedStatement ps = c.prepareStatement("DELETE FROM component WHERE project_id = ? AND import_id <> ?")) {
                ps.setObject(1, projectId);
                ps.setObject(2, importId);
                ps.executeUpdate();
            }
        } catch (SQLException e) {
            throw new IllegalStateException("enregistrement de l'inventaire impossible", e);
        }
        replaced.fire(new InventoryReplaced(projectId, origin));
    }

    @Transactional(Transactional.TxType.REQUIRES_NEW)
    public void recordFailure(UUID projectId, String reason, String sourceRef) {
        try (Connection c = dataSource.getConnection(); PreparedStatement ps = c.prepareStatement("""
                INSERT INTO inventory_import_failure(id, project_id, reason, source_ref, received_at) VALUES (?, ?, ?, ?, ?)
                """)) {
            ps.setObject(1, UUID.randomUUID());
            ps.setObject(2, projectId);
            ps.setString(3, reason.length() > 1000 ? reason.substring(0, 1000) : reason);
            ps.setString(4, cut(sourceRef));
            ps.setObject(5, Instant.now().atOffset(ZoneOffset.UTC));
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("enregistrement de l'échec d'import impossible", e);
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
