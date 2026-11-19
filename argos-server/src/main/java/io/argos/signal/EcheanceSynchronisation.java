package io.argos.signal;

import java.time.Instant;

/**
 * Échéance de la synchronisation planifiée des sources d'avis (COL-09), publiée avant la synchronisation
 * des sources. Le module {@code vuln-intel} l'observe pour rafraîchir ses index d'enrichissement
 * (COL-09-S5) : {@code signal} ne dépend pas de {@code vulnintel} (arc42 §5, {@code ModuleBoundariesTest}).
 *
 * @param instant instant de l'échéance
 */
public record EcheanceSynchronisation(Instant instant) {
}
