// Statuts HTTP des réponses refusées de l'API REST lus par l'IHM (RFC 9110, §15.5). Module pur, sans appel
// réseau : importable par les modules testés directement par `node --test`.

/** Requête refusée par le serveur ou par le client avant tout appel (donnée invalide, `DONNEE_INVALIDE`). */
export const STATUT_REQUETE_INVALIDE = 400

/** Session absente ou expirée : l'utilisateur doit se connecter. */
export const STATUT_NON_AUTHENTIFIE = 401

/** Utilisateur connecté dont les droits ne permettent pas l'opération. */
export const STATUT_INTERDIT = 403

/** Ressource inexistante ou hors des espaces de l'utilisateur, sans distinction (Q-4). */
export const STATUT_INTROUVABLE = 404

/** Vrai pour une ressource absente ou refusée faute de droits : l'IHM ne distingue pas les deux cas. */
export function introuvableOuInterdit(statut: number | undefined): boolean {
  return statut === STATUT_INTERDIT || statut === STATUT_INTROUVABLE
}
