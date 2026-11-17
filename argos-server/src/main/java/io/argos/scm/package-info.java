/**
 * Module {@code scm-connector} (arc42 §5).
 *
 * <p>GitLab et GitHub en lecture seule : récupération des SBOM et des manifestes
 * ({@code RepositoryFiles}), activité des dépôts sans identité de personne ({@code RepositoryActivity}),
 * quotas d'API ({@code QuotaEpuise}), demandes d'évolution associées à un texte réglementaire
 * ({@code DemandesEvolution}). Aucune dépendance métier.
 *
 * <p>Dépendances autorisées : aucune.
 * Vérifiées par {@code ModuleBoundariesTest}.
 */
package io.argos.scm;
