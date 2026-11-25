package io.argos.regintel;

import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import javax.sql.DataSource;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.eclipse.microprofile.config.inject.ConfigProperty;

import io.argos.catalog.dictionnaire.DictionnaireMetier;
import io.argos.commun.ContexteBase;
import io.argos.commun.ContexteBase.Contexte;
import io.argos.commun.DonneeInvalide;

/**
 * Échéances des entrées en vigueur (REG-07) : jours de rappel de chaque espace (Q-2 : J-90, J-30 et J-7
 * par défaut, {@code argos.reglementaire.rappels-jours}, réglables par un administrateur de l'espace) et
 * échéances du jour à rappeler, lues pour la tâche planifiée des rappels ({@code alerting}).
 *
 * <p>Seules les fiches validées dont la date d'effet est connue produisent une échéance : une fiche en
 * attente n'est jamais rappelée (REG-05), une date à confirmer n'est jamais rappelée (REG-03-S2).
 */
@ApplicationScoped
public class EcheancesReglementaires {

    /** Nombre maximal de jours de rappel d'un réglage. */
    private static final int JOURS_RAPPEL_MAX = 10;
    /** Écart maximal, en jours, entre un rappel et son échéance. */
    private static final int ECART_RAPPEL_MAX_JOURS = 365;

    /**
     * Échéance à rappeler aujourd'hui, avec les experts des domaines de la fiche.
     *
     * @param jourRappel    jour de rappel concerné (J-n) : il fonde la clé d'idempotence du rappel
     * @param joursRestants jours restants avant l'entrée en vigueur, égal à {@code jourRappel} sauf rattrapage
     */
    public record Echeance(UUID ficheId, UUID espaceId, List<String> domaines, String identifiant, String titre,
            LocalDate dateEffet, int jourRappel, int joursRestants, String lienOfficiel, List<Expert> experts) {
        public Echeance {
            domaines = List.copyOf(domaines);
            experts = List.copyOf(experts);
        }
    }

    /** Expert effectif d'un domaine de la fiche. */
    public record Expert(UUID compteId, String email) {
    }

    @Inject
    Clock horloge;

    @Inject
    ContexteBase base;

    @Inject
    DataSource dataSource;

    @Inject
    DictionnaireMetier dictionnaire;

    @ConfigProperty(name = "argos.reglementaire.rappels-jours")
    List<Integer> rappelsParDefaut;

    @ConfigProperty(name = "argos.reglementaire.legifrance.url")
    String legifrance;

    @ConfigProperty(name = "argos.reglementaire.rappels.rattrapage-jours")
    int rattrapageJours;

    /** Jours de rappel par défaut (Q-2). */
    public List<Integer> rappelsParDefaut() {
        return List.copyOf(rappelsParDefaut);
    }

    /** Jours de rappel de l'espace, ou jours par défaut s'il n'en a pas réglé. */
    public List<Integer> parametres(UUID compte, UUID espace) {
        return base.executer(new Contexte(compte, null, espace), connexion -> {
            try (PreparedStatement requete = connexion.prepareStatement("SELECT rappels_jours FROM reg_parametres WHERE espace_id = ?")) {
                requete.setObject(1, espace);
                try (ResultSet resultat = requete.executeQuery()) {
                    return resultat.next() ? Arrays.asList((Integer[]) resultat.getArray(1).getArray()) : rappelsParDefaut();
                }
            }
        });
    }

    /** Règle les jours de rappel de l'espace (administrateur de l'espace). */
    public List<Integer> modifierParametres(UUID compte, UUID espace, List<Integer> jours) {
        List<Integer> valides = valider(jours);
        return base.executer(new Contexte(compte, null, espace), connexion -> {
            try (PreparedStatement requete = connexion.prepareStatement("""
                    INSERT INTO reg_parametres (espace_id, rappels_jours, modifie_par, modifie_le)
                    VALUES (?, ?, argos_compte_courant(), ?)
                    ON CONFLICT (espace_id) DO UPDATE SET rappels_jours = excluded.rappels_jours,
                        modifie_par = excluded.modifie_par, modifie_le = excluded.modifie_le
                    """)) {
                requete.setObject(1, espace);
                requete.setArray(2, connexion.createArrayOf("integer", valides.toArray()));
                requete.setTimestamp(3, Timestamp.from(horloge.instant()));
                requete.executeUpdate();
            }
            return valides;
        });
    }

    /**
     * Échéances à rappeler aujourd'hui, tous espaces confondus (lecture technique pour la tâche planifiée).
     * Un rappel J-n est dû du jour J-n jusqu'à {@code argos.reglementaire.rappels.rattrapage-jours} jours
     * plus tard : un rappel que la tâche n'a pas envoyé le jour exact (arrêt, échec) part au passage suivant ;
     * la clé d'idempotence empêche tout second message. Quand plusieurs rappels sont dus, seul le plus proche
     * de l'échéance part.
     */
    public List<Echeance> echeancesDuJour(LocalDate aujourdhui) {
        List<Echeance> liste = new ArrayList<>();
        String sql = """
                SELECT f.id, f.espace_id, t.identifiant, t.titre, f.date_effet, p.rappels_jours,
                       ARRAY(SELECT fd.domaine FROM fiche_domaine fd WHERE fd.fiche_id = f.id ORDER BY fd.domaine)
                FROM fiche_reglementaire f
                JOIN texte_officiel t ON t.id = f.texte_id
                LEFT JOIN reg_parametres p ON p.espace_id = f.espace_id
                WHERE f.etat_validation = 'VALIDEE' AND f.date_effet BETWEEN ? AND ?
                ORDER BY f.date_effet, f.id
                """;
        record Ligne(UUID fiche, UUID espace, String identifiant, String titre, LocalDate effet, List<Integer> jours,
                List<String> domaines) {
        }
        List<Ligne> lignes = new ArrayList<>();
        try (Connection connexion = dataSource.getConnection(); PreparedStatement requete = connexion.prepareStatement(sql)) {
            requete.setDate(1, Date.valueOf(aujourdhui));
            requete.setDate(2, Date.valueOf(aujourdhui.plusDays(365)));
            try (ResultSet resultat = requete.executeQuery()) {
                while (resultat.next()) {
                    List<Integer> jours = resultat.getArray(6) == null ? rappelsParDefaut()
                            : Arrays.asList((Integer[]) resultat.getArray(6).getArray());
                    lignes.add(new Ligne(resultat.getObject(1, UUID.class), resultat.getObject(2, UUID.class), resultat.getString(3),
                            resultat.getString(4), resultat.getObject(5, LocalDate.class), jours,
                            Arrays.asList((String[]) resultat.getArray(7).getArray())));
                }
            }
        } catch (SQLException erreur) {
            throw new IllegalStateException("lecture des échéances réglementaires impossible", erreur);
        }
        String racine = legifrance.replaceAll("/+$", "");
        Map<String, List<Expert>> experts = new LinkedHashMap<>();
        for (Ligne ligne : lignes) {
            int restant = (int) ChronoUnit.DAYS.between(aujourdhui, ligne.effet());
            int rattrapage = Math.max(1, rattrapageJours);
            Integer jour = ligne.jours().stream().filter(rappel -> restant <= rappel && restant > rappel - rattrapage)
                    .min(Integer::compare).orElse(null);
            if (jour == null) {
                continue;
            }
            List<Expert> expertsDuDomaine = experts.computeIfAbsent(ligne.espace() + "|" + ligne.domaines(),
                    cle -> dictionnaire.experts(ligne.espace(), ligne.domaines()).stream()
                            .map(expert -> new Expert(expert.compteId(), expert.email())).toList());
            liste.add(new Echeance(ligne.fiche(), ligne.espace(), ligne.domaines(), ligne.identifiant(), ligne.titre(),
                    ligne.effet(), jour, restant, racine + "/jorf/id/" + ligne.identifiant(), expertsDuDomaine));
        }
        return liste;
    }

    /** De 1 à 10 jours distincts entre 0 et 365, triés du plus lointain au plus proche. */
    public static List<Integer> valider(List<Integer> jours) {
        if (jours == null || jours.isEmpty() || jours.size() > JOURS_RAPPEL_MAX) {
            throw new DonneeInvalide("DONNEE_INVALIDE", "Indiquez de 1 à 10 jours de rappel.", "rappelsJours");
        }
        Set<Integer> distincts = new LinkedHashSet<>();
        for (Integer jour : jours) {
            if (jour == null || jour < 0 || jour > ECART_RAPPEL_MAX_JOURS) {
                throw new DonneeInvalide("DONNEE_INVALIDE", "Chaque jour de rappel est compris entre 0 et 365.",
                        "rappelsJours");
            }
            distincts.add(jour);
        }
        return distincts.stream().sorted((gauche, droite) -> droite - gauche).toList();
    }
}
