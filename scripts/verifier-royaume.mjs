#!/usr/bin/env node
// Contrôle du royaume Keycloak livré pour l'installation (ACC-13-S4, changement rendre-portail-deployable) :
//   - protection contre la force brute : 5 échecs, attente incrémentale de 60 s, attente maximale de 15 min ;
//   - politique de mot de passe : length(12) and notUsername and notEmail and passwordHistory(3) ;
//   - TLS exigé pour les adresses externes (sslRequired « external ») ;
//   - aucune adresse de retour, origine ni adresse de fin de session du client argos-ui ne contient de joker.
// Usage : node scripts/verifier-royaume.mjs
// Code de sortie : 0 sans écart, sans sortie ; 1 sinon, une ligne par écart sur la sortie d'erreur.

import { readFileSync } from 'node:fs';
import { dirname, join, resolve } from 'node:path';
import { fileURLToPath, pathToFileURL } from 'node:url';

const RACINE = resolve(dirname(fileURLToPath(import.meta.url)), '..');

export const FICHIER_ROYAUME = 'deploiement/keycloak/argos-realm.json';
export const CLIENT_PORTAIL = 'argos-ui';
export const REGLES_MOT_DE_PASSE = 'length(12) and notUsername and notEmail and passwordHistory(3)';
export const SEUILS_FORCE_BRUTE = Object.freeze({ failureFactor: 5, waitIncrementSeconds: 60, maxFailureWaitSeconds: 900 });
export const TLS_EXIGE = 'external';
const JOKER = '*';
const ATTRIBUT_FIN_DE_SESSION = 'post.logout.redirect.uris';
const SEPARATEUR_ADRESSES = '##';

/** Adresses déclarées par le client du portail : retours, origines et fins de session. */
function adressesClientPortail(client) {
  const finsDeSession = (client.attributes?.[ATTRIBUT_FIN_DE_SESSION] ?? '')
    .split(SEPARATEUR_ADRESSES)
    .filter((adresse) => adresse !== '');
  return [
    ...(client.redirectUris ?? []).map((adresse) => ({ nature: 'redirectUris', adresse })),
    ...(client.webOrigins ?? []).map((adresse) => ({ nature: 'webOrigins', adresse })),
    ...finsDeSession.map((adresse) => ({ nature: ATTRIBUT_FIN_DE_SESSION, adresse })),
  ];
}

/** Écarts de la protection contre la force brute : activation, seuils, blocage temporaire. */
function ecartsForceBrute(royaume) {
  const ecarts = [];
  if (royaume.bruteForceProtected !== true) {
    ecarts.push('bruteForceProtected : la protection contre la force brute doit être activée');
  }
  for (const [cle, attendu] of Object.entries(SEUILS_FORCE_BRUTE)) {
    if (royaume[cle] !== attendu) ecarts.push(`${cle} : ${attendu} attendu, ${royaume[cle] ?? 'absent'} trouvé`);
  }
  if (royaume.permanentLockout === true) {
    ecarts.push('permanentLockout : le blocage définitif ne doit pas être activé (blocage temporaire croissant)');
  }
  return ecarts;
}

/** Écarts des adresses du client du portail : client présent, aucun joker. */
function ecartsClientPortail(royaume) {
  const client = (royaume.clients ?? []).find((candidat) => candidat.clientId === CLIENT_PORTAIL);
  if (client === undefined) return [`client ${CLIENT_PORTAIL} : absent du royaume`];
  return adressesClientPortail(client)
    .filter(({ adresse }) => adresse.includes(JOKER))
    .map(({ nature, adresse }) => `client ${CLIENT_PORTAIL}, ${nature} : joker interdit dans « ${adresse} »`);
}

/** Écarts, triés, entre un royaume (JSON déjà lu) et les réglages de sécurité exigés. */
export function ecartsRoyaume(royaume) {
  const ecarts = ecartsForceBrute(royaume);
  if (royaume.passwordPolicy !== REGLES_MOT_DE_PASSE) {
    ecarts.push(`passwordPolicy : « ${REGLES_MOT_DE_PASSE} » attendue, « ${royaume.passwordPolicy ?? ''} » trouvée`);
  }
  if (royaume.sslRequired !== TLS_EXIGE) {
    ecarts.push(`sslRequired : « ${TLS_EXIGE} » attendu, « ${royaume.sslRequired ?? 'absent'} » trouvé`);
  }
  ecarts.push(...ecartsClientPortail(royaume));
  return ecarts.sort((premier, second) => premier.localeCompare(second, 'fr'));
}

/** Écarts du royaume livré par le dépôt. */
export function ecartsDepot(racine = RACINE) {
  return ecartsRoyaume(JSON.parse(readFileSync(join(racine, FICHIER_ROYAUME), 'utf8')));
}

if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) {
  const ecarts = ecartsDepot();
  for (const ecart of ecarts) console.error(`${FICHIER_ROYAUME} : ${ecart}`);
  process.exit(ecarts.length === 0 ? 0 : 1);
}
