package io.argos.projectintel;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import io.argos.inventory.ProjectDirectory;
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
 * (D1) et remplace les compteurs de ces jours. Un quota épuisé reporte la collecte du projet, et des
 * projets suivants de la même forge, après la réinitialisation annoncée ; les données déjà enregistrées
 * et leur date de dernière mise à jour restent affichées (PIN-05-S1).
 */
@ApplicationScoped
public class CollecteActivite {

    private static final Logger LOG = Logger.getLogger(CollecteActivite.class);

    /** Issue de la collecte d'un projet. */
    public enum Issue { A_JOUR, REPORTEE_QUOTA, ECHEC, NON_ECHUE, SANS_DEPOT }

    /** Bilan d'un passage. */
    public record Bilan(int aJour, int reportees, int echecs) {
    }

    @Inject
    ProjectDirectory projets;

    @Inject
    RepositoryActivity forges;

    @Inject
    ActiviteStore store;

    @ConfigProperty(name = "argos.alerting.fuseau")
    String fuseau;

    @ConfigProperty(name = "argos.activite.fenetre-jours", defaultValue = "30")
    int fenetreJours;

    @ConfigProperty(name = "argos.activite.frequence", defaultValue = "PT24H")
    Duration frequence;

    @ConfigProperty(name = "argos.activite.delai-apres-echec", defaultValue = "PT1H")
    Duration delaiApresEchec;

    @Scheduled(identity = "argos-activite-collecte", every = "{argos.activite.cadence}",
            concurrentExecution = Scheduled.ConcurrentExecution.SKIP)
    void planifiee() {
        collecter(Instant.now());
    }

    /** Collecte les projets échus à l'instant {@code maintenant}. */
    public Bilan collecter(Instant maintenant) {
        int aJour = 0;
        int reportees = 0;
        int echecs = 0;
        Map<String, QuotaEpuise> quotas = new HashMap<>();
        for (ProjectDirectory.Project projet : projets.withRepository()) {
            try {
                Optional<Instant> prochaine = store.prochaineCollecte(projet.id());
                if (prochaine.isPresent() && prochaine.get().isAfter(maintenant)) {
                    continue;
                }
                RepositoryRef depot = projet.repository().orElseThrow();
                String forge = depot.provider().toUpperCase(Locale.ROOT);
                QuotaEpuise quota = quotas.get(forge);
                if (quota != null && quota.reprise().isAfter(maintenant)) {
                    store.reporter(projet.id(), "REPORTEE_QUOTA", quota.reprise(), quota.getMessage(), maintenant);
                    reportees++;
                    continue;
                }
                Issue issue = collecterDepot(projet.id(), depot, maintenant, epuisement -> quotas.put(forge, epuisement));
                switch (issue) {
                    case A_JOUR -> aJour++;
                    case REPORTEE_QUOTA -> reportees++;
                    case ECHEC -> echecs++;
                    default -> { }
                }
            } catch (RuntimeException erreur) {
                echecs++;
                LOG.errorf(erreur, "collecte de l'activité du projet %s en échec", projet.id());
            }
        }
        return new Bilan(aJour, reportees, echecs);
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
            java.util.function.Consumer<QuotaEpuise> surQuota) {
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
            LOG.infof("collecte de l'activité du projet %s reportée : %s", projectId, epuisement.getMessage());
            return Issue.REPORTEE_QUOTA;
        } catch (ScmException erreur) {
            store.reporter(projectId, "ECHEC", maintenant.plus(delaiApresEchec), erreur.getMessage(), maintenant);
            LOG.warnf("collecte de l'activité du projet %s impossible : %s", projectId, erreur.getMessage());
            return Issue.ECHEC;
        }
    }
}
