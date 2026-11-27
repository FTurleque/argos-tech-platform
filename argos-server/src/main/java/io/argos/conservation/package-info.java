/**
 * Module technique {@code conservation} (arc42 §5, ADR-0041).
 *
 * <p>Conservation bornée des données journalisées : contrat {@link io.argos.conservation.RegleConservation} que chaque
 * module implémente pour ses propres tables, tâche planifiée unique {@link io.argos.conservation.PurgesConservation}
 * et suppression par lots {@link io.argos.conservation.SuppressionParLots}. Aucun nom de table n'y figure.
 *
 * <p>Dépendances autorisées : modules techniques transverses seulement. Vérifiées par {@code ModuleBoundariesTest}.
 */
package io.argos.conservation;
