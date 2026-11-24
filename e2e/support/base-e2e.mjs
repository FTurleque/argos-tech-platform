// Base e2e jetable (e2e/compose.yaml) en authentification « trust » : elle ne vérifie aucun mot de passe. Quarkus en
// exige un pour la connexion et un pour les migrations ; le nom public de chaque rôle
// (deploiement/postgres/initialiser-bases.sh) tient lieu de valeur. Partagé par Playwright et la campagne de
// performance (performance/mesurer-p95.mjs).
export const ROLES_BASE_E2E = Object.freeze({ connexion: 'argos_connexion', proprietaire: 'argos' })

/** Variables d'environnement des deux rôles de la base e2e, valorisées par leur nom. */
export function identificationBaseE2E() {
  return { ARGOS_DB_PASSWORD: ROLES_BASE_E2E.connexion, ARGOS_DB_OWNER_PASSWORD: ROLES_BASE_E2E.proprietaire }
}
