package io.argos.catalog.standard;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Clock;
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

import io.argos.commun.ContexteBase;
import io.argos.commun.ContexteBase.Contexte;
import io.argos.commun.Introuvable;
import io.argos.commun.RegleViolee;
import io.argos.execution.PublicationAsynchrone;
import io.argos.horloge.LectureHeure;

/**
 * Référentiel des standards d'un espace (RAD-01, RAD-02, RAD-06) : chaque espace construit le sien,
 * sans référentiel modèle (Q-3, ADR-0025).
 *
 * <p>Les lectures et les écritures demandées par un compte s'exécutent sous le rôle {@code argos_app}
 * avec l'espace courant ({@link ContexteBase}) : la RLS de la migration V12 n'expose le référentiel
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
    Clock horloge;

    @Inject
    ContexteBase base;

    @Inject
    DataSource dataSource;

    @Inject
    Event<StandardModifie> modifications;

    @Inject
    PublicationAsynchrone publication;

    /** Référentiel de l'espace, par libellé. */
    public List<EntreeReferentiel> lister(UUID compteId, UUID espaceId) {
        return base.executer(new Contexte(compteId, null, espaceId), connexion -> {
            List<EntreeReferentiel> entrees = new ArrayList<>();
            try (PreparedStatement requete = connexion.prepareStatement(COLONNES + " WHERE espace_id = ? ORDER BY lower(libelle), cle")) {
                requete.setObject(1, espaceId);
                try (ResultSet resultat = requete.executeQuery()) {
                    while (resultat.next()) {
                        entrees.add(entree(resultat));
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
        SaisieStandard.Validee saisieValidee = Objects.requireNonNull(saisie, "saisie").valider();
        Resultat bilan = base.executer(new Contexte(compteId, null, espaceId),
                connexion -> appliquer(connexion, compteId, espaceId, saisieValidee, "SAISIE"));
        bilan.changement().ifPresent(changement -> publication.publier(modifications, changement));
        return bilan.entree();
    }

    /** Historique d'une technologie, du plus récent au plus ancien ; vide si elle est absente. */
    public List<Version> historique(UUID compteId, UUID espaceId, String cle) {
        return base.executer(new Contexte(compteId, null, espaceId), connexion -> {
            List<Version> versions = new ArrayList<>();
            try (PreparedStatement requete = connexion.prepareStatement("""
                    SELECT h.statut, h.version_recommandee, h.responsable, h.justification, h.date_revue, h.origine,
                           h.modifie_par, h.modifie_le
                    FROM standard_historique h JOIN standard_technologie s ON s.id = h.standard_id
                    WHERE s.espace_id = ? AND s.cle = ? ORDER BY h.modifie_le DESC, h.id
                    """)) {
                requete.setObject(1, espaceId);
                requete.setString(2, cle);
                try (ResultSet resultat = requete.executeQuery()) {
                    while (resultat.next()) {
                        versions.add(new Version(ReferentielStandards.Statut.valueOf(resultat.getString(1)), resultat.getString(2),
                                resultat.getString(3), resultat.getString(4), resultat.getObject(5, LocalDate.class), resultat.getString(6),
                                resultat.getObject(7, UUID.class), instant(resultat, 8)));
                    }
                }
            }
            return versions;
        });
    }

    /**
     * Enregistre une suggestion de l'IA en attente de décision (RAD-02-S2, RAD-08). Traitement technique : le
     * référentiel n'est ni lu ni modifié ; la suggestion n'a d'effet qu'acceptée par un administrateur. Une
     * suggestion encore en attente pour la même technologie de l'espace n'est pas dupliquée (RAD-08-S2).
     *
     * @return identifiant de la suggestion enregistrée, vide si une suggestion était déjà en attente
     */
    public Optional<UUID> suggerer(UUID espaceId, String cle, String statut, String versionRecommandee, String justification) {
        SaisieStandard.Validee saisieValidee = new SaisieStandard(cle, null, statut, versionRecommandee, "IA", justification,
                LectureHeure.jour(horloge).toString(), null).valider();
        UUID id = UUID.randomUUID();
        try (Connection connexion = dataSource.getConnection(); PreparedStatement requete = connexion.prepareStatement("""
                INSERT INTO standard_suggestion (id, espace_id, cle, statut, version_recommandee, justification, origine, cree_le)
                SELECT ?, ?, ?, ?, ?, ?, 'IA', ?
                WHERE NOT EXISTS (SELECT 1 FROM standard_suggestion
                                  WHERE espace_id = ? AND cle = ? AND etat = 'EN_ATTENTE')
                """)) {
            requete.setObject(1, id);
            requete.setObject(2, espaceId);
            requete.setString(3, saisieValidee.cle());
            requete.setString(4, saisieValidee.statut().name());
            requete.setString(5, saisieValidee.versionRecommandee());
            requete.setString(6, saisieValidee.justification());
            requete.setObject(7, horloge.instant().atOffset(ZoneOffset.UTC));
            requete.setObject(8, espaceId);
            requete.setString(9, saisieValidee.cle());
            return requete.executeUpdate() == 1 ? Optional.of(id) : Optional.empty();
        } catch (SQLException erreur) {
            throw new IllegalStateException("enregistrement de la suggestion impossible", erreur);
        }
    }

    /** Vrai si une suggestion attend une décision pour cette technologie de l'espace (traitement technique). */
    public boolean suggestionEnAttente(UUID espaceId, String cle) {
        try (Connection connexion = dataSource.getConnection(); PreparedStatement requete = connexion.prepareStatement(
                "SELECT 1 FROM standard_suggestion WHERE espace_id = ? AND cle = ? AND etat = 'EN_ATTENTE'")) {
            requete.setObject(1, espaceId);
            requete.setString(2, cle);
            try (ResultSet resultat = requete.executeQuery()) {
                return resultat.next();
            }
        } catch (SQLException erreur) {
            throw new IllegalStateException("lecture des suggestions impossible", erreur);
        }
    }

    /** Suggestions de l'espace, en attente d'abord. */
    public List<Suggestion> suggestions(UUID compteId, UUID espaceId) {
        return base.executer(new Contexte(compteId, null, espaceId), connexion -> {
            List<Suggestion> liste = new ArrayList<>();
            try (PreparedStatement requete = connexion.prepareStatement("""
                    SELECT id, espace_id, cle, statut, version_recommandee, justification, origine, etat, cree_le, decide_le
                    FROM standard_suggestion WHERE espace_id = ?
                    ORDER BY (etat = 'EN_ATTENTE') DESC, cree_le DESC, id
                    """)) {
                requete.setObject(1, espaceId);
                try (ResultSet resultat = requete.executeQuery()) {
                    while (resultat.next()) {
                        liste.add(new Suggestion(resultat.getObject(1, UUID.class), resultat.getObject(2, UUID.class),
                                resultat.getString(3), ReferentielStandards.Statut.valueOf(resultat.getString(4)),
                                resultat.getString(5), resultat.getString(6),
                                resultat.getString(7), resultat.getString(8), instant(resultat, 9), instant(resultat, 10)));
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
        Resultat bilan = base.executer(new Contexte(compteId, null, espaceId), connexion -> {
            Suggestion suggestion = lireSuggestion(connexion, espaceId, suggestionId);
            Resultat resultat = null;
            if (decision.accepter()) {
                Optional<EntreeReferentiel> existante = lire(connexion, espaceId, suggestion.cle(), false);
                SaisieStandard saisie = new SaisieStandard(suggestion.cle(), existante.map(EntreeReferentiel::libelle).orElse(null),
                        suggestion.statut().name(), suggestion.versionRecommandee(),
                        premier(decision.responsable(), existante.map(EntreeReferentiel::responsable).orElse(null)),
                        suggestion.justification(),
                        premier(decision.dateRevue(), existante.map(entree -> entree.dateRevue().toString()).orElse(null)),
                        existante.map(EntreeReferentiel::produitCycleVie).orElse(null));
                resultat = appliquer(connexion, compteId, espaceId, saisie.valider(), "SUGGESTION");
            }
            try (PreparedStatement requete = connexion.prepareStatement("""
                    UPDATE standard_suggestion SET etat = ?, decide_par = argos_compte_courant(), decide_le = now()
                    WHERE id = ? AND espace_id = ? AND etat = 'EN_ATTENTE'
                    """)) {
                requete.setString(1, decision.accepter() ? "ACCEPTEE" : "REJETEE");
                requete.setObject(2, suggestionId);
                requete.setObject(3, espaceId);
                if (requete.executeUpdate() != 1) {
                    throw new RegleViolee("SUGGESTION_DECIDEE", "La suggestion n'est plus en attente.");
                }
            }
            return resultat;
        });
        if (bilan == null) {
            return Optional.empty();
        }
        bilan.changement().ifPresent(changement -> publication.publier(modifications, changement));
        return Optional.of(bilan.entree());
    }

    // ------------------------------------------------------------------ interne

    private record Resultat(EntreeReferentiel entree, Optional<StandardModifie> changement) {
    }

    /** Crée ou modifie l'entrée dans la transaction courante, écrit l'historique et décrit le changement éventuel. */
    private Resultat appliquer(Connection connexion, UUID compteId, UUID espaceId, SaisieStandard.Validee saisieValidee, String origine)
            throws SQLException {
        Optional<EntreeReferentiel> avant = lire(connexion, espaceId, saisieValidee.cle(), true);
        UUID id = avant.map(EntreeReferentiel::id).orElseGet(UUID::randomUUID);
        if (avant.isEmpty()) {
            try (PreparedStatement requete = connexion.prepareStatement("""
                    INSERT INTO standard_technologie (id, espace_id, cle, libelle, statut, version_recommandee, responsable,
                        justification, date_revue, produit_cycle_vie, cree_le, modifie_le, modifie_par)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, now(), now(), argos_compte_courant())
                    """)) {
                requete.setObject(1, id);
                requete.setObject(2, espaceId);
                requete.setString(3, saisieValidee.cle());
                lier(requete, 4, saisieValidee);
                requete.executeUpdate();
            } catch (SQLException erreur) {
                if ("23505".equals(erreur.getSQLState())) {
                    throw new RegleViolee("MODIFICATION_CONCURRENTE",
                            "Cette technologie vient d'être ajoutée au référentiel : rechargez puis recommencez.");
                }
                throw erreur;
            }
        } else {
            try (PreparedStatement requete = connexion.prepareStatement("""
                    UPDATE standard_technologie SET libelle = ?, statut = ?, version_recommandee = ?, responsable = ?,
                        justification = ?, date_revue = ?, produit_cycle_vie = ?, modifie_le = now(),
                        modifie_par = argos_compte_courant()
                    WHERE id = ?
                    """)) {
                lier(requete, 1, saisieValidee);
                requete.setObject(8, id);
                requete.executeUpdate();
            }
        }
        UUID historiqueId = UUID.randomUUID();
        try (PreparedStatement requete = connexion.prepareStatement("""
                INSERT INTO standard_historique (id, standard_id, espace_id, statut, version_recommandee, responsable,
                    justification, date_revue, origine, modifie_par, modifie_le)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, argos_compte_courant(), now())
                """)) {
            requete.setObject(1, historiqueId);
            requete.setObject(2, id);
            requete.setObject(3, espaceId);
            requete.setString(4, saisieValidee.statut().name());
            requete.setString(5, saisieValidee.versionRecommandee());
            requete.setString(6, saisieValidee.responsable());
            requete.setString(7, saisieValidee.justification());
            requete.setObject(8, saisieValidee.dateRevue());
            requete.setString(9, origine);
            requete.executeUpdate();
        }
        EntreeReferentiel apres = lire(connexion, espaceId, saisieValidee.cle(), false).orElseThrow();
        Optional<StandardModifie> changement = avant
                .filter(precedente -> precedente.statut() != apres.statut()
                        || !Objects.equals(precedente.versionRecommandee(), apres.versionRecommandee()))
                .map(precedente -> new StandardModifie(espaceId, apres.cle(), apres.libelle(), precedente.statut(),
                        precedente.versionRecommandee(),
                        apres.statut(), apres.versionRecommandee(), apres.justification(), compteId, historiqueId));
        return new Resultat(apres, changement);
    }

    private static void lier(PreparedStatement requete, int debut, SaisieStandard.Validee saisieValidee) throws SQLException {
        requete.setString(debut, saisieValidee.libelle());
        requete.setString(debut + 1, saisieValidee.statut().name());
        requete.setString(debut + 2, saisieValidee.versionRecommandee());
        requete.setString(debut + 3, saisieValidee.responsable());
        requete.setString(debut + 4, saisieValidee.justification());
        requete.setObject(debut + 5, saisieValidee.dateRevue());
        requete.setString(debut + 6, saisieValidee.produitCycleVie());
    }

    private static Optional<EntreeReferentiel> lire(Connection connexion, UUID espaceId, String cle, boolean verrouiller)
            throws SQLException {
        try (PreparedStatement requete = connexion.prepareStatement(COLONNES + " WHERE espace_id = ? AND cle = ?"
                + (verrouiller ? " FOR UPDATE" : ""))) {
            requete.setObject(1, espaceId);
            requete.setString(2, cle);
            try (ResultSet resultat = requete.executeQuery()) {
                return resultat.next() ? Optional.of(entree(resultat)) : Optional.empty();
            }
        }
    }

    private static Suggestion lireSuggestion(Connection connexion, UUID espaceId, UUID suggestionId) throws SQLException {
        try (PreparedStatement requete = connexion.prepareStatement("""
                SELECT id, espace_id, cle, statut, version_recommandee, justification, origine, etat, cree_le, decide_le
                FROM standard_suggestion WHERE id = ? AND espace_id = ?
                """)) {
            requete.setObject(1, suggestionId);
            requete.setObject(2, espaceId);
            try (ResultSet resultat = requete.executeQuery()) {
                if (!resultat.next()) {
                    throw new Introuvable("Aucune suggestion de cet espace ne porte cet identifiant.");
                }
                if (!"EN_ATTENTE".equals(resultat.getString(8))) {
                    throw new RegleViolee("SUGGESTION_DECIDEE", "La suggestion n'est plus en attente.");
                }
                return new Suggestion(resultat.getObject(1, UUID.class), resultat.getObject(2, UUID.class), resultat.getString(3),
                        ReferentielStandards.Statut.valueOf(resultat.getString(4)), resultat.getString(5), resultat.getString(6),
                        resultat.getString(7), resultat.getString(8), instant(resultat, 9), instant(resultat, 10));
            }
        }
    }

    private static EntreeReferentiel entree(ResultSet resultat) throws SQLException {
        return new EntreeReferentiel(resultat.getObject(1, UUID.class), resultat.getObject(2, UUID.class), resultat.getString(3),
                resultat.getString(4), ReferentielStandards.Statut.valueOf(resultat.getString(5)), resultat.getString(6),
                resultat.getString(7), resultat.getString(8), resultat.getObject(9, LocalDate.class), resultat.getString(10),
                instant(resultat, 11));
    }

    private static Instant instant(ResultSet resultat, int colonne) throws SQLException {
        OffsetDateTime horodatage = resultat.getObject(colonne, OffsetDateTime.class);
        return horodatage == null ? null : horodatage.toInstant();
    }

    private static String premier(String valeur, String defaut) {
        return valeur == null || valeur.isBlank() ? defaut : valeur;
    }
}
