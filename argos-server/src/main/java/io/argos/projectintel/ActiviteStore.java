package io.argos.projectintel;

import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.sql.DataSource;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import io.argos.scm.ActiviteDepot;

/**
 * Écriture de l'activité agrégée et de l'état de la collecte. Traitement technique, exécuté sous
 * l'utilisateur de connexion comme les imports d'inventaire ; la lecture par un utilisateur passe par
 * {@link ActiviteQueries}, sous {@code argos_app}.
 */
@ApplicationScoped
class ActiviteStore {

    private static final int MOTIF_MAX = 500;

    @Inject
    DataSource dataSource;

    /** Date de prochaine collecte de chaque projet déjà collecté, en une requête (AUD-PER-13). */
    @Transactional
    Map<UUID, Instant> prochainesCollectes() {
        Map<UUID, Instant> echeances = new HashMap<>();
        try (Connection connexion = dataSource.getConnection();
                PreparedStatement requete = connexion.prepareStatement("SELECT project_id, prochaine_collecte FROM activite_collecte");
                ResultSet resultat = requete.executeQuery()) {
            while (resultat.next()) {
                echeances.put(resultat.getObject(1, UUID.class), resultat.getTimestamp(2).toInstant());
            }
        } catch (SQLException erreur) {
            throw new IllegalStateException("lecture de l'état de collecte impossible", erreur);
        }
        return echeances;
    }

    /**
     * Remplace les compteurs des jours {@code debut} à {@code fin} et l'état des jalons, puis marque la
     * collecte à jour. Rejouée sur la même fenêtre, elle produit le même résultat (idempotence).
     */
    @Transactional
    void enregistrer(UUID projectId, LocalDate debut, LocalDate fin, Map<LocalDate, Map<Agregation.Type, Integer>> compteurs,
            List<ActiviteDepot.Jalon> jalons, boolean complet, Instant maintenant, Instant prochaine) {
        try (Connection connexion = dataSource.getConnection()) {
            try (PreparedStatement requete = connexion.prepareStatement(
                    "DELETE FROM activite_compteur WHERE project_id = ? AND jour BETWEEN ? AND ?")) {
                requete.setObject(1, projectId);
                requete.setDate(2, Date.valueOf(debut));
                requete.setDate(3, Date.valueOf(fin));
                requete.executeUpdate();
            }
            try (PreparedStatement requete = connexion.prepareStatement(
                    "INSERT INTO activite_compteur(project_id, jour, type, valeur) VALUES (?, ?, ?, ?)")) {
                for (Map.Entry<LocalDate, Map<Agregation.Type, Integer>> jour : compteurs.entrySet()) {
                    for (Map.Entry<Agregation.Type, Integer> type : jour.getValue().entrySet()) {
                        requete.setObject(1, projectId);
                        requete.setDate(2, Date.valueOf(jour.getKey()));
                        requete.setString(3, type.getKey().name());
                        requete.setInt(4, type.getValue());
                        requete.addBatch();
                    }
                }
                requete.executeBatch();
            }
            try (PreparedStatement requete = connexion.prepareStatement("DELETE FROM activite_jalon WHERE project_id = ?")) {
                requete.setObject(1, projectId);
                requete.executeUpdate();
            }
            try (PreparedStatement requete = connexion.prepareStatement("""
                    INSERT INTO activite_jalon(project_id, reference, titre, etat, echeance, tickets_fermes, tickets_total, releve_le)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?) ON CONFLICT (project_id, reference) DO NOTHING
                    """)) {
                for (ActiviteDepot.Jalon jalon : jalons) {
                    requete.setObject(1, projectId);
                    requete.setString(2, jalon.reference());
                    requete.setString(3, jalon.titre());
                    requete.setString(4, jalon.ouvert() ? "OUVERT" : "FERME");
                    requete.setDate(5, jalon.echeance() == null ? null : Date.valueOf(jalon.echeance()));
                    requete.setInt(6, jalon.ticketsFermes());
                    requete.setInt(7, jalon.ticketsTotal());
                    requete.setTimestamp(8, Timestamp.from(maintenant));
                    requete.addBatch();
                }
                requete.executeBatch();
            }
            try (PreparedStatement requete = connexion.prepareStatement("""
                    INSERT INTO activite_collecte(project_id, etat, derniere_mise_a_jour, prochaine_collecte, complet,
                                                  donnees_depuis, motif, tentee_le)
                    VALUES (?, 'A_JOUR', ?, ?, ?, ?, NULL, ?)
                    ON CONFLICT (project_id) DO UPDATE SET etat = 'A_JOUR',
                        derniere_mise_a_jour = excluded.derniere_mise_a_jour,
                        prochaine_collecte = excluded.prochaine_collecte, complet = excluded.complet,
                        donnees_depuis = LEAST(activite_collecte.donnees_depuis, excluded.donnees_depuis),
                        motif = NULL, tentee_le = excluded.tentee_le
                    """)) {
                requete.setObject(1, projectId);
                requete.setTimestamp(2, Timestamp.from(maintenant));
                requete.setTimestamp(3, Timestamp.from(prochaine));
                requete.setBoolean(4, complet);
                requete.setDate(5, Date.valueOf(debut));
                requete.setTimestamp(6, Timestamp.from(maintenant));
                requete.executeUpdate();
            }
        } catch (SQLException erreur) {
            throw new IllegalStateException("enregistrement de l'activité impossible", erreur);
        }
    }

    /**
     * Reporte la collecte ({@code REPORTEE_QUOTA} ou {@code ECHEC}) sans toucher aux données ni à la date
     * de dernière mise à jour : l'âge des données affichées reste exact (PIN-05-S1).
     */
    @Transactional
    void reporter(UUID projectId, String etat, Instant prochaine, String motif, Instant maintenant) {
        try (Connection connexion = dataSource.getConnection(); PreparedStatement requete = connexion.prepareStatement("""
                INSERT INTO activite_collecte(project_id, etat, derniere_mise_a_jour, prochaine_collecte, complet,
                                              donnees_depuis, motif, tentee_le)
                VALUES (?, ?, NULL, ?, true, NULL, ?, ?)
                ON CONFLICT (project_id) DO UPDATE SET etat = excluded.etat,
                    prochaine_collecte = excluded.prochaine_collecte, motif = excluded.motif, tentee_le = excluded.tentee_le
                """)) {
            requete.setObject(1, projectId);
            requete.setString(2, etat);
            requete.setTimestamp(3, Timestamp.from(prochaine));
            requete.setString(4, motif == null ? null : motif.length() > MOTIF_MAX ? motif.substring(0, MOTIF_MAX) : motif);
            requete.setTimestamp(5, Timestamp.from(maintenant));
            requete.executeUpdate();
        } catch (SQLException erreur) {
            throw new IllegalStateException("report de la collecte impossible", erreur);
        }
    }

    /**
     * Supprime l'historique antérieur au jour {@code limite} (conservation de 13 mois, Q-2) : compteurs
     * des jours plus anciens et jalons relevés avant cette date.
     *
     * @return nombre de lignes supprimées
     */
    @Transactional
    int purger(LocalDate limite, Instant limiteInstant) {
        int supprimees = 0;
        try (Connection connexion = dataSource.getConnection()) {
            try (PreparedStatement requete = connexion.prepareStatement("DELETE FROM activite_compteur WHERE jour < ?")) {
                requete.setDate(1, Date.valueOf(limite));
                supprimees += requete.executeUpdate();
            }
            try (PreparedStatement requete = connexion.prepareStatement("DELETE FROM activite_jalon WHERE releve_le < ?")) {
                requete.setTimestamp(1, Timestamp.from(limiteInstant));
                supprimees += requete.executeUpdate();
            }
            try (PreparedStatement requete = connexion.prepareStatement(
                    "UPDATE activite_collecte SET donnees_depuis = ? WHERE donnees_depuis < ?")) {
                requete.setDate(1, Date.valueOf(limite));
                requete.setDate(2, Date.valueOf(limite));
                requete.executeUpdate();
            }
        } catch (SQLException erreur) {
            throw new IllegalStateException("purge de l'activité impossible", erreur);
        }
        return supprimees;
    }
}
