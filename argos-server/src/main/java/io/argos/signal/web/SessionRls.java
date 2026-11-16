package io.argos.signal.web;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;

import javax.sql.DataSource;

/**
 * Transaction soumise à la RLS (ADR-0016) : rôle {@code argos_app}, compte courant et, s'il est
 * fourni, espace courant. Même protocole que {@code io.argos.workspace.ContexteBase}, que le module
 * {@code signal} ne peut pas utiliser directement ({@code ModuleBoundariesTest}).
 */
final class SessionRls {

    @FunctionalInterface
    interface Travail<T> {
        T executer(Connection connexion) throws SQLException;
    }

    private SessionRls() {
    }

    static <T> T executer(DataSource dataSource, UUID compteId, UUID espaceId, Travail<T> travail) {
        try (Connection c = dataSource.getConnection()) {
            boolean autoCommit = c.getAutoCommit();
            c.setAutoCommit(false);
            try {
                try (Statement st = c.createStatement()) {
                    st.execute("SET LOCAL ROLE argos_app");
                }
                try (PreparedStatement ps = c.prepareStatement(
                        "SELECT set_config('argos.compte_courant', ?, true), set_config('argos.sujet_courant', '', true),"
                                + " set_config('argos.email_courant', '', true), set_config('argos.espace_courant', ?, true)")) {
                    ps.setString(1, compteId == null ? "" : compteId.toString());
                    ps.setString(2, espaceId == null ? "" : espaceId.toString());
                    ps.execute();
                }
                T resultat = travail.executer(c);
                c.commit();
                return resultat;
            } catch (SQLException | RuntimeException e) {
                c.rollback();
                throw e;
            } finally {
                c.setAutoCommit(autoCommit);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("accès à la base impossible", e);
        }
    }
}
