package io.argos.workspace;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;

import javax.sql.DataSource;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * Exécute un traitement dans une transaction PostgreSQL soumise à la RLS (ACC-04, ADR-0016).
 *
 * <p>Chaque transaction bascule sur le rôle {@code argos_app} et transmet le compte courant,
 * son e-mail vérifié et l'espace courant. Sans espace courant, la base ne renvoie aucune
 * donnée d'espace : un oubli de contexte dans un service ne suffit pas à fuiter des données.
 */
@ApplicationScoped
public class ContexteBase {

    /** Contexte transmis à la base ; chaque champ peut être absent ({@code null}). */
    public record Contexte(UUID compteId, String email, UUID espaceId) {

        public static Contexte aucun() {
            return new Contexte(null, null, null);
        }

        public Contexte dansEspace(UUID espace) {
            return new Contexte(compteId, email, espace);
        }
    }

    /** Traitement JDBC exécuté dans la transaction. */
    @FunctionalInterface
    public interface Travail<T> {
        T executer(Connection connexion) throws SQLException;
    }

    @Inject
    DataSource dataSource;

    public <T> T executer(Contexte contexte, Travail<T> travail) {
        try (Connection connexion = dataSource.getConnection()) {
            boolean autoCommit = connexion.getAutoCommit();
            connexion.setAutoCommit(false);
            try {
                try (Statement st = connexion.createStatement()) {
                    st.execute("SET LOCAL ROLE argos_app");
                }
                try (PreparedStatement ps = connexion.prepareStatement(
                        "SELECT set_config('argos.compte_courant', ?, true),"
                                + " set_config('argos.email_courant', ?, true),"
                                + " set_config('argos.espace_courant', ?, true)")) {
                    ps.setString(1, texte(contexte.compteId()));
                    ps.setString(2, contexte.email() == null ? "" : contexte.email());
                    ps.setString(3, texte(contexte.espaceId()));
                    ps.execute();
                }
                T resultat = travail.executer(connexion);
                connexion.commit();
                return resultat;
            } catch (SQLException | RuntimeException e) {
                connexion.rollback();
                throw e;
            } finally {
                connexion.setAutoCommit(autoCommit);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Accès à la base impossible", e);
        }
    }

    private static String texte(UUID id) {
        return id == null ? "" : id.toString();
    }
}
