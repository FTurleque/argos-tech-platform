package io.argos.impact;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import javax.sql.DataSource;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Consultation des impacts, limitée aux espaces autorisés de l'appelant (COR-08, ADR-0016) :
 * filtre explicite dans chaque requête et, en garde-fou, politique RLS appliquée sous le rôle
 * {@code argos_app} avec le compte de l'appelant ({@link RlsSession}). Le contrôle d'intégrité et
 * la recherche de l'espace d'un impact sont des traitements techniques, hors rôle applicatif.
 *
 * <p>Chaque impact porte son niveau de confiance ({@code level}) et son état de traitement ({@code etat}), deux axes
 * distincts (COR-09). Les listes restituent par défaut les impacts {@link EtatImpact#ACTIFS} (ouverts et confirmés,
 * COR-12-S2) ; un impact écarté ou résolu reste consultable par son identifiant, avec son historique. Le composant
 * d'un impact résolu peut avoir quitté l'inventaire : son PURL et sa version sont alors ceux de l'instantané
 * enregistré avec l'impact (migration V20).
 */
@ApplicationScoped
public class ImpactQueries {

    /**
     * Impact et sa provenance (COR-03-S1), son état de traitement (COR-09) et, à la consultation d'un impact, son
     * historique (COR-12-S1) et les transitions que l'appelant peut demander (COR-10) ; ces deux derniers champs sont
     * absents des listes. {@code componentId} est nul si le composant a quitté l'inventaire.
     */
    public record ImpactView(UUID id, UUID signalId, String signalSource, String signalExternalId, String signalTitle,
            UUID signalVersionId, UUID projectId, String projectName, UUID workspaceId, UUID componentId,
            String componentPurl, String componentVersion, Boolean transitive, String level, String method,
            String inventoryOrigin, Instant inventoryDate, String provenance, Instant createdAt, Instant updatedAt,
            String etat, @JsonInclude(JsonInclude.Include.NON_NULL) List<TransitionView> historique,
            @JsonInclude(JsonInclude.Include.NON_NULL) List<String> transitionsPossibles) {

        /** Même impact, avec son historique. */
        public ImpactView avecHistorique(List<TransitionView> transitions) {
            return new ImpactView(id, signalId, signalSource, signalExternalId, signalTitle, signalVersionId, projectId,
                    projectName, workspaceId, componentId, componentPurl, componentVersion, transitive, level, method,
                    inventoryOrigin, inventoryDate, provenance, createdAt, updatedAt, etat, List.copyOf(transitions),
                    transitionsPossibles);
        }

        /** Même impact, avec les transitions que l'appelant peut demander (vide pour un lecteur). */
        public ImpactView avecTransitionsPossibles(List<String> possibles) {
            return new ImpactView(id, signalId, signalSource, signalExternalId, signalTitle, signalVersionId, projectId,
                    projectName, workspaceId, componentId, componentPurl, componentVersion, transitive, level, method,
                    inventoryOrigin, inventoryDate, provenance, createdAt, updatedAt, etat, historique,
                    List.copyOf(possibles));
        }
    }

    /**
     * Transition de l'historique d'un impact (COR-12-S1) : états de départ et d'arrivée, auteur (nom affiché du compte,
     * ou {@code SYSTEME}), motif ou commentaire, date. {@code auteur} est nul si le compte auteur n'est plus visible
     * de l'appelant.
     */
    public record TransitionView(String de, String vers, String auteur, UUID auteurCompteId, boolean systeme,
            String motif, String commentaire, Instant date) {
    }

    /** Page de résultats (arc42 §8.10). */
    public record Page(List<ImpactView> items, int page, int size, long total) {
    }

    /** Résultat du contrôle d'intégrité (COR-03-S2, QS-01). */
    public record IntegrityReport(long impacts, long withoutSignal, long withoutProject, long withoutComponent,
            long withoutMethod, long withoutProvenance) {

        public boolean intact() {
            return withoutSignal + withoutProject + withoutComponent + withoutMethod + withoutProvenance == 0;
        }
    }

    private static final String SELECT = """
            SELECT i.id, i.signal_id, s.source, s.external_id, s.title, i.signal_version_id, i.project_id, p.name,
                   i.workspace_id, i.component_id, coalesce(c.purl, i.component_purl),
                   CASE WHEN c.id IS NULL THEN i.component_version ELSE c.version END, i.transitive, i.level, i.method,
                   i.inventory_origin, i.inventory_date, i.provenance::text, i.created_at, i.updated_at, i.state
            FROM project_impact i
            JOIN signal s ON s.id = i.signal_id
            JOIN project p ON p.id = i.project_id
            LEFT JOIN component c ON c.id = i.component_id
            """;

    @Inject
    DataSource dataSource;

    /** Impacts actifs (ouverts et confirmés) d'un signal dans les espaces donnés (liste paginée). */
    @Transactional
    public Page forSignal(UUID signalId, UUID accountId, Set<UUID> workspaces, int page, int size) {
        return forSignal(signalId, accountId, workspaces, EtatImpact.ACTIFS, page, size);
    }

    /** Impacts d'un signal aux états donnés, dans les espaces donnés (liste paginée, COR-12-S2). */
    @Transactional
    public Page forSignal(UUID signalId, UUID accountId, Set<UUID> workspaces, Set<EtatImpact> etats, int page,
            int size) {
        int p = Math.max(page, 0);
        int s = Math.min(Math.max(size, 1), 200);
        try (Connection c = dataSource.getConnection()) {
            RlsSession.account(c, accountId);
            long total;
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT count(*) FROM project_impact WHERE signal_id = ? AND workspace_id = ANY (?) AND state = ANY (?)")) {
                ps.setObject(1, signalId);
                ps.setArray(2, uuids(c, workspaces));
                ps.setArray(3, etats(c, etats));
                try (ResultSet rs = ps.executeQuery()) {
                    rs.next();
                    total = rs.getLong(1);
                }
            }
            List<ImpactView> items = new ArrayList<>();
            try (PreparedStatement ps = c.prepareStatement(SELECT
                    + " WHERE i.signal_id = ? AND i.workspace_id = ANY (?) AND i.state = ANY (?)"
                    + " ORDER BY i.created_at, i.id LIMIT ? OFFSET ?")) {
                ps.setObject(1, signalId);
                ps.setArray(2, uuids(c, workspaces));
                ps.setArray(3, etats(c, etats));
                ps.setInt(4, s);
                ps.setLong(5, (long) p * s);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        items.add(view(rs));
                    }
                }
            }
            return new Page(items, p, s, total);
        } catch (SQLException e) {
            throw new IllegalStateException("lecture des impacts impossible", e);
        }
    }

    /** Impact, quel que soit son état, avec son historique, s'il appartient à l'un des espaces donnés. */
    @Transactional
    public Optional<ImpactView> find(UUID impactId, UUID accountId, Set<UUID> workspaces) {
        try (Connection c = dataSource.getConnection()) {
            RlsSession.account(c, accountId);
            ImpactView vue;
            try (PreparedStatement ps = c.prepareStatement(SELECT + " WHERE i.id = ? AND i.workspace_id = ANY (?)")) {
                ps.setObject(1, impactId);
                ps.setArray(2, uuids(c, workspaces));
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) {
                        return Optional.empty();
                    }
                    vue = view(rs);
                }
            }
            return Optional.of(vue.avecHistorique(historique(c, impactId)));
        } catch (SQLException e) {
            throw new IllegalStateException("lecture de l'impact impossible", e);
        }
    }

    /**
     * Impacts actifs visibles dans les espaces donnés pour un ensemble de signaux (restitution de la veille, alertes :
     * un impact écarté ou résolu n'est jamais alerté, ALR-07).
     */
    @Transactional
    public List<ImpactView> forSignals(Collection<UUID> signalIds, UUID accountId, Set<UUID> workspaces) {
        List<ImpactView> items = new ArrayList<>();
        if (signalIds.isEmpty() || workspaces.isEmpty()) {
            return items;
        }
        try (Connection c = dataSource.getConnection()) {
            RlsSession.account(c, accountId);
            try (PreparedStatement ps = c.prepareStatement(SELECT
                    + " WHERE i.signal_id = ANY (?) AND i.workspace_id = ANY (?) AND i.state = ANY (?)"
                    + " ORDER BY i.created_at, i.id")) {
                ps.setArray(1, c.createArrayOf("uuid", signalIds.toArray()));
                ps.setArray(2, uuids(c, workspaces));
                ps.setArray(3, etats(c, EtatImpact.ACTIFS));
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        items.add(view(rs));
                    }
                }
            }
        } catch (SQLException e) {
            throw new IllegalStateException("lecture des impacts impossible", e);
        }
        return items;
    }

    /** Impacts actifs d'un projet, vides si le projet n'appartient à aucun des espaces donnés. */
    @Transactional
    public List<ImpactView> forProject(UUID projectId, UUID accountId, Set<UUID> workspaces) {
        return forProject(projectId, accountId, workspaces, EtatImpact.ACTIFS);
    }

    /** Impacts d'un projet aux états donnés (COR-12-S2), vides si le projet n'appartient à aucun des espaces donnés. */
    @Transactional
    public List<ImpactView> forProject(UUID projectId, UUID accountId, Set<UUID> workspaces, Set<EtatImpact> etats) {
        List<ImpactView> items = new ArrayList<>();
        if (workspaces.isEmpty()) {
            return items;
        }
        try (Connection c = dataSource.getConnection()) {
            RlsSession.account(c, accountId);
            try (PreparedStatement ps = c.prepareStatement(SELECT
                    + " WHERE i.project_id = ? AND i.workspace_id = ANY (?) AND i.state = ANY (?)"
                    + " ORDER BY i.created_at, i.id")) {
                ps.setObject(1, projectId);
                ps.setArray(2, uuids(c, workspaces));
                ps.setArray(3, etats(c, etats));
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        items.add(view(rs));
                    }
                }
            }
        } catch (SQLException e) {
            throw new IllegalStateException("lecture des impacts impossible", e);
        }
        return items;
    }

    /** Impacts actifs les plus récents des espaces donnés (serveur MCP, {@code get_security_alerts}). */
    @Transactional
    public List<ImpactView> forWorkspaces(UUID accountId, Set<UUID> workspaces, int limit) {
        List<ImpactView> items = new ArrayList<>();
        if (workspaces.isEmpty()) {
            return items;
        }
        try (Connection c = dataSource.getConnection()) {
            RlsSession.account(c, accountId);
            try (PreparedStatement ps = c.prepareStatement(SELECT
                    + " WHERE i.workspace_id = ANY (?) AND i.state = ANY (?) ORDER BY i.updated_at DESC, i.id LIMIT ?")) {
                ps.setArray(1, uuids(c, workspaces));
                ps.setArray(2, etats(c, EtatImpact.ACTIFS));
                ps.setInt(3, Math.min(Math.max(limit, 1), 200));
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        items.add(view(rs));
                    }
                }
            }
        } catch (SQLException e) {
            throw new IllegalStateException("lecture des impacts impossible", e);
        }
        return items;
    }

    /**
     * Espace d'un impact, lu hors rôle applicatif pour décider d'un refus à journaliser ;
     * jamais restitué à l'appelant.
     */
    @Transactional
    public Optional<UUID> workspaceOf(UUID impactId) {
        try (Connection c = dataSource.getConnection()) {
            try (PreparedStatement ps = c.prepareStatement("SELECT workspace_id FROM project_impact WHERE id = ?")) {
                ps.setObject(1, impactId);
                try (ResultSet rs = ps.executeQuery()) {
                    return rs.next() ? Optional.of(rs.getObject(1, UUID.class)) : Optional.empty();
                }
            }
        } catch (SQLException e) {
            throw new IllegalStateException("lecture de l'impact impossible", e);
        }
    }

    /**
     * Contrôle d'intégrité : aucun impact sans signal, projet, méthode ou provenance ; aucun impact ouvert ou confirmé
     * sans composant (INV-C3 : un impact dont le composant a quitté l'inventaire est résolu ou écarté une fois la
     * recorrélation du projet faite).
     */
    @Transactional
    public IntegrityReport integrity() {
        String sql = """
                SELECT count(*),
                       count(*) FILTER (WHERE NOT EXISTS (SELECT 1 FROM signal s WHERE s.id = i.signal_id)),
                       count(*) FILTER (WHERE NOT EXISTS (SELECT 1 FROM project p WHERE p.id = i.project_id)),
                       count(*) FILTER (WHERE i.state NOT IN ('RESOLU', 'ECARTE')
                                         AND NOT EXISTS (SELECT 1 FROM component c WHERE c.id = i.component_id)),
                       count(*) FILTER (WHERE i.method IS NULL OR i.method = ''),
                       count(*) FILTER (WHERE i.provenance IS NULL OR i.provenance = '{}'::jsonb
                                         OR NOT EXISTS (SELECT 1 FROM signal_version v WHERE v.id = i.signal_version_id))
                FROM project_impact i
                """;
        try (Connection c = dataSource.getConnection()) {
            try (PreparedStatement ps = c.prepareStatement(sql); ResultSet rs = ps.executeQuery()) {
                rs.next();
                return new IntegrityReport(rs.getLong(1), rs.getLong(2), rs.getLong(3), rs.getLong(4), rs.getLong(5),
                        rs.getLong(6));
            }
        } catch (SQLException e) {
            throw new IllegalStateException("contrôle d'intégrité impossible", e);
        }
    }

    private static java.sql.Array uuids(Connection c, Set<UUID> ids) throws SQLException {
        return c.createArrayOf("uuid", ids.toArray());
    }

    private static java.sql.Array etats(Connection c, Set<EtatImpact> etats) throws SQLException {
        return c.createArrayOf("varchar", etats.stream().map(EtatImpact::name).toArray());
    }

    /** Historique d'un impact, dans l'ordre des transitions, lu sous le contexte déjà appliqué à la connexion. */
    private static List<TransitionView> historique(Connection c, UUID impactId) throws SQLException {
        List<TransitionView> transitions = new ArrayList<>();
        try (PreparedStatement ps = c.prepareStatement("""
                SELECT t.from_state, t.to_state, t.actor_system, t.actor_account_id, co.nom_affiche, t.reason, t.comment,
                       t.occurred_at
                FROM project_impact_transition t LEFT JOIN compte co ON co.id = t.actor_account_id
                WHERE t.impact_id = ? ORDER BY t.occurred_at, t.id
                """)) {
            ps.setObject(1, impactId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    boolean systeme = rs.getBoolean(3);
                    transitions.add(new TransitionView(rs.getString(1), rs.getString(2),
                            systeme ? "SYSTEME" : rs.getString(5), rs.getObject(4, UUID.class), systeme, rs.getString(6),
                            rs.getString(7), instant(rs, 8)));
                }
            }
        }
        return transitions;
    }

    private static ImpactView view(ResultSet rs) throws SQLException {
        return new ImpactView(rs.getObject(1, UUID.class), rs.getObject(2, UUID.class), rs.getString(3),
                rs.getString(4), rs.getString(5), rs.getObject(6, UUID.class), rs.getObject(7, UUID.class),
                rs.getString(8), rs.getObject(9, UUID.class), rs.getObject(10, UUID.class), rs.getString(11),
                rs.getString(12), (Boolean) rs.getObject(13), rs.getString(14), rs.getString(15), rs.getString(16),
                instant(rs, 17), rs.getString(18), instant(rs, 19), instant(rs, 20), rs.getString(21), null, null);
    }

    private static Instant instant(ResultSet rs, int column) throws SQLException {
        OffsetDateTime value = rs.getObject(column, OffsetDateTime.class);
        return value == null ? null : value.toInstant();
    }
}
