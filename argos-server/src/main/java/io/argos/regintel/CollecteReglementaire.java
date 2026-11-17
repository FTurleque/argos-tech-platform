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
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
 * <p>Chaque exécution lit l'index des archives une fois, puis parcourt les jours dans l'ordre : les
 * {@code argos.reglementaire.relecture-jours} derniers jours, jour courant compris, précédés des jours
 * manqués (premier jour en échec, ou lendemain du dernier jour collecté), dans la limite de
 * {@code argos.reglementaire.reprise-jours-max} jours. Seules les archives encore non lues sont
 * téléchargées ({@code reg_collecte_jour.archives_lues}) : une archive publiée après le passage de la
 * collecte de son jour est lue à l'exécution suivante, tant que ce jour reste dans la fenêtre de relecture.
 * Un texte déjà enregistré ne l'est pas une seconde fois (REG-01-S1).
 *
 * <p>Une source injoignable marque la source {@code DEGRADE} et le jour en échec ; les jours suivants ne
 * sont pas collectés avant le jour manqué, repris au retour de la source (REG-01-S2). Une archive en
 * erreur permanente (absente, trop volumineuse) est écartée sans bloquer son jour. Un jour dont les
 * archives échouent {@code argos.reglementaire.tentatives-max} fois de suite, ou resté en échec au-delà de
 * la fenêtre de reprise, est abandonné ({@code ABANDONNE}) : la collecte passe aux jours suivants, et
 * l'administrateur plateforme peut le rejouer ({@link #rejouer}).
 */
@ApplicationScoped
public class CollecteReglementaire {

    private static final Logger LOG = Logger.getLogger(CollecteReglementaire.class);

    /** Bilan d'une collecte. */
    public record Rapport(List<LocalDate> joursCollectes, LocalDate jourEnEchec, String erreur, int textesNouveaux,
            int textesDejaConnus, int fichiersRefuses, int fichesPreparees, String etatSource,
            List<LocalDate> joursAbandonnes, List<String> archivesEcartees) {
        public Rapport {
            joursCollectes = List.copyOf(joursCollectes);
            joursAbandonnes = List.copyOf(joursAbandonnes);
            archivesEcartees = List.copyOf(archivesEcartees);
        }
    }

    /** État de la source et des jours collectés (observabilité, administrateur plateforme). */
    public record EtatSource(String source, String libelle, String etat, int echecsConsecutifs, Instant dernierSucces,
            Instant dernierEchec, String derniereErreur, LocalDate dernierJourCollecte, List<LocalDate> joursEnEchec,
            List<LocalDate> joursAbandonnes, String mentionSource) {
        public EtatSource {
            joursEnEchec = List.copyOf(joursEnEchec);
            joursAbandonnes = List.copyOf(joursAbandonnes);
        }
    }

    static final String SOURCE = "JORF";

    /** Suivi d'un jour : état, échecs consécutifs de ses archives et archives déjà lues. */
    record Jour(String etat, int echecs, Set<String> archivesLues) {
    }

    private static final Jour INCONNU = new Jour(null, 0, Set.of());

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

    @ConfigProperty(name = "argos.reglementaire.relecture-jours")
    int relectureJours;

    @ConfigProperty(name = "argos.reglementaire.tentatives-max")
    int tentativesMax;

    @ConfigProperty(name = "argos.alerting.fuseau")
    String fuseau;

    @Scheduled(identity = "collecte-reglementaire", cron = "{argos.reglementaire.collecte.cron}",
            timeZone = "{argos.alerting.fuseau}", concurrentExecution = Scheduled.ConcurrentExecution.SKIP)
    void quotidien() {
        collecter(LocalDate.now(ZoneId.of(fuseau)), Instant.now());
    }

    /** Collecte la fenêtre de relecture et les jours manqués qui la précèdent. */
    public synchronized Rapport collecter(LocalDate aujourdhui, Instant maintenant) {
        abandonnerHorsFenetre(aujourdhui, maintenant);
        return executer(joursACollecter(aujourdhui), false, maintenant);
    }

    /**
     * Rejoue la collecte d'un seul jour, même abandonné (administrateur plateforme) : toutes ses archives
     * sont relues, les textes connus ignorés, et les fiches de ses textes préparées là où elles manquent
     * (préparation en échec, dictionnaire créé après la collecte).
     */
    public synchronized Rapport rejouer(LocalDate jour, Instant maintenant) {
        return executer(List.of(jour), true, maintenant);
    }

    private Rapport executer(List<LocalDate> jours, boolean rejeu, Instant maintenant) {
        List<LocalDate> collectes = new ArrayList<>();
        List<LocalDate> abandonnes = new ArrayList<>();
        List<String> ecartees = new ArrayList<>();
        Set<UUID> aPreparer = new LinkedHashSet<>();
        int nouveaux = 0;
        int connus = 0;
        int refuses = 0;
        LocalDate enEchec = null;
        String erreur = null;
        if (jours.isEmpty()) {
            return new Rapport(collectes, null, null, 0, 0, 0, 0, etat().etat(), abandonnes, ecartees);
        }
        Map<LocalDate, Jour> suivis = suivis(jours.get(0), jours.get(jours.size() - 1));
        List<String> index;
        try {
            index = archives.index();
        } catch (SourceException e) {
            // Source injoignable : le premier jour non encore collecté reste à collecter ; aucun échec propre au jour.
            LocalDate jour = jours.stream().filter(j -> !"COLLECTE".equals(suivis.getOrDefault(j, INCONNU).etat()))
                    .findFirst().orElse(null);
            LOG.warnf("collecte réglementaire en échec, index injoignable : %s", e.getMessage());
            marquerEchec(jour, e.getMessage(), false, maintenant);
            return new Rapport(collectes, jour, e.getMessage(), 0, 0, 0, 0, etat().etat(), abandonnes, ecartees);
        }
        for (LocalDate jour : jours) {
            Jour suivi = suivis.getOrDefault(jour, INCONNU);
            if (!rejeu && "ABANDONNE".equals(suivi.etat())) {
                continue;
            }
            ArchivesJorf.Lecture lecture;
            try {
                lecture = archives.lire(jour, index, rejeu ? Set.of() : suivi.archivesLues());
            } catch (SourceException e) {
                boolean abandon = !rejeu && !"COLLECTE".equals(suivi.etat()) && suivi.echecs() + 1 >= tentativesMax;
                LOG.warnf("collecte réglementaire du %s en échec%s : %s", jour, abandon ? ", jour abandonné" : "",
                        e.getMessage());
                marquerEchec(jour, e.getMessage(), true, maintenant);
                if (abandon) {
                    abandonner(jour, "abandonné après " + tentativesMax + " échecs consécutifs : " + e.getMessage(),
                            maintenant);
                    abandonnes.add(jour);
                    continue;
                }
                enEchec = jour;
                erreur = e.getMessage();
                break;
            }
            int nouveauxDuJour = 0;
            for (TexteJorf t : lecture.textes()) {
                var id = textes.enregistrer(t, maintenant);
                if (id.isPresent()) {
                    aPreparer.add(id.get());
                    nouveauxDuJour++;
                } else {
                    connus++;
                }
            }
            nouveaux += nouveauxDuJour;
            refuses += lecture.fichiersRefuses();
            ecartees.addAll(lecture.archivesEcartees());
            String remarque = lecture.archivesEcartees().isEmpty() ? null
                    : "archives écartées : " + String.join(" ; ", lecture.archivesEcartees());
            marquerCollecte(jour, lecture.archives(), nouveauxDuJour, remarque, maintenant);
            if (rejeu) {
                aPreparer.addAll(textes.idsDesArchives(ArchivesJorf.archivesDu(index, jour)));
            }
            collectes.add(jour);
        }
        if (enEchec == null && abandonnes.isEmpty() && !collectes.isEmpty()) {
            marquerSucces(maintenant);
        }
        int fiches = 0;
        try {
            fiches = preparation.preparer(aPreparer, maintenant);
        } catch (RuntimeException e) {
            LOG.errorf(e, "préparation des fiches réglementaires en échec");
        }
        return new Rapport(collectes, enEchec, erreur, nouveaux, connus, refuses, fiches, etat().etat(), abandonnes,
                ecartees);
    }

    /**
     * Jours à collecter, dans l'ordre : la fenêtre de relecture, précédée des jours manqués depuis le premier
     * jour en échec ou le lendemain du dernier jour collecté, au plus {@code repriseJoursMax} jours.
     */
    List<LocalDate> joursACollecter(LocalDate aujourdhui) {
        LocalDate debut = aujourdhui.minusDays(Math.max(0, relectureJours - 1L));
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
        LocalDate borne = borneReprise(aujourdhui);
        if (debut.isBefore(borne)) {
            debut = borne;
        }
        List<LocalDate> jours = new ArrayList<>();
        for (LocalDate j = debut; !j.isAfter(aujourdhui); j = j.plusDays(1)) {
            jours.add(j);
        }
        return jours;
    }

    private LocalDate borneReprise(LocalDate aujourdhui) {
        return aujourdhui.minusDays(Math.max(0, repriseJoursMax - 1L));
    }

    /** Abandonne les jours en échec sortis de la fenêtre de reprise : ils ne sont plus repris automatiquement. */
    private void abandonnerHorsFenetre(LocalDate aujourdhui, Instant maintenant) {
        executer("""
                UPDATE reg_collecte_jour SET etat = 'ABANDONNE', modifie_le = ?,
                    derniere_erreur = left('hors de la fenêtre de reprise : ' || coalesce(derniere_erreur, ''), 1000)
                WHERE etat = 'EN_ECHEC' AND jour < ?
                """, (c, ps) -> {
            ps.setTimestamp(1, Timestamp.from(maintenant));
            ps.setDate(2, Date.valueOf(borneReprise(aujourdhui)));
        });
    }

    private Map<LocalDate, Jour> suivis(LocalDate depuis, LocalDate jusqua) {
        Map<LocalDate, Jour> suivis = new HashMap<>();
        try (Connection c = dataSource.getConnection(); PreparedStatement ps = c.prepareStatement(
                "SELECT jour, etat, echecs, archives_lues FROM reg_collecte_jour WHERE jour BETWEEN ? AND ?")) {
            ps.setDate(1, Date.valueOf(depuis));
            ps.setDate(2, Date.valueOf(jusqua));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    suivis.put(rs.getObject(1, LocalDate.class), new Jour(rs.getString(2), rs.getInt(3),
                            Set.copyOf(TextesOfficiels.tableau(rs.getArray(4)))));
                }
            }
        } catch (SQLException e) {
            throw new IllegalStateException("lecture des jours collectés impossible", e);
        }
        return suivis;
    }

    /** État de la source (administrateur plateforme). */
    public EtatSource etat() {
        try (Connection c = dataSource.getConnection()) {
            List<LocalDate> echecs = new ArrayList<>();
            List<LocalDate> abandonnes = new ArrayList<>();
            LocalDate dernier = null;
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT jour, etat FROM reg_collecte_jour WHERE etat IN ('EN_ECHEC', 'ABANDONNE') ORDER BY jour");
                    ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    ("EN_ECHEC".equals(rs.getString(2)) ? echecs : abandonnes).add(rs.getObject(1, LocalDate.class));
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
                            instant(rs.getTimestamp(5)), rs.getString(6), dernier, echecs, abandonnes, FicheAnalyse.SOURCE);
                }
            }
        } catch (SQLException e) {
            throw new IllegalStateException("lecture de l'état de la source impossible", e);
        }
    }

    private void marquerCollecte(LocalDate jour, List<String> lues, int nbTextes, String remarque, Instant maintenant) {
        executer("""
                INSERT INTO reg_collecte_jour (jour, etat, tentatives, echecs, archives_lues, textes, derniere_erreur, modifie_le)
                VALUES (?, 'COLLECTE', 1, 0, ?, ?, ?, ?)
                ON CONFLICT (jour) DO UPDATE SET etat = 'COLLECTE', tentatives = reg_collecte_jour.tentatives + 1, echecs = 0,
                    archives_lues = ARRAY(SELECT DISTINCT a FROM unnest(reg_collecte_jour.archives_lues || excluded.archives_lues) a
                                          ORDER BY a),
                    textes = reg_collecte_jour.textes + excluded.textes,
                    derniere_erreur = excluded.derniere_erreur, modifie_le = excluded.modifie_le
                """, (c, ps) -> {
            ps.setDate(1, Date.valueOf(jour));
            ps.setArray(2, c.createArrayOf("varchar", lues.toArray()));
            ps.setInt(3, nbTextes);
            ps.setString(4, borne(remarque));
            ps.setTimestamp(5, Timestamp.from(maintenant));
        });
    }

    /**
     * Enregistre un échec : la source passe {@code DEGRADE} ; le jour, s'il est connu, reste {@code COLLECTE}
     * s'il l'était déjà (relecture), passe {@code EN_ECHEC} sinon. Un échec propre aux archives du jour
     * ({@code compter}) incrémente ses échecs consécutifs ; une source injoignable ne les incrémente pas.
     */
    private void marquerEchec(LocalDate jour, String erreur, boolean compter, Instant maintenant) {
        String e = borne(erreur);
        if (jour != null) {
            executer("""
                    INSERT INTO reg_collecte_jour (jour, etat, tentatives, echecs, derniere_erreur, modifie_le)
                    VALUES (?, 'EN_ECHEC', 1, ?, ?, ?)
                    ON CONFLICT (jour) DO UPDATE SET
                        etat = CASE WHEN reg_collecte_jour.etat = 'COLLECTE' THEN 'COLLECTE' ELSE 'EN_ECHEC' END,
                        tentatives = reg_collecte_jour.tentatives + 1, echecs = reg_collecte_jour.echecs + excluded.echecs,
                        derniere_erreur = excluded.derniere_erreur, modifie_le = excluded.modifie_le
                    """, (c, ps) -> {
                ps.setDate(1, Date.valueOf(jour));
                ps.setInt(2, compter ? 1 : 0);
                ps.setString(3, e);
                ps.setTimestamp(4, Timestamp.from(maintenant));
            });
        }
        executer("""
                UPDATE reg_source SET etat = 'DEGRADE', echecs_consecutifs = echecs_consecutifs + 1,
                    dernier_echec_le = ?, derniere_erreur = ? WHERE id = ?
                """, (c, ps) -> {
            ps.setTimestamp(1, Timestamp.from(maintenant));
            ps.setString(2, e);
            ps.setString(3, SOURCE);
        });
    }

    private void abandonner(LocalDate jour, String motif, Instant maintenant) {
        executer("UPDATE reg_collecte_jour SET etat = 'ABANDONNE', derniere_erreur = ?, modifie_le = ? WHERE jour = ?",
                (c, ps) -> {
                    ps.setString(1, borne(motif));
                    ps.setTimestamp(2, Timestamp.from(maintenant));
                    ps.setDate(3, Date.valueOf(jour));
                });
    }

    private void marquerSucces(Instant maintenant) {
        executer("""
                UPDATE reg_source SET etat = 'NOMINAL', echecs_consecutifs = 0, dernier_succes_le = ?, derniere_erreur = NULL
                WHERE id = ?
                """, (c, ps) -> {
            ps.setTimestamp(1, Timestamp.from(maintenant));
            ps.setString(2, SOURCE);
        });
    }

    @FunctionalInterface
    private interface Parametrage {
        void appliquer(Connection c, PreparedStatement ps) throws SQLException;
    }

    private void executer(String sql, Parametrage p) {
        try (Connection c = dataSource.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            p.appliquer(c, ps);
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
