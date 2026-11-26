package io.argos.testsupport;

import java.time.Duration;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.awaitility.Awaitility;

import io.argos.execution.ExecuteurEvenements;

/**
 * Attente bornée de la fin des observateurs asynchrones (ADR-0039, arc42 §8.11) : la chaîne radar et l'évaluation des
 * alertes s'exécutent sur {@link ExecuteurEvenements} après la réponse de leur déclencheur. Un test qui vérifie leurs
 * effets attend que l'exécuteur soit inactif, sans sommeil fixe.
 */
@ApplicationScoped
public class Asynchrone {

    private static final Duration ATTENTE_MAX = Duration.ofSeconds(30);

    @Inject
    ExecuteurEvenements evenements;

    /** Attend qu'aucune notification asynchrone ne soit en cours ni en file. */
    public void attendre() {
        Awaitility.await().atMost(ATTENTE_MAX).until(evenements::inactif);
    }
}
