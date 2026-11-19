// Statuts HTTP des réponses de l'API REST lus par l'IHM (RFC 9110, §15). Module partagé unique : tout
// écran ou module de argos-ui compare un statut à ces constantes, jamais à un nombre écrit en dur.
// Module pur, sans appel réseau ni import : importable par les modules testés directement par `node --test`.

/** Réponse acceptée sans corps (RFC 9110, §15.3.5) : l'appel ne renvoie aucune donnée. */
export const STATUT_SANS_CONTENU = 204

/** Requête refusée par le serveur ou par le client avant tout appel (donnée invalide, `DONNEE_INVALIDE`). */
export const STATUT_REQUETE_INVALIDE = 400

/** Session absente ou expirée : l'utilisateur doit se connecter. */
export const STATUT_NON_AUTHENTIFIE = 401

/** Utilisateur connecté dont les droits ne permettent pas l'opération. */
export const STATUT_INTERDIT = 403

/** Ressource inexistante ou hors des espaces de l'utilisateur, sans distinction (Q-4). */
export const STATUT_INTROUVABLE = 404

/** Opération en conflit avec l'état de la ressource (par exemple un nom de vue déjà pris). */
export const STATUT_CONFLIT = 409

/** Trop de demandes en peu de temps : le serveur limite le débit de l'utilisateur. */
export const STATUT_TROP_DE_DEMANDES = 429

/** Vrai pour une ressource absente ou refusée faute de droits : l'IHM ne distingue pas les deux cas. */
export function introuvableOuInterdit(statut: number | undefined): boolean {
  return statut === STATUT_INTERDIT || statut === STATUT_INTROUVABLE
}

/** Vrai pour une session absente, expirée ou sans droits suffisants (401 ou 403). */
export function nonAutorise(statut: number | undefined): boolean {
  return statut === STATUT_NON_AUTHENTIFIE || statut === STATUT_INTERDIT
}
