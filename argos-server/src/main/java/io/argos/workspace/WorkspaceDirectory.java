package io.argos.workspace;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;

import javax.sql.DataSource;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

/**
 * Espaces d'équipe (ADR-0016). Les membres et leurs rôles ne sont pas gérés ici : ils relèvent
 * du contrôle d'accès (changement gerer-comptes-et-acces).
 */
@ApplicationScoped
public class WorkspaceDirectory {

    @Inject
    DataSource dataSource;

    @Transactional
    public UUID create(String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("nom d'espace obligatoire");
        }
        UUID id = UUID.randomUUID();
        try (Connection c = dataSource.getConnection();
                PreparedStatement ps = c.prepareStatement("INSERT INTO workspace(id, name) VALUES (?, ?)")) {
            ps.setObject(1, id);
            ps.setString(2, name.strip());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("création de l'espace impossible", e);
        }
        return id;
    }

    @Transactional
    public boolean exists(UUID id) {
        try (Connection c = dataSource.getConnection();
                PreparedStatement ps = c.prepareStatement("SELECT 1 FROM workspace WHERE id = ?")) {
            ps.setObject(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            throw new IllegalStateException("lecture de l'espace impossible", e);
        }
    }
}
