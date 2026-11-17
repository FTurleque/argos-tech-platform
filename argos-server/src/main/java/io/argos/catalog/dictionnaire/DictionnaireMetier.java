package io.argos.catalog.dictionnaire;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import javax.sql.DataSource;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import io.argos.workspace.ContexteBase;
import io.argos.workspace.ContexteBase.Contexte;
import io.argos.workspace.DonneeInvalide;
import io.argos.workspace.Introuvable;

/**
 * Domaines métier, experts et dictionnaire de chaque espace (module {@code catalog}, décision D1 du
 * changement {@code suivre-veille-reglementaire} ; tables de la migration V16). Aucun dictionnaire type
 * n'est fourni à la création d'un espace (Q-4).
 *
 * <p>Les lectures et écritures d'un utilisateur s'exécutent sous le rôle {@code argos_app}
 * ({@link ContexteBase}) : la RLS n'expose que les espaces dont il est membre et n'autorise l'écriture
 * qu'à leurs administrateurs. Un expert est désigné parmi les membres CONTRIBUTEUR ou ADMINISTRATEUR ;
 * il perd ce statut dès qu'il quitte l'espace ou y devient lecteur.
 *
 * <p>{@link #dictionnaires()} et {@link #experts} sont des lectures techniques, hors rôle applicatif,
 * réservées à la qualification des textes collectés et aux rappels planifiés.
 */
@ApplicationScoped
public class DictionnaireMetier {

    /** Nombre maximal de règles par domaine et de domaines par espace. */
    public static final int REGLES_MAX = 200;
    public static final int DOMAINES_MAX = 100;
    public static final int EXPERTS_MAX = 50;

    /** Type de règle de rattachement déterministe (REG-02). */
    public enum TypeRegle {
        /** Nature du texte (décret, arrêté, loi, ordonnance…), comparée sans tenir compte de la casse. */
        NATURE,
        /** Ministère ou autorité : la valeur doit figurer dans le ministère du texte. */
        MINISTERE,
        /** Préfixe du numéro NOR (par exemple {@code TFPF} : ministère et direction). */
        NOR,
        /** Code consolidé modifié : identifiant {@code LEGITEXT} ou libellé exact du code. */
        CODE,
        /** Mot ou expression du titre, sans tenir compte de la casse ni des accents. */
        MOT_CLE
    }

    /** Règle de rattachement. */
    public record Regle(TypeRegle type, String valeur) {
    }

    /** Expert d'un domaine. */
    public record Expert(UUID compteId, String email) {
    }

    /** Domaine métier d'un espace, avec ses règles et ses experts désignés. */
    public record Domaine(String code, String libelle, List<Regle> regles, List<Expert> experts) {
        public Domaine {
            regles = List.copyOf(regles);
            experts = List.copyOf(experts);
        }
    }

    /** Dictionnaire d'un espace, pour la qualification. */
    public record DictionnaireEspace(UUID espaceId, List<Domaine> domaines) {
        public DictionnaireEspace {
            domaines = List.copyOf(domaines);
        }
    }

    @Inject
    ContexteBase base;

    @Inject
    DataSource dataSource;

    // ------------------------------------------------------------------ lectures d'un membre

    /** Domaines de l'espace, visibles de tout membre. */
    public List<Domaine> domaines(UUID compte, UUID espace) {
        return base.executer(new Contexte(compte, null, espace), c -> lire(c, List.of(espace)).getOrDefault(espace, List.of()));
    }

    // ------------------------------------------------------------------ écritures d'un administrateur

    /** Crée le domaine ou change son libellé. */
    public Domaine enregistrerDomaine(UUID compte, UUID espace, String code, String libelle) {
        String c0 = code(code);
        String l = libelle == null ? "" : libelle.strip();
        if (l.isEmpty() || l.length() > 200) {
            throw new DonneeInvalide("DONNEE_INVALIDE", "Indiquez un libellé de 1 à 200 caractères.", "libelle");
        }
        return base.executer(new Contexte(compte, null, espace), c -> {
            if (compter(c, espace) >= DOMAINES_MAX && trouver(c, espace, c0) == null) {
                throw new DonneeInvalide("DONNEE_INVALIDE", DOMAINES_MAX + " domaines au plus par espace.", "code");
            }
            try (PreparedStatement ps = c.prepareStatement("""
                    INSERT INTO domaine_metier (espace_id, code, libelle, modifie_par, modifie_le)
                    VALUES (?, ?, ?, argos_compte_courant(), ?)
                    ON CONFLICT (espace_id, code) DO UPDATE
                    SET libelle = excluded.libelle, modifie_par = excluded.modifie_par, modifie_le = excluded.modifie_le
                    """)) {
                ps.setObject(1, espace);
                ps.setString(2, c0);
                ps.setString(3, l);
                ps.setTimestamp(4, Timestamp.from(Instant.now()));
                ps.executeUpdate();
            }
            return exiger(c, espace, c0);
        });
    }

    /** Supprime le domaine, ses règles, ses experts et ses rattachements aux fiches. */
    public void supprimerDomaine(UUID compte, UUID espace, String code) {
        String c0 = code(code);
        base.executer(new Contexte(compte, null, espace), c -> {
            try (PreparedStatement ps = c.prepareStatement("DELETE FROM domaine_metier WHERE espace_id = ? AND code = ?")) {
                ps.setObject(1, espace);
                ps.setString(2, c0);
                if (ps.executeUpdate() == 0) {
                    throw new Introuvable("Domaine introuvable.");
                }
            }
            return null;
        });
    }

    /** Remplace les règles du domaine ; chaque valeur est nettoyée et bornée. */
    public Domaine remplacerRegles(UUID compte, UUID espace, String code, List<Regle> regles) {
        String c0 = code(code);
        Set<Regle> valides = valider(regles);
        return base.executer(new Contexte(compte, null, espace), c -> {
            exiger(c, espace, c0);
            try (PreparedStatement ps = c.prepareStatement(
                    "DELETE FROM regle_dictionnaire WHERE espace_id = ? AND domaine = ?")) {
                ps.setObject(1, espace);
                ps.setString(2, c0);
                ps.executeUpdate();
            }
            try (PreparedStatement ps = c.prepareStatement("""
                    INSERT INTO regle_dictionnaire (espace_id, domaine, type, valeur, modifie_par, modifie_le)
                    VALUES (?, ?, ?, ?, argos_compte_courant(), ?)
                    """)) {
                Timestamp maintenant = Timestamp.from(Instant.now());
                for (Regle r : valides) {
                    ps.setObject(1, espace);
                    ps.setString(2, c0);
                    ps.setString(3, r.type().name());
                    ps.setString(4, r.valeur());
                    ps.setTimestamp(5, maintenant);
                    ps.addBatch();
                }
                ps.executeBatch();
            }
            return exiger(c, espace, c0);
        });
    }

    /**
     * Remplace les experts du domaine. Chaque compte doit être membre CONTRIBUTEUR ou ADMINISTRATEUR de
     * l'espace ; sinon la désignation est refusée.
     */
    public Domaine remplacerExperts(UUID compte, UUID espace, String code, List<UUID> experts) {
        String c0 = code(code);
        if (experts == null || experts.size() > EXPERTS_MAX || experts.stream().anyMatch(Objects::isNull)) {
            throw new DonneeInvalide("DONNEE_INVALIDE", "Indiquez de 0 à " + EXPERTS_MAX + " comptes.", "comptes");
        }
        Set<UUID> distincts = new LinkedHashSet<>(experts);
        return base.executer(new Contexte(compte, null, espace), c -> {
            exiger(c, espace, c0);
            for (UUID e : distincts) {
                try (PreparedStatement ps = c.prepareStatement("""
                        SELECT 1 FROM appartenance
                        WHERE espace_id = ? AND compte_id = ? AND role IN ('CONTRIBUTEUR', 'ADMINISTRATEUR')
                        """)) {
                    ps.setObject(1, espace);
                    ps.setObject(2, e);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (!rs.next()) {
                            throw new DonneeInvalide("EXPERT_NON_MEMBRE",
                                    "Un expert doit être membre contributeur ou administrateur de l'espace.", "comptes");
                        }
                    }
                }
            }
            try (PreparedStatement ps = c.prepareStatement("DELETE FROM expert_domaine WHERE espace_id = ? AND domaine = ?")) {
                ps.setObject(1, espace);
                ps.setString(2, c0);
                ps.executeUpdate();
            }
            try (PreparedStatement ps = c.prepareStatement("""
                    INSERT INTO expert_domaine (espace_id, domaine, compte_id, modifie_par, modifie_le)
                    VALUES (?, ?, ?, argos_compte_courant(), ?)
                    """)) {
                Timestamp maintenant = Timestamp.from(Instant.now());
                for (UUID e : distincts) {
                    ps.setObject(1, espace);
                    ps.setString(2, c0);
                    ps.setObject(3, e);
                    ps.setTimestamp(4, maintenant);
                    ps.addBatch();
                }
                ps.executeBatch();
            }
            return exiger(c, espace, c0);
        });
    }

    // ------------------------------------------------------------------ lectures techniques

    /** Dictionnaires de tous les espaces qui ont au moins un domaine (qualification des textes). */
    public List<DictionnaireEspace> dictionnaires() {
        try (Connection c = dataSource.getConnection()) {
            List<UUID> espaces = new ArrayList<>();
            try (PreparedStatement ps = c.prepareStatement("SELECT DISTINCT espace_id FROM domaine_metier ORDER BY espace_id");
                    ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    espaces.add(rs.getObject(1, UUID.class));
                }
            }
            Map<UUID, List<Domaine>> parEspace = lire(c, espaces);
            return espaces.stream().map(e -> new DictionnaireEspace(e, parEspace.getOrDefault(e, List.of()))).toList();
        } catch (SQLException e) {
            throw new IllegalStateException("lecture des dictionnaires métier impossible", e);
        }
    }

    /**
     * Experts effectifs des domaines d'un espace : désignés et encore membres CONTRIBUTEUR ou
     * ADMINISTRATEUR (rappels planifiés).
     */
    public List<Expert> experts(UUID espace, Collection<String> domaines) {
        List<Expert> liste = new ArrayList<>();
        if (domaines.isEmpty()) {
            return liste;
        }
        try (Connection c = dataSource.getConnection(); PreparedStatement ps = c.prepareStatement("""
                SELECT DISTINCT x.compte_id, co.email FROM expert_domaine x
                JOIN appartenance a ON a.espace_id = x.espace_id AND a.compte_id = x.compte_id
                                   AND a.role IN ('CONTRIBUTEUR', 'ADMINISTRATEUR')
                JOIN compte co ON co.id = x.compte_id
                WHERE x.espace_id = ? AND x.domaine = ANY (?) ORDER BY x.compte_id
                """)) {
            ps.setObject(1, espace);
            ps.setArray(2, c.createArrayOf("varchar", domaines.toArray()));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    liste.add(new Expert(rs.getObject(1, UUID.class), rs.getString(2)));
                }
            }
        } catch (SQLException e) {
            throw new IllegalStateException("lecture des experts impossible", e);
        }
        return liste;
    }

    // ------------------------------------------------------------------ outils

    /** Code de domaine : minuscules, chiffres et tirets, 64 caractères au plus. */
    public static String code(String code) {
        String c = code == null ? "" : code.strip();
        if (!c.matches("[a-z0-9][a-z0-9-]{0,63}")) {
            throw new DonneeInvalide("DONNEE_INVALIDE",
                    "Code de domaine invalide : minuscules, chiffres et tirets, 64 caractères au plus.", "code");
        }
        return c;
    }

    static Set<Regle> valider(List<Regle> regles) {
        if (regles == null || regles.size() > REGLES_MAX) {
            throw new DonneeInvalide("DONNEE_INVALIDE", "Indiquez de 0 à " + REGLES_MAX + " règles.", "regles");
        }
        Set<Regle> valides = new LinkedHashSet<>();
        for (Regle r : regles) {
            if (r == null || r.type() == null) {
                throw new DonneeInvalide("DONNEE_INVALIDE", "Chaque règle a un type : NATURE, MINISTERE, NOR, CODE ou MOT_CLE.",
                        "regles");
            }
            String v = r.valeur() == null ? "" : r.valeur().strip().replaceAll("\\s+", " ");
            if (v.isEmpty() || v.length() > 200 || v.chars().anyMatch(Character::isISOControl)) {
                throw new DonneeInvalide("DONNEE_INVALIDE", "Chaque règle a une valeur de 1 à 200 caractères.", "regles");
            }
            if (r.type() == TypeRegle.NOR) {
                v = v.toUpperCase(Locale.ROOT);
                if (!v.matches("[A-Z]{1,4}[A-Z0-9]{0,8}")) {
                    throw new DonneeInvalide("DONNEE_INVALIDE", "Un préfixe NOR commence par des lettres (par exemple TFPF).",
                            "regles");
                }
            }
            if (r.type() == TypeRegle.NATURE) {
                v = v.toUpperCase(Locale.ROOT);
            }
            valides.add(new Regle(r.type(), v));
        }
        return valides;
    }

    private static int compter(Connection c, UUID espace) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("SELECT count(*) FROM domaine_metier WHERE espace_id = ?")) {
            ps.setObject(1, espace);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        }
    }

    private static Domaine exiger(Connection c, UUID espace, String code) throws SQLException {
        Domaine d = trouver(c, espace, code);
        if (d == null) {
            throw new Introuvable("Domaine introuvable.");
        }
        return d;
    }

    private static Domaine trouver(Connection c, UUID espace, String code) throws SQLException {
        return lire(c, List.of(espace)).getOrDefault(espace, List.of()).stream()
                .filter(d -> d.code().equals(code)).findFirst().orElse(null);
    }

    /** Domaines des espaces, avec leurs règles et leurs experts (lus selon le contexte de la connexion). */
    private static Map<UUID, List<Domaine>> lire(Connection c, List<UUID> espaces) throws SQLException {
        Map<UUID, Map<String, String>> libelles = new LinkedHashMap<>();
        Map<String, List<Regle>> regles = new LinkedHashMap<>();
        Map<String, List<Expert>> experts = new LinkedHashMap<>();
        if (espaces.isEmpty()) {
            return Map.of();
        }
        java.sql.Array tableau = c.createArrayOf("uuid", espaces.toArray());
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT espace_id, code, libelle FROM domaine_metier WHERE espace_id = ANY (?) ORDER BY espace_id, code")) {
            ps.setArray(1, tableau);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    libelles.computeIfAbsent(rs.getObject(1, UUID.class), k -> new LinkedHashMap<>())
                            .put(rs.getString(2), rs.getString(3));
                }
            }
        }
        try (PreparedStatement ps = c.prepareStatement("""
                SELECT espace_id, domaine, type, valeur FROM regle_dictionnaire WHERE espace_id = ANY (?)
                ORDER BY espace_id, domaine, type, valeur
                """)) {
            ps.setArray(1, tableau);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    regles.computeIfAbsent(rs.getObject(1, UUID.class) + "|" + rs.getString(2), k -> new ArrayList<>())
                            .add(new Regle(TypeRegle.valueOf(rs.getString(3)), rs.getString(4)));
                }
            }
        }
        try (PreparedStatement ps = c.prepareStatement("""
                SELECT x.espace_id, x.domaine, x.compte_id, co.email FROM expert_domaine x
                LEFT JOIN compte co ON co.id = x.compte_id
                WHERE x.espace_id = ANY (?) ORDER BY x.espace_id, x.domaine, co.email
                """)) {
            ps.setArray(1, tableau);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    experts.computeIfAbsent(rs.getObject(1, UUID.class) + "|" + rs.getString(2), k -> new ArrayList<>())
                            .add(new Expert(rs.getObject(3, UUID.class), rs.getString(4)));
                }
            }
        }
        Map<UUID, List<Domaine>> resultat = new LinkedHashMap<>();
        libelles.forEach((espace, domaines) -> domaines.forEach((code, libelle) -> resultat
                .computeIfAbsent(espace, k -> new ArrayList<>())
                .add(new Domaine(code, libelle, regles.getOrDefault(espace + "|" + code, List.of()),
                        experts.getOrDefault(espace + "|" + code, List.of())))));
        return resultat;
    }
}
