package io.argos.impact;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import javax.sql.DataSource;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Event;
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
import io.argos.inventory.InventoryStore;
import io.argos.signal.AffectedPackage;
import io.argos.signal.SignalChanged;
import io.argos.signal.SignalQueries;

/**
 * Corrélation déterministe signal ↔ projets (DF-COR-2, ADR-0013).
 *
 * <p>Les impacts sont calculés et enregistrés dans une transaction propre, avant toute analyse
 * complémentaire (décision D3) : l'indisponibilité ou le plafond de l'IA ne peut pas les bloquer
 * (COR-05). Un nouveau calcul met à jour l'impact existant du triplet signal + projet + composant
 * au lieu d'en créer un second (COR-07) ; il conserve son état de traitement (COR-09-S2).
 *
 * <p>Un impact ouvert ou confirmé qui n'est plus vérifié n'est pas supprimé : il passe {@code RESOLU} avec l'auteur
 * « système » et un {@link MotifSysteme} (COR-11, décisions J4 et J5 du porteur du projet, 2026-11-19 ; P-3). Un
 * impact {@code ECARTE} n'est jamais modifié par le système (COR-11-S4) ; un impact {@code RESOLU} de nouveau vérifié
 * repasse {@code OUVERT} (COR-11-S5, P-6). Un impact dont le composant a quitté l'inventaire, puis y revient à la même
 * version, retrouve ce composant au lieu d'être dupliqué (INV-C4).
 */
@ApplicationScoped
public class CorrelationEngine {

    private static final Logger LOG = Logger.getLogger(CorrelationEngine.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** Résultat d'une corrélation. {@code analysis} : NONE, DONE, UNAVAILABLE ou BUDGET_EXHAUSTED. */
    public record CorrelationReport(UUID signalId, int created, int updated, int resolved, int reopened,
            List<UUID> impactIds, String analysis, List<ComplementaryAnalysis.Suggestion> suggestions) {
    }

    private record Deterministic(int created, int updated, int resolved, int reopened, List<UUID> impactIds,
            ImpactsRecorded recorded) {
    }

    @Inject
    DataSource dataSource;

    @Inject
    SignalQueries signals;

    @Inject
    InventoryQueries inventories;

    @Inject
    InventoryStore inventoryStore;

    @Inject
    Instance<ComplementaryAnalysis> analysis;

    @Inject
    Event<ImpactsRecorded> impactsRecorded;

    @Inject
    Event<ImpactsTraites> impactsTraites;

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

    /**
     * Recorrèle un projet (INV-09, décision D4 du changement automatiser-chaine-veille) : les signaux qui affectent
     * un paquet de son inventaire et ceux qui portent un impact ouvert ou confirmé sur ce projet, afin de résoudre les impacts
     * devenus sans objet. Quand tous les signaux sont traités, le marqueur de recorrélation posé par l'import est
     * effacé, sauf si l'inventaire a changé entre-temps ; un échec le laisse posé, et l'import planifié suivant
     * relance la recorrélation (INV-09-S4).
     */
    public List<CorrelationReport> correlateProject(UUID projectId) {
        Optional<UUID> importCourant = QuarkusTransaction.requiringNew().call(() -> inventories.importCourant(projectId));
        List<UUID> signalIds = QuarkusTransaction.requiringNew().call(() -> signalsToRecorrelate(projectId));
        List<CorrelationReport> reports = new ArrayList<>();
        for (UUID signalId : signalIds) {
            reports.add(correlate(signalId, Optional.of(projectId)));
        }
        importCourant.ifPresent(importId -> QuarkusTransaction.requiringNew()
                .run(() -> inventoryStore.recorrelationTerminee(projectId, importId)));
        return reports;
    }

    /**
     * Signaux qui affectent l'inventaire du projet, puis signaux porteurs d'un impact ouvert ou confirmé sur ce projet,
     * sans doublon. Un impact résolu dont le composant revient est retrouvé par le premier ensemble ; un impact écarté
     * n'est jamais modifié par le système (COR-11-S3).
     */
    private List<UUID> signalsToRecorrelate(UUID projectId) throws SQLException {
        Set<UUID> ids = new LinkedHashSet<>(signals.signalsAffecting(inventories.packageKeys(projectId)));
        ids.addAll(signalsWithActiveImpacts(projectId));
        return List.copyOf(ids);
    }

    /** Corrèle un signal avec tous les projets, ou avec un seul projet. */
    public CorrelationReport correlate(UUID signalId, Optional<UUID> projectId) {
        Deterministic d = QuarkusTransaction.requiringNew().call(() -> deterministic(signalId, projectId));
        publish(d.recorded());
        if (d.resolved() > 0) {
            try {
                impactsTraites.fire(new ImpactsTraites(signalId));
            } catch (RuntimeException e) {
                LOG.errorf(e, "publication des impacts résolus du signal %s en échec", signalId);
            }
        }
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
        return new CorrelationReport(signalId, d.created(), d.updated(), d.resolved(), d.reopened(), d.impactIds(),
                status, suggestions);
    }

    /**
     * Publie les impacts enregistrés, après la validation de leur transaction et avant l'analyse
     * complémentaire : un échec du traitement des alertes n'affecte ni les impacts ni la corrélation.
     */
    private void publish(ImpactsRecorded recorded) {
        if (recorded.impacts().isEmpty()) {
            return;
        }
        try {
            impactsRecorded.fire(recorded);
        } catch (RuntimeException e) {
            LOG.errorf(e, "publication des impacts du signal %s en échec", recorded.signalId());
        }
    }

    private List<UUID> signalsWithActiveImpacts(UUID projectId) throws SQLException {
        List<UUID> ids = new ArrayList<>();
        try (Connection c = dataSource.getConnection(); PreparedStatement ps = c.prepareStatement(
                "SELECT DISTINCT signal_id FROM project_impact WHERE project_id = ? AND state IN ('OUVERT', 'CONFIRME')")) {
            ps.setObject(1, projectId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    ids.add(rs.getObject(1, UUID.class));
                }
            }
        }
        return ids;
    }

    private Deterministic deterministic(UUID signalId, Optional<UUID> projectId) throws SQLException {
        SignalQueries.SignalView signal = signals.find(signalId)
                .orElseThrow(() -> new IllegalArgumentException("signal inconnu : " + signalId));
        Map<String, List<AffectedPackage>> byKey = new HashMap<>();
        for (AffectedPackage a : signal.affected()) {
            byKey.computeIfAbsent(a.packageKey(), k -> new ArrayList<>()).add(a);
        }
        Instant now = Instant.now();
        int created = 0;
        int updated = 0;
        int reopened = 0;
        List<UUID> kept = new ArrayList<>();
        // Paquets (projet + clé) pour lesquels un composant reste affecté : un impact de l'ancienne version de ce
        // paquet est résolu comme composant retiré, et non comme version hors plage.
        Set<String> stillAffected = new HashSet<>();
        List<ImpactsRecorded.Item> items = new ArrayList<>();
        try (Connection c = dataSource.getConnection()) {
            // Traitement technique, hors rôle applicatif. Le verrou par signal sérialise une corrélation
            // globale et une corrélation par projet du même signal : la résolution des impacts devenus
            // caducs ne peut pas résoudre un impact validé entre-temps par l'autre traitement.
            try (PreparedStatement lock = c.prepareStatement("SELECT pg_advisory_xact_lock(hashtext(?))")) {
                lock.setString(1, "project_impact:" + signalId);
                lock.execute();
            }
            for (InventoryQueries.ComponentMatch m : inventories.componentsMatching(byKey.keySet(), projectId)) {
                Optional<Match> best = best(byKey.get(m.packageKey()), m);
                if (best.isEmpty()) {
                    continue;
                }
                stillAffected.add(m.projectId() + " " + m.packageKey());
                reattach(c, signalId, m);
                Optional<Precedent> avant = precedent(c, signalId, m);
                UUID id = UUID.randomUUID();
                try (PreparedStatement ps = c.prepareStatement("""
                        INSERT INTO project_impact(id, signal_id, signal_version_id, project_id, workspace_id, component_id,
                               level, method, transitive, inventory_origin, inventory_date, provenance, created_at, updated_at)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb, now(), now())
                        ON CONFLICT (signal_id, project_id, component_id) DO UPDATE SET
                               signal_version_id = excluded.signal_version_id, level = excluded.level,
                               method = excluded.method, transitive = excluded.transitive,
                               inventory_origin = excluded.inventory_origin, inventory_date = excluded.inventory_date,
                               provenance = excluded.provenance, component_id = excluded.component_id, updated_at = now()
                        RETURNING id, (xmax = 0), state
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
                        UUID impactId = rs.getObject(1, UUID.class);
                        EtatImpact etat = EtatImpact.valueOf(rs.getString(3));
                        boolean change = avant.map(a -> a.reactive() || !a.level().equals(best.get().level().name())
                                || !a.signalVersionId().equals(signal.currentVersionId())).orElse(true);
                        kept.add(impactId);
                        if (rs.getBoolean(2)) {
                            created++;
                        } else {
                            updated++;
                        }
                        // Seul un impact résolu par le système redevient ouvert ; une résolution humaine est respectée (J5).
                        if (etat == EtatImpact.RESOLU && TraitementImpacts.resoluParLeSysteme(c, impactId)
                                && TraitementImpacts.systeme(c, impactId, m.workspaceId(),
                                EtatImpact.RESOLU, EtatImpact.OUVERT, MotifSysteme.DE_NOUVEAU_VERIFIE, now)) {
                            reopened++;
                            etat = EtatImpact.OUVERT;
                            change = true;
                        }
                        // Un impact écarté reste enregistré et à jour, mais n'est publié à aucune alerte (ALR-07).
                        // Seul un impact nouveau, rouvert (par le système, ou par un utilisateur depuis la corrélation
                        // précédente), de niveau changé ou porté par une nouvelle version de l'avis est publié : une recorrélation sans changement (import quotidien) ne produit aucune alerte,
                        // notamment au titre d'un abonnement réactivé (ABO-07-S2, [PROPOSITION] PI-4).
                        if (EtatImpact.ACTIFS.contains(etat) && change) {
                            items.add(new ImpactsRecorded.Item(impactId, m.projectId(), m.workspaceId(),
                                    best.get().level().name(), m.packageKey()));
                        }
                    }
                }
            }
            int resolved = resolveStale(c, signal, projectId, kept, stillAffected, now);
            return new Deterministic(created, updated, resolved, reopened, List.copyOf(kept), new ImpactsRecorded(signalId,
                    signal.title(), signal.severity(), signal.source(), signal.externalId(), items));
        }
    }

    /** Impact existant : niveau, version de l'avis, et vrai s'il est redevenu actif depuis sa dernière corrélation. */
    private record Precedent(String level, UUID signalVersionId, boolean reactive) {
    }

    /** État de l'impact existant pour ce signal, ce projet et ce composant, s'il existe. */
    private static Optional<Precedent> precedent(Connection c, UUID signalId, InventoryQueries.ComponentMatch m)
            throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("""
                SELECT i.level, i.signal_version_id,
                       EXISTS (SELECT 1 FROM project_impact_transition t WHERE t.impact_id = i.id
                               AND t.to_state IN ('OUVERT', 'CONFIRME') AND t.occurred_at > i.updated_at)
                FROM project_impact i
                WHERE i.signal_id = ? AND i.project_id = ? AND i.component_id = ?
                """)) {
            ps.setObject(1, signalId);
            ps.setObject(2, m.projectId());
            ps.setObject(3, m.componentId());
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(new Precedent(rs.getString(1), rs.getObject(2, UUID.class), rs.getBoolean(3)))
                        : Optional.empty();
            }
        }
    }

    /**
     * Rattache au composant revenu dans l'inventaire l'impact que ce signal portait sur la même clé de paquet et la même
     * version avant son retrait (composant nul), s'il n'existe pas déjà d'impact pour ce composant : l'upsert qui suit
     * met alors à jour cet impact, avec son historique, au lieu d'en créer un second.
     */
    private static void reattach(Connection c, UUID signalId, InventoryQueries.ComponentMatch m) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("""
                UPDATE project_impact SET component_id = ?, updated_at = now()
                WHERE id = (SELECT i.id FROM project_impact i
                            WHERE i.signal_id = ? AND i.project_id = ? AND i.component_id IS NULL AND i.package_key = ?
                              AND coalesce(i.component_version, '') = coalesce(?, '')
                            ORDER BY i.updated_at DESC, i.id LIMIT 1)
                  AND NOT EXISTS (SELECT 1 FROM project_impact j
                                  WHERE j.signal_id = ? AND j.project_id = ? AND j.component_id = ?)
                """)) {
            ps.setObject(1, m.componentId());
            ps.setObject(2, signalId);
            ps.setObject(3, m.projectId());
            ps.setString(4, m.packageKey());
            ps.setString(5, m.version());
            ps.setObject(6, signalId);
            ps.setObject(7, m.projectId());
            ps.setObject(8, m.componentId());
            ps.executeUpdate();
        }
    }

    /**
     * Résout les impacts ouverts ou confirmés du signal (et du projet donné) qui ne sont plus vérifiés, avec le motif
     * qui l'explique (COR-11). Les impacts écartés et déjà résolus ne sont pas touchés.
     */
    private int resolveStale(Connection c, SignalQueries.SignalView signal, Optional<UUID> projectId, List<UUID> kept,
            Set<String> stillAffected, Instant now) throws SQLException {
        record Stale(UUID id, UUID workspaceId, UUID projectId, EtatImpact etat, UUID componentId, String packageKey) {
        }
        List<Stale> stale = new ArrayList<>();
        try (PreparedStatement ps = c.prepareStatement("""
                SELECT id, workspace_id, project_id, state, component_id, package_key FROM project_impact
                WHERE signal_id = ? AND (?::uuid IS NULL OR project_id = ?::uuid) AND state IN ('OUVERT', 'CONFIRME')
                  AND NOT (id = ANY (?))
                ORDER BY id
                """)) {
            ps.setObject(1, signal.id());
            ps.setObject(2, projectId.orElse(null));
            ps.setObject(3, projectId.orElse(null));
            ps.setArray(4, c.createArrayOf("uuid", kept.toArray()));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    stale.add(new Stale(rs.getObject(1, UUID.class), rs.getObject(2, UUID.class),
                            rs.getObject(3, UUID.class), EtatImpact.valueOf(rs.getString(4)), rs.getObject(5, UUID.class),
                            rs.getString(6)));
                }
            }
        }
        int resolved = 0;
        for (Stale s : stale) {
            MotifSysteme motif;
            if (signal.affected().isEmpty()) {
                // Avis retiré par sa source (OSV « withdrawn ») : il ne déclare plus aucun paquet affecté.
                motif = MotifSysteme.AVIS_RETIRE;
            } else if (s.componentId() != null) {
                motif = MotifSysteme.AVIS_MODIFIE;
            } else if (!stillAffected.contains(s.projectId() + " " + s.packageKey())
                    && packagePresent(c, s.projectId(), s.packageKey())) {
                motif = MotifSysteme.VERSION_HORS_PLAGE;
            } else {
                motif = MotifSysteme.COMPOSANT_RETIRE;
            }
            if (TraitementImpacts.systeme(c, s.id(), s.workspaceId(), s.etat(), EtatImpact.RESOLU, motif, now)) {
                resolved++;
            }
        }
        return resolved;
    }

    private static boolean packagePresent(Connection c, UUID projectId, String packageKey) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT 1 FROM component WHERE project_id = ? AND package_key = ? LIMIT 1")) {
            ps.setObject(1, projectId);
            ps.setString(2, packageKey);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
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
        p.put("strategy", MatchRules.STRATEGY);
        return p;
    }
}
