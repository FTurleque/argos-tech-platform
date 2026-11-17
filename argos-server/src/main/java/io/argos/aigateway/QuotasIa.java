package io.argos.aigateway;

import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import javax.sql.DataSource;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.eclipse.microprofile.config.inject.ConfigProperty;

/**
 * Plafond journalier de l'instance (Q-1 : 1 000 appels), quota journalier par espace (Q-2 : 100
 * appels) et quota journalier par compte pour les demandes explicites d'un utilisateur (20 appels),
 * paramétrables (IAG-03). Le jour est celui du fuseau commun d'Argos ({@code argos.ia.fuseau},
 * par défaut celui des alertes).
 *
 * <p>Une réservation incrémente atomiquement, dans une seule transaction, le compteur de l'instance
 * et ceux de l'espace et du compte imputés, chacun seulement s'il est sous sa limite : deux demandes
 * simultanées ne peuvent pas dépasser une limite. Seuls les appels transmis au fournisseur sont
 * comptés : un refus de classification ne consomme rien, et une réservation est rendue
 * ({@link #liberer}) quand le fournisseur n'a pas pu être joint.
 */
@ApplicationScoped
public class QuotasIa {

    /** Résultat d'une réservation. */
    enum Reservation { ACCORDEE, PLAFOND_INSTANCE, QUOTA_ESPACE, QUOTA_COMPTE }

    static final String INSTANCE = "INSTANCE";

    @Inject
    DataSource dataSource;

    @ConfigProperty(name = "argos.ia.plafond-instance-jour")
    int plafondInstance;

    @ConfigProperty(name = "argos.ia.quota-espace-jour")
    int quotaEspace;

    @ConfigProperty(name = "argos.ia.quota-compte-jour")
    int quotaCompte;

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

    /** Portée du compteur d'un compte : « C » suivi des 32 chiffres hexadécimaux de l'identifiant. */
    static String porteeCompte(UUID compteId) {
        return "C" + compteId.toString().replace("-", "");
    }

    Reservation reserver(UUID espaceId) {
        return reserver(espaceId, null);
    }

    Reservation reserver(UUID espaceId, UUID compteId) {
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
                if (compteId != null && !incrementer(c, jour, porteeCompte(compteId), quotaCompte)) {
                    c.rollback();
                    return Reservation.QUOTA_COMPTE;
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

    /** Rend une réservation accordée dont l'appel n'a pas été transmis au fournisseur. */
    void liberer(UUID espaceId, UUID compteId) {
        LocalDate jour = aujourdhui();
        try (Connection c = dataSource.getConnection();
                PreparedStatement ps = c.prepareStatement(
                        "UPDATE ia_quota_jour SET appels = appels - 1 WHERE jour = ? AND portee = ? AND appels > 0")) {
            for (String portee : portees(espaceId, compteId)) {
                ps.setDate(1, Date.valueOf(jour));
                ps.setString(2, portee);
                ps.addBatch();
            }
            ps.executeBatch();
        } catch (SQLException e) {
            throw new IllegalStateException("restitution d'un appel IA impossible", e);
        }
    }

    private static List<String> portees(UUID espaceId, UUID compteId) {
        List<String> portees = new ArrayList<>();
        portees.add(INSTANCE);
        if (espaceId != null) {
            portees.add(espaceId.toString());
        }
        if (compteId != null) {
            portees.add(porteeCompte(compteId));
        }
        return portees;
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
