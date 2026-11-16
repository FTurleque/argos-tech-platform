package io.argos.projectintel;

import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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

    /** Date de prochaine collecte du projet, absente s'il n'a jamais été collecté. */
    @Transactional
    Optional<Instant> prochaineCollecte(UUID projectId) {
        try (Connection c = dataSource.getConnection();
                PreparedStatement ps = c.prepareStatement("SELECT prochaine_collecte FROM activite_collecte WHERE project_id = ?")) {
            ps.setObject(1, projectId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(rs.getTimestamp(1).toInstant()) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new IllegalStateException("lecture de l'état de collecte impossible", e);
        }
    }

    /**
     * Remplace les compteurs des jours {@code debut} à {@code fin} et l'état des jalons, puis marque la
     * collecte à jour. Rejouée sur la même fenêtre, elle produit le même résultat (idempotence).
     */
    @Transactional
    void enregistrer(UUID projectId, LocalDate debut, LocalDate fin, Map<LocalDate, Map<Agregation.Type, Integer>> compteurs,
            List<ActiviteDepot.Jalon> jalons, boolean complet, Instant maintenant, Instant prochaine) {
        try (Connection c = dataSource.getConnection()) {
            try (PreparedStatement ps = c.prepareStatement(
                    "DELETE FROM activite_compteur WHERE project_id = ? AND jour BETWEEN ? AND ?")) {
                ps.setObject(1, projectId);
                ps.setDate(2, Date.valueOf(debut));
                ps.setDate(3, Date.valueOf(fin));
                ps.executeUpdate();
            }
            try (PreparedStatement ps = c.prepareStatement(
                    "INSERT INTO activite_compteur(project_id, jour, type, valeur) VALUES (?, ?, ?, ?)")) {
                for (Map.Entry<LocalDate, Map<Agregation.Type, Integer>> jour : compteurs.entrySet()) {
                    for (Map.Entry<Agregation.Type, Integer> type : jour.getValue().entrySet()) {
                        ps.setObject(1, projectId);
                        ps.setDate(2, Date.valueOf(jour.getKey()));
                        ps.setString(3, type.getKey().name());
                        ps.setInt(4, type.getValue());
                        ps.addBatch();
                    }
                }
                ps.executeBatch();
            }
            try (PreparedStatement ps = c.prepareStatement("DELETE FROM activite_jalon WHERE project_id = ?")) {
                ps.setObject(1, projectId);
                ps.executeUpdate();
            }
            try (PreparedStatement ps = c.prepareStatement("""
                    INSERT INTO activite_jalon(project_id, reference, titre, etat, echeance, tickets_fermes, tickets_total, releve_le)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?) ON CONFLICT (project_id, reference) DO NOTHING
                    """)) {
                for (ActiviteDepot.Jalon j : jalons) {
                    ps.setObject(1, projectId);
                    ps.setString(2, j.reference());
                    ps.setString(3, j.titre());
                    ps.setString(4, j.ouvert() ? "OUVERT" : "FERME");
                    ps.setDate(5, j.echeance() == null ? null : Date.valueOf(j.echeance()));
                    ps.setInt(6, j.ticketsFermes());
                    ps.setInt(7, j.ticketsTotal());
                    ps.setTimestamp(8, Timestamp.from(maintenant));
                    ps.addBatch();
                }
                ps.executeBatch();
            }
            try (PreparedStatement ps = c.prepareStatement("""
                    INSERT INTO activite_collecte(project_id, etat, derniere_mise_a_jour, prochaine_collecte, complet,
                                                  donnees_depuis, motif, tentee_le)
                    VALUES (?, 'A_JOUR', ?, ?, ?, ?, NULL, ?)
                    ON CONFLICT (project_id) DO UPDATE SET etat = 'A_JOUR',
                        derniere_mise_a_jour = excluded.derniere_mise_a_jour,
                        prochaine_collecte = excluded.prochaine_collecte, complet = excluded.complet,
                        donnees_depuis = LEAST(activite_collecte.donnees_depuis, excluded.donnees_depuis),
                        motif = NULL, tentee_le = excluded.tentee_le
                    """)) {
                ps.setObject(1, projectId);
                ps.setTimestamp(2, Timestamp.from(maintenant));
                ps.setTimestamp(3, Timestamp.from(prochaine));
                ps.setBoolean(4, complet);
                ps.setDate(5, Date.valueOf(debut));
                ps.setTimestamp(6, Timestamp.from(maintenant));
                ps.executeUpdate();
            }
        } catch (SQLException e) {
            throw new IllegalStateException("enregistrement de l'activité impossible", e);
        }
    }

    /**
     * Reporte la collecte ({@code REPORTEE_QUOTA} ou {@code ECHEC}) sans toucher aux données ni à la date
     * de dernière mise à jour : l'âge des données affichées reste exact (PIN-05-S1).
     */
    @Transactional
    void reporter(UUID projectId, String etat, Instant prochaine, String motif, Instant maintenant) {
        try (Connection c = dataSource.getConnection(); PreparedStatement ps = c.prepareStatement("""
                INSERT INTO activite_collecte(project_id, etat, derniere_mise_a_jour, prochaine_collecte, complet,
                                              donnees_depuis, motif, tentee_le)
                VALUES (?, ?, NULL, ?, true, NULL, ?, ?)
                ON CONFLICT (project_id) DO UPDATE SET etat = excluded.etat,
                    prochaine_collecte = excluded.prochaine_collecte, motif = excluded.motif, tentee_le = excluded.tentee_le
                """)) {
            ps.setObject(1, projectId);
            ps.setString(2, etat);
            ps.setTimestamp(3, Timestamp.from(prochaine));
            ps.setString(4, motif == null ? null : motif.length() > MOTIF_MAX ? motif.substring(0, MOTIF_MAX) : motif);
            ps.setTimestamp(5, Timestamp.from(maintenant));
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("report de la collecte impossible", e);
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
        try (Connection c = dataSource.getConnection()) {
            try (PreparedStatement ps = c.prepareStatement("DELETE FROM activite_compteur WHERE jour < ?")) {
                ps.setDate(1, Date.valueOf(limite));
                supprimees += ps.executeUpdate();
            }
            try (PreparedStatement ps = c.prepareStatement("DELETE FROM activite_jalon WHERE releve_le < ?")) {
                ps.setTimestamp(1, Timestamp.from(limiteInstant));
                supprimees += ps.executeUpdate();
            }
            try (PreparedStatement ps = c.prepareStatement(
                    "UPDATE activite_collecte SET donnees_depuis = ? WHERE donnees_depuis < ?")) {
                ps.setDate(1, Date.valueOf(limite));
                ps.setDate(2, Date.valueOf(limite));
                ps.executeUpdate();
            }
        } catch (SQLException e) {
            throw new IllegalStateException("purge de l'activité impossible", e);
        }
        return supprimees;
    }
}
