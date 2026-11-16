package io.argos.impact;

import java.sql.Array;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

import javax.sql.DataSource;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import io.argos.signal.SignalQueries;

/**
 * Recherche plein texte et filtres combinables sur les éléments de veille (RCH-01 à RCH-03,
 * ADR-0015) : signaux de sécurité publics et actualités web cloisonnées, avec les projets visibles
 * qu'ils concernent. Une seule requête sert le portail, le client desktop (API REST) et l'outil MCP
 * {@code search_signals}.
 *
 * <p>Toute la recherche s'exécute sous le rôle {@code argos_app} avec le compte de l'appelant
 * ({@link RlsSession}). La sélection des candidats passe par la fonction
 * {@code argos_rechercher_veille} (migration V11), qui applique explicitement les règles de
 * visibilité des politiques RLS pour que l'index GIN reste utilisable ; chaque élément restitué est
 * ensuite relu sous la RLS, qui écarte tout élément que l'appelant ne voit pas. Les projets cités
 * sont limités, en plus, aux espaces de l'appelant.
 *
 * <p>Les mots recherchés sont analysés par les configurations {@code french}, {@code english} et
 * {@code simple} (Q-1) ; ils ne sont jamais concaténés au SQL.
 */
@ApplicationScoped
public class RechercheVeille {

    /** Avis de sécurité (module signal). */
    public static final String AVIS_SECURITE = "AVIS_SECURITE";
    /** Article d'un flux RSS/Atom (veille web). */
    public static final String ARTICLE = "ARTICLE";
    /** Changement d'une page surveillée (veille web). */
    public static final String CHANGEMENT_PAGE = "CHANGEMENT_PAGE";
    /** Types d'éléments cherchés. */
    public static final List<String> TYPES = List.of(AVIS_SECURITE, ARTICLE, CHANGEMENT_PAGE);

    /**
     * Catégorie de sources des profils de veille (manifestes, {@code categoriesSources}) dont relève
     * chaque type d'élément. Les actualités web ne relèvent d'aucune catégorie publiée.
     */
    private static final Map<String, String> CATEGORIES = Map.of(AVIS_SECURITE, "avis-securite");

    /** Message d'une recherche sans résultat (RCH-01-S2). */
    public static final String AUCUN_RESULTAT = "Aucun élément ne correspond à votre recherche.";

    /** Longueur de l'extrait restitué, en caractères. */
    static final int EXTRAIT_MAX = 300;
    private static final int TAILLE_MAX = 100;
    private static final Pattern LIEN_HTTP = Pattern.compile("(?i)^https?://\\S+$");

    /**
     * Critères validés d'une recherche. Un ensemble vide ou {@code null} signifie « filtre absent » ;
     * les filtres se combinent par ET, les valeurs d'un même filtre par OU.
     *
     * @param texte        mots recherchés (syntaxe web : mots, "expression", or, -exclusion)
     * @param types        types d'éléments ({@link #TYPES}) ; ensemble vide non nul : aucun type admis
     * @param projets      projets visibles concernés par un impact ou un rapprochement
     * @param technologies technologies : dans le texte, écosystème ou paquet affecté, technologie rapprochée
     * @param gravites     gravités déclarées admises (majuscules)
     * @param depuis       horizon : éléments datés après cet instant
     * @param paquets      clés de paquet (PURL sans version) affectées
     * @param sources      sources des signaux (OSV…)
     */
    public record Criteres(String texte, Set<String> types, Set<UUID> projets, Set<String> technologies,
            Set<String> gravites, Instant depuis, Set<String> paquets, Set<String> sources) {
    }

    /** Projet visible de l'appelant concerné par l'élément. */
    public record ProjetVise(UUID projetId, String projet) {
    }

    /**
     * Élément trouvé. Le titre, l'extrait et le lien viennent de la source : ce sont des données
     * externes non fiables, à rendre comme texte.
     */
    public record Element(String type, UUID id, String source, String identifiant, String titre, String extrait,
            String gravite, Instant date, String lien, double pertinence, List<ProjetVise> projets) {
    }

    /** Page de résultats, du plus pertinent au moins pertinent (puis du plus récent au plus ancien). */
    public record Resultats(List<Element> elements, int page, int taille, long total, String message) {
    }

    private record Candidat(String type, UUID id, double rang) {
    }

    @Inject
    DataSource dataSource;

    /** Types d'éléments relevant de l'une des catégories de sources données (filtre « profil »). */
    public static Set<String> typesDesCategories(Collection<String> categories) {
        Set<String> types = new LinkedHashSet<>();
        CATEGORIES.forEach((type, categorie) -> {
            if (categories.contains(categorie)) {
                types.add(type);
            }
        });
        return types;
    }

    @Transactional
    public Resultats rechercher(Criteres criteres, UUID compteId, Set<UUID> espaces, int page, int taille) {
        int p = Math.max(page, 0);
        int t = Math.min(Math.max(taille, 1), TAILLE_MAX);
        if (criteres.types() != null && criteres.types().isEmpty()) {
            return new Resultats(List.of(), p, t, 0, AUCUN_RESULTAT);
        }
        try (Connection c = dataSource.getConnection()) {
            RlsSession.account(c, compteId);
            List<Candidat> candidats = new ArrayList<>();
            long total = 0;
            // Sélection matérialisée une fois : le total la compte, la page en garde les meilleurs (tri partiel).
            try (PreparedStatement ps = c.prepareStatement("WITH r AS MATERIALIZED (SELECT * " + selection() + ")"
                    + " SELECT r.type, r.id, r.rang, (SELECT count(*) FROM r) FROM r"
                    + " ORDER BY r.rang DESC, r.date_ref DESC NULLS LAST, r.id LIMIT ? OFFSET ?")) {
                int n = parametres(c, ps, criteres);
                ps.setInt(n, t);
                ps.setLong(n + 1, (long) p * t);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        candidats.add(new Candidat(rs.getString(1), rs.getObject(2, UUID.class), rs.getDouble(3)));
                        total = rs.getLong(4);
                    }
                }
            }
            if (candidats.isEmpty() && p > 0) {
                try (PreparedStatement ps = c.prepareStatement("SELECT count(*) " + selection())) {
                    parametres(c, ps, criteres);
                    try (ResultSet rs = ps.executeQuery()) {
                        rs.next();
                        total = rs.getLong(1);
                    }
                }
            }
            List<Element> elements = restituer(c, candidats, espaces);
            return new Resultats(elements, p, t, total, total == 0 ? AUCUN_RESULTAT : null);
        } catch (SQLException e) {
            throw new IllegalStateException("recherche de veille impossible", e);
        }
    }

    private static String selection() {
        return """
                FROM argos_rechercher_veille(argos_requete_recherche(?), ?::timestamptz, ?::varchar[], ?::uuid[],
                     argos_requete_recherche(?), ?::varchar[], ?::varchar[], ?::varchar[], ?::varchar[], ?::varchar[]) r
                """;
    }

    /** Lie les critères aux dix paramètres de la sélection ; renvoie l'indice du paramètre suivant. */
    private static int parametres(Connection c, PreparedStatement ps, Criteres k) throws SQLException {
        ps.setString(1, vide(k.texte()) ? null : k.texte().strip());
        if (k.depuis() == null) {
            ps.setNull(2, Types.TIMESTAMP_WITH_TIMEZONE);
        } else {
            ps.setTimestamp(2, Timestamp.from(k.depuis()));
        }
        tableau(c, ps, 3, "varchar", k.types());
        tableau(c, ps, 4, "uuid", k.projets());
        Set<String> technologies = minuscules(k.technologies());
        if (technologies.isEmpty()) {
            ps.setString(5, null);
            tableau(c, ps, 6, "varchar", null);
            tableau(c, ps, 7, "varchar", null);
        } else {
            ps.setString(5, String.join(" or ", technologies.stream()
                    .map(tech -> "\"" + tech.replace("\"", " ") + "\"").toList()));
            Set<String> termes = new LinkedHashSet<>();
            Set<String> ecosystemes = new LinkedHashSet<>();
            for (String tech : technologies) {
                termes.addAll(SignalQueries.technologyTerms(tech));
                SignalQueries.ecosystemsOf(tech).forEach(e -> ecosystemes.add(e.toLowerCase(Locale.ROOT)));
            }
            tableau(c, ps, 6, "varchar", termes);
            tableau(c, ps, 7, "varchar", ecosystemes);
        }
        tableau(c, ps, 8, "varchar", k.gravites());
        tableau(c, ps, 9, "varchar", k.paquets());
        tableau(c, ps, 10, "varchar", k.sources());
        return 11;
    }

    /** Relit sous la RLS les éléments de la page, dans l'ordre de pertinence, avec leurs projets visibles. */
    private static List<Element> restituer(Connection c, List<Candidat> candidats, Set<UUID> espaces) throws SQLException {
        List<UUID> signaux = candidats.stream().filter(k -> AVIS_SECURITE.equals(k.type())).map(Candidat::id).toList();
        List<UUID> actualites = candidats.stream().filter(k -> !AVIS_SECURITE.equals(k.type())).map(Candidat::id).toList();
        Map<UUID, Element> lus = new HashMap<>();
        Map<UUID, List<ProjetVise>> projets = new HashMap<>();
        if (!signaux.isEmpty()) {
            projets.putAll(projets(c, """
                    SELECT DISTINCT i.signal_id, p.id, p.name FROM project_impact i JOIN project p ON p.id = i.project_id
                    WHERE i.signal_id = ANY (?) AND i.workspace_id = ANY (?) ORDER BY p.name, p.id
                    """, signaux, espaces));
            try (PreparedStatement ps = c.prepareStatement("""
                    SELECT id, source, external_id, title, left(summary, ?), severity, coalesce(published_at, modified_at)
                    FROM signal WHERE id = ANY (?)
                    """)) {
                ps.setInt(1, EXTRAIT_MAX);
                ps.setArray(2, c.createArrayOf("uuid", signaux.toArray()));
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        UUID id = rs.getObject(1, UUID.class);
                        lus.put(id, new Element(AVIS_SECURITE, id, rs.getString(2), rs.getString(3), rs.getString(4),
                                rs.getString(5), rs.getString(6), instant(rs, 7), null, 0, List.of()));
                    }
                }
            }
        }
        if (!actualites.isEmpty()) {
            projets.putAll(projets(c, """
                    SELECT DISTINCT ai.actualite_id, p.id, p.name FROM actualite_impact ai JOIN project p ON p.id = ai.project_id
                    WHERE ai.actualite_id = ANY (?) AND ai.workspace_id = ANY (?) ORDER BY p.name, p.id
                    """, actualites, espaces));
            // Lecture sous la RLS : une actualité que l'appelant ne voit pas n'est pas restituée.
            try (PreparedStatement ps = c.prepareStatement("""
                    SELECT id, type, titre, left(texte, ?), lien, coalesce(publie_le, recu_le) FROM actualite WHERE id = ANY (?)
                    """)) {
                ps.setInt(1, EXTRAIT_MAX);
                ps.setArray(2, c.createArrayOf("uuid", actualites.toArray()));
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        UUID id = rs.getObject(1, UUID.class);
                        lus.put(id, new Element(rs.getString(2), id, "WEB", null, rs.getString(3), rs.getString(4), null,
                                instant(rs, 6), lienSur(rs.getString(5)), 0, List.of()));
                    }
                }
            }
        }
        List<Element> elements = new ArrayList<>();
        for (Candidat k : candidats) {
            Element e = lus.get(k.id());
            if (e != null) {
                elements.add(new Element(e.type(), e.id(), e.source(), e.identifiant(), e.titre(), e.extrait(),
                        e.gravite(), e.date(), e.lien(), k.rang(), projets.getOrDefault(k.id(), List.of())));
            }
        }
        return elements;
    }

    private static Map<UUID, List<ProjetVise>> projets(Connection c, String sql, List<UUID> ids, Set<UUID> espaces)
            throws SQLException {
        Map<UUID, List<ProjetVise>> projets = new LinkedHashMap<>();
        if (espaces.isEmpty()) {
            return projets;
        }
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setArray(1, c.createArrayOf("uuid", ids.toArray()));
            ps.setArray(2, c.createArrayOf("uuid", espaces.toArray()));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    projets.computeIfAbsent(rs.getObject(1, UUID.class), k -> new ArrayList<>())
                            .add(new ProjetVise(rs.getObject(2, UUID.class), rs.getString(3)));
                }
            }
        }
        return projets;
    }

    /** Lien restituable : seules les adresses absolues http et https le sont (XSS, R-11). */
    static String lienSur(String lien) {
        return lien != null && LIEN_HTTP.matcher(lien.strip()).matches() ? lien.strip() : null;
    }

    private static void tableau(Connection c, PreparedStatement ps, int indice, String type, Collection<?> valeurs)
            throws SQLException {
        if (valeurs == null || valeurs.isEmpty()) {
            ps.setNull(indice, Types.ARRAY);
        } else {
            Array a = c.createArrayOf(type, valeurs.toArray());
            ps.setArray(indice, a);
        }
    }

    private static Set<String> minuscules(Set<String> valeurs) {
        Set<String> resultat = new LinkedHashSet<>();
        if (valeurs != null) {
            for (String v : valeurs) {
                if (!vide(v)) {
                    resultat.add(v.strip().toLowerCase(Locale.ROOT));
                }
            }
        }
        return resultat;
    }

    private static boolean vide(String s) {
        return s == null || s.isBlank();
    }

    private static Instant instant(ResultSet rs, int column) throws SQLException {
        OffsetDateTime value = rs.getObject(column, OffsetDateTime.class);
        return value == null ? null : value.toInstant();
    }
}
