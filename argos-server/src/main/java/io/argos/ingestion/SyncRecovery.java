package io.argos.ingestion;

import java.time.Duration;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import io.quarkus.runtime.StartupEvent;

/**
 * Au démarrage, reclasse {@code RETRYABLE} les synchronisations {@code RUNNING} expirées ;
 * elles reprennent depuis le curseur persisté au lancement suivant (arc42 §6.4, COL-05-S2, QS-07).
 * La durée d'expiration est un paramètre d'exploitation ({@code argos.ingestion.running-expiry}).
 */
@ApplicationScoped
public class SyncRecovery {

    private static final Logger LOG = Logger.getLogger(SyncRecovery.class);

    @Inject
    SyncStore store;

    @ConfigProperty(name = "argos.ingestion.running-expiry", defaultValue = "PT30M")
    Duration runningExpiry;

    void onStart(@Observes StartupEvent event) {
        recover();
    }

    public int recover() {
        int recovered = store.recoverExpired(runningExpiry);
        if (recovered > 0) {
            LOG.infof("%d synchronisation(s) interrompue(s) reclassée(s) RETRYABLE", recovered);
        }
        return recovered;
    }
}
