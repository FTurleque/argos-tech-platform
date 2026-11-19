// Gestion des abonnements de l'utilisateur (couche contenu, ABO-07, ABO-08 du changement
// `automatiser-chaine-veille`) : libellés de l'état, actions proposées, confirmation de la suppression et
// mise à jour de la liste après une réponse du serveur. Module pur, sans appel réseau ni dépendance à Vue :
// testé directement par `node --test` (tests/abonnements.test.mjs).
//
// Aucune règle métier ici (arc42 §5.1) : l'état affiché est celui que le serveur renvoie, et le serveur
// décide seul des droits (propriétaire d'un abonnement personnel, administrateur de l'espace d'un abonnement
// d'équipe ; refus 403 ou 404). L'IHM ne propose les actions que sur les abonnements personnels, qui
// appartiennent toujours à l'appelant ; les abonnements d'équipe sont affichés avec leur état, sans action
// (proposition PA-1 du volet A).
import type { Abonnement } from '../api/veille'
import {
  STATUT_INTERDIT,
  STATUT_INTROUVABLE,
  STATUT_NON_AUTHENTIFIE,
  STATUT_TROP_DE_DEMANDES,
} from '../api/statuts-http.ts'
import { libelleFiltre } from './veille-profils.ts'

/** États renvoyés par le serveur. */
export const ETAT_ACTIF = 'ACTIVE'
export const ETAT_SUSPENDU = 'SUSPENDED'

/** Action proposée sur un abonnement. */
export type ActionAbonnement = 'SUSPENDRE' | 'REACTIVER' | 'SUPPRIMER'

const LIBELLES_ETAT: Readonly<Record<string, string>> = {
  [ETAT_ACTIF]: 'Actif',
  [ETAT_SUSPENDU]: 'Suspendu',
}

/** Libellé de l'état ; un état inconnu est affiché tel quel, jamais remplacé par un état supposé. */
export function libelleEtat(etat: string): string {
  return LIBELLES_ETAT[etat] ?? etat
}

const LIBELLES_ACTION: Readonly<Record<ActionAbonnement, string>> = {
  SUSPENDRE: 'Suspendre',
  REACTIVER: 'Réactiver',
  SUPPRIMER: 'Supprimer',
}

/** Libellé d'un bouton d'action. */
export function libelleAction(action: ActionAbonnement): string {
  return LIBELLES_ACTION[action]
}

/** Nom d'un abonnement : portée puis critères, par exemple « Abonnement personnel — paquet : pkg:cargo/hyper ». */
export function nomAbonnement(abonnement: Abonnement): string {
  const portee = abonnement.scope === 'TEAM' ? "Abonnement d'équipe" : 'Abonnement personnel'
  if (abonnement.filters.length === 0) return portee
  return `${portee} — ${abonnement.filters.map(libelleFiltre).join(', ')}`
}

/**
 * Actions proposées : suspendre un abonnement personnel actif, réactiver un abonnement personnel suspendu,
 * supprimer un abonnement personnel dans les deux cas (ABO-07, ABO-08). Aucune action sur un abonnement
 * d'équipe ni sur un état inconnu.
 */
export function actionsAbonnement(abonnement: Abonnement): ActionAbonnement[] {
  if (abonnement.scope !== 'PERSONAL') return []
  if (abonnement.state === ETAT_ACTIF) return ['SUSPENDRE', 'SUPPRIMER']
  if (abonnement.state === ETAT_SUSPENDU) return ['REACTIVER', 'SUPPRIMER']
  return []
}

/** Question posée avant toute suppression : définitive, contrairement à la suspension (ABO-08). */
export function confirmationSuppression(abonnement: Abonnement): string {
  return `Supprimer définitivement « ${nomAbonnement(abonnement)} » ? Ses critères sont effacés et il ne pourra pas être réactivé. Pour arrêter les notifications sans perdre les critères, suspendez-le plutôt.`
}

/** Liste après une suspension ou une réactivation : l'abonnement renvoyé par le serveur remplace l'ancien. */
export function remplacerAbonnement(liste: readonly Abonnement[], modifie: Abonnement): Abonnement[] {
  return liste.map((a) => (a.id === modifie.id ? modifie : a))
}

/** Liste après une suppression confirmée par le serveur. */
export function retirerAbonnement(liste: readonly Abonnement[], id: string): Abonnement[] {
  return liste.filter((a) => a.id !== id)
}

/** Message affiché pour une action refusée ou en échec. */
export function messageErreurAbonnement(status: number | undefined, message: string): string {
  if (status === undefined) return message
  if (status === STATUT_NON_AUTHENTIFIE) return 'Connectez-vous pour gérer vos abonnements.'
  if (status === STATUT_INTERDIT) return 'Accès refusé : seuls les administrateurs de l’espace gèrent cet abonnement.'
  if (status === STATUT_INTROUVABLE) return 'Cet abonnement n’existe plus : la liste est rechargée.'
  if (status === STATUT_TROP_DE_DEMANDES) return 'Trop de demandes en peu de temps : réessayez dans quelques instants.'
  return `Le serveur a répondu ${status}.`
}
