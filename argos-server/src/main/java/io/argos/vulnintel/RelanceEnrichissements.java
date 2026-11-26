package io.argos.vulnintel;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import io.argos.observabilite.TacheObservee;
import io.quarkus.scheduler.Scheduled;

/**
 * Relance planifiée des enrichissements manquants (COL-04-S3) : une source secondaire indisponible au moment de
 * l'enregistrement d'un signal laisse ce signal sans enrichissement ; cette tâche relance l'enrichissement des
 * signaux à CVE qui n'en ont encore aucun, par lots bornés ({@code argos.vulnintel.relance.limite}).
 */
@ApplicationScoped
public class RelanceEnrichissements {

    private static final Logger LOG = Logger.getLogger(RelanceEnrichissements.class);

    @Inject
    Enricher enricher;

    @ConfigProperty(name = "argos.vulnintel.relance.limite", defaultValue = "100")
    int limite;

    @TacheObservee
    @Scheduled(identity = "vulnintel-enrichissements-relance", cron = "{argos.vulnintel.relance.cron}",
            timeZone = "{argos.alerting.fuseau}", concurrentExecution = Scheduled.ConcurrentExecution.SKIP)
    void planifiee() {
        relancer();
    }

    /** Relance un lot d'enrichissements manquants et renvoie le nombre de signaux traités. */
    int relancer() {
        int traites = enricher.retryMissing(limite);
        if (traites > 0) {
            LOG.infof("enrichissement relancé pour %d signal(s)", traites);
        }
        return traites;
    }
}
