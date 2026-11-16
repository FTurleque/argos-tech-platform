package io.argos.aigateway;

import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;

import javax.sql.DataSource;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.eclipse.microprofile.config.inject.ConfigProperty;

/**
 * Plafond journalier de l'instance (Q-1 : 1 000 appels) et quota journalier par espace (Q-2 : 100
 * appels), paramétrables (IAG-03). Le jour est celui du fuseau commun d'Argos ({@code argos.ia.fuseau},
 * par défaut celui des alertes).
 *
 * <p>Une réservation incrémente atomiquement, dans une seule transaction, le compteur de l'instance
 * et celui de l'espace imputé, chacun seulement s'il est sous sa limite : deux demandes simultanées ne
 * peuvent pas dépasser une limite. Seuls les appels transmis au fournisseur sont comptés ; un refus de
 * classification ne consomme rien.
 */
@ApplicationScoped
public class QuotasIa {

    /** Résultat d'une réservation. */
    enum Reservation { ACCORDEE, PLAFOND_INSTANCE, QUOTA_ESPACE }

    static final String INSTANCE = "INSTANCE";

    @Inject
    DataSource dataSource;

    @ConfigProperty(name = "argos.ia.plafond-instance-jour")
    int plafondInstance;

    @ConfigProperty(name = "argos.ia.quota-espace-jour")
    int quotaEspace;

    @ConfigProperty(name = "argos.ia.fuseau")
    ZoneId fuseau;

    LocalDate aujourdhui() {
        return LocalDate.now(fuseau);
    }

    /** Vrai si un appel imputé à cet espace ({@code null} : instance seule) serait accepté maintenant. */
    public boolean disponible(UUID espaceId) {
        LocalDate jour = aujourdhui();
        try (Connection c = dataSource.getConnection()) {
            if (appels(c, jour, INSTANCE) >= plafondInstance) {
                return false;
            }
            return espaceId == null || appels(c, jour, espaceId.toString()) < quotaEspace;
        } catch (SQLException e) {
            throw new IllegalStateException("lecture des quotas IA impossible", e);
        }
    }

    Reservation reserver(UUID espaceId) {
        LocalDate jour = aujourdhui();
        try (Connection c = dataSource.getConnection()) {
            boolean autoCommit = c.getAutoCommit();
            c.setAutoCommit(false);
            try {
                if (!incrementer(c, jour, INSTANCE, plafondInstance)) {
                    c.rollback();
                    return Reservation.PLAFOND_INSTANCE;
                }
                if (espaceId != null && !incrementer(c, jour, espaceId.toString(), quotaEspace)) {
                    c.rollback();
                    return Reservation.QUOTA_ESPACE;
                }
                c.commit();
                return Reservation.ACCORDEE;
            } catch (SQLException e) {
                c.rollback();
                throw e;
            } finally {
                c.setAutoCommit(autoCommit);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("réservation d'un appel IA impossible", e);
        }
    }

    private static boolean incrementer(Connection c, LocalDate jour, String portee, int limite) throws SQLException {
        if (limite <= 0) {
            return false;
        }
        try (PreparedStatement ps = c.prepareStatement("""
                INSERT INTO ia_quota_jour (jour, portee, appels) VALUES (?, ?, 1)
                ON CONFLICT (jour, portee) DO UPDATE SET appels = ia_quota_jour.appels + 1
                WHERE ia_quota_jour.appels < ?
                RETURNING appels
                """)) {
            ps.setDate(1, Date.valueOf(jour));
            ps.setString(2, portee);
            ps.setInt(3, limite);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    private static int appels(Connection c, LocalDate jour, String portee) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("SELECT appels FROM ia_quota_jour WHERE jour = ? AND portee = ?")) {
            ps.setDate(1, Date.valueOf(jour));
            ps.setString(2, portee);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        }
    }
}
