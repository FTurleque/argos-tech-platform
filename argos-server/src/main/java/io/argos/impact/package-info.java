/**
 * Module {@code impact-engine} (arc42 §5).
 *
 * <p>Corrélation déterministe signal ↔ projet, niveau de confiance, statut d'action.
 *
 * <p>Dépendances autorisées : {@code signal}, {@code vulnintel}, {@code releaseradar}, {@code inventory}, {@code aigateway},
 * {@code catalog} (référentiel des standards : dette de version et annonces, {@link io.argos.impact.RadarProjets}).
 * Vérifiées par {@code ModuleBoundariesTest}.
 */
package io.argos.impact;
