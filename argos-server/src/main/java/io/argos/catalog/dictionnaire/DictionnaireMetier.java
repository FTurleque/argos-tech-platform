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
        return base.executer(new Contexte(compte, null, espace),
                connexion -> lire(connexion, List.of(espace)).getOrDefault(espace, List.of()));
    }

    // ------------------------------------------------------------------ écritures d'un administrateur

    /** Crée le domaine ou change son libellé. */
    public Domaine enregistrerDomaine(UUID compte, UUID espace, String code, String libelle) {
        String codeNormalise = code(code);
        String libelleNormalise = libelle == null ? "" : libelle.strip();
        if (libelleNormalise.isEmpty() || libelleNormalise.length() > 200) {
            throw new DonneeInvalide("DONNEE_INVALIDE", "Indiquez un libellé de 1 à 200 caractères.", "libelle");
        }
        return base.executer(new Contexte(compte, null, espace), connexion -> {
            if (compter(connexion, espace) >= DOMAINES_MAX && trouver(connexion, espace, codeNormalise) == null) {
                throw new DonneeInvalide("DONNEE_INVALIDE", DOMAINES_MAX + " domaines au plus par espace.", "code");
            }
            try (PreparedStatement requete = connexion.prepareStatement("""
                    INSERT INTO domaine_metier (espace_id, code, libelle, modifie_par, modifie_le)
                    VALUES (?, ?, ?, argos_compte_courant(), ?)
                    ON CONFLICT (espace_id, code) DO UPDATE
                    SET libelle = excluded.libelle, modifie_par = excluded.modifie_par, modifie_le = excluded.modifie_le
                    """)) {
                requete.setObject(1, espace);
                requete.setString(2, codeNormalise);
                requete.setString(3, libelleNormalise);
                requete.setTimestamp(4, Timestamp.from(Instant.now()));
                requete.executeUpdate();
            }
            return exiger(connexion, espace, codeNormalise);
        });
    }

    /** Supprime le domaine, ses règles, ses experts et ses rattachements aux fiches. */
    public void supprimerDomaine(UUID compte, UUID espace, String code) {
        String codeNormalise = code(code);
        base.executer(new Contexte(compte, null, espace), connexion -> {
            try (PreparedStatement requete = connexion.prepareStatement("DELETE FROM domaine_metier WHERE espace_id = ? AND code = ?")) {
                requete.setObject(1, espace);
                requete.setString(2, codeNormalise);
                if (requete.executeUpdate() == 0) {
                    throw new Introuvable("Domaine introuvable.");
                }
            }
            return null;
        });
    }

    /** Remplace les règles du domaine ; chaque valeur est nettoyée et bornée. */
    public Domaine remplacerRegles(UUID compte, UUID espace, String code, List<Regle> regles) {
        String codeNormalise = code(code);
        Set<Regle> valides = valider(regles);
        return base.executer(new Contexte(compte, null, espace), connexion -> {
            exiger(connexion, espace, codeNormalise);
            try (PreparedStatement requete = connexion.prepareStatement(
                    "DELETE FROM regle_dictionnaire WHERE espace_id = ? AND domaine = ?")) {
                requete.setObject(1, espace);
                requete.setString(2, codeNormalise);
                requete.executeUpdate();
            }
            try (PreparedStatement requete = connexion.prepareStatement("""
                    INSERT INTO regle_dictionnaire (espace_id, domaine, type, valeur, modifie_par, modifie_le)
                    VALUES (?, ?, ?, ?, argos_compte_courant(), ?)
                    """)) {
                Timestamp maintenant = Timestamp.from(Instant.now());
                for (Regle regle : valides) {
                    requete.setObject(1, espace);
                    requete.setString(2, codeNormalise);
                    requete.setString(3, regle.type().name());
                    requete.setString(4, regle.valeur());
                    requete.setTimestamp(5, maintenant);
                    requete.addBatch();
                }
                requete.executeBatch();
            }
            return exiger(connexion, espace, codeNormalise);
        });
    }

    /**
     * Remplace les experts du domaine. Chaque compte doit être membre CONTRIBUTEUR ou ADMINISTRATEUR de
     * l'espace ; sinon la désignation est refusée.
     */
    public Domaine remplacerExperts(UUID compte, UUID espace, String code, List<UUID> experts) {
        String codeNormalise = code(code);
        if (experts == null || experts.size() > EXPERTS_MAX || experts.stream().anyMatch(Objects::isNull)) {
            throw new DonneeInvalide("DONNEE_INVALIDE", "Indiquez de 0 à " + EXPERTS_MAX + " comptes.", "comptes");
        }
        Set<UUID> distincts = new LinkedHashSet<>(experts);
        return base.executer(new Contexte(compte, null, espace), connexion -> {
            exiger(connexion, espace, codeNormalise);
            for (UUID expert : distincts) {
                try (PreparedStatement requete = connexion.prepareStatement("""
                        SELECT 1 FROM appartenance
                        WHERE espace_id = ? AND compte_id = ? AND role IN ('CONTRIBUTEUR', 'ADMINISTRATEUR')
                        """)) {
                    requete.setObject(1, espace);
                    requete.setObject(2, expert);
                    try (ResultSet resultat = requete.executeQuery()) {
                        if (!resultat.next()) {
                            throw new DonneeInvalide("EXPERT_NON_MEMBRE",
                                    "Un expert doit être membre contributeur ou administrateur de l'espace.", "comptes");
                        }
                    }
                }
            }
            try (PreparedStatement requete = connexion.prepareStatement("DELETE FROM expert_domaine WHERE espace_id = ? AND domaine = ?")) {
                requete.setObject(1, espace);
                requete.setString(2, codeNormalise);
                requete.executeUpdate();
            }
            try (PreparedStatement requete = connexion.prepareStatement("""
                    INSERT INTO expert_domaine (espace_id, domaine, compte_id, modifie_par, modifie_le)
                    VALUES (?, ?, ?, argos_compte_courant(), ?)
                    """)) {
                Timestamp maintenant = Timestamp.from(Instant.now());
                for (UUID expert : distincts) {
                    requete.setObject(1, espace);
                    requete.setString(2, codeNormalise);
                    requete.setObject(3, expert);
                    requete.setTimestamp(4, maintenant);
                    requete.addBatch();
                }
                requete.executeBatch();
            }
            return exiger(connexion, espace, codeNormalise);
        });
    }

    // ------------------------------------------------------------------ lectures techniques

    /** Dictionnaires de tous les espaces qui ont au moins un domaine (qualification des textes). */
    public List<DictionnaireEspace> dictionnaires() {
        try (Connection connexion = dataSource.getConnection()) {
            List<UUID> espaces = new ArrayList<>();
            try (PreparedStatement requete = connexion.prepareStatement("SELECT DISTINCT espace_id FROM domaine_metier ORDER BY espace_id");
                    ResultSet resultat = requete.executeQuery()) {
                while (resultat.next()) {
                    espaces.add(resultat.getObject(1, UUID.class));
                }
            }
            Map<UUID, List<Domaine>> parEspace = lire(connexion, espaces);
            return espaces.stream().map(espace -> new DictionnaireEspace(espace, parEspace.getOrDefault(espace, List.of()))).toList();
        } catch (SQLException erreur) {
            throw new IllegalStateException("lecture des dictionnaires métier impossible", erreur);
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
        try (Connection connexion = dataSource.getConnection(); PreparedStatement requete = connexion.prepareStatement("""
                SELECT DISTINCT x.compte_id, co.email FROM expert_domaine x
                JOIN appartenance a ON a.espace_id = x.espace_id AND a.compte_id = x.compte_id
                                   AND a.role IN ('CONTRIBUTEUR', 'ADMINISTRATEUR')
                JOIN compte co ON co.id = x.compte_id
                WHERE x.espace_id = ? AND x.domaine = ANY (?) ORDER BY x.compte_id
                """)) {
            requete.setObject(1, espace);
            requete.setArray(2, connexion.createArrayOf("varchar", domaines.toArray()));
            try (ResultSet resultat = requete.executeQuery()) {
                while (resultat.next()) {
                    liste.add(new Expert(resultat.getObject(1, UUID.class), resultat.getString(2)));
                }
            }
        } catch (SQLException erreur) {
            throw new IllegalStateException("lecture des experts impossible", erreur);
        }
        return liste;
    }

    // ------------------------------------------------------------------ outils

    /** Code de domaine : minuscules, chiffres et tirets, 64 caractères au plus. */
    public static String code(String code) {
        String normalise = code == null ? "" : code.strip();
        if (!normalise.matches("[a-z0-9][a-z0-9-]{0,63}")) {
            throw new DonneeInvalide("DONNEE_INVALIDE",
                    "Code de domaine invalide : minuscules, chiffres et tirets, 64 caractères au plus.", "code");
        }
        return normalise;
    }

    static Set<Regle> valider(List<Regle> regles) {
        if (regles == null || regles.size() > REGLES_MAX) {
            throw new DonneeInvalide("DONNEE_INVALIDE", "Indiquez de 0 à " + REGLES_MAX + " règles.", "regles");
        }
        Set<Regle> valides = new LinkedHashSet<>();
        for (Regle regle : regles) {
            if (regle == null || regle.type() == null) {
                throw new DonneeInvalide("DONNEE_INVALIDE", "Chaque règle a un type : NATURE, MINISTERE, NOR, CODE ou MOT_CLE.",
                        "regles");
            }
            String valeur = regle.valeur() == null ? "" : regle.valeur().strip().replaceAll("\\s+", " ");
            if (valeur.isEmpty() || valeur.length() > 200 || valeur.chars().anyMatch(Character::isISOControl)) {
                throw new DonneeInvalide("DONNEE_INVALIDE", "Chaque règle a une valeur de 1 à 200 caractères.", "regles");
            }
            if (regle.type() == TypeRegle.NOR) {
                valeur = valeur.toUpperCase(Locale.ROOT);
                if (!valeur.matches("[A-Z]{1,4}[A-Z0-9]{0,8}")) {
                    throw new DonneeInvalide("DONNEE_INVALIDE", "Un préfixe NOR commence par des lettres (par exemple TFPF).",
                            "regles");
                }
            }
            if (regle.type() == TypeRegle.NATURE) {
                valeur = valeur.toUpperCase(Locale.ROOT);
            }
            valides.add(new Regle(regle.type(), valeur));
        }
        return valides;
    }

    private static int compter(Connection connexion, UUID espace) throws SQLException {
        try (PreparedStatement requete = connexion.prepareStatement("SELECT count(*) FROM domaine_metier WHERE espace_id = ?")) {
            requete.setObject(1, espace);
            try (ResultSet resultat = requete.executeQuery()) {
                resultat.next();
                return resultat.getInt(1);
            }
        }
    }

    private static Domaine exiger(Connection connexion, UUID espace, String code) throws SQLException {
        Domaine domaine = trouver(connexion, espace, code);
        if (domaine == null) {
            throw new Introuvable("Domaine introuvable.");
        }
        return domaine;
    }

    private static Domaine trouver(Connection connexion, UUID espace, String code) throws SQLException {
        return lire(connexion, List.of(espace)).getOrDefault(espace, List.of()).stream()
                .filter(domaine -> domaine.code().equals(code)).findFirst().orElse(null);
    }

    /** Domaines des espaces, avec leurs règles et leurs experts (lus selon le contexte de la connexion). */
    private static Map<UUID, List<Domaine>> lire(Connection connexion, List<UUID> espaces) throws SQLException {
        Map<UUID, Map<String, String>> libelles = new LinkedHashMap<>();
        Map<String, List<Regle>> regles = new LinkedHashMap<>();
        Map<String, List<Expert>> experts = new LinkedHashMap<>();
        if (espaces.isEmpty()) {
            return Map.of();
        }
        java.sql.Array tableau = connexion.createArrayOf("uuid", espaces.toArray());
        try (PreparedStatement requete = connexion.prepareStatement(
                "SELECT espace_id, code, libelle FROM domaine_metier WHERE espace_id = ANY (?) ORDER BY espace_id, code")) {
            requete.setArray(1, tableau);
            try (ResultSet lignes = requete.executeQuery()) {
                while (lignes.next()) {
                    libelles.computeIfAbsent(lignes.getObject(1, UUID.class), cle -> new LinkedHashMap<>())
                            .put(lignes.getString(2), lignes.getString(3));
                }
            }
        }
        try (PreparedStatement requete = connexion.prepareStatement("""
                SELECT espace_id, domaine, type, valeur FROM regle_dictionnaire WHERE espace_id = ANY (?)
                ORDER BY espace_id, domaine, type, valeur
                """)) {
            requete.setArray(1, tableau);
            try (ResultSet lignes = requete.executeQuery()) {
                while (lignes.next()) {
                    regles.computeIfAbsent(lignes.getObject(1, UUID.class) + "|" + lignes.getString(2), cle -> new ArrayList<>())
                            .add(new Regle(TypeRegle.valueOf(lignes.getString(3)), lignes.getString(4)));
                }
            }
        }
        try (PreparedStatement requete = connexion.prepareStatement("""
                SELECT x.espace_id, x.domaine, x.compte_id, co.email FROM expert_domaine x
                LEFT JOIN compte co ON co.id = x.compte_id
                WHERE x.espace_id = ANY (?) ORDER BY x.espace_id, x.domaine, co.email
                """)) {
            requete.setArray(1, tableau);
            try (ResultSet lignes = requete.executeQuery()) {
                while (lignes.next()) {
                    experts.computeIfAbsent(lignes.getObject(1, UUID.class) + "|" + lignes.getString(2), cle -> new ArrayList<>())
                            .add(new Expert(lignes.getObject(3, UUID.class), lignes.getString(4)));
                }
            }
        }
        Map<UUID, List<Domaine>> resultat = new LinkedHashMap<>();
        libelles.forEach((espace, domaines) -> domaines.forEach((code, libelle) -> resultat
                .computeIfAbsent(espace, cle -> new ArrayList<>())
                .add(new Domaine(code, libelle, regles.getOrDefault(espace + "|" + code, List.of()),
                        experts.getOrDefault(espace + "|" + code, List.of())))));
        return resultat;
    }
}
