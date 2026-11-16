'use strict';
// Contenu des notifications d'alerte et du compteur de la zone de notification
// (changement notifier-sur-le-poste : NOT-01, NOT-03, NOT-05).
// Module sans dépendance à Electron pour pouvoir être testé avec « node --test ».
//
// Le processus principal construit lui-même le texte affiché : il ne reçoit de l'IHM que
// l'identifiant, le titre, la gravité et le nombre de projets d'une alerte, jamais le nom d'un
// projet ni d'un composant (NOT-05). Une valeur inattendue est rejetée.

const TITRE_MAX = 120;
const COMPTEUR_MAX = 9999;
const UUID = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;
// Caractères de contrôle (dont retours à la ligne) et marques de direction du texte.
const CONTROLES = /[\u0000-\u001f\u007f-\u009f‎‏‪-‮⁦-⁩]/g;

/** Gravités des alertes (`alertes-digests`) et leur libellé affiché. */
const GRAVITES = Object.freeze({
  CRITICAL: 'critique',
  HIGH: 'élevée',
  MEDIUM: 'moyenne',
  MODERATE: 'modérée',
  LOW: 'faible',
});

/** Texte sur une ligne, sans caractère de contrôle, tronqué à `max` caractères. */
function nettoyer(texte, max) {
  if (typeof texte !== 'string') return '';
  const propre = texte.replace(CONTROLES, ' ').replace(/\s+/g, ' ').trim();
  return propre.length > max ? `${propre.slice(0, max - 1)}…` : propre;
}

/**
 * Alerte reçue de l'IHM, réduite aux champs autorisés, ou null si elle est invalide.
 * @returns {{ id: string, titre: string, gravite: string|null, projets: number } | null}
 */
function validerAlerte(payload) {
  if (!payload || typeof payload !== 'object') return null;
  const { id, titre, gravite, projets } = payload;
  if (typeof id !== 'string' || !UUID.test(id)) return null;
  const titrePropre = nettoyer(titre, TITRE_MAX);
  if (!titrePropre) return null;
  if (!Number.isInteger(projets) || projets < 0 || projets > COMPTEUR_MAX) return null;
  const code = typeof gravite === 'string' ? gravite.trim().toUpperCase() : '';
  return {
    id: id.toLowerCase(),
    titre: titrePropre,
    gravite: Object.hasOwn(GRAVITES, code) ? code : null,
    projets,
  };
}

/** « 1 projet », « 3 projets ». */
function libelleProjets(n) {
  return n === 1 ? '1 projet' : `${n} projets`;
}

/**
 * Titre et corps d'une notification : le titre de l'alerte, sa gravité et le nombre de projets
 * concernés, rien d'autre (NOT-05).
 */
function texteNotification(alerte) {
  const gravite = alerte.gravite ? GRAVITES[alerte.gravite] : 'non précisée';
  return {
    title: alerte.titre,
    body: `Gravité ${gravite} · ${libelleProjets(alerte.projets)}`,
  };
}

/** Nombre d'alertes ouvertes accepté pour le compteur, ou null. */
function compteurValide(n) {
  return Number.isInteger(n) && n >= 0 && n <= COMPTEUR_MAX ? n : null;
}

/** Libellé du compteur (infobulle et menu de la zone de notification, NOT-03). */
function libelleCompteur(n) {
  if (n === 0) return 'aucune alerte ouverte';
  return n === 1 ? '1 alerte ouverte' : `${n} alertes ouvertes`;
}

/** Infobulle de l'icône de la zone de notification. */
function infobulle(n) {
  return `ARGOS — ${libelleCompteur(n)}`;
}

module.exports = {
  GRAVITES, validerAlerte, texteNotification, compteurValide, libelleCompteur, infobulle, nettoyer,
};
