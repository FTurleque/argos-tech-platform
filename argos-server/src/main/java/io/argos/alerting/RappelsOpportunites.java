package io.argos.alerting;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Event;
import jakarta.inject.Inject;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import io.argos.impact.RapprochementOpportunites;
import io.argos.impact.ResumeOpportunite;
import io.argos.workspace.DonneeInvalide;
import io.argos.workspace.suivi.SuivisProfil;
import io.quarkus.scheduler.Scheduled;

/**
 * Rappels de date limite du profil {@code artiste} (ART-04, Q-4).
 *
 * <p>Tâche quotidienne ({@link #executer}) : pour chaque compte dont le profil {@code artiste} est actif
 * et qui a enregistré ses suivis, les opportunités qui lui sont restituées (stratégie discipline +
 * territoire + date limite, {@link RapprochementOpportunites}) produisent un rappel chaque jour de rappel
 * du compte (J-30, J-7 et J-1 par défaut, {@code argos.profil-artiste.rappels-jours}, réglables par le
 * compte). Une opportunité « date à confirmer » ne produit aucun rappel.
 *
 * <p>Le rappel porte la date limite, le lien vers l'appel officiel et la mention « aucun avis
 * d'éligibilité » (ART-06). Il part sur le canal {@code EMAIL} quand le compte a une adresse, sinon sur
 * le canal {@code DESKTOP}, et reste consultable dans Argos ; sa clé d'idempotence (opportunité, date
 * limite et jour de rappel) empêche tout second message pour le même rappel.
 */
@ApplicationScoped
public class RappelsOpportunites {

    private static final Logger LOG = Logger.getLogger(RappelsOpportunites.class);

    @Inject
    SuivisProfil suivis;

    @Inject
    RapprochementOpportunites rapprochement;

    @Inject
    RappelsRadar messages;

    @Inject
    EnvoiAlertes envoi;

    @Inject
    Event<AlerteEmise> emises;

    @ConfigProperty(name = "argos.profil-artiste.rappels-jours")
    List<Integer> rappelsParDefaut;

    @ConfigProperty(name = "argos.alerting.lien-base")
    String lienBase;

    @ConfigProperty(name = "argos.alerting.fuseau")
    String fuseau;

    @Scheduled(identity = "rappels-profil-artiste", cron = "{argos.profil-artiste.rappels.cron}",
            timeZone = "{argos.alerting.fuseau}", concurrentExecution = Scheduled.ConcurrentExecution.SKIP)
    void quotidien() {
        executer(LocalDate.now(ZoneId.of(fuseau)), Instant.now());
    }

    /** Jours de rappel par défaut (Q-4). */
    public List<Integer> rappelsParDefaut() {
        return List.copyOf(rappelsParDefaut);
    }

    /** Envoie les rappels du jour ; renvoie les messages créés. Un compte en échec n'interrompt pas les autres. */
    public List<UUID> executer(LocalDate aujourdhui, Instant maintenant) {
        List<UUID> crees = new ArrayList<>();
        if (!rapprochement.actif()) {
            return crees;
        }
        List<SuivisProfil.Suiveur> suiveurs;
        try {
            suiveurs = suivis.suiveurs(RapprochementOpportunites.PROFIL);
        } catch (RuntimeException erreur) {
            LOG.errorf(erreur, "lecture des suiveurs du profil artiste impossible");
            return crees;
        }
        for (SuivisProfil.Suiveur suiveur : suiveurs) {
            try {
                crees.addAll(rappeler(suiveur, aujourdhui, maintenant));
            } catch (RuntimeException erreur) {
                LOG.errorf(erreur, "rappels de date limite du compte %s en échec", suiveur.compteId());
            }
        }
        envoi.envoyer(crees, maintenant);
        return List.copyOf(crees);
    }

    private List<UUID> rappeler(SuivisProfil.Suiveur suiveur, LocalDate aujourdhui, Instant maintenant) {
        List<UUID> crees = new ArrayList<>();
        Set<Integer> jours = new LinkedHashSet<>(suiveur.suivis().rappelsJours().orElse(rappelsParDefaut));
        Set<String> disciplines = Set.copyOf(suiveur.suivis().valeurs("discipline"));
        Set<String> territoires = Set.copyOf(suiveur.suivis().valeurs("territoire"));
        String canal = suiveur.email() == null || suiveur.email().isBlank() ? "DESKTOP" : "EMAIL";
        for (RapprochementOpportunites.Restitution restitution : rapprochement.restituer(disciplines, territoires, aujourdhui)) {
            LocalDate limite = restitution.dateLimite();
            if (limite == null) {
                continue;
            }
            int restant = (int) ChronoUnit.DAYS.between(aujourdhui, limite);
            if (!jours.contains(restant)) {
                continue;
            }
            String quand = restant == 0 ? "aujourd'hui" : restant == 1 ? "demain" : "dans " + restant + " jours";
            String titre = "Date limite " + quand + " (" + limite + ") : " + restitution.titre();
            String cle = "OPPORTUNITE|" + restitution.id() + "|" + limite + "|J-" + restant;
            messages.creer("RAPPEL", suiveur.compteId(), canal, cle, titre, contenu(restitution, titre), maintenant).ifPresent(id -> {
                crees.add(id);
                emises.fire(new AlerteEmise(id, suiveur.compteId(), canal, "RAPPEL"));
            });
        }
        return crees;
    }

    private ContenuAlerte contenu(RapprochementOpportunites.Restitution restitution, String titre) {
        String officiel = restitution.lien();
        String lien = officiel != null ? officiel
                : lienBase.replaceAll("/+$", "") + "/profils/artiste/opportunites/" + restitution.id();
        return new ContenuAlerte(List.of(new ContenuAlerte.Element(null, titre + ". " + ResumeOpportunite.MENTION, null,
                List.of(), List.of(new ContenuAlerte.Source(restitution.sourceLibelle(), restitution.organisme(), officiel)),
                lien)), null, "SANS_OBJET");
    }

    /** Valide des jours de rappel : de 1 à 10 valeurs distinctes entre 0 (jour même) et 365, décroissantes. */
    public static List<Integer> valider(List<Integer> jours) {
        if (jours == null || jours.isEmpty() || jours.size() > 10) {
            throw new DonneeInvalide("DONNEE_INVALIDE", "Indiquez de 1 à 10 jours de rappel.", "rappelsJours");
        }
        Set<Integer> distincts = new LinkedHashSet<>();
        for (Integer jour : jours) {
            if (jour == null || jour < 0 || jour > 365) {
                throw new DonneeInvalide("DONNEE_INVALIDE", "Chaque jour de rappel est compris entre 0 et 365.",
                        "rappelsJours");
            }
            distincts.add(jour);
        }
        return distincts.stream().sorted((gauche, droite) -> droite - gauche).toList();
    }
}
