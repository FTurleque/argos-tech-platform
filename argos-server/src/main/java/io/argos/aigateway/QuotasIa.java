package io.argos.aigateway;

import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import javax.sql.DataSource;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.eclipse.microprofile.config.inject.ConfigProperty;

import io.quarkus.narayana.jta.QuarkusTransaction;
import io.argos.commun.ErreurBase;
import io.argos.horloge.LectureHeure;

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
    Clock horloge;

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
        return LectureHeure.jour(horloge.withZone(fuseau));
    }

    /** Vrai si un appel imputé à cet espace ({@code null} : instance seule) serait accepté maintenant. */
    public boolean disponible(UUID espaceId) {
        LocalDate jour = aujourdhui();
        try (Connection connexion = dataSource.getConnection()) {
            if (appels(connexion, jour, INSTANCE) >= plafondInstance) {
                return false;
            }
            return espaceId == null || appels(connexion, jour, espaceId.toString()) < quotaEspace;
        } catch (SQLException erreur) {
            throw new IllegalStateException("lecture des quotas IA impossible", erreur);
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
        try {
            return QuarkusTransaction.requiringNew().call(() -> {
                try (Connection connexion = dataSource.getConnection()) {
                    if (!incrementer(connexion, jour, INSTANCE, plafondInstance)) {
                        throw new Refus(Reservation.PLAFOND_INSTANCE);
                    }
                    if (espaceId != null && !incrementer(connexion, jour, espaceId.toString(), quotaEspace)) {
                        throw new Refus(Reservation.QUOTA_ESPACE);
                    }
                    if (compteId != null && !incrementer(connexion, jour, porteeCompte(compteId), quotaCompte)) {
                        throw new Refus(Reservation.QUOTA_COMPTE);
                    }
                    return Reservation.ACCORDEE;
                } catch (SQLException erreur) {
                    throw new ErreurBase("réservation d'un appel IA impossible", erreur);
                }
            });
        } catch (Refus refus) {
            return refus.reservation;
        }
    }

    /** Refus d'une réservation : lancé pour annuler les compteurs déjà incrémentés dans la transaction. */
    private static final class Refus extends RuntimeException {

        private static final long serialVersionUID = 1L;

        private final transient Reservation reservation;

        Refus(Reservation reservation) {
            super(reservation.name(), null, false, false);
            this.reservation = reservation;
        }
    }

    /** Rend une réservation accordée dont l'appel n'a pas été transmis au fournisseur. */
    void liberer(UUID espaceId, UUID compteId) {
        LocalDate jour = aujourdhui();
        try (Connection connexion = dataSource.getConnection();
                PreparedStatement requete = connexion.prepareStatement(
                        "UPDATE ia_quota_jour SET appels = appels - 1 WHERE jour = ? AND portee = ? AND appels > 0")) {
            for (String portee : portees(espaceId, compteId)) {
                requete.setDate(1, Date.valueOf(jour));
                requete.setString(2, portee);
                requete.addBatch();
            }
            requete.executeBatch();
        } catch (SQLException erreur) {
            throw new IllegalStateException("restitution d'un appel IA impossible", erreur);
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

    private static boolean incrementer(Connection connexion, LocalDate jour, String portee, int limite) throws SQLException {
        if (limite <= 0) {
            return false;
        }
        try (PreparedStatement requete = connexion.prepareStatement("""
                INSERT INTO ia_quota_jour (jour, portee, appels) VALUES (?, ?, 1)
                ON CONFLICT (jour, portee) DO UPDATE SET appels = ia_quota_jour.appels + 1
                WHERE ia_quota_jour.appels < ?
                RETURNING appels
                """)) {
            requete.setDate(1, Date.valueOf(jour));
            requete.setString(2, portee);
            requete.setInt(3, limite);
            try (ResultSet resultat = requete.executeQuery()) {
                return resultat.next();
            }
        }
    }

    private static int appels(Connection connexion, LocalDate jour, String portee) throws SQLException {
        try (PreparedStatement requete = connexion.prepareStatement("SELECT appels FROM ia_quota_jour WHERE jour = ? AND portee = ?")) {
            requete.setDate(1, Date.valueOf(jour));
            requete.setString(2, portee);
            try (ResultSet resultat = requete.executeQuery()) {
                return resultat.next() ? resultat.getInt(1) : 0;
            }
        }
    }
}
