// Déclaration de support/base-e2e.mjs, module JavaScript partagé avec la campagne de performance
// (performance/mesurer-p95.mjs) et importé par la configuration de Playwright.

/** Noms des rôles de connexion et de migration de la base e2e jetable. */
export const ROLES_BASE_E2E: Readonly<{ connexion: string; proprietaire: string }>

/** Variables d'environnement des deux rôles de la base e2e, valorisées par leur nom. */
export function identificationBaseE2E(): { ARGOS_DB_PASSWORD: string; ARGOS_DB_OWNER_PASSWORD: string }
