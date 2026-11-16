'use strict';
// Réglages du poste (changement notifier-sur-le-poste : NOT-04, question Q-2).
// Module sans dépendance à Electron pour pouvoir être testé avec « node --test ».
//
// Les réglages concernent ce poste seulement et restent sur lui (dossier des données de
// l'application) ; ils ne modifient aucun autre canal d'alerte côté serveur (NOT-04).

const path = require('node:path');

/** Valeurs par défaut : notifications du poste actives ; démarrage avec la session désactivé (Q-2). */
const DEFAUTS = Object.freeze({ notificationsPoste: true, demarrageSession: false });

/** Réglages complets à partir d'un objet quelconque : seules les clés connues de type booléen comptent. */
function normaliser(valeur) {
  const source = valeur && typeof valeur === 'object' ? valeur : {};
  const resultat = { ...DEFAUTS };
  for (const cle of Object.keys(DEFAUTS)) {
    if (typeof source[cle] === 'boolean') resultat[cle] = source[cle];
  }
  return resultat;
}

/**
 * Applique une modification demandée par l'IHM ou par le menu : seules les clés connues de type
 * booléen sont prises en compte. Renvoie null si la demande ne contient aucune clé valide.
 */
function fusionner(actuels, demande) {
  if (!demande || typeof demande !== 'object') return null;
  const changements = {};
  for (const cle of Object.keys(DEFAUTS)) {
    if (typeof demande[cle] === 'boolean') changements[cle] = demande[cle];
  }
  if (Object.keys(changements).length === 0) return null;
  return normaliser({ ...actuels, ...changements });
}

/** Lit les réglages ; fichier absent ou illisible : valeurs par défaut. */
function lire(fs, fichier) {
  try {
    return normaliser(JSON.parse(fs.readFileSync(fichier, 'utf8')));
  } catch {
    return { ...DEFAUTS };
  }
}

/** Écrit les réglages par un fichier temporaire renommé, pour ne jamais laisser un fichier tronqué. */
function ecrire(fs, fichier, reglages) {
  fs.mkdirSync(path.dirname(fichier), { recursive: true });
  const temporaire = `${fichier}.tmp`;
  fs.writeFileSync(temporaire, JSON.stringify(normaliser(reglages), null, 2), 'utf8');
  fs.renameSync(temporaire, fichier);
}

module.exports = { DEFAUTS, normaliser, fusionner, lire, ecrire };
