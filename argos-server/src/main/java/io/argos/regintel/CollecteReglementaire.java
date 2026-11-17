package io.argos.regintel;

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
import java.util.List;
import java.util.UUID;

import javax.sql.DataSource;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import io.argos.ingestion.SourceException;
import io.argos.ingestion.legi.ArchivesJorf;
import io.argos.ingestion.legi.TexteJorf;
import io.quarkus.scheduler.Scheduled;

/**
 * Collecte quotidienne des textes du Journal officiel (REG-01, DC-1) depuis les jeux de données JORF de
 * la DILA ({@link ArchivesJorf}), puis préparation des fiches des nouveaux textes
 * ({@link PreparationFiches}).
 *
 * <p>Chaque exécution collecte le jour courant et reprend les jours manqués : à partir du premier jour en
 * échec, ou du lendemain du dernier jour collecté, dans la limite de {@code argos.reglementaire.reprise-jours-max}
 * jours. Le jour courant est relu à chaque exécution (archives publiées dans la journée) : un texte déjà
 * enregistré ne l'est pas une seconde fois (REG-01-S1). Une source injoignable marque le jour en échec et
 * la source {@code DEGRADE} ; les jours suivants ne sont pas collectés avant le jour manqué, qui est repris
 * au retour de la source (REG-01-S2).
 */
@ApplicationScoped
public class CollecteReglementaire {

    private static final Logger LOG = Logger.getLogger(CollecteReglementaire.class);

    /** Bilan d'une collecte. */
    public record Rapport(List<LocalDate> joursCollectes, LocalDate jourEnEchec, String erreur, int textesNouveaux,
            int textesDejaConnus, int fichiersRefuses, int fichesPreparees, String etatSource) {
        public Rapport {
            joursCollectes = List.copyOf(joursCollectes);
        }
    }

    /** État de la source et des jours collectés (observabilité, administrateur plateforme). */
    public record EtatSource(String source, String libelle, String etat, int echecsConsecutifs, Instant dernierSucces,
            Instant dernierEchec, String derniereErreur, LocalDate dernierJourCollecte, List<LocalDate> joursEnEchec,
            String mentionSource) {
        public EtatSource {
            joursEnEchec = List.copyOf(joursEnEchec);
        }
    }

    static final String SOURCE = "JORF";

    @Inject
    ArchivesJorf archives;

    @Inject
    TextesOfficiels textes;

    @Inject
    PreparationFiches preparation;

    @Inject
    DataSource dataSource;

    @ConfigProperty(name = "argos.reglementaire.reprise-jours-max")
    int repriseJoursMax;

    @ConfigProperty(name = "argos.alerting.fuseau")
    String fuseau;

    @Scheduled(identity = "collecte-reglementaire", cron = "{argos.reglementaire.collecte.cron}",
            timeZone = "{argos.alerting.fuseau}", concurrentExecution = Scheduled.ConcurrentExecution.SKIP)
    void quotidien() {
        collecter(LocalDate.now(ZoneId.of(fuseau)), Instant.now());
    }

    /** Collecte le jour donné et les jours manqués qui le précèdent. */
    public synchronized Rapport collecter(LocalDate aujourdhui, Instant maintenant) {
        return executer(joursACollecter(aujourdhui), maintenant);
    }

    /** Rejoue la collecte d'un seul jour (administrateur plateforme) ; les textes connus sont ignorés. */
    public synchronized Rapport rejouer(LocalDate jour, Instant maintenant) {
        return executer(List.of(jour), maintenant);
    }

    private Rapport executer(List<LocalDate> jours, Instant maintenant) {
        List<LocalDate> collectes = new ArrayList<>();
        List<UUID> nouveaux = new ArrayList<>();
        int connus = 0;
        int refuses = 0;
        LocalDate enEchec = null;
        String erreur = null;
        for (LocalDate jour : jours) {
            ArchivesJorf.Lecture lecture;
            try {
                lecture = archives.lire(jour);
            } catch (SourceException e) {
                enEchec = jour;
                erreur = e.getMessage();
                LOG.warnf("collecte réglementaire du %s en échec : %s", jour, e.getMessage());
                marquerEchec(jour, e.getMessage(), maintenant);
                break;
            }
            int nouveauxDuJour = 0;
            for (TexteJorf t : lecture.textes()) {
                var id = textes.enregistrer(t, maintenant);
                if (id.isPresent()) {
                    nouveaux.add(id.get());
                    nouveauxDuJour++;
                } else {
                    connus++;
                }
            }
            refuses += lecture.fichiersRefuses();
            marquerCollecte(jour, lecture.archives().size(), nouveauxDuJour, maintenant);
            collectes.add(jour);
        }
        if (enEchec == null && !collectes.isEmpty()) {
            marquerSucces(maintenant);
        }
        int fiches = 0;
        try {
            fiches = preparation.preparer(nouveaux, maintenant);
        } catch (RuntimeException e) {
            LOG.errorf(e, "préparation des fiches réglementaires en échec");
        }
        return new Rapport(collectes, enEchec, erreur, nouveaux.size(), connus, refuses, fiches, etat().etat());
    }

    /**
     * Jours à collecter, dans l'ordre : depuis le premier jour en échec ou le lendemain du dernier jour
     * collecté, jusqu'au jour courant, au plus {@code repriseJoursMax} jours. Première collecte : le jour
     * courant seulement.
     */
    List<LocalDate> joursACollecter(LocalDate aujourdhui) {
        LocalDate debut = aujourdhui;
        try (Connection c = dataSource.getConnection(); PreparedStatement ps = c.prepareStatement("""
                SELECT (SELECT min(jour) FROM reg_collecte_jour WHERE etat = 'EN_ECHEC'),
                       (SELECT max(jour) FROM reg_collecte_jour WHERE etat = 'COLLECTE')
                """); ResultSet rs = ps.executeQuery()) {
            rs.next();
            LocalDate echec = rs.getObject(1, LocalDate.class);
            LocalDate collecte = rs.getObject(2, LocalDate.class);
            if (collecte != null && collecte.plusDays(1).isBefore(debut)) {
                debut = collecte.plusDays(1);
            }
            if (echec != null && echec.isBefore(debut)) {
                debut = echec;
            }
        } catch (SQLException e) {
            throw new IllegalStateException("lecture des jours collectés impossible", e);
        }
        LocalDate borne = aujourdhui.minusDays(Math.max(0, repriseJoursMax - 1L));
        if (debut.isBefore(borne)) {
            debut = borne;
        }
        List<LocalDate> jours = new ArrayList<>();
        for (LocalDate j = debut; !j.isAfter(aujourdhui); j = j.plusDays(1)) {
            jours.add(j);
        }
        return jours;
    }

    /** État de la source (administrateur plateforme). */
    public EtatSource etat() {
        try (Connection c = dataSource.getConnection()) {
            List<LocalDate> echecs = new ArrayList<>();
            LocalDate dernier = null;
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT jour FROM reg_collecte_jour WHERE etat = 'EN_ECHEC' ORDER BY jour");
                    ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    echecs.add(rs.getObject(1, LocalDate.class));
                }
            }
            try (PreparedStatement ps = c.prepareStatement("SELECT max(jour) FROM reg_collecte_jour WHERE etat = 'COLLECTE'");
                    ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    dernier = rs.getObject(1, LocalDate.class);
                }
            }
            try (PreparedStatement ps = c.prepareStatement("""
                    SELECT libelle, etat, echecs_consecutifs, dernier_succes_le, dernier_echec_le, derniere_erreur
                    FROM reg_source WHERE id = ?
                    """)) {
                ps.setString(1, SOURCE);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) {
                        throw new IllegalStateException("source " + SOURCE + " absente");
                    }
                    return new EtatSource(SOURCE, rs.getString(1), rs.getString(2), rs.getInt(3), instant(rs.getTimestamp(4)),
                            instant(rs.getTimestamp(5)), rs.getString(6), dernier, echecs, FicheAnalyse.SOURCE);
                }
            }
        } catch (SQLException e) {
            throw new IllegalStateException("lecture de l'état de la source impossible", e);
        }
    }

    private void marquerCollecte(LocalDate jour, int nbArchives, int nbTextes, Instant maintenant) {
        executer("""
                INSERT INTO reg_collecte_jour (jour, etat, tentatives, archives, textes, derniere_erreur, modifie_le)
                VALUES (?, 'COLLECTE', 1, ?, ?, NULL, ?)
                ON CONFLICT (jour) DO UPDATE SET etat = 'COLLECTE', tentatives = reg_collecte_jour.tentatives + 1,
                    archives = excluded.archives, textes = reg_collecte_jour.textes + excluded.textes,
                    derniere_erreur = NULL, modifie_le = excluded.modifie_le
                """, ps -> {
            ps.setDate(1, Date.valueOf(jour));
            ps.setInt(2, nbArchives);
            ps.setInt(3, nbTextes);
            ps.setTimestamp(4, Timestamp.from(maintenant));
        });
    }

    private void marquerEchec(LocalDate jour, String erreur, Instant maintenant) {
        String e = borne(erreur);
        executer("""
                INSERT INTO reg_collecte_jour (jour, etat, tentatives, derniere_erreur, modifie_le)
                VALUES (?, 'EN_ECHEC', 1, ?, ?)
                ON CONFLICT (jour) DO UPDATE SET etat = 'EN_ECHEC', tentatives = reg_collecte_jour.tentatives + 1,
                    derniere_erreur = excluded.derniere_erreur, modifie_le = excluded.modifie_le
                """, ps -> {
            ps.setDate(1, Date.valueOf(jour));
            ps.setString(2, e);
            ps.setTimestamp(3, Timestamp.from(maintenant));
        });
        executer("""
                UPDATE reg_source SET etat = 'DEGRADE', echecs_consecutifs = echecs_consecutifs + 1,
                    dernier_echec_le = ?, derniere_erreur = ? WHERE id = ?
                """, ps -> {
            ps.setTimestamp(1, Timestamp.from(maintenant));
            ps.setString(2, e);
            ps.setString(3, SOURCE);
        });
    }

    private void marquerSucces(Instant maintenant) {
        executer("""
                UPDATE reg_source SET etat = 'NOMINAL', echecs_consecutifs = 0, dernier_succes_le = ?, derniere_erreur = NULL
                WHERE id = ?
                """, ps -> {
            ps.setTimestamp(1, Timestamp.from(maintenant));
            ps.setString(2, SOURCE);
        });
    }

    @FunctionalInterface
    private interface Parametrage {
        void appliquer(PreparedStatement ps) throws SQLException;
    }

    private void executer(String sql, Parametrage p) {
        try (Connection c = dataSource.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            p.appliquer(ps);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("mise à jour de la collecte réglementaire impossible", e);
        }
    }

    private static String borne(String s) {
        if (s == null) {
            return null;
        }
        return s.length() <= 1000 ? s : s.substring(0, 1000);
    }

    private static Instant instant(Timestamp t) {
        return t == null ? null : t.toInstant();
    }
}
