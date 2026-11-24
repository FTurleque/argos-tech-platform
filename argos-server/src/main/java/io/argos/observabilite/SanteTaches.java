package io.argos.observabilite;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;

import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import jakarta.interceptor.Interceptor;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.health.HealthCheckResponse;
import org.eclipse.microprofile.health.HealthCheckResponseBuilder;
import org.eclipse.microprofile.health.Readiness;

import io.quarkus.runtime.StartupEvent;
import io.quarkus.scheduler.Scheduler;
import io.quarkus.scheduler.Trigger;
import io.smallrye.health.api.HealthRegistry;

/**
 * Un contrôle de disponibilité par tâche planifiée active (EXP-15-S2, EXP-15-S3, design.md DC-2), nommé
 * {@code tache-<identité>}. Une tâche désactivée par configuration ({@code off}) n'a pas de déclencheur et donc pas
 * de contrôle ; planificateur arrêté (profil de test), aucune tâche n'en a. Chaque contrôle reste {@code UP} : son
 * état ({@link EvaluationTache.Etat}), sa dernière exécution et le retard toléré sont publiés dans ses données ; seule
 * la base de données rend le serveur indisponible (INV-3).
 */
@ApplicationScoped
public class SanteTaches {

    /** Préfixe du nom des contrôles de tâche. */
    public static final String PREFIXE = "tache-";

    /** Priorité de l'enregistrement au démarrage : après la création des déclencheurs par le planificateur. */
    static final int PRIORITE_APRES_PLANIFICATEUR = Interceptor.Priority.APPLICATION + 500;

    @Inject
    Clock horloge;

    @Inject
    Scheduler planificateur;

    @Inject
    EtatTaches etat;

    @Inject
    @Readiness
    HealthRegistry registre;

    @ConfigProperty(name = "argos.observabilite.taches.facteur-retard")
    long facteurRetard;

    private Instant demarrage;

    /** Enregistre les contrôles après la création des déclencheurs par le planificateur. */
    void enregistrer(@Observes @Priority(PRIORITE_APRES_PLANIFICATEUR) StartupEvent demarrageServeur) {
        demarrage = horloge.instant();
        if (!planificateur.isStarted()) {
            return;
        }
        for (Trigger declencheur : planificateur.getScheduledJobs()) {
            String tache = declencheur.getId();
            registre.register(PREFIXE + tache, () -> controler(tache));
        }
    }

    /** Contrôle d'une tâche à l'instant présent. */
    HealthCheckResponse controler(String tache) {
        HealthCheckResponseBuilder reponse = HealthCheckResponse.named(PREFIXE + tache).up();
        Trigger declencheur = planificateur.getScheduledJob(tache);
        if (declencheur == null) {
            return reponse.withData("etat", "desactivee").build();
        }
        Optional<EtatTaches.Execution> derniere = etat.derniere(tache);
        EvaluationTache.Echeances echeances = new EvaluationTache.Echeances(
                Optional.ofNullable(declencheur.getPreviousFireTime()), Optional.ofNullable(declencheur.getNextFireTime()));
        EvaluationTache evaluation = EvaluationTache.evaluer(echeances, derniere, demarrage, facteurRetard, horloge.instant());
        reponse.withData("etat", evaluation.etat().libelle());
        derniere.ifPresent(execution -> reponse.withData("derniere-execution", execution.fin().toString())
                .withData("derniere-issue", execution.reussie() ? EtatTaches.SUCCES : EtatTaches.ECHEC));
        evaluation.retardTolere().ifPresent(retard -> reponse.withData("retard-tolere", retard.toString()));
        return reponse.build();
    }
}
