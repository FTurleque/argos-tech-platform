package io.argos.impact;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Collection;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Déclare à PostgreSQL, pour la transaction courante, le périmètre utilisé par la politique RLS
 * de {@code project_impact} (ADR-0016) : liste d'espaces autorisés, ou traitement technique.
 */
final class RlsSession {

    private RlsSession() {
    }

    static void workspaces(Connection c, Collection<UUID> workspaceIds) throws SQLException {
        set(c, "argos.technical", "off");
        set(c, "argos.workspaces", workspaceIds.stream().map(UUID::toString).collect(Collectors.joining(",")));
    }

    static void technical(Connection c) throws SQLException {
        set(c, "argos.technical", "on");
    }

    private static void set(Connection c, String name, String value) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("SELECT set_config(?, ?, true)")) {
            ps.setString(1, name);
            ps.setString(2, value);
            ps.execute();
        }
    }
}
