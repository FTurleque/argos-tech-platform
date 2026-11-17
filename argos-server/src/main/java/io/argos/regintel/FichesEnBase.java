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
import io.argos.workspace.ContexteBase;
import io.argos.workspace.ContexteBase.Contexte;

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
    public List<FicheAnalyse> lister(UUID compte, Criteres k) {
        return base.executer(new Contexte(compte, null, null), c -> lister(c, k, false));
    }

    /**
     * Calendrier des entrées en vigueur (REG-07) : fiches validées, visibles du compte, dont la date
     * d'effet est comprise entre les bornes, dans l'ordre chronologique.
     */
    public List<FicheAnalyse> calendrier(UUID compte, UUID espace, LocalDate depuis, LocalDate jusqua) {
        Criteres k = new Criteres(espace, null, "VALIDEE", depuis, jusqua, LIMITE_MAX);
        return base.executer(new Contexte(compte, null, null), c -> lister(c, k, true));
    }

    /** Fiche visible du compte, avec son historique. */
    public Optional<FicheAnalyse> detail(UUID compte, UUID fiche) {
        return base.executer(new Contexte(compte, null, null), c -> lire(c, fiche, true));
    }

    /** Espace d'une fiche existante, lu hors RLS : distingue une fiche inconnue d'une fiche hors périmètre. */
    public Optional<UUID> espaceDe(UUID fiche) {
        try (Connection c = dataSource.getConnection();
                PreparedStatement ps = c.prepareStatement("SELECT espace_id FROM fiche_reglementaire WHERE id = ?")) {
            ps.setObject(1, fiche);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(rs.getObject(1, UUID.class)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new IllegalStateException("lecture de la fiche impossible", e);
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
        List<FicheAnalyse> visibles = base.executer(new Contexte(compte, null, null), c -> {
            List<FicheAnalyse> toutes = new ArrayList<>();
            for (UUID espace : espaces) {
                toutes.addAll(lister(c, new Criteres(espace, domaine.orElse(null), "VALIDEE", null, null, LIMITE_MAX), false));
            }
            return toutes;
        });
        return visibles.stream().filter(f -> espaces.contains(f.espaceId()))
                .sorted(java.util.Comparator.comparing((FicheAnalyse f) -> f.texte().datePublication()).reversed()
                        .thenComparing(f -> f.texte().identifiant()))
                .limit(LIMITE_MAX)
                .map(f -> new Fiche(f.id(), f.espaceId(), f.domaines().stream().map(FicheAnalyse.DomaineRattache::code).toList(),
                        Etat.valueOf(f.etatValidation()), f.texte().titre(), f.texte().identifiant(), f.texte().nor(),
                        f.resume(), f.texte().datePublication(), f.dateEffet(), "JORF", f.texte().lienPublie(), f.mentionIa(),
                        f.mentionSource()))
                .toList();
    }

    // ------------------------------------------------------------------ lecture commune

    List<FicheAnalyse> lister(Connection c, Criteres k, boolean chronologique) throws SQLException {
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
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setObject(1, k.espace());
            ps.setObject(2, k.espace());
            ps.setString(3, k.etatValidation());
            ps.setString(4, k.etatValidation());
            ps.setString(5, k.domaine());
            ps.setString(6, k.domaine());
            date(ps, 7, k.effetDepuis());
            date(ps, 8, k.effetDepuis());
            date(ps, 9, k.effetJusqua());
            date(ps, 10, k.effetJusqua());
            ps.setInt(11, Math.max(1, Math.min(LIMITE_MAX, k.limite())));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    fiches.add(fiche(rs, List.of(), List.of()));
                }
            }
        }
        return completer(c, fiches, false);
    }

    Optional<FicheAnalyse> lire(Connection c, UUID id, boolean avecHistorique) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("SELECT " + COLONNES_FICHE
                + " FROM fiche_reglementaire f JOIN texte_officiel t ON t.id = f.texte_id WHERE f.id = ?")) {
            ps.setObject(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                return Optional.of(completer(c, List.of(fiche(rs, List.of(), List.of())), avecHistorique).get(0));
            }
        }
    }

    private List<FicheAnalyse> completer(Connection c, List<FicheAnalyse> fiches, boolean avecHistorique) throws SQLException {
        if (fiches.isEmpty()) {
            return fiches;
        }
        Map<UUID, List<FicheAnalyse.DomaineRattache>> domaines = new LinkedHashMap<>();
        Map<UUID, List<FicheAnalyse.EvenementHistorique>> historiques = new LinkedHashMap<>();
        java.sql.Array ids = c.createArrayOf("uuid", fiches.stream().map(FicheAnalyse::id).toArray());
        try (PreparedStatement ps = c.prepareStatement("""
                SELECT fd.fiche_id, fd.domaine, dm.libelle, fd.origine, fd.confiance, fd.motif FROM fiche_domaine fd
                LEFT JOIN domaine_metier dm ON dm.espace_id = fd.espace_id AND dm.code = fd.domaine
                WHERE fd.fiche_id = ANY (?) ORDER BY fd.fiche_id, fd.domaine
                """)) {
            ps.setArray(1, ids);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Integer confiance = rs.getObject(5) == null ? null : rs.getInt(5);
                    domaines.computeIfAbsent(rs.getObject(1, UUID.class), x -> new ArrayList<>())
                            .add(new FicheAnalyse.DomaineRattache(rs.getString(2), rs.getString(3), rs.getString(4), confiance,
                                    rs.getString(6)));
                }
            }
        }
        if (avecHistorique) {
            try (PreparedStatement ps = c.prepareStatement("""
                    SELECT fiche_id, action, detail::text, auteur, le FROM fiche_historique
                    WHERE fiche_id = ANY (?) ORDER BY le, id
                    """)) {
                ps.setArray(1, ids);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        historiques.computeIfAbsent(rs.getObject(1, UUID.class), x -> new ArrayList<>())
                                .add(new FicheAnalyse.EvenementHistorique(rs.getString(2), rs.getString(3),
                                        rs.getObject(4, UUID.class), rs.getTimestamp(5).toInstant()));
                    }
                }
            }
        }
        List<FicheAnalyse> resultat = new ArrayList<>();
        for (FicheAnalyse f : fiches) {
            resultat.add(new FicheAnalyse(f.id(), f.espaceId(), f.texte(), f.etatValidation(), f.etatSuivi(), f.libelleSuivi(),
                    f.resume(), f.resumeOrigine(), f.mentionIa(), f.dateEffet(), f.dateEffetOrigine(), f.dateEffetLibelle(),
                    f.motifDateEffet(), f.dateEffetIa(), f.articlesModifies(), domaines.getOrDefault(f.id(), List.of()),
                    f.demande(), f.decideLe(), f.peutExpertiser(), f.mentionValidation(), f.mentionAvisJuridique(),
                    f.mentionSource(), avecHistorique ? historiques.getOrDefault(f.id(), List.of()) : List.of()));
        }
        return resultat;
    }

    private FicheAnalyse fiche(ResultSet rs, List<FicheAnalyse.DomaineRattache> domaines,
            List<FicheAnalyse.EvenementHistorique> historique) throws SQLException {
        UUID id = rs.getObject(1, UUID.class);
        UUID espace = rs.getObject(2, UUID.class);
        String validation = rs.getString(3);
        CycleVie.Etat suivi = CycleVie.Etat.valueOf(rs.getString(4));
        String resume = rs.getString(5);
        String resumeOrigine = rs.getString(6);
        LocalDate effet = rs.getObject(7, LocalDate.class);
        String effetOrigine = rs.getString(8);
        LocalDate effetIa = rs.getObject(9, LocalDate.class);
        List<String> articles = TextesOfficiels.tableau(rs.getArray(10));
        String demandeUrl = rs.getString(11);
        String demandeForge = rs.getString(12);
        String demandeEtat = rs.getString(13);
        Timestamp decide = rs.getTimestamp(14);
        boolean expert = rs.getBoolean(15);
        TexteOfficiel t = TextesOfficiels.texte(rs, 16);
        LocalDate miseAJour = FicheAnalyse.dateMiseAJour(rs.getString(31), instant(rs.getTimestamp(32)));
        String base = legifrance.replaceAll("/+$", "");
        FicheAnalyse.Texte texte = new FicheAnalyse.Texte(t.identifiant(), t.nature(), t.numero(), t.nor(), t.ministere(),
                t.titre(), t.datePublication(), t.codes(), base + "/jorf/id/" + t.identifiant(),
                base + "/loda/id/" + t.identifiant());
        String mentionValidation = switch (validation) {
            case "VALIDEE" -> FicheAnalyse.MENTION_VALIDEE;
            case "REJETEE" -> "rejetée par l'expert métier : non diffusée";
            default -> "en attente de validation par l'expert métier : non diffusée";
        };
        String motif = "TEXTE".equals(effetOrigine) ? t.motifDateEffet()
                : "EXPERT".equals(effetOrigine) ? "date corrigée par l'expert métier" : null;
        return new FicheAnalyse(id, espace, texte, validation, suivi, CycleVie.libelle(suivi), resume, resumeOrigine,
                "IA".equals(resumeOrigine) ? ReponseIa.MENTION : null, effet, effetOrigine,
                effet == null ? DatesTexte.DATE_A_CONFIRMER : effet.toString(), motif, effetIa, articles, domaines,
                demandeUrl == null ? null : new FicheAnalyse.Demande(demandeUrl, demandeForge, demandeEtat),
                decide == null ? null : decide.toInstant(), expert, mentionValidation, FicheAnalyse.AVIS_JURIDIQUE,
                FicheAnalyse.mentionSource(miseAJour), historique);
    }

    private static void date(PreparedStatement ps, int i, LocalDate d) throws SQLException {
        if (d == null) {
            ps.setNull(i, Types.DATE);
        } else {
            ps.setDate(i, Date.valueOf(d));
        }
    }

    static Instant instant(Timestamp t) {
        return t == null ? null : t.toInstant();
    }
}
