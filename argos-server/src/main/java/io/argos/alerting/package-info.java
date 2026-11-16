/**
 * Module {@code alerting} (arc42 §5).
 *
 * <p>Règles d'abonnement, seuils, regroupement, envoi des notifications : alertes immédiates
 * ({@link io.argos.alerting.MoteurAlertes}), résumés quotidiens et hebdomadaires
 * ({@link io.argos.alerting.ResumesPeriodiques}), diffusion avec nouvelles tentatives
 * ({@link io.argos.alerting.EnvoiAlertes}) sur les canaux {@link io.argos.alerting.CanalDiffusion}
 * (capacité {@code alertes-digests}).
 *
 * <p>Dépendances autorisées : {@code impact}, {@code workspace}.
 * Vérifiées par {@code ModuleBoundariesTest}.
 */
package io.argos.alerting;
