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
        } catch (RuntimeException erreur) {
            LOG.errorf(erreur, "corrélation du signal %s en échec", event.signalId());
        }
    }

    void onInventoryReplaced(@Observes(during = TransactionPhase.AFTER_SUCCESS) InventoryReplaced event) {
        try {
            correlateProject(event.projectId());
        } catch (RuntimeException erreur) {
            LOG.errorf(erreur, "corrélation du projet %s en échec", event.projectId());
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
        Deterministic outcome = QuarkusTransaction.requiringNew().call(() -> deterministic(signalId, projectId));
        publish(outcome.recorded());
        if (outcome.resolved() > 0) {
            try {
                impactsTraites.fire(new ImpactsTraites(signalId));
            } catch (RuntimeException erreur) {
                LOG.errorf(erreur, "publication des impacts résolus du signal %s en échec", signalId);
            }
        }
        String status = "NONE";
        List<ComplementaryAnalysis.Suggestion> suggestions = List.of();
        if (analysis.isResolvable()) {
            ComplementaryAnalysis complementary = analysis.get();
            if (!complementary.budgetAvailable()) {
                status = "BUDGET_EXHAUSTED";
            } else {
                try {
                    // Les suggestions sont informatives : elles ne créent aucun impact (COR-04).
                    suggestions = List.copyOf(complementary.analyze(signalId, outcome.impactIds()));
                    status = "DONE";
                } catch (RuntimeException erreur) {
                    status = "UNAVAILABLE";
                    LOG.infof("analyse complémentaire indisponible pour le signal %s : %s", signalId, erreur.getMessage());
                }
            }
        }
        return new CorrelationReport(signalId, outcome.created(), outcome.updated(), outcome.resolved(), outcome.reopened(),
                outcome.impactIds(), status, suggestions);
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
        } catch (RuntimeException erreur) {
            LOG.errorf(erreur, "publication des impacts du signal %s en échec", recorded.signalId());
        }
    }

    private List<UUID> signalsWithActiveImpacts(UUID projectId) throws SQLException {
        List<UUID> ids = new ArrayList<>();
        try (Connection connexion = dataSource.getConnection(); PreparedStatement requete = connexion.prepareStatement(
                "SELECT DISTINCT signal_id FROM project_impact WHERE project_id = ? AND state IN ('OUVERT', 'CONFIRME')")) {
            requete.setObject(1, projectId);
            try (ResultSet resultat = requete.executeQuery()) {
                while (resultat.next()) {
                    ids.add(resultat.getObject(1, UUID.class));
                }
            }
        }
        return ids;
    }

    private Deterministic deterministic(UUID signalId, Optional<UUID> projectId) throws SQLException {
        SignalQueries.SignalView signal = signals.find(signalId)
                .orElseThrow(() -> new IllegalArgumentException("signal inconnu : " + signalId));
        Map<String, List<AffectedPackage>> byKey = new HashMap<>();
        for (AffectedPackage affected : signal.affected()) {
            byKey.computeIfAbsent(affected.packageKey(), key -> new ArrayList<>()).add(affected);
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
        try (Connection connexion = dataSource.getConnection()) {
            // Traitement technique, hors rôle applicatif. Le verrou par signal sérialise une corrélation
            // globale et une corrélation par projet du même signal : la résolution des impacts devenus
            // caducs ne peut pas résoudre un impact validé entre-temps par l'autre traitement.
            try (PreparedStatement lock = connexion.prepareStatement("SELECT pg_advisory_xact_lock(hashtext(?))")) {
                lock.setString(1, "project_impact:" + signalId);
                lock.execute();
            }
            for (InventoryQueries.ComponentMatch component : inventories.componentsMatching(byKey.keySet(), projectId)) {
                Optional<Match> best = best(byKey.get(component.packageKey()), component);
                if (best.isEmpty()) {
                    continue;
                }
                stillAffected.add(component.projectId() + " " + component.packageKey());
                reattach(connexion, signalId, component);
                Optional<Precedent> avant = precedent(connexion, signalId, component);
                UUID id = UUID.randomUUID();
                try (PreparedStatement requete = connexion.prepareStatement("""
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
                    requete.setObject(1, id);
                    requete.setObject(2, signalId);
                    requete.setObject(3, signal.currentVersionId());
                    requete.setObject(4, component.projectId());
                    requete.setObject(5, component.workspaceId());
                    requete.setObject(6, component.componentId());
                    requete.setString(7, best.get().level().name());
                    requete.setString(8, MatchRules.METHOD);
                    if (component.transitive() == null) {
                        requete.setNull(9, Types.BOOLEAN);
                    } else {
                        requete.setBoolean(9, component.transitive());
                    }
                    requete.setString(10, component.inventoryOrigin());
                    requete.setObject(11, component.inventoryDate() == null ? null : component.inventoryDate().atOffset(ZoneOffset.UTC));
                    requete.setString(12, provenance(signal, component, best.get()).toString());
                    try (ResultSet resultat = requete.executeQuery()) {
                        resultat.next();
                        UUID impactId = resultat.getObject(1, UUID.class);
                        EtatImpact etat = EtatImpact.valueOf(resultat.getString(3));
                        boolean change = avant.map(precedent -> precedent.reactive() || !precedent.level().equals(best.get().level().name())
                                || !precedent.signalVersionId().equals(signal.currentVersionId())).orElse(true);
                        kept.add(impactId);
                        if (resultat.getBoolean(2)) {
                            created++;
                        } else {
                            updated++;
                        }
                        // Seul un impact résolu par le système redevient ouvert ; une résolution humaine est respectée (J5).
                        if (etat == EtatImpact.RESOLU && TraitementImpacts.resoluParLeSysteme(connexion, impactId)
                                && TraitementImpacts.systeme(connexion, impactId, component.workspaceId(),
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
                            items.add(new ImpactsRecorded.Item(impactId, component.projectId(), component.workspaceId(),
                                    best.get().level().name(), component.packageKey()));
                        }
                    }
                }
            }
            int resolved = resolveStale(connexion, signal, projectId, kept, stillAffected, now);
            return new Deterministic(created, updated, resolved, reopened, List.copyOf(kept), new ImpactsRecorded(signalId,
                    signal.title(), signal.severity(), signal.source(), signal.externalId(), items));
        }
    }

    /** Impact existant : niveau, version de l'avis, et vrai s'il est redevenu actif depuis sa dernière corrélation. */
    private record Precedent(String level, UUID signalVersionId, boolean reactive) {
    }

    /** État de l'impact existant pour ce signal, ce projet et ce composant, s'il existe. */
    private static Optional<Precedent> precedent(Connection connexion, UUID signalId, InventoryQueries.ComponentMatch component)
            throws SQLException {
        try (PreparedStatement requete = connexion.prepareStatement("""
                SELECT i.level, i.signal_version_id,
                       EXISTS (SELECT 1 FROM project_impact_transition t WHERE t.impact_id = i.id
                               AND t.to_state IN ('OUVERT', 'CONFIRME') AND t.occurred_at > i.updated_at)
                FROM project_impact i
                WHERE i.signal_id = ? AND i.project_id = ? AND i.component_id = ?
                """)) {
            requete.setObject(1, signalId);
            requete.setObject(2, component.projectId());
            requete.setObject(3, component.componentId());
            try (ResultSet resultat = requete.executeQuery()) {
                return resultat.next() ? Optional.of(new Precedent(resultat.getString(1), resultat.getObject(2, UUID.class),
                        resultat.getBoolean(3)))
                        : Optional.empty();
            }
        }
    }

    /**
     * Rattache au composant revenu dans l'inventaire l'impact que ce signal portait sur la même clé de paquet et la même
     * version avant son retrait (composant nul), s'il n'existe pas déjà d'impact pour ce composant : l'upsert qui suit
     * met alors à jour cet impact, avec son historique, au lieu d'en créer un second.
     */
    private static void reattach(Connection connexion, UUID signalId, InventoryQueries.ComponentMatch component) throws SQLException {
        try (PreparedStatement requete = connexion.prepareStatement("""
                UPDATE project_impact SET component_id = ?, updated_at = now()
                WHERE id = (SELECT i.id FROM project_impact i
                            WHERE i.signal_id = ? AND i.project_id = ? AND i.component_id IS NULL AND i.package_key = ?
                              AND coalesce(i.component_version, '') = coalesce(?, '')
                            ORDER BY i.updated_at DESC, i.id LIMIT 1)
                  AND NOT EXISTS (SELECT 1 FROM project_impact j
                                  WHERE j.signal_id = ? AND j.project_id = ? AND j.component_id = ?)
                """)) {
            requete.setObject(1, component.componentId());
            requete.setObject(2, signalId);
            requete.setObject(3, component.projectId());
            requete.setString(4, component.packageKey());
            requete.setString(5, component.version());
            requete.setObject(6, signalId);
            requete.setObject(7, component.projectId());
            requete.setObject(8, component.componentId());
            requete.executeUpdate();
        }
    }

    /**
     * Résout les impacts ouverts ou confirmés du signal (et du projet donné) qui ne sont plus vérifiés, avec le motif
     * qui l'explique (COR-11). Les impacts écartés et déjà résolus ne sont pas touchés.
     */
    private int resolveStale(Connection connexion, SignalQueries.SignalView signal, Optional<UUID> projectId, List<UUID> kept,
            Set<String> stillAffected, Instant now) throws SQLException {
        record Stale(UUID id, UUID workspaceId, UUID projectId, EtatImpact etat, UUID componentId, String packageKey) {
        }
        List<Stale> stale = new ArrayList<>();
        try (PreparedStatement requete = connexion.prepareStatement("""
                SELECT id, workspace_id, project_id, state, component_id, package_key FROM project_impact
                WHERE signal_id = ? AND (?::uuid IS NULL OR project_id = ?::uuid) AND state IN ('OUVERT', 'CONFIRME')
                  AND NOT (id = ANY (?))
                ORDER BY id
                """)) {
            requete.setObject(1, signal.id());
            requete.setObject(2, projectId.orElse(null));
            requete.setObject(3, projectId.orElse(null));
            requete.setArray(4, connexion.createArrayOf("uuid", kept.toArray()));
            try (ResultSet resultat = requete.executeQuery()) {
                while (resultat.next()) {
                    stale.add(new Stale(resultat.getObject(1, UUID.class), resultat.getObject(2, UUID.class),
                            resultat.getObject(3, UUID.class), EtatImpact.valueOf(resultat.getString(4)), resultat.getObject(5, UUID.class),
                            resultat.getString(6)));
                }
            }
        }
        int resolved = 0;
        for (Stale staleImpact : stale) {
            MotifSysteme motif;
            if (signal.affected().isEmpty()) {
                // Avis retiré par sa source (OSV « withdrawn ») : il ne déclare plus aucun paquet affecté.
                motif = MotifSysteme.AVIS_RETIRE;
            } else if (staleImpact.componentId() != null) {
                motif = MotifSysteme.AVIS_MODIFIE;
            } else if (!stillAffected.contains(staleImpact.projectId() + " " + staleImpact.packageKey())
                    && packagePresent(connexion, staleImpact.projectId(), staleImpact.packageKey())) {
                motif = MotifSysteme.VERSION_HORS_PLAGE;
            } else {
                motif = MotifSysteme.COMPOSANT_RETIRE;
            }
            if (TraitementImpacts.systeme(connexion, staleImpact.id(), staleImpact.workspaceId(), staleImpact.etat(),
                    EtatImpact.RESOLU, motif, now)) {
                resolved++;
            }
        }
        return resolved;
    }

    private static boolean packagePresent(Connection connexion, UUID projectId, String packageKey) throws SQLException {
        try (PreparedStatement requete = connexion.prepareStatement(
                "SELECT 1 FROM component WHERE project_id = ? AND package_key = ? LIMIT 1")) {
            requete.setObject(1, projectId);
            requete.setString(2, packageKey);
            try (ResultSet resultat = requete.executeQuery()) {
                return resultat.next();
            }
        }
    }

    private record Match(MatchRules.Level level, AffectedPackage affected) {
    }

    /** Niveau le plus fort parmi les entrées de l'avis pour ce paquet. */
    private static Optional<Match> best(List<AffectedPackage> candidates, InventoryQueries.ComponentMatch component) {
        Match best = null;
        for (AffectedPackage candidate : candidates) {
            Optional<MatchRules.Level> level = MatchRules.evaluate(candidate, component.version(), !component.inventoryObsolete());
            if (level.isPresent() && (best == null || level.get().ordinal() < best.level().ordinal())) {
                best = new Match(level.get(), candidate);
            }
        }
        return Optional.ofNullable(best);
    }

    private static ObjectNode provenance(SignalQueries.SignalView signal, InventoryQueries.ComponentMatch component, Match match) {
        ObjectNode provenance = MAPPER.createObjectNode();
        provenance.put("signalSource", signal.source());
        provenance.put("signalExternalId", signal.externalId());
        provenance.put("signalVersionId", signal.currentVersionId().toString());
        provenance.put("packageKey", component.packageKey());
        provenance.put("componentPurl", component.purl());
        provenance.put("componentVersion", component.version());
        provenance.put("declaredPurl", match.affected().declaredPurl());
        provenance.set("affectedRanges", MAPPER.valueToTree(match.affected().ranges()));
        provenance.set("affectedVersions", MAPPER.valueToTree(match.affected().versions()));
        provenance.put("inventoryOrigin", component.inventoryOrigin());
        provenance.put("inventoryDate", component.inventoryDate() == null ? null : component.inventoryDate().toString());
        provenance.put("inventoryObsolete", component.inventoryObsolete());
        provenance.put("method", MatchRules.METHOD);
        provenance.put("strategy", MatchRules.STRATEGY);
        return provenance;
    }
}
