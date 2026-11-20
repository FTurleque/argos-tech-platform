'use strict';
// Contenu des notifications d'alerte et du compteur de la zone de notification
// (changement notifier-sur-le-poste : NOT-01, NOT-03, NOT-05).
// Module sans dépendance à Electron pour pouvoir être testé avec « node --test ».
//
// Le processus principal construit lui-même le texte affiché : il ne reçoit de l'IHM que
// l'identifiant, la gravité et le nombre de projets d'une alerte. Le titre de la notification est
// fixe : le titre public d'un signal nomme souvent le composant touché (« … in lodash »), et une
// notification ne nomme aucun projet ni composant (NOT-05-S1). Une valeur inattendue est rejetée.

/** Titre de toute notification d'alerte : générique, sans nom de projet ni de composant (NOT-05). */
const TITRE_NOTIFICATION = 'Nouvelle alerte ARGOS';
const COMPTEUR_MAX = 9999;
const UUID = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;

/** Gravités des alertes (`alertes-digests`) et leur libellé affiché. */
const GRAVITES = Object.freeze({
  CRITICAL: 'critique',
  HIGH: 'élevée',
  MEDIUM: 'moyenne',
  MODERATE: 'modérée',
  LOW: 'faible',
});

/**
 * Alerte reçue de l'IHM, réduite aux champs autorisés, ou undefined si elle est invalide. Tout autre
 * champ (titre, noms, liens) est ignoré.
 * @returns {{ id: string, gravite: string|null, projets: number } | undefined}
 */
function validerAlerte(payload) {
  if (!payload || typeof payload !== 'object') return undefined;
  const { id, gravite, projets } = payload;
  if (typeof id !== 'string' || !UUID.test(id)) return undefined;
  if (!Number.isInteger(projets) || projets < 0 || projets > COMPTEUR_MAX) return undefined;
  const code = typeof gravite === 'string' ? gravite.trim().toUpperCase() : '';
  return {
    id: id.toLowerCase(),
    gravite: Object.hasOwn(GRAVITES, code) ? code : null,
    projets,
  };
}

/** « 1 projet », « 3 projets ». */
function libelleProjets(nombre) {
  return nombre === 1 ? '1 projet' : `${nombre} projets`;
}

/**
 * Titre et corps d'une notification : un titre générique, la gravité et le nombre de projets
 * concernés, rien d'autre (NOT-05).
 */
function texteNotification(alerte) {
  const gravite = alerte.gravite ? GRAVITES[alerte.gravite] : 'non précisée';
  return {
    title: TITRE_NOTIFICATION,
    body: `Gravité ${gravite} · ${libelleProjets(alerte.projets)}`,
  };
}

/** Nombre d'alertes ouvertes accepté pour le compteur, ou undefined. */
function compteurValide(nombre) {
  return Number.isInteger(nombre) && nombre >= 0 && nombre <= COMPTEUR_MAX ? nombre : undefined;
}

/** Libellé du compteur (infobulle et menu de la zone de notification, NOT-03). */
function libelleCompteur(nombre) {
  if (nombre === 0) return 'aucune alerte ouverte';
  return nombre === 1 ? '1 alerte ouverte' : `${nombre} alertes ouvertes`;
}

/** Infobulle de l'icône de la zone de notification. */
function infobulle(nombre) {
  return `ARGOS — ${libelleCompteur(nombre)}`;
}

module.exports = {
  GRAVITES, TITRE_NOTIFICATION, validerAlerte, texteNotification, compteurValide, libelleCompteur, infobulle,
};
