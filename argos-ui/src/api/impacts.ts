// Impacts d'un signal, détail d'un impact et transitions d'état (COR-09, COR-10, COR-12) : appels de
// `GET /api/v1/signals/{id}/impacts`, `GET /api/v1/impacts/{id}` et `POST /api/v1/impacts/{id}/transitions` par le
// point d'appel commun `appelerApi` du client REST. Aucune règle métier ici (arc42 §5.1).
import { ApiError, appelerApi } from './client'
import {
  cheminImpact,
  cheminImpactsSignal,
  cheminTransitions,
  type DemandeTransitionApi,
  type ImpactApi,
  type PageImpactsApi,
} from './impacts-modele.ts'
import { STATUT_REQUETE_INVALIDE } from './statuts-http.ts'

export type { DemandeTransitionApi, ImpactApi, PageImpactsApi, TransitionApi } from './impacts-modele.ts'

function refuse<T>(message: string): Promise<T> {
  return Promise.reject(new ApiError(message, STATUT_REQUETE_INVALIDE))
}

/** Impacts d'un signal dans les espaces de l'appelant, tous états confondus. */
export function fetchImpactsSignal(signalId: string): Promise<PageImpactsApi> {
  const chemin = cheminImpactsSignal(signalId)
  return chemin ? appelerApi<PageImpactsApi>(chemin) : refuse('Identifiant de signal invalide.')
}

/** Impact avec son historique et les transitions que l'appelant peut demander. */
export function fetchImpact(impactId: string): Promise<ImpactApi> {
  const chemin = cheminImpact(impactId)
  return chemin ? appelerApi<ImpactApi>(chemin) : refuse('Identifiant d’impact invalide.')
}

/** Demande une transition d'état ; renvoie l'impact après la transition. */
export function demanderTransition(impactId: string, demande: DemandeTransitionApi): Promise<ImpactApi> {
  const chemin = cheminTransitions(impactId)
  return chemin
    ? appelerApi<ImpactApi>(chemin, { methode: 'POST', corps: demande })
    : refuse('Identifiant d’impact invalide.')
}
