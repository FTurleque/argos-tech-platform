package io.argos.execution;

import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Event;
import jakarta.enterprise.event.NotificationOptions;
import jakarta.inject.Inject;

import org.jboss.logging.Logger;

import io.argos.observabilite.EchecsAsynchrones;

/**
 * Publication d'un événement à ses observateurs {@code @ObservesAsync} (ADR-0039, arc42 §8, « Événements entre
 * modules ») : les observateurs s'exécutent sur {@link ExecuteurEvenements}, avec la corrélation des journaux de
 * l'émetteur, et l'émetteur n'attend pas leur fin. Une erreur sortie d'un observateur n'a plus d'appelant à qui
 * remonter : elle est journalisée au niveau erreur avec sa cause et comptée
 * ({@code argos_evenements_asynchrones_echecs_total}).
 */
@ApplicationScoped
public class PublicationAsynchrone {

    private static final Logger LOG = Logger.getLogger(PublicationAsynchrone.class);

    @Inject
    ExecuteurEvenements executeur;

    @Inject
    EchecsAsynchrones echecs;

    /** Publie l'événement sans attendre ses observateurs ; l'étape rendue se termine avec eux. */
    public <T> CompletionStage<T> publier(Event<T> evenement, T charge) {
        String nom = charge.getClass().getSimpleName();
        return evenement.fireAsync(charge, NotificationOptions.ofExecutor(executeur::lancerOuExecuter))
                .whenComplete((notifie, erreur) -> {
                    if (erreur != null) {
                        Throwable cause = erreur instanceof CompletionException && erreur.getCause() != null
                                ? erreur.getCause() : erreur;
                        LOG.errorf(cause, "observateur asynchrone de l'événement %s en échec", nom);
                        echecs.compterEvenement(nom);
                    }
                });
    }
}
