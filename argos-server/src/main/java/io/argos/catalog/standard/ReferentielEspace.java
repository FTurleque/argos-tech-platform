package io.argos.catalog.standard;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import javax.sql.DataSource;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Event;
import jakarta.inject.Inject;

import io.argos.workspace.ContexteBase;
import io.argos.workspace.ContexteBase.Contexte;
import io.argos.workspace.Introuvable;
import io.argos.workspace.RegleViolee;

/**
 * Référentiel des standards d'un espace (RAD-01, RAD-02, RAD-06) : chaque espace construit le sien,
 * sans référentiel modèle (Q-3, ADR-0025).
 *
 * <p>Les lectures et les écritures demandées par un compte s'exécutent sous le rôle {@code argos_app}
 * avec l'espace courant ({@link ContexteBase}) : la RLS de la migration V13 n'expose le référentiel
 * qu'aux membres de l'espace et n'en accepte la modification que d'un administrateur. Le contrôle du
 * rôle reste fait en amont par l'API, qui répond 403 à un lecteur (RAD-02-S1).
 *
 * <p>Une suggestion de l'IA ({@link #suggerer}) est enregistrée en attente par un traitement technique
 * et ne modifie jamais le référentiel : seul un administrateur l'applique ({@link #decider}, RAD-02-S2).
 * Un changement de statut ou de version recommandée publie {@link StandardModifie} après validation
 * de la transaction (RAD-06).
 */
@ApplicationScoped
public class ReferentielEspace {

    /** Entrée du référentiel d'un espace. */
    public record EntreeReferentiel(UUID id, UUID espaceId, String cle, String libelle, ReferentielStandards.Statut statut,
            String versionRecommandee, String responsable, String justification, LocalDate dateRevue,
            String produitCycleVie, Instant modifieLe) {
    }

    /** Valeurs successives d'une entrée. */
    public record Version(ReferentielStandards.Statut statut, String versionRecommandee, String responsable,
            String justification, LocalDate dateRevue, String origine, UUID modifiePar, Instant modifieLe) {
    }

    /** Suggestion en attente ou décidée. */
    public record Suggestion(UUID id, UUID espaceId, String cle, ReferentielStandards.Statut statut,
            String versionRecommandee, String justification, String origine, String etat, Instant creeLe,
            Instant decideLe) {
    }

    /** Décision d'un administrateur sur une suggestion : responsable et date de revue complètent une nouvelle entrée. */
    public record Decision(boolean accepter, String responsable, String dateRevue) {
    }

    private static final String COLONNES = """
            SELECT id, espace_id, cle, libelle, statut, version_recommandee, responsable, justification, date_revue,
                   produit_cycle_vie, modifie_le FROM standard_technologie
            """;

    @Inject
    ContexteBase base;

    @Inject
    DataSource dataSource;

    @Inject
    Event<StandardModifie> modifications;

    /** Référentiel de l'espace, par libellé. */
    public List<EntreeReferentiel> lister(UUID compteId, UUID espaceId) {
        return base.executer(new Contexte(compteId, null, espaceId), c -> {
            List<EntreeReferentiel> entrees = new ArrayList<>();
            try (PreparedStatement ps = c.prepareStatement(COLONNES + " WHERE espace_id = ? ORDER BY lower(libelle), cle")) {
                ps.setObject(1, espaceId);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        entrees.add(entree(rs));
                    }
                }
            }
            return entrees;
        });
    }

    /**
     * Crée ou modifie l'entrée de la technologie dans l'espace (RAD-01), avec une ligne d'historique.
     * Une saisie invalide est refusée sans rien modifier (RAD-01-S2).
     */
    public EntreeReferentiel enregistrer(UUID compteId, UUID espaceId, SaisieStandard saisie) {
        SaisieStandard.Validee v = Objects.requireNonNull(saisie, "saisie").valider();
        Resultat r = base.executer(new Contexte(compteId, null, espaceId),
                c -> appliquer(c, compteId, espaceId, v, "SAISIE"));
        r.changement().ifPresent(modifications::fire);
        return r.entree();
    }

    /** Historique d'une technologie, du plus récent au plus ancien ; vide si elle est absente. */
    public List<Version> historique(UUID compteId, UUID espaceId, String cle) {
        return base.executer(new Contexte(compteId, null, espaceId), c -> {
            List<Version> versions = new ArrayList<>();
            try (PreparedStatement ps = c.prepareStatement("""
                    SELECT h.statut, h.version_recommandee, h.responsable, h.justification, h.date_revue, h.origine,
                           h.modifie_par, h.modifie_le
                    FROM standard_historique h JOIN standard_technologie s ON s.id = h.standard_id
                    WHERE s.espace_id = ? AND s.cle = ? ORDER BY h.modifie_le DESC, h.id
                    """)) {
                ps.setObject(1, espaceId);
                ps.setString(2, cle);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        versions.add(new Version(ReferentielStandards.Statut.valueOf(rs.getString(1)), rs.getString(2),
                                rs.getString(3), rs.getString(4), rs.getObject(5, LocalDate.class), rs.getString(6),
                                rs.getObject(7, UUID.class), instant(rs, 8)));
                    }
                }
            }
            return versions;
        });
    }

    /**
     * Enregistre une suggestion de l'IA en attente de décision (RAD-02-S2). Traitement technique : le
     * référentiel n'est ni lu ni modifié ; la suggestion n'a d'effet qu'acceptée par un administrateur.
     */
    public UUID suggerer(UUID espaceId, String cle, String statut, String versionRecommandee, String justification) {
        SaisieStandard.Validee v = new SaisieStandard(cle, null, statut, versionRecommandee, "IA", justification,
                LocalDate.now().toString(), null).valider();
        UUID id = UUID.randomUUID();
        try (Connection c = dataSource.getConnection(); PreparedStatement ps = c.prepareStatement("""
                INSERT INTO standard_suggestion (id, espace_id, cle, statut, version_recommandee, justification, origine, cree_le)
                VALUES (?, ?, ?, ?, ?, ?, 'IA', ?)
                """)) {
            ps.setObject(1, id);
            ps.setObject(2, espaceId);
            ps.setString(3, v.cle());
            ps.setString(4, v.statut().name());
            ps.setString(5, v.versionRecommandee());
            ps.setString(6, v.justification());
            ps.setObject(7, OffsetDateTime.now(ZoneOffset.UTC));
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("enregistrement de la suggestion impossible", e);
        }
        return id;
    }

    /** Suggestions de l'espace, en attente d'abord. */
    public List<Suggestion> suggestions(UUID compteId, UUID espaceId) {
        return base.executer(new Contexte(compteId, null, espaceId), c -> {
            List<Suggestion> liste = new ArrayList<>();
            try (PreparedStatement ps = c.prepareStatement("""
                    SELECT id, espace_id, cle, statut, version_recommandee, justification, origine, etat, cree_le, decide_le
                    FROM standard_suggestion WHERE espace_id = ?
                    ORDER BY (etat = 'EN_ATTENTE') DESC, cree_le DESC, id
                    """)) {
                ps.setObject(1, espaceId);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        liste.add(new Suggestion(rs.getObject(1, UUID.class), rs.getObject(2, UUID.class), rs.getString(3),
                                ReferentielStandards.Statut.valueOf(rs.getString(4)), rs.getString(5), rs.getString(6),
                                rs.getString(7), rs.getString(8), instant(rs, 9), instant(rs, 10)));
                    }
                }
            }
            return liste;
        });
    }

    /**
     * Décision d'un administrateur sur une suggestion en attente. Acceptée, elle s'applique comme une
     * saisie (validation, historique, annonce) ; le responsable et la date de revue viennent de la
     * décision ou, à défaut, de l'entrée existante. Rejetée, le référentiel est inchangé.
     *
     * @return l'entrée modifiée, absente pour un rejet
     */
    public Optional<EntreeReferentiel> decider(UUID compteId, UUID espaceId, UUID suggestionId, Decision decision) {
        Objects.requireNonNull(decision, "decision");
        Resultat r = base.executer(new Contexte(compteId, null, espaceId), c -> {
            Suggestion s = lireSuggestion(c, espaceId, suggestionId);
            Resultat resultat = null;
            if (decision.accepter()) {
                Optional<EntreeReferentiel> existante = lire(c, espaceId, s.cle(), false);
                SaisieStandard saisie = new SaisieStandard(s.cle(), existante.map(EntreeReferentiel::libelle).orElse(null),
                        s.statut().name(), s.versionRecommandee(),
                        premier(decision.responsable(), existante.map(EntreeReferentiel::responsable).orElse(null)),
                        s.justification(),
                        premier(decision.dateRevue(), existante.map(e -> e.dateRevue().toString()).orElse(null)),
                        existante.map(EntreeReferentiel::produitCycleVie).orElse(null));
                resultat = appliquer(c, compteId, espaceId, saisie.valider(), "SUGGESTION");
            }
            try (PreparedStatement ps = c.prepareStatement("""
                    UPDATE standard_suggestion SET etat = ?, decide_par = argos_compte_courant(), decide_le = now()
                    WHERE id = ? AND espace_id = ? AND etat = 'EN_ATTENTE'
                    """)) {
                ps.setString(1, decision.accepter() ? "ACCEPTEE" : "REJETEE");
                ps.setObject(2, suggestionId);
                ps.setObject(3, espaceId);
                if (ps.executeUpdate() != 1) {
                    throw new RegleViolee("SUGGESTION_DECIDEE", "La suggestion n'est plus en attente.");
                }
            }
            return resultat;
        });
        if (r == null) {
            return Optional.empty();
        }
        r.changement().ifPresent(modifications::fire);
        return Optional.of(r.entree());
    }

    // ------------------------------------------------------------------ interne

    private record Resultat(EntreeReferentiel entree, Optional<StandardModifie> changement) {
    }

    /** Crée ou modifie l'entrée dans la transaction courante, écrit l'historique et décrit le changement éventuel. */
    private Resultat appliquer(Connection c, UUID compteId, UUID espaceId, SaisieStandard.Validee v, String origine)
            throws SQLException {
        Optional<EntreeReferentiel> avant = lire(c, espaceId, v.cle(), true);
        UUID id = avant.map(EntreeReferentiel::id).orElseGet(UUID::randomUUID);
        if (avant.isEmpty()) {
            try (PreparedStatement ps = c.prepareStatement("""
                    INSERT INTO standard_technologie (id, espace_id, cle, libelle, statut, version_recommandee, responsable,
                        justification, date_revue, produit_cycle_vie, cree_le, modifie_le, modifie_par)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, now(), now(), argos_compte_courant())
                    """)) {
                ps.setObject(1, id);
                ps.setObject(2, espaceId);
                ps.setString(3, v.cle());
                lier(ps, 4, v);
                ps.executeUpdate();
            } catch (SQLException e) {
                if ("23505".equals(e.getSQLState())) {
                    throw new RegleViolee("MODIFICATION_CONCURRENTE",
                            "Cette technologie vient d'être ajoutée au référentiel : rechargez puis recommencez.");
                }
                throw e;
            }
        } else {
            try (PreparedStatement ps = c.prepareStatement("""
                    UPDATE standard_technologie SET libelle = ?, statut = ?, version_recommandee = ?, responsable = ?,
                        justification = ?, date_revue = ?, produit_cycle_vie = ?, modifie_le = now(),
                        modifie_par = argos_compte_courant()
                    WHERE id = ?
                    """)) {
                lier(ps, 1, v);
                ps.setObject(8, id);
                ps.executeUpdate();
            }
        }
        try (PreparedStatement ps = c.prepareStatement("""
                INSERT INTO standard_historique (id, standard_id, espace_id, statut, version_recommandee, responsable,
                    justification, date_revue, origine, modifie_par, modifie_le)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, argos_compte_courant(), now())
                """)) {
            ps.setObject(1, UUID.randomUUID());
            ps.setObject(2, id);
            ps.setObject(3, espaceId);
            ps.setString(4, v.statut().name());
            ps.setString(5, v.versionRecommandee());
            ps.setString(6, v.responsable());
            ps.setString(7, v.justification());
            ps.setObject(8, v.dateRevue());
            ps.setString(9, origine);
            ps.executeUpdate();
        }
        EntreeReferentiel apres = lire(c, espaceId, v.cle(), false).orElseThrow();
        Optional<StandardModifie> changement = avant
                .filter(a -> a.statut() != apres.statut() || !Objects.equals(a.versionRecommandee(), apres.versionRecommandee()))
                .map(a -> new StandardModifie(espaceId, apres.cle(), apres.libelle(), a.statut(), a.versionRecommandee(),
                        apres.statut(), apres.versionRecommandee(), apres.justification(), compteId));
        return new Resultat(apres, changement);
    }

    private static void lier(PreparedStatement ps, int debut, SaisieStandard.Validee v) throws SQLException {
        ps.setString(debut, v.libelle());
        ps.setString(debut + 1, v.statut().name());
        ps.setString(debut + 2, v.versionRecommandee());
        ps.setString(debut + 3, v.responsable());
        ps.setString(debut + 4, v.justification());
        ps.setObject(debut + 5, v.dateRevue());
        ps.setString(debut + 6, v.produitCycleVie());
    }

    private static Optional<EntreeReferentiel> lire(Connection c, UUID espaceId, String cle, boolean verrouiller)
            throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(COLONNES + " WHERE espace_id = ? AND cle = ?"
                + (verrouiller ? " FOR UPDATE" : ""))) {
            ps.setObject(1, espaceId);
            ps.setString(2, cle);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(entree(rs)) : Optional.empty();
            }
        }
    }

    private static Suggestion lireSuggestion(Connection c, UUID espaceId, UUID suggestionId) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("""
                SELECT id, espace_id, cle, statut, version_recommandee, justification, origine, etat, cree_le, decide_le
                FROM standard_suggestion WHERE id = ? AND espace_id = ?
                """)) {
            ps.setObject(1, suggestionId);
            ps.setObject(2, espaceId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    throw new Introuvable("Aucune suggestion de cet espace ne porte cet identifiant.");
                }
                if (!"EN_ATTENTE".equals(rs.getString(8))) {
                    throw new RegleViolee("SUGGESTION_DECIDEE", "La suggestion n'est plus en attente.");
                }
                return new Suggestion(rs.getObject(1, UUID.class), rs.getObject(2, UUID.class), rs.getString(3),
                        ReferentielStandards.Statut.valueOf(rs.getString(4)), rs.getString(5), rs.getString(6),
                        rs.getString(7), rs.getString(8), instant(rs, 9), instant(rs, 10));
            }
        }
    }

    private static EntreeReferentiel entree(ResultSet rs) throws SQLException {
        return new EntreeReferentiel(rs.getObject(1, UUID.class), rs.getObject(2, UUID.class), rs.getString(3),
                rs.getString(4), ReferentielStandards.Statut.valueOf(rs.getString(5)), rs.getString(6), rs.getString(7),
                rs.getString(8), rs.getObject(9, LocalDate.class), rs.getString(10), instant(rs, 11));
    }

    private static Instant instant(ResultSet rs, int colonne) throws SQLException {
        OffsetDateTime t = rs.getObject(colonne, OffsetDateTime.class);
        return t == null ? null : t.toInstant();
    }

    private static String premier(String valeur, String defaut) {
        return valeur == null || valeur.isBlank() ? defaut : valeur;
    }
}
