package io.argos.audit;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import javax.sql.DataSource;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

/** Journal des refus d'accès : utilisateur, ressource demandée et date (COR-08-S2, ADR-0016). */
@ApplicationScoped
public class AccessRefusalLog {

    /** Refus journalisé. */
    public record Refusal(UUID id, String userId, String resourceType, String resourceId, String reason,
            Instant occurredAt) {
    }

    @Inject
    DataSource dataSource;

    @Transactional(Transactional.TxType.REQUIRES_NEW)
    public void record(String userId, String resourceType, String resourceId, String reason) {
        try (Connection c = dataSource.getConnection(); PreparedStatement ps = c.prepareStatement("""
                INSERT INTO access_refusal(id, user_id, resource_type, resource_id, reason, occurred_at)
                VALUES (?, ?, ?, ?, ?, now())
                """)) {
            ps.setObject(1, UUID.randomUUID());
            ps.setString(2, userId);
            ps.setString(3, resourceType);
            ps.setString(4, resourceId);
            ps.setString(5, reason);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("journalisation du refus impossible", e);
        }
    }

    @Transactional
    public List<Refusal> forUser(String userId) {
        List<Refusal> refusals = new ArrayList<>();
        try (Connection c = dataSource.getConnection(); PreparedStatement ps = c.prepareStatement("""
                SELECT id, user_id, resource_type, resource_id, reason, occurred_at FROM access_refusal
                WHERE user_id = ? ORDER BY occurred_at
                """)) {
            ps.setString(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    refusals.add(new Refusal(rs.getObject(1, UUID.class), rs.getString(2), rs.getString(3),
                            rs.getString(4), rs.getString(5), rs.getObject(6, OffsetDateTime.class).toInstant()));
                }
            }
        } catch (SQLException e) {
            throw new IllegalStateException("lecture du journal des refus impossible", e);
        }
        return refusals;
    }
}
