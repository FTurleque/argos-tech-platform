// Traitement des impacts d'une alerte (COR-09, COR-10, COR-12 ; décision J5 du porteur du projet, 2026-11-19) : mise
// en forme de l'état, du niveau et de l'historique d'un impact, actions de transition proposées et envoi d'une
// demande. Module pur, sans appel réseau ni dépendance à Vue : les appels de l'API lui sont passés en paramètre, ce
// qui le rend testable directement par `node --test`.
//
// Aucune règle métier ici (arc42 §5.1) : les actions affichées sont exactement les `transitionsPossibles` renvoyées
// par le serveur pour l'utilisateur (aucune pour un lecteur) ; les textes venus du serveur (titre, motif, commentaire,
// nom d'auteur) sont restitués comme texte par la vue, jamais interprétés comme HTML.
import {
  demandeTransition,
  libelleAction,
  libelleEtat,
  libelleMotif,
  libelleNiveau,
  type DemandeTransitionApi,
  type ImpactApi,
} from '../api/impacts-modele.ts'
import { STATUT_CONFLIT, STATUT_INTERDIT, STATUT_INTROUVABLE, STATUT_REQUETE_INVALIDE } from '../api/statuts-http.ts'

/** Ligne d'historique prête à afficher. */
export interface LigneHistorique {
  de: string
  vers: string
  auteur: string
  motif: string | null
  commentaire: string | null
  date: string | null
}

/** Action de transition proposée à l'utilisateur. */
export interface ActionTransition {
  vers: string
  libelle: string
  /** Vrai pour « Écarter » : le motif est demandé avant l'envoi. */
  motifObligatoire: boolean
}

/** Impact prêt à afficher. */
export interface ImpactAffiche {
  id: string
  projet: string
  composant: string
  etat: string
  codeEtat: string
  niveau: string
  historique: LigneHistorique[]
  actions: ActionTransition[]
}

/** Erreur d'appel minimale : statut HTTP et message du serveur. */
interface ErreurAppel {
  status?: number
  message?: string
}

function texte(valeur: unknown, defaut: string): string {
  return typeof valeur === 'string' && valeur.trim() !== '' ? valeur : defaut
}

/** Impact mis en forme ; les actions ne sont proposées que si le serveur en renvoie pour l'utilisateur. */
export function impactAffiche(impact: ImpactApi): ImpactAffiche {
  const historique = (impact.historique ?? []).map((transition) => ({
    de: libelleEtat(transition.de),
    vers: libelleEtat(transition.vers),
    auteur: transition.systeme ? 'système' : texte(transition.auteur, 'compte retiré'),
    motif: libelleMotif(transition.motif, transition.systeme),
    commentaire: transition.commentaire ?? null,
    date: transition.date ?? null,
  }))
  const actions = (impact.transitionsPossibles ?? []).map((vers) => ({
    vers,
    libelle: libelleAction(vers),
    motifObligatoire: vers === 'ECARTE',
  }))
  return {
    id: impact.id,
    projet: texte(impact.projectName, 'projet sans nom'),
    composant: texte(impact.componentPurl, 'composant inconnu'),
    etat: libelleEtat(impact.etat),
    codeEtat: impact.etat,
    niveau: libelleNiveau(impact.level),
    historique,
    actions,
  }
}

/** Message affiché après un refus du serveur. */
export function messageRefusTransition(cause: unknown): string {
  const erreur = (cause ?? {}) as ErreurAppel
  switch (erreur.status) {
    case STATUT_INTERDIT:
      return 'Votre rôle dans cet espace ne permet pas de changer l’état de cet impact.'
    case STATUT_INTROUVABLE:
      return 'Impact introuvable.'
    case STATUT_CONFLIT:
    case STATUT_REQUETE_INVALIDE:
      return texte(erreur.message, 'Transition refusée.')
    default:
      return 'La transition n’a pas pu être enregistrée. Réessayez.'
  }
}

/** Issue d'une demande de transition : impact à jour, ou message d'erreur. */
export type IssueTransition = { impact: ImpactAffiche } | { erreur: string }

/**
 * Vérifie la demande puis l'envoie ; l'impact renvoyé par le serveur (état, historique, actions) remplace
 * l'affichage. Une demande mal formée n'est pas envoyée.
 */
export async function appliquerTransition(
  impactId: string,
  vers: string,
  motif: string,
  commentaire: string,
  poster: (id: string, demande: DemandeTransitionApi) => Promise<ImpactApi>,
): Promise<IssueTransition> {
  const demande = demandeTransition(vers, motif, commentaire)
  if ('erreur' in demande) return demande
  try {
    return { impact: impactAffiche(await poster(impactId, demande.corps)) }
  } catch (erreur) {
    return { erreur: messageRefusTransition(erreur) }
  }
}
