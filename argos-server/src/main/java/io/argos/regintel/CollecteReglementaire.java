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
import io.argos.observabilite.TacheObservee;
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

    @TacheObservee
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
        if (jours.isEmpty()) {
            return new Rapport(List.of(), null, null, 0, 0, 0, 0, etat().etat(), List.of(), List.of());
        }
        Map<LocalDate, Jour> suivis = suivis(jours.get(0), jours.get(jours.size() - 1));
        List<String> index;
        try {
            index = archives.index();
        } catch (SourceException injoignable) {
            LOG.warnf(injoignable, "collecte réglementaire en échec, index injoignable : %s", injoignable.getMessage());
            return indexInjoignable(jours, suivis, injoignable, maintenant);
        }
        Execution execution = new Execution(index, rejeu, maintenant);
        Passage passage = new Passage();
        for (LocalDate jour : jours) {
            Jour suivi = suivis.getOrDefault(jour, INCONNU);
            if (!rejeu && "ABANDONNE".equals(suivi.etat())) {
                continue;
            }
            if (!collecterJour(jour, suivi, execution, passage)) {
                break;
            }
        }
        if (passage.enEchec == null && passage.abandonnes.isEmpty() && !passage.collectes.isEmpty()) {
            marquerSucces(maintenant);
        }
        int fiches = preparerFiches(passage.aPreparer, maintenant);
        return new Rapport(passage.collectes, passage.enEchec, passage.erreur, passage.nouveaux, passage.connus,
                passage.refuses, fiches, etat().etat(), passage.abandonnes, passage.ecartees);
    }

    /** Paramètres communs aux jours d'une exécution : index des archives, rejeu et instant de l'exécution. */
    private record Execution(List<String> index, boolean rejeu, Instant maintenant) {
    }

    /** Bilan en cours d'une exécution. */
    private static final class Passage {
        private final List<LocalDate> collectes = new ArrayList<>();
        private final List<LocalDate> abandonnes = new ArrayList<>();
        private final List<String> ecartees = new ArrayList<>();
        private final Set<UUID> aPreparer = new LinkedHashSet<>();
        private int nouveaux;
        private int connus;
        private int refuses;
        private LocalDate enEchec;
        private String erreur;
    }

    /** Source injoignable, déjà journalisée : le premier jour non encore collecté reste à collecter ; aucun échec propre au jour. */
    private Rapport indexInjoignable(List<LocalDate> jours, Map<LocalDate, Jour> suivis, SourceException injoignable,
            Instant maintenant) {
        LocalDate jour = jours.stream().filter(candidat -> !"COLLECTE".equals(suivis.getOrDefault(candidat, INCONNU).etat()))
                .findFirst().orElse(null);
        marquerEchec(jour, injoignable.getMessage(), false, maintenant);
        return new Rapport(List.of(), jour, injoignable.getMessage(), 0, 0, 0, 0, etat().etat(), List.of(), List.of());
    }

    /** Collecte un jour ; faux si son échec arrête l'exécution (jour manqué, repris à l'exécution suivante). */
    private boolean collecterJour(LocalDate jour, Jour suivi, Execution execution, Passage passage) {
        ArchivesJorf.Lecture lecture;
        try {
            lecture = archives.lire(jour, execution.index(), execution.rejeu() ? Set.of() : suivi.archivesLues());
        } catch (SourceException echec) {
            boolean abandon = !execution.rejeu() && !"COLLECTE".equals(suivi.etat()) && suivi.echecs() + 1 >= tentativesMax;
            LOG.warnf(echec, "collecte réglementaire du %s en échec%s", jour, abandon ? ", jour abandonné" : "");
            return echecDuJour(jour, abandon, echec, execution, passage);
        }
        integrerLecture(jour, lecture, execution, passage);
        return true;
    }

    /**
     * Échec des archives du jour, déjà journalisé : le jour est abandonné après {@code tentativesMax} échecs consécutifs
     * ({@code abandon}) et la collecte continue ; sinon il reste en échec et la collecte s'arrête (faux).
     */
    private boolean echecDuJour(LocalDate jour, boolean abandon, SourceException echec, Execution execution, Passage passage) {
        marquerEchec(jour, echec.getMessage(), true, execution.maintenant());
        if (abandon) {
            abandonner(jour, "abandonné après " + tentativesMax + " échecs consécutifs : " + echec.getMessage(),
                    execution.maintenant());
            passage.abandonnes.add(jour);
            return true;
        }
        passage.enEchec = jour;
        passage.erreur = echec.getMessage();
        return false;
    }

    /** Enregistre les textes lus pour le jour et marque le jour collecté. */
    private void integrerLecture(LocalDate jour, ArchivesJorf.Lecture lecture, Execution execution, Passage passage) {
        int nouveauxDuJour = enregistrerTextes(lecture, execution.maintenant(), passage);
        passage.nouveaux += nouveauxDuJour;
        passage.refuses += lecture.fichiersRefuses();
        passage.ecartees.addAll(lecture.archivesEcartees());
        String remarque = lecture.archivesEcartees().isEmpty() ? null
                : "archives écartées : " + String.join(" ; ", lecture.archivesEcartees());
        marquerCollecte(jour, lecture.archives(), nouveauxDuJour, remarque, execution.maintenant());
        if (execution.rejeu()) {
            passage.aPreparer.addAll(textes.idsDesArchives(ArchivesJorf.archivesDu(execution.index(), jour)));
        }
        passage.collectes.add(jour);
    }

    /** Enregistre les textes lus ; renvoie le nombre de textes nouveaux, à préparer. */
    private int enregistrerTextes(ArchivesJorf.Lecture lecture, Instant maintenant, Passage passage) {
        int nouveauxDuJour = 0;
        for (TexteJorf texteJorf : lecture.textes()) {
            var id = textes.enregistrer(texteJorf, maintenant);
            if (id.isPresent()) {
                passage.aPreparer.add(id.get());
                nouveauxDuJour++;
            } else {
                passage.connus++;
            }
        }
        return nouveauxDuJour;
    }

    /** Prépare les fiches des textes nouveaux ; une panne d'accès aux données est journalisée et ne compte aucune fiche. */
    private int preparerFiches(Set<UUID> aPreparer, Instant maintenant) {
        try {
            return preparation.preparer(aPreparer, maintenant);
        } catch (IllegalStateException echecPreparation) {
            LOG.errorf(echecPreparation, "préparation des fiches réglementaires en échec");
            return 0;
        }
    }

    /**
     * Jours à collecter, dans l'ordre : la fenêtre de relecture, précédée des jours manqués depuis le premier
     * jour en échec ou le lendemain du dernier jour collecté, au plus {@code repriseJoursMax} jours.
     */
    List<LocalDate> joursACollecter(LocalDate aujourdhui) {
        LocalDate debut = aujourdhui.minusDays(Math.max(0, relectureJours - 1L));
        try (Connection connexion = dataSource.getConnection(); PreparedStatement requete = connexion.prepareStatement("""
                SELECT (SELECT min(jour) FROM reg_collecte_jour WHERE etat = 'EN_ECHEC'),
                       (SELECT max(jour) FROM reg_collecte_jour WHERE etat = 'COLLECTE')
                """); ResultSet resultat = requete.executeQuery()) {
            resultat.next();
            LocalDate echec = resultat.getObject(1, LocalDate.class);
            LocalDate collecte = resultat.getObject(2, LocalDate.class);
            if (collecte != null && collecte.plusDays(1).isBefore(debut)) {
                debut = collecte.plusDays(1);
            }
            if (echec != null && echec.isBefore(debut)) {
                debut = echec;
            }
        } catch (SQLException erreur) {
            throw new IllegalStateException("lecture des jours collectés impossible", erreur);
        }
        LocalDate borne = borneReprise(aujourdhui);
        if (debut.isBefore(borne)) {
            debut = borne;
        }
        List<LocalDate> jours = new ArrayList<>();
        for (LocalDate jour = debut; !jour.isAfter(aujourdhui); jour = jour.plusDays(1)) {
            jours.add(jour);
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
                """, (connexion, requete) -> {
            requete.setTimestamp(1, Timestamp.from(maintenant));
            requete.setDate(2, Date.valueOf(borneReprise(aujourdhui)));
        });
    }

    private Map<LocalDate, Jour> suivis(LocalDate depuis, LocalDate jusqua) {
        Map<LocalDate, Jour> suivis = new HashMap<>();
        try (Connection connexion = dataSource.getConnection(); PreparedStatement requete = connexion.prepareStatement(
                "SELECT jour, etat, echecs, archives_lues FROM reg_collecte_jour WHERE jour BETWEEN ? AND ?")) {
            requete.setDate(1, Date.valueOf(depuis));
            requete.setDate(2, Date.valueOf(jusqua));
            try (ResultSet resultat = requete.executeQuery()) {
                while (resultat.next()) {
                    suivis.put(resultat.getObject(1, LocalDate.class), new Jour(resultat.getString(2), resultat.getInt(3),
                            Set.copyOf(TextesOfficiels.tableau(resultat.getArray(4)))));
                }
            }
        } catch (SQLException erreur) {
            throw new IllegalStateException("lecture des jours collectés impossible", erreur);
        }
        return suivis;
    }

    /** État de la source (administrateur plateforme). */
    public EtatSource etat() {
        try (Connection connexion = dataSource.getConnection()) {
            List<LocalDate> echecs = new ArrayList<>();
            List<LocalDate> abandonnes = new ArrayList<>();
            LocalDate dernier = null;
            try (PreparedStatement requete = connexion.prepareStatement(
                    "SELECT jour, etat FROM reg_collecte_jour WHERE etat IN ('EN_ECHEC', 'ABANDONNE') ORDER BY jour");
                    ResultSet resultat = requete.executeQuery()) {
                while (resultat.next()) {
                    ("EN_ECHEC".equals(resultat.getString(2)) ? echecs : abandonnes).add(resultat.getObject(1, LocalDate.class));
                }
            }
            try (PreparedStatement requete = connexion.prepareStatement("SELECT max(jour) FROM reg_collecte_jour WHERE etat = 'COLLECTE'");
                    ResultSet resultat = requete.executeQuery()) {
                if (resultat.next()) {
                    dernier = resultat.getObject(1, LocalDate.class);
                }
            }
            try (PreparedStatement requete = connexion.prepareStatement("""
                    SELECT libelle, etat, echecs_consecutifs, dernier_succes_le, dernier_echec_le, derniere_erreur
                    FROM reg_source WHERE id = ?
                    """)) {
                requete.setString(1, SOURCE);
                try (ResultSet resultat = requete.executeQuery()) {
                    if (!resultat.next()) {
                        throw new IllegalStateException("source " + SOURCE + " absente");
                    }
                    return new EtatSource(SOURCE, resultat.getString(1), resultat.getString(2), resultat.getInt(3),
                            instant(resultat.getTimestamp(4)), instant(resultat.getTimestamp(5)), resultat.getString(6), dernier,
                            echecs, abandonnes, FicheAnalyse.SOURCE);
                }
            }
        } catch (SQLException erreur) {
            throw new IllegalStateException("lecture de l'état de la source impossible", erreur);
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
                """, (connexion, requete) -> {
            requete.setDate(1, Date.valueOf(jour));
            requete.setArray(2, connexion.createArrayOf("varchar", lues.toArray()));
            requete.setInt(3, nbTextes);
            requete.setString(4, borne(remarque));
            requete.setTimestamp(5, Timestamp.from(maintenant));
        });
    }

    /**
     * Enregistre un échec : la source passe {@code DEGRADE} ; le jour, s'il est connu, reste {@code COLLECTE}
     * s'il l'était déjà (relecture), passe {@code EN_ECHEC} sinon. Un échec propre aux archives du jour
     * ({@code compter}) incrémente ses échecs consécutifs ; une source injoignable ne les incrémente pas.
     */
    private void marquerEchec(LocalDate jour, String erreur, boolean compter, Instant maintenant) {
        String erreurBornee = borne(erreur);
        if (jour != null) {
            executer("""
                    INSERT INTO reg_collecte_jour (jour, etat, tentatives, echecs, derniere_erreur, modifie_le)
                    VALUES (?, 'EN_ECHEC', 1, ?, ?, ?)
                    ON CONFLICT (jour) DO UPDATE SET
                        etat = CASE WHEN reg_collecte_jour.etat = 'COLLECTE' THEN 'COLLECTE' ELSE 'EN_ECHEC' END,
                        tentatives = reg_collecte_jour.tentatives + 1, echecs = reg_collecte_jour.echecs + excluded.echecs,
                        derniere_erreur = excluded.derniere_erreur, modifie_le = excluded.modifie_le
                    """, (connexion, requete) -> {
                requete.setDate(1, Date.valueOf(jour));
                requete.setInt(2, compter ? 1 : 0);
                requete.setString(3, erreurBornee);
                requete.setTimestamp(4, Timestamp.from(maintenant));
            });
        }
        executer("""
                UPDATE reg_source SET etat = 'DEGRADE', echecs_consecutifs = echecs_consecutifs + 1,
                    dernier_echec_le = ?, derniere_erreur = ? WHERE id = ?
                """, (connexion, requete) -> {
            requete.setTimestamp(1, Timestamp.from(maintenant));
            requete.setString(2, erreurBornee);
            requete.setString(3, SOURCE);
        });
    }

    private void abandonner(LocalDate jour, String motif, Instant maintenant) {
        executer("UPDATE reg_collecte_jour SET etat = 'ABANDONNE', derniere_erreur = ?, modifie_le = ? WHERE jour = ?",
                (connexion, requete) -> {
                    requete.setString(1, borne(motif));
                    requete.setTimestamp(2, Timestamp.from(maintenant));
                    requete.setDate(3, Date.valueOf(jour));
                });
    }

    private void marquerSucces(Instant maintenant) {
        executer("""
                UPDATE reg_source SET etat = 'NOMINAL', echecs_consecutifs = 0, dernier_succes_le = ?, derniere_erreur = NULL
                WHERE id = ?
                """, (connexion, requete) -> {
            requete.setTimestamp(1, Timestamp.from(maintenant));
            requete.setString(2, SOURCE);
        });
    }

    @FunctionalInterface
    private interface Parametrage {
        void appliquer(Connection connexion, PreparedStatement requete) throws SQLException;
    }

    private void executer(String sql, Parametrage parametrage) {
        try (Connection connexion = dataSource.getConnection(); PreparedStatement requete = connexion.prepareStatement(sql)) {
            parametrage.appliquer(connexion, requete);
            requete.executeUpdate();
        } catch (SQLException erreur) {
            throw new IllegalStateException("mise à jour de la collecte réglementaire impossible", erreur);
        }
    }

    private static String borne(String texte) {
        if (texte == null) {
            return null;
        }
        return texte.length() <= 1000 ? texte : texte.substring(0, 1000);
    }

    private static Instant instant(Timestamp horodatage) {
        return horodatage == null ? null : horodatage.toInstant();
    }
}
