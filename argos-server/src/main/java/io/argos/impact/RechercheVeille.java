package io.argos.impact;

import java.sql.Array;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.Instant;
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

import io.argos.commun.ContexteBase;
import io.argos.commun.Conversions;
import io.argos.commun.Jdbc;
import io.argos.signal.SignalQueries;

/**
 * Recherche plein texte et filtres combinables sur les éléments de veille (RCH-01 à RCH-03,
 * ADR-0015) : signaux de sécurité publics et actualités web cloisonnées, avec les projets visibles
 * qu'ils concernent. Une seule requête sert le portail, le client desktop (API REST) et l'outil MCP
 * {@code search_signals}.
 *
 * <p>Toute la recherche s'exécute sous le rôle {@code argos_app} avec le compte de l'appelant
 * ({@link ContexteBase}). La sélection des candidats passe par la fonction
 * {@code argos_selectionner_veille} (migration V28), qui applique explicitement les règles de
 * visibilité des politiques RLS pour que l'index GIN reste utilisable et renvoie au plus
 * {@value #PLAFOND} + 1 candidats, les plus récents ; la pertinence n'est calculée que pour les
 * {@value #PLAFOND} plus récents, le total est plafonné à {@value #PLAFOND} (RCH-07). Chaque élément
 * restitué est ensuite relu sous la RLS, qui écarte tout élément que l'appelant ne voit pas. Les
 * projets cités sont limités, en plus, aux espaces de l'appelant.
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

    /** Nombre maximal de candidats classés par pertinence, et plafond du total restitué (RCH-07). */
    public static final int PLAFOND = 1000;
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

    /**
     * Page de résultats, du plus pertinent au moins pertinent (puis du plus récent au plus ancien). {@code total} vaut
     * au plus {@value #PLAFOND} ; {@code totalPlafonne} est vrai quand plus de {@value #PLAFOND} éléments
     * correspondent (RCH-07).
     */
    public record Resultats(List<Element> elements, int page, int taille, long total, boolean totalPlafonne,
            String message) {
    }

    private record Candidat(String type, UUID id, double rang) {
    }

    /** Candidat de la page et nombre de candidats sélectionnés (au plus {@value #PLAFOND} + 1), répété sur chaque ligne. */
    private record LigneCandidat(Candidat candidat, long selectionnes) {
    }

    /**
     * Candidats bornés ({@value #PLAFOND} + 1 au plus, les plus récents), pertinence calculée sur les {@value #PLAFOND}
     * plus récents seulement, puis page classée : pertinence, date, identifiant (RCH-01). Le nombre de candidats
     * sélectionnés, répété sur chaque ligne, donne le total plafonné.
     */
    private static final String PAGE = """
            WITH candidats AS (
                SELECT c.type, c.id, c.date_ref FROM argos_selectionner_veille(argos_requete_recherche(?), ?::timestamptz,
                     ?::varchar[], ?::uuid[], argos_requete_recherche(?), ?::varchar[], ?::varchar[], ?::varchar[],
                     ?::varchar[], ?::varchar[], ?) c
                ORDER BY c.date_ref DESC NULLS LAST, c.id LIMIT ?),
            classes AS (
                SELECT r.type, r.id, r.date_ref,
                       CASE WHEN q.requete IS NULL THEN 0::real
                            ELSE coalesce(ts_rank(s.recherche, q.requete), ts_rank(a.recherche, q.requete), 0::real) END AS rang
                FROM (SELECT * FROM candidats ORDER BY date_ref DESC NULLS LAST, id LIMIT ?) r
                CROSS JOIN (SELECT argos_requete_recherche(?) AS requete) q
                LEFT JOIN signal s ON r.type = 'AVIS_SECURITE' AND s.id = r.id
                LEFT JOIN actualite a ON r.type <> 'AVIS_SECURITE' AND a.id = r.id)
            SELECT type, id, rang, (SELECT count(*) FROM candidats) FROM classes
            ORDER BY rang DESC, date_ref DESC NULLS LAST, id LIMIT ? OFFSET ?
            """;

    private static final String UUID_SQL = "uuid";

    /** Projets visibles touchés par les avis : identifiants des avis, espaces de l'appelant. */
    private static final String PROJETS_DES_SIGNAUX = """
            SELECT DISTINCT i.signal_id, p.id, p.name FROM project_impact i JOIN project p ON p.id = i.project_id
            WHERE i.signal_id = ANY (?) AND i.workspace_id = ANY (?) ORDER BY p.name, p.id
            """;

    /** Projets visibles touchés par les actualités : identifiants des actualités, espaces de l'appelant. */
    private static final String PROJETS_DES_ACTUALITES = """
            SELECT DISTINCT ai.actualite_id, p.id, p.name FROM actualite_impact ai JOIN project p ON p.id = ai.project_id
            WHERE ai.actualite_id = ANY (?) AND ai.workspace_id = ANY (?) ORDER BY p.name, p.id
            """;

    /** Avis de la page : longueur de l'extrait, identifiants. */
    private static final String SIGNAUX = """
            SELECT id, source, external_id, title, left(summary, ?), severity, coalesce(published_at, modified_at)
            FROM signal WHERE id = ANY (?)
            """;

    /**
     * Actualités de la page : longueur de l'extrait, identifiants. Lecture sous la RLS : une actualité que l'appelant
     * ne voit pas n'est pas restituée.
     */
    private static final String ACTUALITES = """
            SELECT id, type, titre, left(texte, ?), lien, coalesce(publie_le, recu_le) FROM actualite WHERE id = ANY (?)
            """;

    /** Nombre de candidats sélectionnés (au plus {@value #PLAFOND} + 1), pour une page au-delà des résultats. */
    private static final String NOMBRE = """
            SELECT count(*) FROM argos_selectionner_veille(argos_requete_recherche(?), ?::timestamptz, ?::varchar[],
                 ?::uuid[], argos_requete_recherche(?), ?::varchar[], ?::varchar[], ?::varchar[], ?::varchar[],
                 ?::varchar[], ?) c
            """;

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
        int pageBornee = Math.max(page, 0);
        int tailleBornee = Math.min(Math.max(taille, 1), TAILLE_MAX);
        if (criteres.types() != null && criteres.types().isEmpty()) {
            return new Resultats(List.of(), pageBornee, tailleBornee, 0, false, AUCUN_RESULTAT);
        }
        try (Connection connexion = dataSource.getConnection()) {
            ContexteBase.appliquer(connexion, ContexteBase.Contexte.duCompte(compteId));
            List<LigneCandidat> lignes = Jdbc.lister(connexion, PAGE, requete -> {
                int suivant = parametres(connexion, requete, criteres);
                requete.setInt(suivant, PLAFOND + 1);
                requete.setInt(suivant + 1, PLAFOND + 1);
                requete.setInt(suivant + 2, PLAFOND);
                requete.setString(suivant + 3, texte(criteres));
                requete.setInt(suivant + 4, tailleBornee);
                requete.setLong(suivant + 5, (long) pageBornee * tailleBornee);
            }, resultat -> new LigneCandidat(
                    new Candidat(resultat.getString(1), resultat.getObject(2, UUID.class), resultat.getDouble(3)),
                    resultat.getLong(4)));
            List<Candidat> candidats = lignes.stream().map(LigneCandidat::candidat).toList();
            long selectionnes = lignes.isEmpty() ? 0 : lignes.get(lignes.size() - 1).selectionnes();
            if (candidats.isEmpty() && pageBornee > 0) {
                selectionnes = Jdbc.unique(connexion, NOMBRE, requete -> {
                    int suivant = parametres(connexion, requete, criteres);
                    requete.setInt(suivant, PLAFOND + 1);
                }, resultat -> resultat.getLong(1)).orElse(0L);
            }
            long total = Math.min(selectionnes, PLAFOND);
            List<Element> elements = restituer(connexion, candidats, espaces);
            return new Resultats(elements, pageBornee, tailleBornee, total, selectionnes > PLAFOND,
                    total == 0 ? AUCUN_RESULTAT : null);
        } catch (SQLException erreur) {
            throw new IllegalStateException("recherche de veille impossible", erreur);
        }
    }

    /** Mots recherchés nettoyés, nuls s'ils sont absents. */
    private static String texte(Criteres criteres) {
        return vide(criteres.texte()) ? null : criteres.texte().strip();
    }

    /** Lie les critères aux dix paramètres de la sélection ; renvoie l'indice du paramètre suivant. */
    private static int parametres(Connection connexion, PreparedStatement requete, Criteres criteres) throws SQLException {
        requete.setString(1, texte(criteres));
        if (criteres.depuis() == null) {
            requete.setNull(2, Types.TIMESTAMP_WITH_TIMEZONE);
        } else {
            requete.setTimestamp(2, Timestamp.from(criteres.depuis()));
        }
        tableau(connexion, requete, 3, "varchar", criteres.types());
        tableau(connexion, requete, 4, "uuid", criteres.projets());
        Set<String> technologies = minuscules(criteres.technologies());
        if (technologies.isEmpty()) {
            requete.setString(5, null);
            tableau(connexion, requete, 6, "varchar", null);
            tableau(connexion, requete, 7, "varchar", null);
        } else {
            requete.setString(5, String.join(" or ", technologies.stream()
                    .map(tech -> "\"" + tech.replace("\"", " ") + "\"").toList()));
            Set<String> termes = new LinkedHashSet<>();
            Set<String> ecosystemes = new LinkedHashSet<>();
            for (String tech : technologies) {
                termes.addAll(SignalQueries.technologyTerms(tech));
                SignalQueries.ecosystemsOf(tech).forEach(ecosysteme -> ecosystemes.add(ecosysteme.toLowerCase(Locale.ROOT)));
            }
            tableau(connexion, requete, 6, "varchar", termes);
            tableau(connexion, requete, 7, "varchar", ecosystemes);
        }
        tableau(connexion, requete, 8, "varchar", criteres.gravites());
        tableau(connexion, requete, 9, "varchar", criteres.paquets());
        tableau(connexion, requete, 10, "varchar", criteres.sources());
        return 11;
    }

    /** Relit sous la RLS les éléments de la page, dans l'ordre de pertinence, avec leurs projets visibles. */
    private static List<Element> restituer(Connection connexion, List<Candidat> candidats, Set<UUID> espaces) {
        List<UUID> signaux = candidats.stream().filter(candidat -> AVIS_SECURITE.equals(candidat.type())).map(Candidat::id).toList();
        List<UUID> actualites = candidats.stream().filter(candidat -> !AVIS_SECURITE.equals(candidat.type())).map(Candidat::id).toList();
        Map<UUID, Element> lus = new HashMap<>();
        Map<UUID, List<ProjetVise>> projets = new HashMap<>();
        if (!signaux.isEmpty()) {
            projets.putAll(projets(connexion, PROJETS_DES_SIGNAUX, signaux, espaces));
            lire(connexion, SIGNAUX, signaux, RechercheVeille::signal, lus);
        }
        if (!actualites.isEmpty()) {
            projets.putAll(projets(connexion, PROJETS_DES_ACTUALITES, actualites, espaces));
            lire(connexion, ACTUALITES, actualites, RechercheVeille::actualite, lus);
        }
        List<Element> elements = new ArrayList<>();
        for (Candidat candidat : candidats) {
            Element element = lus.get(candidat.id());
            if (element != null) {
                elements.add(new Element(element.type(), element.id(), element.source(), element.identifiant(), element.titre(),
                        element.extrait(), element.gravite(), element.date(), element.lien(), candidat.rang(),
                        projets.getOrDefault(candidat.id(), List.of())));
            }
        }
        return elements;
    }

    private static Map<UUID, List<ProjetVise>> projets(Connection connexion, String sql, List<UUID> ids, Set<UUID> espaces) {
        Map<UUID, List<ProjetVise>> projets = new LinkedHashMap<>();
        if (espaces.isEmpty()) {
            return projets;
        }
        Jdbc.lister(connexion, sql, Jdbc.parametres(new Jdbc.Tableau(UUID_SQL, ids), new Jdbc.Tableau(UUID_SQL, espaces)),
                resultat -> Map.entry(resultat.getObject(1, UUID.class),
                new ProjetVise(resultat.getObject(2, UUID.class), resultat.getString(3))))
                .forEach(ligne -> projets.computeIfAbsent(ligne.getKey(), cle -> new ArrayList<>()).add(ligne.getValue()));
        return projets;
    }

    /** Ajoute à {@code lus} les éléments relus par {@code sql} (extrait borné, identifiants), indexés par identifiant. */
    private static void lire(Connection connexion, String sql, List<UUID> ids, Jdbc.Lecture<Element> lecture,
            Map<UUID, Element> lus) {
        Jdbc.lister(connexion, sql, Jdbc.parametres(EXTRAIT_MAX, new Jdbc.Tableau(UUID_SQL, ids)), lecture)
                .forEach(element -> lus.put(element.id(), element));
    }

    /** Avis de sécurité lu par {@link #SIGNAUX}, sans rang ni projet. */
    private static Element signal(ResultSet resultat) throws SQLException {
        return new Element(AVIS_SECURITE, resultat.getObject(1, UUID.class), resultat.getString(2), resultat.getString(3),
                resultat.getString(4), resultat.getString(5), resultat.getString(6), Conversions.instant(resultat, 7), null, 0,
                List.of());
    }

    /** Actualité lue par {@link #ACTUALITES}, sans rang ni projet. */
    private static Element actualite(ResultSet resultat) throws SQLException {
        return new Element(resultat.getString(2), resultat.getObject(1, UUID.class), "WEB", null, resultat.getString(3),
                resultat.getString(4), null, Conversions.instant(resultat, 6), lienSur(resultat.getString(5)), 0, List.of());
    }

    /** Lien restituable : seules les adresses absolues http et https le sont (XSS, R-11). */
    static String lienSur(String lien) {
        return lien != null && LIEN_HTTP.matcher(lien.strip()).matches() ? lien.strip() : null;
    }

    private static void tableau(Connection connexion, PreparedStatement requete, int indice, String type, Collection<?> valeurs)
            throws SQLException {
        if (valeurs == null || valeurs.isEmpty()) {
            requete.setNull(indice, Types.ARRAY);
        } else {
            Array tableau = connexion.createArrayOf(type, valeurs.toArray());
            requete.setArray(indice, tableau);
        }
    }

    private static Set<String> minuscules(Set<String> valeurs) {
        Set<String> resultat = new LinkedHashSet<>();
        if (valeurs != null) {
            for (String valeur : valeurs) {
                if (!vide(valeur)) {
                    resultat.add(valeur.strip().toLowerCase(Locale.ROOT));
                }
            }
        }
        return resultat;
    }

    private static boolean vide(String texte) {
        return texte == null || texte.isBlank();
    }
}
