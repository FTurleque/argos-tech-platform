package io.argos.projectintel;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.function.Consumer;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import io.argos.execution.ExecuteurBorne;
import io.argos.inventory.ProjectDirectory;
import io.argos.observabilite.ErreursImprevues;
import io.argos.observabilite.TacheObservee;
import io.argos.scm.ActiviteDepot;
import io.argos.scm.QuotaEpuise;
import io.argos.scm.RepositoryActivity;
import io.argos.scm.RepositoryRef;
import io.argos.scm.ScmException;
import io.quarkus.scheduler.Scheduled;

/**
 * Collecte planifiée de l'activité des projets reliés à un dépôt (PIN-01, PIN-05).
 *
 * <p>À chaque passage ({@code argos.activite.cadence}), chaque projet dont la collecte est échue lit
 * l'activité de sa fenêtre ({@code argos.activite.fenetre-jours}), l'agrège par jour sans identité
 * (D1) et remplace les compteurs de ces jours. Les forges sont interrogées en parallèle, les projets d'une même
 * forge en séquence. Un quota épuisé reporte la collecte du projet, et des projets suivants de la même forge, après la
 * réinitialisation annoncée ; les données déjà enregistrées et leur date de dernière mise à jour restent affichées
 * (PIN-05-S1).
 */
@ApplicationScoped
public class CollecteActivite {

    private static final Logger LOG = Logger.getLogger(CollecteActivite.class);

    /** Issue de la collecte d'un projet. */
    public enum Issue { A_JOUR, REPORTEE_QUOTA, ECHEC, SANS_DEPOT }

    /** Bilan d'un passage, avec les erreurs imprévues retenues, que {@link #relancerErreurImprevue()} relance. */
    public record Bilan(int aJour, int reportees, int echecs, List<RuntimeException> erreursImprevues) {

        /** Relance la première erreur imprévue du passage ; sans effet s'il n'y en a pas. */
        public void relancerErreurImprevue() {
            ErreursImprevues.relancer(erreursImprevues);
        }

        /** Somme des deux bilans, erreurs imprévues dans l'ordre. */
        Bilan ajouter(Bilan autre) {
            List<RuntimeException> erreurs = new ArrayList<>(erreursImprevues);
            erreurs.addAll(autre.erreursImprevues());
            return new Bilan(aJour + autre.aJour(), reportees + autre.reportees(), echecs + autre.echecs(), List.copyOf(erreurs));
        }
    }

    @Inject
    Clock horloge;

    @Inject
    ProjectDirectory projets;

    @Inject
    RepositoryActivity forges;

    @Inject
    ActiviteStore store;

    @Inject
    ExecuteurCollecteActivite executeur;

    @ConfigProperty(name = "argos.activite.duree-max-passage", defaultValue = "PT50M")
    Duration dureeMaxPassage;

    @ConfigProperty(name = "argos.alerting.fuseau")
    String fuseau;

    @ConfigProperty(name = "argos.activite.fenetre-jours", defaultValue = "30")
    int fenetreJours;

    @ConfigProperty(name = "argos.activite.frequence", defaultValue = "PT24H")
    Duration frequence;

    @ConfigProperty(name = "argos.activite.delai-apres-echec", defaultValue = "PT1H")
    Duration delaiApresEchec;

    @TacheObservee
    @Scheduled(identity = "argos-activite-collecte", every = "{argos.activite.cadence}",
            concurrentExecution = Scheduled.ConcurrentExecution.SKIP)
    void planifiee() {
        collecter(horloge.instant()).relancerErreurImprevue();
    }

    /**
     * Collecte les projets échus à l'instant {@code maintenant} : échéances lues en une requête, une tâche par forge
     * sur {@link ExecuteurCollecteActivite}, les projets d'une forge en séquence, le tout borné par
     * {@code argos.activite.duree-max-passage}.
     */
    public Bilan collecter(Instant maintenant) {
        Map<UUID, Instant> echeances = store.prochainesCollectes();
        Map<String, List<ProjectDirectory.Project>> parForge = new LinkedHashMap<>();
        for (ProjectDirectory.Project projet : projets.withRepository()) {
            Instant prochaine = echeances.get(projet.id());
            if (prochaine == null || !prochaine.isAfter(maintenant)) {
                parForge.computeIfAbsent(forge(projet), cle -> new ArrayList<>()).add(projet);
            }
        }
        List<List<ProjectDirectory.Project>> lots = List.copyOf(parForge.values());
        List<Callable<Bilan>> taches = lots.stream().map(lot -> (Callable<Bilan>) () -> collecterForge(lot, maintenant)).toList();
        List<ExecuteurBorne.Issue<Bilan>> issues = executeur.executerTous(taches, dureeMaxPassage);
        Bilan bilan = new Bilan(0, 0, 0, List.of());
        for (int indice = 0; indice < issues.size(); indice++) {
            bilan = bilan.ajouter(issueDeForge(issues.get(indice), lots.get(indice), maintenant));
        }
        return bilan;
    }

    /** Bilan d'une forge : celui de sa tâche, la forge collectée sur ce fil si la tâche est refusée, des échecs sinon. */
    private Bilan issueDeForge(ExecuteurBorne.Issue<Bilan> issue, List<ProjectDirectory.Project> lot, Instant maintenant) {
        String forge = forge(lot.get(0));
        return switch (issue.etat()) {
            case OBTENU -> issue.valeur().orElseThrow();
            case REFUSEE -> collecterForge(lot, maintenant);
            case ECHEC -> {
                Throwable cause = issue.cause().orElseThrow();
                LOG.errorf(cause, "collecte de l'activité de la forge %s interrompue", forge);
                yield new Bilan(0, 0, lot.size(),
                        cause instanceof RuntimeException imprevue ? List.of(imprevue) : List.of());
            }
            case DELAI_DEPASSE, INTERROMPUE -> {
                LOG.errorf(issue.cause().orElse(null), "collecte de l'activité de la forge %s arrêtée (%s)", forge, issue.etat());
                yield new Bilan(0, 0, lot.size(), List.of());
            }
        };
    }

    /** Collecte en séquence les projets échus d'une forge ; un quota épuisé reporte les projets suivants (PIN-05-S1). */
    private Bilan collecterForge(List<ProjectDirectory.Project> lot, Instant maintenant) {
        int aJour = 0;
        int reportees = 0;
        int echecs = 0;
        Map<String, QuotaEpuise> quotas = new HashMap<>();
        ErreursImprevues imprevues = new ErreursImprevues();
        for (ProjectDirectory.Project projet : lot) {
            switch (collecterEchu(projet, maintenant, quotas, imprevues)) {
                case A_JOUR -> aJour++;
                case REPORTEE_QUOTA -> reportees++;
                case ECHEC -> echecs++;
                default -> { }
            }
        }
        return new Bilan(aJour, reportees, echecs, imprevues.retenues());
    }

    private static String forge(ProjectDirectory.Project projet) {
        return projet.repository().orElseThrow().provider().toUpperCase(Locale.ROOT);
    }

    /**
     * Collecte un projet échu ; un quota épuisé sur sa forge pendant ce passage reporte sa collecte. Une panne d'accès
     * aux données ou une erreur imprévue est journalisée avec sa cause et compte comme un échec ; l'erreur imprévue est
     * retenue pour être relancée à la fin du passage (arc42 §8.5).
     */
    private Issue collecterEchu(ProjectDirectory.Project projet, Instant maintenant, Map<String, QuotaEpuise> quotas,
            ErreursImprevues imprevues) {
        try {
            RepositoryRef depot = projet.repository().orElseThrow();
            String forge = depot.provider().toUpperCase(Locale.ROOT);
            QuotaEpuise quota = quotas.get(forge);
            if (quota != null && quota.reprise().isAfter(maintenant)) {
                store.reporter(projet.id(), "REPORTEE_QUOTA", quota.reprise(), quota.getMessage(), maintenant);
                return Issue.REPORTEE_QUOTA;
            }
            return collecterDepot(projet.id(), depot, maintenant, epuisement -> quotas.put(forge, epuisement));
        } catch (IllegalStateException erreur) {
            LOG.errorf(erreur, "collecte de l'activité du projet %s en échec", projet.id());
            return Issue.ECHEC;
        } catch (RuntimeException imprevue) {
            LOG.errorf(imprevue, "collecte de l'activité du projet %s interrompue par une erreur imprévue", projet.id());
            imprevues.retenir(imprevue);
            return Issue.ECHEC;
        }
    }

    /** Collecte immédiate d'un projet, échue ou non. */
    public Issue collecterProjet(UUID projectId, Instant maintenant) {
        Optional<ProjectDirectory.Project> projet = projets.find(projectId);
        if (projet.isEmpty() || projet.get().repository().isEmpty()) {
            return Issue.SANS_DEPOT;
        }
        return collecterDepot(projectId, projet.get().repository().get(), maintenant, epuisement -> { });
    }

    private Issue collecterDepot(UUID projectId, RepositoryRef depot, Instant maintenant,
            Consumer<QuotaEpuise> surQuota) {
        ZoneId zone = ZoneId.of(fuseau);
        LocalDate fin = LocalDate.ofInstant(maintenant, zone);
        LocalDate debut = fin.minusDays(Math.max(1, fenetreJours) - 1L);
        try {
            ActiviteDepot activite = forges.lire(depot, debut.atStartOfDay(zone).toInstant());
            store.enregistrer(projectId, debut, fin, Agregation.compter(activite, debut, fin, zone), activite.jalons(),
                    activite.complet(), maintenant, maintenant.plus(frequence));
            return Issue.A_JOUR;
        } catch (QuotaEpuise epuisement) {
            surQuota.accept(epuisement);
            store.reporter(projectId, "REPORTEE_QUOTA", epuisement.reprise(), epuisement.getMessage(), maintenant);
            LOG.infof(epuisement, "collecte de l'activité du projet %s reportée : %s", projectId, epuisement.getMessage());
            return Issue.REPORTEE_QUOTA;
        } catch (ScmException erreur) {
            store.reporter(projectId, "ECHEC", maintenant.plus(delaiApresEchec), erreur.getMessage(), maintenant);
            LOG.warnf(erreur, "collecte de l'activité du projet %s impossible : %s", projectId, erreur.getMessage());
            return Issue.ECHEC;
        }
    }
}
