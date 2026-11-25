'use strict';
// Résolution des adresses du serveur et de l'émetteur OIDC du client desktop (AUD-DEP-21).
// Module sans dépendance à Electron pour pouvoir être testé avec « node --test ».
// Priorité : variable d'environnement, puis champ `argos` du package.json livré (renseigné à l'emballage par
// `electron-builder -c.extraMetadata.argos.apiUrl=…`), puis valeur par défaut du poste de développement.

const API_URL_PAR_DEFAUT = 'http://localhost:8080';
const OIDC_URL_PAR_DEFAUT = 'http://localhost:8180/realms/argos';

/**
 * @param {Record<string, string | undefined>} environnement variables d'environnement du processus
 * @param {{apiUrl?: string, oidcUrl?: string} | undefined} emballes champ `argos` du package.json livré
 * @returns {{apiUrl: string, oidcUrl: string}}
 */
function resoudreReglages(environnement, emballes) {
  const livres = emballes || {};
  return {
    apiUrl: environnement.ARGOS_API_URL || livres.apiUrl || API_URL_PAR_DEFAUT,
    oidcUrl: environnement.ARGOS_OIDC_URL || livres.oidcUrl || OIDC_URL_PAR_DEFAUT,
  };
}

module.exports = { resoudreReglages, API_URL_PAR_DEFAUT, OIDC_URL_PAR_DEFAUT };
