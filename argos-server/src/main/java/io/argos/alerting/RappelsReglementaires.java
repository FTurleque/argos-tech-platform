package io.argos.alerting;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Event;
import jakarta.inject.Inject;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

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

    @Scheduled(identity = "rappels-reglementaires", cron = "{argos.reglementaire.rappels.cron}",
            timeZone = "{argos.alerting.fuseau}", concurrentExecution = Scheduled.ConcurrentExecution.SKIP)
    void quotidien() {
        executer(LocalDate.now(ZoneId.of(fuseau)), Instant.now());
    }

    /** Envoie les rappels du jour ; renvoie les messages créés. Une échéance en échec n'interrompt pas les autres. */
    public List<UUID> executer(LocalDate aujourdhui, Instant maintenant) {
        List<UUID> crees = new ArrayList<>();
        List<EcheancesReglementaires.Echeance> liste;
        try {
            liste = echeances.echeancesDuJour(aujourdhui);
        } catch (RuntimeException e) {
            LOG.errorf(e, "lecture des échéances réglementaires impossible");
            return crees;
        }
        for (EcheancesReglementaires.Echeance e : liste) {
            try {
                crees.addAll(rappeler(e, maintenant));
            } catch (RuntimeException ex) {
                LOG.errorf(ex, "rappel de la fiche réglementaire %s en échec", e.ficheId());
            }
        }
        envoi.envoyer(crees, maintenant);
        return List.copyOf(crees);
    }

    private List<UUID> rappeler(EcheancesReglementaires.Echeance e, Instant maintenant) {
        Map<UUID, String> comptes = new LinkedHashMap<>();
        for (EcheancesReglementaires.Expert x : e.experts()) {
            comptes.putIfAbsent(x.compteId(), x.email());
        }
        for (Destinataires.Destinataire d : destinataires.membresAvecProfil(e.espaceId(), PROFIL)) {
            comptes.putIfAbsent(d.compteId(), d.email());
        }
        String quand = e.joursRestants() == 0 ? "aujourd'hui" : e.joursRestants() == 1 ? "demain"
                : "dans " + e.joursRestants() + " jours";
        String titre = "Entrée en vigueur " + quand + " (" + e.dateEffet() + ") : " + borne(e.titre(), 300);
        String cle = "REGLEMENTAIRE|" + e.ficheId() + "|" + e.dateEffet() + "|J-" + e.jourRappel();
        // L'IHM n'a pas encore d'écran réglementaire : le lien ouvre le texte officiel (comme les rappels
        // d'opportunités) ; la fiche reste consultable par l'API.
        String lien = e.lienOfficiel();
        ContenuAlerte contenu = new ContenuAlerte(List.of(new ContenuAlerte.Element(null,
                titre + ". Domaines : " + String.join(", ", e.domaines()) + ". " + FicheAnalyse.AVIS_JURIDIQUE, null,
                List.of(), List.of(new ContenuAlerte.Source("Journal officiel (DILA, Licence Ouverte)", e.identifiant(),
                        e.lienOfficiel())), lien)), null, "SANS_OBJET");
        List<UUID> crees = new ArrayList<>();
        comptes.forEach((compte, email) -> {
            String canal = email == null || email.isBlank() ? "DESKTOP" : "EMAIL";
            messages.creer("RAPPEL", compte, canal, cle, titre, contenu, maintenant).ifPresent(id -> {
                crees.add(id);
                emises.fire(new AlerteEmise(id, compte, canal, "RAPPEL"));
            });
        });
        return crees;
    }

    private static String borne(String s, int max) {
        return s == null || s.length() <= max ? s : s.substring(0, max) + "…";
    }
}
