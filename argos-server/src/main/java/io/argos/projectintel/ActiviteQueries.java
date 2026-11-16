package io.argos.projectintel;

import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.sql.DataSource;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import org.eclipse.microprofile.config.inject.ConfigProperty;

import io.argos.workspace.ContexteBase;
import io.argos.workspace.DonneeInvalide;

/**
 * Consultation de l'activité d'un projet (PIN-01, PIN-02, PIN-05). La lecture s'exécute sous le rôle
 * {@code argos_app} avec le compte de l'appelant : la RLS des tables d'activité ne laisse voir que les
 * projets des espaces dont il est membre (ADR-0016). Aucune requête ne filtre ni ne regroupe par
 * personne : les tables n'en contiennent aucune (PIN-03).
 */
@ApplicationScoped
public class ActiviteQueries {

    /** Période consultable : de 1 jour à la durée de conservation de l'historique (13 mois, Q-2). */
    public static final int JOURS_MAX = 397;

    @Inject
    DataSource dataSource;

    @ConfigProperty(name = "argos.alerting.fuseau")
    String fuseau;

    /** Activité du projet sur les {@code jours} derniers jours, aujourd'hui compris (fuseau commun). */
    public VueActivite activite(UUID projectId, UUID accountId, int jours) {
        return activite(projectId, accountId, jours, LocalDate.now(ZoneId.of(fuseau)));
    }

    /** Activité du projet sur les {@code jours} jours qui se terminent le jour {@code fin}. */
    @Transactional
    public VueActivite activite(UUID projectId, UUID accountId, int jours, LocalDate fin) {
        if (jours < 1 || jours > JOURS_MAX) {
            throw new DonneeInvalide("PERIODE_INVALIDE", "la période doit compter de 1 à " + JOURS_MAX + " jours", "jours");
        }
        LocalDate debut = fin.minusDays(jours - 1L);
        try (Connection c = dataSource.getConnection()) {
            ContexteBase.appliquer(c, new ContexteBase.Contexte(accountId, null, null));
            return new VueActivite(projectId, new VueActivite.Periode(debut, fin, jours), compteurs(c, projectId, debut, fin),
                    jalons(c, projectId), fraicheur(c, projectId));
        } catch (SQLException e) {
            throw new IllegalStateException("lecture de l'activité impossible", e);
        }
    }

    private static VueActivite.Compteurs compteurs(Connection c, UUID projectId, LocalDate debut, LocalDate fin)
            throws SQLException {
        Map<Agregation.Type, Integer> totaux = new EnumMap<>(Agregation.Type.class);
        try (PreparedStatement ps = c.prepareStatement("""
                SELECT type, sum(valeur) FROM activite_compteur
                WHERE project_id = ? AND jour BETWEEN ? AND ? GROUP BY type
                """)) {
            ps.setObject(1, projectId);
            ps.setDate(2, Date.valueOf(debut));
            ps.setDate(3, Date.valueOf(fin));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    totaux.put(Agregation.Type.valueOf(rs.getString(1)), (int) Math.min(Integer.MAX_VALUE, rs.getLong(2)));
                }
            }
        }
        return new VueActivite.Compteurs(n(totaux, Agregation.Type.TICKET_OUVERT), n(totaux, Agregation.Type.TICKET_FERME),
                n(totaux, Agregation.Type.FUSION_OUVERTE), n(totaux, Agregation.Type.FUSION_FUSIONNEE),
                n(totaux, Agregation.Type.PIPELINE_REUSSI), n(totaux, Agregation.Type.PIPELINE_ECHOUE),
                n(totaux, Agregation.Type.PIPELINE_AUTRE), n(totaux, Agregation.Type.VERSION_PUBLIEE));
    }

    private static int n(Map<Agregation.Type, Integer> totaux, Agregation.Type type) {
        return totaux.getOrDefault(type, 0);
    }

    private static List<VueActivite.Jalon> jalons(Connection c, UUID projectId) throws SQLException {
        List<VueActivite.Jalon> jalons = new ArrayList<>();
        try (PreparedStatement ps = c.prepareStatement("""
                SELECT titre, etat, echeance, tickets_fermes, tickets_total FROM activite_jalon WHERE project_id = ?
                ORDER BY etat DESC, echeance NULLS LAST, titre, reference
                """)) {
            ps.setObject(1, projectId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Date echeance = rs.getDate(3);
                    int fermes = rs.getInt(4);
                    int total = rs.getInt(5);
                    jalons.add(new VueActivite.Jalon(rs.getString(1), rs.getString(2),
                            echeance == null ? null : echeance.toLocalDate(), fermes, total,
                            Avancement.de(fermes, total).orElse(null)));
                }
            }
        }
        return jalons;
    }

    private static VueActivite.Fraicheur fraicheur(Connection c, UUID projectId) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("""
                SELECT etat, derniere_mise_a_jour, prochaine_collecte, complet, donnees_depuis, motif
                FROM activite_collecte WHERE project_id = ?
                """)) {
            ps.setObject(1, projectId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return new VueActivite.Fraicheur("JAMAIS_COLLECTE", null, null, false, null, null);
                }
                Date depuis = rs.getDate(5);
                return new VueActivite.Fraicheur(rs.getString(1), instant(rs.getTimestamp(2)), instant(rs.getTimestamp(3)),
                        rs.getBoolean(4), depuis == null ? null : depuis.toLocalDate(), rs.getString(6));
            }
        }
    }

    private static Instant instant(Timestamp t) {
        return t == null ? null : t.toInstant();
    }
}
