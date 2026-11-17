package io.argos.projectintel;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Activité factuelle d'un projet sur une période (PIN-01). Aucun champ ne désigne une personne
 * (PIN-03) ; un pourcentage n'apparaît que dans l'avancement d'un jalon, avec son dénominateur (PIN-02).
 *
 * @param projectId projet consulté
 * @param periode période des compteurs, bornes comprises
 * @param compteurs compteurs agrégés sur la période
 * @param jalons jalons ouverts, ou fermés pendant la fenêtre de collecte, au dernier relevé
 * @param fraicheur date de dernière mise à jour et état de la collecte (PIN-05)
 */
public record VueActivite(UUID projectId, Periode periode, Compteurs compteurs, List<Jalon> jalons,
        Fraicheur fraicheur) {

    public VueActivite {
        jalons = List.copyOf(jalons);
    }

    /** Période de consultation : du jour {@code debut} au jour {@code fin} inclus, soit {@code jours} jours. */
    public record Periode(LocalDate debut, LocalDate fin, int jours) {
    }

    /** Compteurs agrégés par type d'événement. */
    public record Compteurs(int ticketsOuverts, int ticketsFermes, int fusionsOuvertes, int fusionsFusionnees,
            int pipelinesReussis, int pipelinesEchoues, int pipelinesAutres, int versionsPubliees) {
    }

    /**
     * Jalon au dernier relevé. {@code titre} est un texte externe (forge). {@code avancement} est absent
     * sans dénominateur (PIN-02-S2).
     */
    public record Jalon(String titre, String etat, LocalDate echeance, int ticketsFermes, int ticketsTotal,
            Avancement avancement) {
    }

    /**
     * Fraîcheur des données.
     *
     * @param etat {@code JAMAIS_COLLECTE}, {@code A_JOUR}, {@code REPORTEE_QUOTA} ou {@code ECHEC}
     * @param derniereMiseAJour date de la dernière collecte réussie, absente si aucune
     * @param prochaineCollecte date à partir de laquelle la prochaine collecte s'exécute
     * @param complet faux si la dernière collecte a atteint le nombre maximal de pages lues
     * @param donneesDepuis premier jour couvert par l'historique conservé
     * @param motif motif du report ou de l'échec, sans jeton
     */
    public record Fraicheur(String etat, Instant derniereMiseAJour, Instant prochaineCollecte, boolean complet,
            LocalDate donneesDepuis, String motif) {
    }
}
