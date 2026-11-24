package io.argos.alerting;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Event;
import jakarta.inject.Inject;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import io.argos.observabilite.TacheObservee;
import io.argos.regintel.EcheancesReglementaires;
import io.argos.regintel.FicheAnalyse;
import io.quarkus.scheduler.Scheduled;

/**
 * Rappels des entrées en vigueur réglementaires (REG-07, Q-2 : J-90, J-30 et J-7 par défaut, réglables
 * par espace).
 *
 * <p>Tâche quotidienne ({@link #executer}) : chaque fiche validée dont l'entrée en vigueur tombe un jour
 * de rappel de son espace, ou dans les jours de rattrapage qui le suivent, produit un rappel pour les
 * utilisateurs du domaine : les experts de ses domaines et les membres de l'espace dont le profil
 * {@code expert-metier} est actif (PRF-06). Le rappel porte la date d'effet, le lien vers le texte officiel
 * et la mention « aucun avis juridique ». Il part sur le canal {@code EMAIL} quand le compte a une adresse,
 * sinon sur le canal {@code DESKTOP}, et reste consultable dans Argos ; sa clé d'idempotence (fiche, date
 * d'effet, jour de rappel) empêche tout second message pour le même rappel.
 */
@ApplicationScoped
public class RappelsReglementaires {

    private static final Logger LOG = Logger.getLogger(RappelsReglementaires.class);

    /** Profil dont les utilisateurs reçoivent les rappels de leur espace. */
    static final String PROFIL = "expert-metier";

    /** Longueur maximale, en caractères, du titre de la fiche repris dans le titre du rappel. */
    private static final int TITRE_FICHE_MAX = 300;

    @Inject
    EcheancesReglementaires echeances;

    @Inject
    Destinataires destinataires;

    @Inject
    RappelsRadar messages;

    @Inject
    EnvoiAlertes envoi;

    @Inject
    Event<AlerteEmise> emises;

    @ConfigProperty(name = "argos.alerting.fuseau")
    String fuseau;

    @TacheObservee
    @Scheduled(identity = "rappels-reglementaires", cron = "{argos.reglementaire.rappels.cron}",
            timeZone = "{argos.alerting.fuseau}", concurrentExecution = Scheduled.ConcurrentExecution.SKIP)
    void quotidien() {
        executer(LocalDate.now(ZoneId.of(fuseau)), Instant.now());
    }

    /** Envoie les rappels du jour ; renvoie les messages créés. Une échéance en échec n'interrompt pas les autres. */
    public List<UUID> executer(LocalDate aujourdhui, Instant maintenant) {
        List<UUID> crees = new ArrayList<>();
        Optional<List<EcheancesReglementaires.Echeance>> echeancesDuJour = lireEcheancesOuJournaliser(aujourdhui);
        if (echeancesDuJour.isEmpty()) {
            return crees;
        }
        for (EcheancesReglementaires.Echeance echeance : echeancesDuJour.get()) {
            rappelerOuJournaliser(echeance, maintenant, crees);
        }
        envoi.envoyer(crees, maintenant);
        return List.copyOf(crees);
    }

    /**
     * Point d'isolation de la tâche planifiée : échéances illisibles, la panne est journalisée avec sa cause et
     * l'exécution s'achève sans rappel ; les jours de rattrapage couvrent l'exécution suivante.
     */
    private Optional<List<EcheancesReglementaires.Echeance>> lireEcheancesOuJournaliser(LocalDate aujourdhui) {
        try {
            return Optional.of(echeances.echeancesDuJour(aujourdhui));
        } catch (IllegalStateException erreur) {
            LOG.errorf(erreur, "lecture des échéances réglementaires impossible");
            return Optional.empty();
        }
    }

    /**
     * Point d'isolation de la tâche planifiée : une panne d'accès aux données pour une échéance est journalisée avec sa
     * cause et n'interrompt pas les rappels des autres.
     */
    private void rappelerOuJournaliser(EcheancesReglementaires.Echeance echeance, Instant maintenant, List<UUID> crees) {
        try {
            crees.addAll(rappeler(echeance, maintenant));
        } catch (IllegalStateException erreur) {
            LOG.errorf(erreur, "rappel de la fiche réglementaire %s en échec", echeance.ficheId());
        }
    }

    private List<UUID> rappeler(EcheancesReglementaires.Echeance echeance, Instant maintenant) {
        Map<UUID, String> comptes = new LinkedHashMap<>();
        for (EcheancesReglementaires.Expert expert : echeance.experts()) {
            comptes.putIfAbsent(expert.compteId(), expert.email());
        }
        for (Destinataires.Destinataire destinataire : destinataires.membresAvecProfil(echeance.espaceId(), PROFIL)) {
            comptes.putIfAbsent(destinataire.compteId(), destinataire.email());
        }
        String quand = echeance.joursRestants() == 0 ? "aujourd'hui" : echeance.joursRestants() == 1 ? "demain"
                : "dans " + echeance.joursRestants() + " jours";
        String titre = "Entrée en vigueur " + quand + " (" + echeance.dateEffet() + ") : " + borne(echeance.titre(), TITRE_FICHE_MAX);
        String cle = "REGLEMENTAIRE|" + echeance.ficheId() + "|" + echeance.dateEffet() + "|J-" + echeance.jourRappel();
        // L'IHM n'a pas encore d'écran réglementaire : le lien ouvre le texte officiel (comme les rappels
        // d'opportunités) ; la fiche reste consultable par l'API.
        String lien = echeance.lienOfficiel();
        ContenuAlerte contenu = new ContenuAlerte(List.of(new ContenuAlerte.Element(null,
                titre + ". Domaines : " + String.join(", ", echeance.domaines()) + ". " + FicheAnalyse.AVIS_JURIDIQUE, null,
                List.of(), List.of(new ContenuAlerte.Source("Journal officiel (DILA, Licence Ouverte)", echeance.identifiant(),
                        echeance.lienOfficiel())), lien)), null, "SANS_OBJET");
        RappelsRadar.Message message = new RappelsRadar.Message("RAPPEL", cle, titre, contenu);
        List<UUID> crees = new ArrayList<>();
        comptes.forEach((compte, email) -> {
            String canal = email == null || email.isBlank() ? "DESKTOP" : "EMAIL";
            messages.creer(message, compte, canal, maintenant).ifPresent(id -> {
                crees.add(id);
                emises.fire(new AlerteEmise(id, compte, canal, "RAPPEL"));
            });
        });
        return crees;
    }

    private static String borne(String texte, int max) {
        return texte == null || texte.length() <= max ? texte : texte.substring(0, max) + "…";
    }
}
