package io.argos.impact;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import javax.sql.DataSource;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.event.TransactionPhase;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;

import org.jboss.logging.Logger;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import io.quarkus.narayana.jta.QuarkusTransaction;

import io.argos.inventory.InventoryQueries;
import io.argos.inventory.InventoryReplaced;
import io.argos.signal.AffectedPackage;
import io.argos.signal.SignalChanged;
import io.argos.signal.SignalQueries;

/**
 * Corrélation déterministe signal ↔ projets (DF-COR-2, ADR-0013).
 *
 * <p>Les impacts sont calculés et enregistrés dans une transaction propre, avant toute analyse
 * complémentaire (décision D3) : l'indisponibilité ou le plafond de l'IA ne peut pas les bloquer
 * (COR-05). Un nouveau calcul met à jour l'impact existant du triplet signal + projet + composant
 * au lieu d'en créer un second (COR-07) et retire les impacts qui ne sont plus vérifiés.
 */
@ApplicationScoped
public class CorrelationEngine {

    private static final Logger LOG = Logger.getLogger(CorrelationEngine.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** Résultat d'une corrélation. {@code analysis} : NONE, DONE, UNAVAILABLE ou BUDGET_EXHAUSTED. */
    public record CorrelationReport(UUID signalId, int created, int updated, int removed, List<UUID> impactIds,
            String analysis, List<ComplementaryAnalysis.Suggestion> suggestions) {
    }

    private record Deterministic(int created, int updated, int removed, List<UUID> impactIds) {
    }

    @Inject
    DataSource dataSource;

    @Inject
    SignalQueries signals;

    @Inject
    InventoryQueries inventories;

    @Inject
    Instance<ComplementaryAnalysis> analysis;

    void onSignalChanged(@Observes(during = TransactionPhase.AFTER_SUCCESS) SignalChanged event) {
        try {
            correlate(event.signalId(), Optional.empty());
        } catch (RuntimeException e) {
            LOG.errorf(e, "corrélation du signal %s en échec", event.signalId());
        }
    }

    void onInventoryReplaced(@Observes(during = TransactionPhase.AFTER_SUCCESS) InventoryReplaced event) {
        try {
            correlateProject(event.projectId());
        } catch (RuntimeException e) {
            LOG.errorf(e, "corrélation du projet %s en échec", event.projectId());
        }
    }

    /** Corrèle tous les signaux qui affectent un paquet de l'inventaire du projet. */
    public List<CorrelationReport> correlateProject(UUID projectId) {
        List<UUID> signalIds = QuarkusTransaction.requiringNew()
                .call(() -> signals.signalsAffecting(inventories.packageKeys(projectId)));
        List<CorrelationReport> reports = new ArrayList<>();
        for (UUID signalId : signalIds) {
            reports.add(correlate(signalId, Optional.of(projectId)));
        }
        return reports;
    }

    /** Corrèle un signal avec tous les projets, ou avec un seul projet. */
    public CorrelationReport correlate(UUID signalId, Optional<UUID> projectId) {
        Deterministic d = QuarkusTransaction.requiringNew().call(() -> deterministic(signalId, projectId));
        String status = "NONE";
        List<ComplementaryAnalysis.Suggestion> suggestions = List.of();
        if (analysis.isResolvable()) {
            ComplementaryAnalysis a = analysis.get();
            if (!a.budgetAvailable()) {
                status = "BUDGET_EXHAUSTED";
            } else {
                try {
                    // Les suggestions sont informatives : elles ne créent aucun impact (COR-04).
                    suggestions = List.copyOf(a.analyze(signalId, d.impactIds()));
                    status = "DONE";
                } catch (RuntimeException e) {
                    status = "UNAVAILABLE";
                    LOG.infof("analyse complémentaire indisponible pour le signal %s : %s", signalId, e.getMessage());
                }
            }
        }
        return new CorrelationReport(signalId, d.created(), d.updated(), d.removed(), d.impactIds(), status, suggestions);
    }

    private Deterministic deterministic(UUID signalId, Optional<UUID> projectId) throws SQLException {
        SignalQueries.SignalView signal = signals.find(signalId)
                .orElseThrow(() -> new IllegalArgumentException("signal inconnu : " + signalId));
        Map<String, List<AffectedPackage>> byKey = new HashMap<>();
        for (AffectedPackage a : signal.affected()) {
            byKey.computeIfAbsent(a.packageKey(), k -> new ArrayList<>()).add(a);
        }
        int created = 0;
        int updated = 0;
        List<UUID> kept = new ArrayList<>();
        try (Connection c = dataSource.getConnection()) {
            RlsSession.technical(c);
            for (InventoryQueries.ComponentMatch m : inventories.componentsMatching(byKey.keySet(), projectId)) {
                Optional<Match> best = best(byKey.get(m.packageKey()), m);
                if (best.isEmpty()) {
                    continue;
                }
                UUID id = UUID.randomUUID();
                try (PreparedStatement ps = c.prepareStatement("""
                        INSERT INTO project_impact(id, signal_id, signal_version_id, project_id, workspace_id, component_id,
                               level, method, transitive, inventory_origin, inventory_date, provenance, created_at, updated_at)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb, now(), now())
                        ON CONFLICT (signal_id, project_id, component_id) DO UPDATE SET
                               signal_version_id = excluded.signal_version_id, level = excluded.level,
                               method = excluded.method, transitive = excluded.transitive,
                               inventory_origin = excluded.inventory_origin, inventory_date = excluded.inventory_date,
                               provenance = excluded.provenance, updated_at = now()
                        RETURNING id, (xmax = 0)
                        """)) {
                    ps.setObject(1, id);
                    ps.setObject(2, signalId);
                    ps.setObject(3, signal.currentVersionId());
                    ps.setObject(4, m.projectId());
                    ps.setObject(5, m.workspaceId());
                    ps.setObject(6, m.componentId());
                    ps.setString(7, best.get().level().name());
                    ps.setString(8, MatchRules.METHOD);
                    if (m.transitive() == null) {
                        ps.setNull(9, Types.BOOLEAN);
                    } else {
                        ps.setBoolean(9, m.transitive());
                    }
                    ps.setString(10, m.inventoryOrigin());
                    ps.setObject(11, m.inventoryDate() == null ? null : m.inventoryDate().atOffset(ZoneOffset.UTC));
                    ps.setString(12, provenance(signal, m, best.get()).toString());
                    try (ResultSet rs = ps.executeQuery()) {
                        rs.next();
                        kept.add(rs.getObject(1, UUID.class));
                        if (rs.getBoolean(2)) {
                            created++;
                        } else {
                            updated++;
                        }
                    }
                }
            }
            int removed;
            try (PreparedStatement ps = c.prepareStatement("""
                    DELETE FROM project_impact WHERE signal_id = ? AND (?::uuid IS NULL OR project_id = ?::uuid)
                      AND NOT (id = ANY (?))
                    """)) {
                ps.setObject(1, signalId);
                ps.setObject(2, projectId.orElse(null));
                ps.setObject(3, projectId.orElse(null));
                ps.setArray(4, c.createArrayOf("uuid", kept.toArray()));
                removed = ps.executeUpdate();
            }
            return new Deterministic(created, updated, removed, List.copyOf(kept));
        }
    }

    private record Match(MatchRules.Level level, AffectedPackage affected) {
    }

    /** Niveau le plus fort parmi les entrées de l'avis pour ce paquet. */
    private static Optional<Match> best(List<AffectedPackage> candidates, InventoryQueries.ComponentMatch m) {
        Match best = null;
        for (AffectedPackage a : candidates) {
            Optional<MatchRules.Level> level = MatchRules.evaluate(a, m.version(), !m.inventoryObsolete());
            if (level.isPresent() && (best == null || level.get().ordinal() < best.level().ordinal())) {
                best = new Match(level.get(), a);
            }
        }
        return Optional.ofNullable(best);
    }

    private static ObjectNode provenance(SignalQueries.SignalView signal, InventoryQueries.ComponentMatch m, Match match) {
        ObjectNode p = MAPPER.createObjectNode();
        p.put("signalSource", signal.source());
        p.put("signalExternalId", signal.externalId());
        p.put("signalVersionId", signal.currentVersionId().toString());
        p.put("packageKey", m.packageKey());
        p.put("componentPurl", m.purl());
        p.put("componentVersion", m.version());
        p.put("declaredPurl", match.affected().declaredPurl());
        p.set("affectedRanges", MAPPER.valueToTree(match.affected().ranges()));
        p.set("affectedVersions", MAPPER.valueToTree(match.affected().versions()));
        p.put("inventoryOrigin", m.inventoryOrigin());
        p.put("inventoryDate", m.inventoryDate() == null ? null : m.inventoryDate().toString());
        p.put("inventoryObsolete", m.inventoryObsolete());
        p.put("method", MatchRules.METHOD);
        return p;
    }
}
