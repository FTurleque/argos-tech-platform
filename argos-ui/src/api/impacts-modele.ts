// État de traitement des impacts (COR-09, COR-10, COR-12 ; décision J5 du porteur du projet, 2026-11-19) : types des
// réponses de `GET /api/v1/impacts/{id}` et de `GET /api/v1/signals/{id}/impacts`, chemins des appels et corps de
// `POST /api/v1/impacts/{id}/transitions`. Module pur, sans appel réseau : testé directement par `node --test`. Les
// appels eux-mêmes passent par `appelerApi` (`impacts.ts`).
//
// Aucune règle métier ici (arc42 §5.1) : les transitions permises pour l'utilisateur (`transitionsPossibles`) et
// l'état restitué viennent du serveur ; ce module les nomme et vérifie seulement la forme d'une demande avant l'appel.
import { estUuid } from './recherche-criteres.ts'

/** États de traitement d'un impact, distincts de son niveau de confiance. */
export const ETATS_IMPACT = ['OUVERT', 'CONFIRME', 'ECARTE', 'RESOLU'] as const
export type EtatImpactApi = (typeof ETATS_IMPACT)[number]

/** Longueur maximale d'un motif ou d'un commentaire acceptée par le serveur. */
export const LONGUEUR_MAX_TEXTE = 1000

/** Transition de l'historique d'un impact (schéma `TransitionView`). */
export interface TransitionApi {
  de: string
  vers: string
  /** Nom affiché du compte auteur, `SYSTEME` pour une transition du système, `null` si le compte n'est plus visible. */
  auteur: string | null
  auteurCompteId: string | null
  systeme: boolean
  motif: string | null
  commentaire: string | null
  date: string | null
}

/** Impact (schéma `ImpactView`) ; `historique` et `transitionsPossibles` ne sont présents qu'à la consultation. */
export interface ImpactApi {
  id: string
  signalId: string
  signalSource: string | null
  signalExternalId: string | null
  signalTitle: string | null
  projectId: string
  projectName: string | null
  componentPurl: string | null
  componentVersion: string | null
  level: string
  etat: string
  historique?: TransitionApi[] | null
  transitionsPossibles?: string[] | null
}

/** Page d'impacts d'un signal (schéma `Page`). */
export interface PageImpactsApi {
  items: ImpactApi[]
  page: number
  size: number
  total: number
}

const LIBELLES_ETATS: Readonly<Record<string, string>> = {
  OUVERT: 'ouvert',
  CONFIRME: 'confirmé',
  ECARTE: 'écarté',
  RESOLU: 'résolu',
}

const LIBELLES_NIVEAUX: Readonly<Record<string, string>> = {
  EXACT: 'exact',
  PROBABLE: 'probable',
  A_VERIFIER: 'à vérifier',
}

const LIBELLES_ACTIONS: Readonly<Record<string, string>> = {
  CONFIRME: 'Confirmer',
  ECARTE: 'Écarter',
  RESOLU: 'Marquer résolu',
  OUVERT: 'Rouvrir',
}

const LIBELLES_MOTIFS_SYSTEME: Readonly<Record<string, string>> = {
  COMPOSANT_RETIRE: 'composant retiré de l’inventaire',
  VERSION_HORS_PLAGE: 'composant mis à jour hors de la plage affectée',
  AVIS_MODIFIE: 'avis modifié par sa source',
  AVIS_RETIRE: 'avis retiré par sa source',
  DE_NOUVEAU_VERIFIE: 'de nouveau vérifié par une corrélation',
}

/** Libellé d'un état de traitement ; un code inconnu est restitué tel quel. */
export function libelleEtat(code: string | null | undefined): string {
  if (!code) return 'non précisé'
  return LIBELLES_ETATS[code] ?? code
}

/** Libellé d'un niveau de confiance ; un code inconnu est restitué tel quel. */
export function libelleNiveau(code: string | null | undefined): string {
  if (!code) return 'non précisé'
  return LIBELLES_NIVEAUX[code] ?? code
}

/** Libellé du bouton qui demande la transition vers l'état donné. */
export function libelleAction(vers: string): string {
  return LIBELLES_ACTIONS[vers] ?? vers
}

/** Libellé d'un motif normalisé de transition système ; un autre motif est restitué tel quel (texte). */
export function libelleMotif(motif: string | null | undefined, systeme: boolean): string | null {
  if (!motif) return null
  return systeme ? (LIBELLES_MOTIFS_SYSTEME[motif] ?? motif) : motif
}

/** Chemin des impacts d'un signal, tous états confondus ; `null` si l'identifiant n'est pas un UUID. */
export function cheminImpactsSignal(signalId: unknown): string | null {
  if (!estUuid(signalId)) return null
  const etats = ETATS_IMPACT.map((e) => `etat=${e}`).join('&')
  return `/api/v1/signals/${encodeURIComponent(signalId.trim())}/impacts?${etats}&size=200`
}

/** Chemin d'un impact ; `null` si l'identifiant n'est pas un UUID. */
export function cheminImpact(impactId: unknown): string | null {
  if (!estUuid(impactId)) return null
  return `/api/v1/impacts/${encodeURIComponent(impactId.trim())}`
}

/** Chemin des transitions d'un impact ; `null` si l'identifiant n'est pas un UUID. */
export function cheminTransitions(impactId: unknown): string | null {
  const base = cheminImpact(impactId)
  return base ? `${base}/transitions` : null
}

/** Corps de `POST …/transitions`. */
export interface DemandeTransitionApi {
  vers: string
  motif?: string
  commentaire?: string
}

/**
 * Corps de la demande, ou message d'erreur affiché sans appeler le serveur : état inconnu, motif manquant pour
 * écarter, texte trop long. Le serveur refait ces contrôles.
 */
export function demandeTransition(
  vers: string,
  motif: string,
  commentaire: string,
): { corps: DemandeTransitionApi } | { erreur: string } {
  if (!(ETATS_IMPACT as readonly string[]).includes(vers)) return { erreur: 'Transition inconnue.' }
  const m = motif.trim()
  const c = commentaire.trim()
  if (vers === 'ECARTE' && m === '') return { erreur: 'Indiquez le motif pour écarter cet impact.' }
  if (m.length > LONGUEUR_MAX_TEXTE || c.length > LONGUEUR_MAX_TEXTE) {
    return { erreur: `Le motif et le commentaire sont limités à ${LONGUEUR_MAX_TEXTE} caractères.` }
  }
  const corps: DemandeTransitionApi = { vers }
  if (m !== '') corps.motif = m
  if (c !== '') corps.commentaire = c
  return { corps }
}
