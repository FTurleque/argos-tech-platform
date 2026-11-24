package io.argos.projectintel;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import io.argos.horloge.LectureHeure;
import io.argos.observabilite.TacheObservee;
import io.quarkus.scheduler.Scheduled;

/**
 * Suppression quotidienne de l'historique d'activité au-delà de la durée de conservation : 13 mois
 * (décision du 2026-11-16, Q-2), réglable par {@code argos.activite.conservation-mois}.
 */
@ApplicationScoped
public class PurgeActivite {

    private static final Logger LOG = Logger.getLogger(PurgeActivite.class);

    @Inject
    Clock horloge;

    @Inject
    ActiviteStore store;

    @ConfigProperty(name = "argos.alerting.fuseau")
    String fuseau;

    @ConfigProperty(name = "argos.activite.conservation-mois", defaultValue = "13")
    int conservationMois;

    @TacheObservee
    @Scheduled(identity = "argos-activite-purge", cron = "{argos.activite.purge.cron}",
            timeZone = "{argos.alerting.fuseau}", concurrentExecution = Scheduled.ConcurrentExecution.SKIP)
    void planifiee() {
        try {
            purger(LectureHeure.jour(horloge));
        } catch (IllegalStateException erreur) {
            LOG.errorf(erreur, "purge de l'historique d'activité en échec");
        }
    }

    /**
     * Supprime l'historique antérieur au jour {@code aujourdhui} moins la durée de conservation.
     *
     * @return nombre de lignes supprimées
     */
    public int purger(LocalDate aujourdhui) {
        LocalDate limite = aujourdhui.minusMonths(Math.max(1, conservationMois));
        int supprimees = store.purger(limite, limite.atStartOfDay(ZoneId.of(fuseau)).toInstant());
        LOG.infof("historique d'activité antérieur au %s supprimé : %d ligne(s)", limite, supprimees);
        return supprimees;
    }
}
