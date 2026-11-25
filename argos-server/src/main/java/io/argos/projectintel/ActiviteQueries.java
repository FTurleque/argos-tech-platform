package io.argos.projectintel;

import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
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

import io.argos.horloge.LectureHeure;
import io.argos.commun.ContexteBase;
import io.argos.commun.DonneeInvalide;

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
    Clock horloge;

    @Inject
    DataSource dataSource;

    @ConfigProperty(name = "argos.alerting.fuseau")
    String fuseau;

    /** Activité du projet sur les {@code jours} derniers jours, aujourd'hui compris (fuseau commun). */
    public VueActivite activite(UUID projectId, UUID accountId, int jours) {
        return activite(projectId, accountId, jours, LectureHeure.jour(horloge));
    }

    /** Activité du projet sur les {@code jours} jours qui se terminent le jour {@code fin}. */
    @Transactional
    public VueActivite activite(UUID projectId, UUID accountId, int jours, LocalDate fin) {
        if (jours < 1 || jours > JOURS_MAX) {
            throw new DonneeInvalide("PERIODE_INVALIDE", "la période doit compter de 1 à " + JOURS_MAX + " jours", "jours");
        }
        LocalDate debut = fin.minusDays(jours - 1L);
        try (Connection connexion = dataSource.getConnection()) {
            ContexteBase.appliquer(connexion, ContexteBase.Contexte.duCompte(accountId));
            return new VueActivite(projectId, new VueActivite.Periode(debut, fin, jours), compteurs(connexion, projectId, debut, fin),
                    jalons(connexion, projectId), fraicheur(connexion, projectId));
        } catch (SQLException erreur) {
            throw new IllegalStateException("lecture de l'activité impossible", erreur);
        }
    }

    private static VueActivite.Compteurs compteurs(Connection connexion, UUID projectId, LocalDate debut, LocalDate fin)
            throws SQLException {
        Map<Agregation.Type, Integer> totaux = new EnumMap<>(Agregation.Type.class);
        try (PreparedStatement requete = connexion.prepareStatement("""
                SELECT type, sum(valeur) FROM activite_compteur
                WHERE project_id = ? AND jour BETWEEN ? AND ? GROUP BY type
                """)) {
            requete.setObject(1, projectId);
            requete.setDate(2, Date.valueOf(debut));
            requete.setDate(3, Date.valueOf(fin));
            try (ResultSet resultat = requete.executeQuery()) {
                while (resultat.next()) {
                    totaux.put(Agregation.Type.valueOf(resultat.getString(1)), (int) Math.min(Integer.MAX_VALUE, resultat.getLong(2)));
                }
            }
        }
        return new VueActivite.Compteurs(nombre(totaux, Agregation.Type.TICKET_OUVERT), nombre(totaux, Agregation.Type.TICKET_FERME),
                nombre(totaux, Agregation.Type.FUSION_OUVERTE), nombre(totaux, Agregation.Type.FUSION_FUSIONNEE),
                nombre(totaux, Agregation.Type.PIPELINE_REUSSI), nombre(totaux, Agregation.Type.PIPELINE_ECHOUE),
                nombre(totaux, Agregation.Type.PIPELINE_AUTRE), nombre(totaux, Agregation.Type.VERSION_PUBLIEE));
    }

    private static int nombre(Map<Agregation.Type, Integer> totaux, Agregation.Type type) {
        return totaux.getOrDefault(type, 0);
    }

    private static List<VueActivite.Jalon> jalons(Connection connexion, UUID projectId) throws SQLException {
        List<VueActivite.Jalon> jalons = new ArrayList<>();
        try (PreparedStatement requete = connexion.prepareStatement("""
                SELECT titre, etat, echeance, tickets_fermes, tickets_total FROM activite_jalon WHERE project_id = ?
                ORDER BY etat DESC, echeance NULLS LAST, titre, reference
                """)) {
            requete.setObject(1, projectId);
            try (ResultSet resultat = requete.executeQuery()) {
                while (resultat.next()) {
                    Date echeance = resultat.getDate(3);
                    int fermes = resultat.getInt(4);
                    int total = resultat.getInt(5);
                    jalons.add(new VueActivite.Jalon(resultat.getString(1), resultat.getString(2),
                            echeance == null ? null : echeance.toLocalDate(), fermes, total,
                            Avancement.calculer(fermes, total).orElse(null)));
                }
            }
        }
        return jalons;
    }

    private static VueActivite.Fraicheur fraicheur(Connection connexion, UUID projectId) throws SQLException {
        try (PreparedStatement requete = connexion.prepareStatement("""
                SELECT etat, derniere_mise_a_jour, prochaine_collecte, complet, donnees_depuis, motif
                FROM activite_collecte WHERE project_id = ?
                """)) {
            requete.setObject(1, projectId);
            try (ResultSet resultat = requete.executeQuery()) {
                if (!resultat.next()) {
                    return new VueActivite.Fraicheur("JAMAIS_COLLECTE", null, null, false, null, null);
                }
                Date depuis = resultat.getDate(5);
                return new VueActivite.Fraicheur(resultat.getString(1), instant(resultat.getTimestamp(2)),
                        instant(resultat.getTimestamp(3)), resultat.getBoolean(4), depuis == null ? null : depuis.toLocalDate(),
                        resultat.getString(6));
            }
        }
    }

    private static Instant instant(Timestamp horodatage) {
        return horodatage == null ? null : horodatage.toInstant();
    }
}
