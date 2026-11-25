package io.argos.impact;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
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

import io.argos.commun.ContexteBase;
import io.argos.commun.Conversions;
import io.argos.commun.Jdbc;

/**
 * Consultation des impacts, limitée aux espaces autorisés de l'appelant (COR-08, ADR-0016) :
 * filtre explicite dans chaque requête et, en garde-fou, politique RLS appliquée sous le rôle
 * {@code argos_app} avec le compte de l'appelant ({@link ContexteBase}). Le contrôle d'intégrité et
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
            String componentPurl, String componentVersion, Boolean transitive, MatchRules.Level level, String method,
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

    private static final int TAILLE_PAGE_MAX = 200;
    private static final int TAILLE_PAGE_MIN = 1;

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

    @Inject
    Jdbc jdbc;

    /** Impacts actifs (ouverts et confirmés) d'un signal dans les espaces donnés (liste paginée). */
    @Transactional
    public Page forSignal(UUID signalId, UUID accountId, Set<UUID> workspaces, int page, int size) {
        return forSignal(signalId, accountId, workspaces, EtatImpact.ACTIFS, page, size);
    }

    /** Impacts d'un signal aux états donnés, dans les espaces donnés (liste paginée, COR-12-S2). */
    @Transactional
    public Page forSignal(UUID signalId, UUID accountId, Set<UUID> workspaces, Set<EtatImpact> etats, int page,
            int size) {
        int boundedPage = Math.max(page, 0);
        int boundedSize = Math.min(Math.max(size, TAILLE_PAGE_MIN), TAILLE_PAGE_MAX);
        try (Connection connexion = dataSource.getConnection()) {
            ContexteBase.appliquer(connexion, ContexteBase.Contexte.duCompte(accountId));
            long total = Jdbc.unique(connexion,
                    "SELECT count(*) FROM project_impact WHERE signal_id = ? AND workspace_id = ANY (?) AND state = ANY (?)",
                    requete -> {
                        requete.setObject(1, signalId);
                        requete.setArray(2, uuids(connexion, workspaces));
                        requete.setArray(3, etats(connexion, etats));
                    }, resultat -> resultat.getLong(1)).orElse(0L);
            List<ImpactView> items = Jdbc.lister(connexion, SELECT
                    + " WHERE i.signal_id = ? AND i.workspace_id = ANY (?) AND i.state = ANY (?)"
                    + " ORDER BY i.created_at, i.id LIMIT ? OFFSET ?", requete -> {
                        requete.setObject(1, signalId);
                        requete.setArray(2, uuids(connexion, workspaces));
                        requete.setArray(3, etats(connexion, etats));
                        requete.setInt(4, boundedSize);
                        requete.setLong(5, (long) boundedPage * boundedSize);
                    }, ImpactQueries::view);
            return new Page(items, boundedPage, boundedSize, total);
        } catch (SQLException erreur) {
            throw new IllegalStateException("lecture des impacts impossible", erreur);
        }
    }

    /** Impact, quel que soit son état, avec son historique, s'il appartient à l'un des espaces donnés. */
    @Transactional
    public Optional<ImpactView> find(UUID impactId, UUID accountId, Set<UUID> workspaces) {
        try (Connection connexion = dataSource.getConnection()) {
            ContexteBase.appliquer(connexion, ContexteBase.Contexte.duCompte(accountId));
            Optional<ImpactView> vue = Jdbc.unique(connexion, SELECT + " WHERE i.id = ? AND i.workspace_id = ANY (?)",
                    requete -> {
                        requete.setObject(1, impactId);
                        requete.setArray(2, uuids(connexion, workspaces));
                    }, ImpactQueries::view);
            if (vue.isEmpty()) {
                return Optional.empty();
            }
            return Optional.of(vue.get().avecHistorique(historique(connexion, impactId)));
        } catch (SQLException erreur) {
            throw new IllegalStateException("lecture de l'impact impossible", erreur);
        }
    }

    /**
     * Impacts actifs visibles dans les espaces donnés pour un ensemble de signaux (restitution de la veille, alertes :
     * un impact écarté ou résolu n'est jamais alerté, ALR-07).
     */
    @Transactional
    public List<ImpactView> forSignals(Collection<UUID> signalIds, UUID accountId, Set<UUID> workspaces) {
        if (signalIds.isEmpty() || workspaces.isEmpty()) {
            return new ArrayList<>();
        }
        try (Connection connexion = dataSource.getConnection()) {
            ContexteBase.appliquer(connexion, ContexteBase.Contexte.duCompte(accountId));
            return Jdbc.lister(connexion, SELECT
                    + " WHERE i.signal_id = ANY (?) AND i.workspace_id = ANY (?) AND i.state = ANY (?)"
                    + " ORDER BY i.created_at, i.id", requete -> {
                        requete.setArray(1, connexion.createArrayOf("uuid", signalIds.toArray()));
                        requete.setArray(2, uuids(connexion, workspaces));
                        requete.setArray(3, etats(connexion, EtatImpact.ACTIFS));
                    }, ImpactQueries::view);
        } catch (SQLException erreur) {
            throw new IllegalStateException("lecture des impacts impossible", erreur);
        }
    }

    /** Impacts actifs d'un projet, vides si le projet n'appartient à aucun des espaces donnés. */
    @Transactional
    public List<ImpactView> forProject(UUID projectId, UUID accountId, Set<UUID> workspaces) {
        return forProject(projectId, accountId, workspaces, EtatImpact.ACTIFS);
    }

    /** Impacts d'un projet aux états donnés (COR-12-S2), vides si le projet n'appartient à aucun des espaces donnés. */
    @Transactional
    public List<ImpactView> forProject(UUID projectId, UUID accountId, Set<UUID> workspaces, Set<EtatImpact> etats) {
        if (workspaces.isEmpty()) {
            return new ArrayList<>();
        }
        try (Connection connexion = dataSource.getConnection()) {
            ContexteBase.appliquer(connexion, ContexteBase.Contexte.duCompte(accountId));
            return Jdbc.lister(connexion, SELECT
                    + " WHERE i.project_id = ? AND i.workspace_id = ANY (?) AND i.state = ANY (?)"
                    + " ORDER BY i.created_at, i.id", requete -> {
                        requete.setObject(1, projectId);
                        requete.setArray(2, uuids(connexion, workspaces));
                        requete.setArray(3, etats(connexion, etats));
                    }, ImpactQueries::view);
        } catch (SQLException erreur) {
            throw new IllegalStateException("lecture des impacts impossible", erreur);
        }
    }

    /** Impacts actifs les plus récents des espaces donnés (serveur MCP, {@code get_security_alerts}). */
    @Transactional
    public List<ImpactView> forWorkspaces(UUID accountId, Set<UUID> workspaces, int limit) {
        if (workspaces.isEmpty()) {
            return new ArrayList<>();
        }
        try (Connection connexion = dataSource.getConnection()) {
            ContexteBase.appliquer(connexion, ContexteBase.Contexte.duCompte(accountId));
            return Jdbc.lister(connexion, SELECT
                    + " WHERE i.workspace_id = ANY (?) AND i.state = ANY (?) ORDER BY i.updated_at DESC, i.id LIMIT ?",
                    requete -> {
                        requete.setArray(1, uuids(connexion, workspaces));
                        requete.setArray(2, etats(connexion, EtatImpact.ACTIFS));
                        requete.setInt(3, Math.min(Math.max(limit, TAILLE_PAGE_MIN), TAILLE_PAGE_MAX));
                    }, ImpactQueries::view);
        } catch (SQLException erreur) {
            throw new IllegalStateException("lecture des impacts impossible", erreur);
        }
    }

    /**
     * Espace d'un impact, lu hors rôle applicatif pour décider d'un refus à journaliser ;
     * jamais restitué à l'appelant.
     */
    @Transactional
    public Optional<UUID> workspaceOf(UUID impactId) {
        return jdbc.unique("SELECT workspace_id FROM project_impact WHERE id = ?", Jdbc.parametres(impactId),
                resultat -> resultat.getObject(1, UUID.class));
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
        return jdbc.unique(sql, Jdbc.sansParametre(), resultat -> new IntegrityReport(resultat.getLong(1),
                resultat.getLong(2), resultat.getLong(3), resultat.getLong(4), resultat.getLong(5), resultat.getLong(6)))
                .orElseThrow();
    }

    private static java.sql.Array uuids(Connection connexion, Set<UUID> ids) throws SQLException {
        return connexion.createArrayOf("uuid", ids.toArray());
    }

    private static java.sql.Array etats(Connection connexion, Set<EtatImpact> etats) throws SQLException {
        return connexion.createArrayOf("varchar", etats.stream().map(EtatImpact::name).toArray());
    }

    /** Historique d'un impact, dans l'ordre des transitions, lu sous le contexte déjà appliqué à la connexion. */
    private static List<TransitionView> historique(Connection connexion, UUID impactId) {
        return Jdbc.lister(connexion, """
                SELECT t.from_state, t.to_state, t.actor_system, t.actor_account_id, co.nom_affiche, t.reason, t.comment,
                       t.occurred_at
                FROM project_impact_transition t LEFT JOIN compte_nom_public co ON co.id = t.actor_account_id
                WHERE t.impact_id = ? ORDER BY t.occurred_at, t.id
                """, Jdbc.parametres(impactId), resultat -> {
                    boolean systeme = resultat.getBoolean(3);
                    return new TransitionView(resultat.getString(1), resultat.getString(2),
                            systeme ? "SYSTEME" : resultat.getString(5), resultat.getObject(4, UUID.class), systeme,
                            resultat.getString(6), resultat.getString(7), Conversions.instant(resultat, 8));
                });
    }

    /** Niveau d'impact lu en base ; une valeur absente ou inconnue est une erreur, jamais un niveau par défaut. */
    static MatchRules.Level niveau(String valeur) {
        if (valeur == null) {
            throw new IllegalStateException("niveau d'impact absent en base");
        }
        try {
            return MatchRules.Level.valueOf(valeur);
        } catch (IllegalArgumentException erreur) {
            throw new IllegalStateException("niveau d'impact inconnu en base : " + valeur, erreur);
        }
    }

    private static ImpactView view(ResultSet resultat) throws SQLException {
        return new ImpactView(resultat.getObject(1, UUID.class), resultat.getObject(2, UUID.class), resultat.getString(3),
                resultat.getString(4), resultat.getString(5), resultat.getObject(6, UUID.class), resultat.getObject(7, UUID.class),
                resultat.getString(8), resultat.getObject(9, UUID.class), resultat.getObject(10, UUID.class), resultat.getString(11),
                resultat.getString(12), (Boolean) resultat.getObject(13), niveau(resultat.getString(14)), resultat.getString(15),
                resultat.getString(16), Conversions.instant(resultat, 17), resultat.getString(18),
                Conversions.instant(resultat, 19), Conversions.instant(resultat, 20), resultat.getString(21), null, null);
    }
}
