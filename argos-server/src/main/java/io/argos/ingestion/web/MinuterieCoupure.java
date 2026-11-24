package io.argos.ingestion.web;

import java.time.Duration;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;

import org.eclipse.microprofile.config.inject.ConfigProperty;

/**
 * Minuterie qui ferme un corps de réponse dont la lecture dépasse l'échéance (WEB-03, AUD-QUA-11) : exécuteur
 * planifié possédé par ce bean, de {@code argos.veille-web.minuterie.fils} fils (un par défaut), arrêté à l'arrêt de
 * l'application. Une coupure annulée est retirée de la file.
 */
@ApplicationScoped
public class MinuterieCoupure {

    @ConfigProperty(name = "argos.veille-web.minuterie.fils")
    int fils;

    private ScheduledThreadPoolExecutor executeur;

    @PostConstruct
    void demarrer() {
        executeur = new ScheduledThreadPoolExecutor(fils, tache -> {
            Thread fil = new Thread(tache, "argos-veille-web-delai");
            fil.setDaemon(true);
            return fil;
        });
        executeur.setRemoveOnCancelPolicy(true);
    }

    @PreDestroy
    void arreter() {
        executeur.shutdownNow();
    }

    /** Vrai une fois la minuterie arrêtée. */
    boolean arretee() {
        return executeur.isShutdown();
    }

    /** Programme {@code coupure} après {@code delai} (une milliseconde au moins). */
    ScheduledFuture<?> programmer(Runnable coupure, Duration delai) {
        return executeur.schedule(coupure, Math.max(1, delai.toMillis()), TimeUnit.MILLISECONDS);
    }
}
