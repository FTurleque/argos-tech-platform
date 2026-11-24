// Déclaration de support/docker.mjs, module JavaScript partagé avec la campagne de performance
// (performance/mesurer-p95.mjs) et importé par les parcours TypeScript.

/** Chemin de l'exécutable Docker ; lève une erreur explicite s'il est introuvable. */
export function cheminDocker(): string
