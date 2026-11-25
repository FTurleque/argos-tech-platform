package io.argos.regintel;

import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import javax.sql.DataSource;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.eclipse.microprofile.config.inject.ConfigProperty;

import io.argos.aigateway.ReponseIa;
import io.argos.commun.ContexteBase;
import io.argos.commun.ContexteBase.Contexte;

/**
 * Lecture des fiches d'analyse sous la RLS (REG-05, REG-07) et implémentation du point d'extension
 * {@link FichesReglementaires} du serveur MCP.
 *
 * <p>Chaque lecture s'exécute sous le rôle {@code argos_app} avec le compte de l'appelant
 * ({@link ContexteBase}) : la base ne restitue que les fiches des espaces dont il est membre, et une
 * fiche non validée seulement aux experts de ses domaines (politique {@code fiche_reglementaire_lecture},
 * migration V16). Aucun filtre applicatif ne remplace cette garantie.
 */
@ApplicationScoped
public class FichesEnBase implements FichesReglementaires {

    /** Nombre maximal de fiches restituées par une liste. */
    public static final int LIMITE_MAX = 200;

    static final String COLONNES_FICHE = """
            f.id, f.espace_id, f.etat_validation, f.etat_suivi, f.resume, f.resume_origine, f.date_effet,
            f.date_effet_origine, f.date_effet_ia, f.articles_modifies, f.demande_url, f.demande_forge, f.demande_etat,
            f.decide_le, argos_expert_fiche(f.id),
            t.id, t.identifiant, t.nature, t.numero, t.nor, t.ministere, t.titre, t.date_publication, t.date_signature,
            t.date_effet, t.motif_date_effet, t.codes_ids, t.codes_libelles, t.articles_modifies, t.extrait,
            t.archive, t.collecte_le""";

    /** Critères d'une liste de fiches ; chaque critère est facultatif. */
    public record Criteres(UUID espace, String domaine, String etatValidation, LocalDate effetDepuis,
            LocalDate effetJusqua, int limite) {
    }

    @Inject
    ContexteBase base;

    @Inject
    DataSource dataSource;

    @ConfigProperty(name = "argos.reglementaire.legifrance.url")
    String legifrance;

    // ------------------------------------------------------------------ lectures d'un membre

    /** Fiches visibles du compte selon les critères, des plus récentes aux plus anciennes publications. */
    public List<FicheAnalyse> lister(UUID compte, Criteres criteres) {
        return base.executer(new Contexte(compte, null, null), connexion -> lister(connexion, criteres, false));
    }

    /**
     * Calendrier des entrées en vigueur (REG-07) : fiches validées, visibles du compte, dont la date
     * d'effet est comprise entre les bornes, dans l'ordre chronologique.
     */
    public List<FicheAnalyse> calendrier(UUID compte, UUID espace, LocalDate depuis, LocalDate jusqua) {
        Criteres criteres = new Criteres(espace, null, "VALIDEE", depuis, jusqua, LIMITE_MAX);
        return base.executer(new Contexte(compte, null, null), connexion -> lister(connexion, criteres, true));
    }

    /** Fiche visible du compte, avec son historique. */
    public Optional<FicheAnalyse> detail(UUID compte, UUID fiche) {
        return base.executer(new Contexte(compte, null, null), connexion -> lire(connexion, fiche, true));
    }

    /** Espace d'une fiche existante, lu hors RLS : distingue une fiche inconnue d'une fiche hors périmètre. */
    public Optional<UUID> espaceDe(UUID fiche) {
        try (Connection connexion = dataSource.getConnection();
                PreparedStatement requete = connexion.prepareStatement("SELECT espace_id FROM fiche_reglementaire WHERE id = ?")) {
            requete.setObject(1, fiche);
            try (ResultSet resultat = requete.executeQuery()) {
                return resultat.next() ? Optional.of(resultat.getObject(1, UUID.class)) : Optional.empty();
            }
        } catch (SQLException erreur) {
            throw new IllegalStateException("lecture de la fiche impossible", erreur);
        }
    }

    // ------------------------------------------------------------------ point d'extension MCP

    @Override
    public List<Fiche> fiches(UUID compte, Set<UUID> espaces, Optional<String> domaine) {
        if (espaces.isEmpty()) {
            return List.of();
        }
        // Fiches validées seulement, filtrées par la requête avant la limite : des fiches en attente ne
        // peuvent pas évincer de la liste les fiches diffusables (garde-fou VALIDATION_HUMAINE, MCP-07).
        List<FicheAnalyse> visibles = base.executer(new Contexte(compte, null, null), connexion -> {
            List<FicheAnalyse> toutes = new ArrayList<>();
            for (UUID espace : espaces) {
                toutes.addAll(lister(connexion, new Criteres(espace, domaine.orElse(null), "VALIDEE", null, null, LIMITE_MAX), false));
            }
            return toutes;
        });
        return visibles.stream().filter(analyse -> espaces.contains(analyse.espaceId()))
                .sorted(java.util.Comparator.comparing((FicheAnalyse analyse) -> analyse.texte().datePublication()).reversed()
                        .thenComparing(analyse -> analyse.texte().identifiant()))
                .limit(LIMITE_MAX)
                .map(analyse -> new Fiche(analyse.id(), analyse.espaceId(),
                        analyse.domaines().stream().map(FicheAnalyse.DomaineRattache::code).toList(),
                        Etat.valueOf(analyse.etatValidation()), analyse.texte().titre(), analyse.texte().identifiant(),
                        analyse.texte().nor(), analyse.resume(), analyse.texte().datePublication(), analyse.dateEffet(), "JORF",
                        analyse.texte().lienPublie(), analyse.mentionIa(), analyse.mentionSource()))
                .toList();
    }

    // ------------------------------------------------------------------ lecture commune

    List<FicheAnalyse> lister(Connection connexion, Criteres criteres, boolean chronologique) throws SQLException {
        String sql = "SELECT " + COLONNES_FICHE + """
                 FROM fiche_reglementaire f JOIN texte_officiel t ON t.id = f.texte_id
                WHERE (CAST(? AS uuid) IS NULL OR f.espace_id = ?)
                  AND (CAST(? AS varchar) IS NULL OR f.etat_validation = ?)
                  AND (CAST(? AS varchar) IS NULL OR EXISTS (SELECT 1 FROM fiche_domaine fd
                                                       WHERE fd.fiche_id = f.id AND fd.domaine = ?))
                  AND (CAST(? AS date) IS NULL OR f.date_effet >= ?)
                  AND (CAST(? AS date) IS NULL OR f.date_effet <= ?)
                """ + (chronologique ? " AND f.date_effet IS NOT NULL ORDER BY f.date_effet, t.identifiant"
                        : " ORDER BY t.date_publication DESC, t.identifiant") + " LIMIT ?";
        List<FicheAnalyse> fiches = new ArrayList<>();
        try (PreparedStatement requete = connexion.prepareStatement(sql)) {
            requete.setObject(1, criteres.espace());
            requete.setObject(2, criteres.espace());
            requete.setString(3, criteres.etatValidation());
            requete.setString(4, criteres.etatValidation());
            requete.setString(5, criteres.domaine());
            requete.setString(6, criteres.domaine());
            date(requete, 7, criteres.effetDepuis());
            date(requete, 8, criteres.effetDepuis());
            date(requete, 9, criteres.effetJusqua());
            date(requete, 10, criteres.effetJusqua());
            requete.setInt(11, Math.max(1, Math.min(LIMITE_MAX, criteres.limite())));
            try (ResultSet resultat = requete.executeQuery()) {
                while (resultat.next()) {
                    fiches.add(fiche(resultat, List.of(), List.of()));
                }
            }
        }
        return completer(connexion, fiches, false);
    }

    Optional<FicheAnalyse> lire(Connection connexion, UUID id, boolean avecHistorique) throws SQLException {
        try (PreparedStatement requete = connexion.prepareStatement("SELECT " + COLONNES_FICHE
                + " FROM fiche_reglementaire f JOIN texte_officiel t ON t.id = f.texte_id WHERE f.id = ?")) {
            requete.setObject(1, id);
            try (ResultSet resultat = requete.executeQuery()) {
                if (!resultat.next()) {
                    return Optional.empty();
                }
                return Optional.of(completer(connexion, List.of(fiche(resultat, List.of(), List.of())), avecHistorique).get(0));
            }
        }
    }

    private List<FicheAnalyse> completer(Connection connexion, List<FicheAnalyse> fiches, boolean avecHistorique) throws SQLException {
        if (fiches.isEmpty()) {
            return fiches;
        }
        Map<UUID, List<FicheAnalyse.DomaineRattache>> domaines = new LinkedHashMap<>();
        Map<UUID, List<FicheAnalyse.EvenementHistorique>> historiques = new LinkedHashMap<>();
        java.sql.Array ids = connexion.createArrayOf("uuid", fiches.stream().map(FicheAnalyse::id).toArray());
        try (PreparedStatement requete = connexion.prepareStatement("""
                SELECT fd.fiche_id, fd.domaine, dm.libelle, fd.origine, fd.confiance, fd.motif FROM fiche_domaine fd
                LEFT JOIN domaine_metier dm ON dm.espace_id = fd.espace_id AND dm.code = fd.domaine
                WHERE fd.fiche_id = ANY (?) ORDER BY fd.fiche_id, fd.domaine
                """)) {
            requete.setArray(1, ids);
            try (ResultSet lignes = requete.executeQuery()) {
                while (lignes.next()) {
                    Integer confiance = lignes.getObject(5) == null ? null : lignes.getInt(5);
                    domaines.computeIfAbsent(lignes.getObject(1, UUID.class), ficheId -> new ArrayList<>())
                            .add(new FicheAnalyse.DomaineRattache(lignes.getString(2), lignes.getString(3), lignes.getString(4), confiance,
                                    lignes.getString(6)));
                }
            }
        }
        if (avecHistorique) {
            try (PreparedStatement requete = connexion.prepareStatement("""
                    SELECT fiche_id, action, detail::text, auteur, le FROM fiche_historique
                    WHERE fiche_id = ANY (?) ORDER BY le, id
                    """)) {
                requete.setArray(1, ids);
                try (ResultSet lignes = requete.executeQuery()) {
                    while (lignes.next()) {
                        historiques.computeIfAbsent(lignes.getObject(1, UUID.class), ficheId -> new ArrayList<>())
                                .add(new FicheAnalyse.EvenementHistorique(lignes.getString(2), lignes.getString(3),
                                        lignes.getObject(4, UUID.class), lignes.getTimestamp(5).toInstant()));
                    }
                }
            }
        }
        List<FicheAnalyse> resultat = new ArrayList<>();
        for (FicheAnalyse analyse : fiches) {
            resultat.add(new FicheAnalyse(analyse.id(), analyse.espaceId(), analyse.texte(), analyse.etatValidation(),
                    analyse.etatSuivi(), analyse.libelleSuivi(), analyse.resume(), analyse.resumeOrigine(), analyse.mentionIa(),
                    analyse.dateEffet(), analyse.dateEffetOrigine(), analyse.dateEffetLibelle(), analyse.motifDateEffet(),
                    analyse.dateEffetIa(), analyse.articlesModifies(), domaines.getOrDefault(analyse.id(), List.of()),
                    analyse.demande(), analyse.decideLe(), analyse.peutExpertiser(), analyse.mentionValidation(),
                    analyse.mentionAvisJuridique(), analyse.mentionSource(),
                    avecHistorique ? historiques.getOrDefault(analyse.id(), List.of()) : List.of()));
        }
        return resultat;
    }

    private FicheAnalyse fiche(ResultSet resultat, List<FicheAnalyse.DomaineRattache> domaines,
            List<FicheAnalyse.EvenementHistorique> historique) throws SQLException {
        UUID id = resultat.getObject(1, UUID.class);
        UUID espace = resultat.getObject(2, UUID.class);
        String validation = resultat.getString(3);
        CycleVie.Etat suivi = CycleVie.Etat.valueOf(resultat.getString(4));
        String resume = resultat.getString(5);
        String resumeOrigine = resultat.getString(6);
        LocalDate effet = resultat.getObject(7, LocalDate.class);
        String effetOrigine = resultat.getString(8);
        LocalDate effetIa = resultat.getObject(9, LocalDate.class);
        List<String> articles = TextesOfficiels.tableau(resultat.getArray(10));
        String demandeUrl = resultat.getString(11);
        String demandeForge = resultat.getString(12);
        String demandeEtat = resultat.getString(13);
        Timestamp decide = resultat.getTimestamp(14);
        boolean expert = resultat.getBoolean(15);
        TexteOfficiel texteOfficiel = TextesOfficiels.texte(resultat, 16);
        LocalDate miseAJour = FicheAnalyse.dateMiseAJour(resultat.getString(31), instant(resultat.getTimestamp(32)));
        String base = legifrance.replaceAll("/+$", "");
        FicheAnalyse.Texte texte = new FicheAnalyse.Texte(texteOfficiel.identifiant(), texteOfficiel.nature(),
                texteOfficiel.numero(), texteOfficiel.nor(), texteOfficiel.ministere(), texteOfficiel.titre(),
                texteOfficiel.datePublication(), texteOfficiel.codes(), base + "/jorf/id/" + texteOfficiel.identifiant(),
                base + "/loda/id/" + texteOfficiel.identifiant());
        String mentionValidation = switch (validation) {
            case "VALIDEE" -> FicheAnalyse.MENTION_VALIDEE;
            case "REJETEE" -> "rejetée par l'expert métier : non diffusée";
            default -> "en attente de validation par l'expert métier : non diffusée";
        };
        String motif = "TEXTE".equals(effetOrigine) ? texteOfficiel.motifDateEffet()
                : "EXPERT".equals(effetOrigine) ? "date corrigée par l'expert métier" : null;
        return new FicheAnalyse(id, espace, texte, validation, suivi, CycleVie.libelle(suivi), resume, resumeOrigine,
                "IA".equals(resumeOrigine) ? ReponseIa.MENTION : null, effet, effetOrigine,
                effet == null ? DatesTexte.DATE_A_CONFIRMER : effet.toString(), motif, effetIa, articles, domaines,
                demandeUrl == null ? null : new FicheAnalyse.Demande(demandeUrl, demandeForge, demandeEtat),
                decide == null ? null : decide.toInstant(), expert, mentionValidation, FicheAnalyse.AVIS_JURIDIQUE,
                FicheAnalyse.mentionSource(miseAJour), historique);
    }

    private static void date(PreparedStatement requete, int position, LocalDate valeur) throws SQLException {
        if (valeur == null) {
            requete.setNull(position, Types.DATE);
        } else {
            requete.setDate(position, Date.valueOf(valeur));
        }
    }

    static Instant instant(Timestamp horodatage) {
        return horodatage == null ? null : horodatage.toInstant();
    }
}
