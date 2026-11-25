package io.argos.commun;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import javax.sql.DataSource;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * Utilitaire d'accès JDBC du serveur (ADR-0037) : le SQL reste explicite, les paramètres positionnels, et une seule
 * traduction de {@link SQLException} en {@link ErreurBase}. Chaque méthode existe en deux formes : sur une connexion
 * déjà obtenue (transaction en cours, contexte RLS déjà appliqué) et sur une connexion prise à la source de données,
 * qui est celle de la transaction JTA en cours s'il y en a une.
 */
@ApplicationScoped
public class Jdbc {

    private static final int LONGUEUR_MAX_SQL_JOURNALISE = 80;

    /** Liaison des paramètres positionnels d'une requête. */
    @FunctionalInterface
    public interface Liaison {
        void lier(PreparedStatement requete) throws SQLException;
    }

    /** Lecture de la ligne courante d'un résultat. */
    @FunctionalInterface
    public interface Lecture<T> {
        T lire(ResultSet resultat) throws SQLException;
    }

    @Inject
    DataSource dataSource;

    /** Liaison d'une requête sans paramètre. */
    public static Liaison sansParametre() {
        return requete -> { };
    }

    /** Liaison des valeurs données, dans l'ordre, à partir du premier paramètre. */
    public static Liaison parametres(Object... valeurs) {
        return requete -> {
            for (int index = 0; index < valeurs.length; index++) {
                requete.setObject(index + 1, valeurs[index]);
            }
        };
    }

    public <T> List<T> lister(String sql, Liaison liaison, Lecture<T> lecture) {
        try (Connection connexion = dataSource.getConnection()) {
            return lister(connexion, sql, liaison, lecture);
        } catch (SQLException erreur) {
            throw traduire(sql, erreur);
        }
    }

    public static <T> List<T> lister(Connection connexion, String sql, Liaison liaison, Lecture<T> lecture) {
        try (PreparedStatement requete = connexion.prepareStatement(sql)) {
            liaison.lier(requete);
            try (ResultSet resultat = requete.executeQuery()) {
                List<T> lignes = new ArrayList<>();
                while (resultat.next()) {
                    lignes.add(lecture.lire(resultat));
                }
                return lignes;
            }
        } catch (SQLException erreur) {
            throw traduire(sql, erreur);
        }
    }

    public <T> Optional<T> unique(String sql, Liaison liaison, Lecture<T> lecture) {
        try (Connection connexion = dataSource.getConnection()) {
            return unique(connexion, sql, liaison, lecture);
        } catch (SQLException erreur) {
            throw traduire(sql, erreur);
        }
    }

    /** Première ligne du résultat, ou vide s'il n'y en a pas. */
    public static <T> Optional<T> unique(Connection connexion, String sql, Liaison liaison, Lecture<T> lecture) {
        try (PreparedStatement requete = connexion.prepareStatement(sql)) {
            liaison.lier(requete);
            try (ResultSet resultat = requete.executeQuery()) {
                return resultat.next() ? Optional.ofNullable(lecture.lire(resultat)) : Optional.empty();
            }
        } catch (SQLException erreur) {
            throw traduire(sql, erreur);
        }
    }

    public int modifier(String sql, Liaison liaison) {
        try (Connection connexion = dataSource.getConnection()) {
            return modifier(connexion, sql, liaison);
        } catch (SQLException erreur) {
            throw traduire(sql, erreur);
        }
    }

    /** Exécute une écriture et rend le nombre de lignes touchées. */
    public static int modifier(Connection connexion, String sql, Liaison liaison) {
        try (PreparedStatement requete = connexion.prepareStatement(sql)) {
            liaison.lier(requete);
            return requete.executeUpdate();
        } catch (SQLException erreur) {
            throw traduire(sql, erreur);
        }
    }

    /** Traduction unique d'un échec SQL : le début de la requête nomme l'opération, la cause est conservée. */
    static ErreurBase traduire(String sql, SQLException cause) {
        String debut = sql.lines().map(String::strip).filter(ligne -> !ligne.isEmpty()).collect(Collectors.joining(" "));
        if (debut.length() > LONGUEUR_MAX_SQL_JOURNALISE) {
            debut = debut.substring(0, LONGUEUR_MAX_SQL_JOURNALISE) + "…";
        }
        return new ErreurBase("Accès à la base impossible : " + debut, cause);
    }
}
