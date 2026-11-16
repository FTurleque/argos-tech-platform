/**
 * Module {@code project-intel} (arc42 §5).
 *
 * <p>Indicateurs factuels d'activité des projets reliés à un dépôt GitHub ou GitLab (capacité
 * {@code activite-projets}) : collecte planifiée et agrégée sans identité de personne, avancement des
 * jalons avec dénominateur explicite, synthèse IA marquée, purge de l'historique après 13 mois.
 *
 * <p>Dépendances autorisées : {@code inventory}, {@code scm} et, dans {@code workspace}, le seul
 * contexte RLS {@code ContexteBase} et l'erreur commune {@code DonneeInvalide}.
 * Vérifiées par {@code ModuleBoundariesTest}.
 */
package io.argos.projectintel;
