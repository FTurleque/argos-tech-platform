// Inventaire d'un projet (INV-01-S3, INV-06) : appels de `GET /api/v1/projects/{projectId}/inventory/summary`
// (synthèse sans composants, décision G5 du 2026-11-19), de `GET /api/v1/projects` (projets visibles, décision F1
// du 2026-11-19) et de `GET /api/v1/projects/{projectId}/inventory/components` (composants paginés, décision F5 du
// 2026-11-19) par le point d'appel commun `appelerApi` du client REST. Aucune règle métier ici (arc42 §5.1) : les
// composants écartés, leur nombre, la borne de la liste, la visibilité des projets, le tri et les bornes des pages
// sont calculés par le serveur.
import { ApiError, appelerApi } from './client'
import {
  cheminComposants,
  cheminProjets,
  cheminSyntheseInventaire,
  type PageComposantsApi,
  type PageProjetsApi,
  type SyntheseInventaireProjetApi,
} from './inventaire-modele.ts'
import { STATUT_REQUETE_INVALIDE } from './statuts-http.ts'

export type {
  ComposantApi,
  ComposantIgnoreApi,
  PageComposantsApi,
  PageProjetsApi,
  ProjetApi,
  SyntheseInventaireApi,
  SyntheseInventaireProjetApi,
} from './inventaire-modele.ts'

/** Synthèse de l'inventaire d'un projet visible de l'appelant ; refuse un identifiant qui n'est pas un UUID. */
export function fetchSyntheseInventaire(projetId: string, signal?: AbortSignal): Promise<SyntheseInventaireProjetApi> {
  const chemin = cheminSyntheseInventaire(projetId)
  if (!chemin) return Promise.reject(new ApiError('Identifiant de projet invalide.', STATUT_REQUETE_INVALIDE))
  return appelerApi<SyntheseInventaireProjetApi>(chemin, { signal })
}

/** Projets des espaces de l'appelant (première page, taille maximale acceptée par l'API). */
export function fetchProjets(signal?: AbortSignal): Promise<PageProjetsApi> {
  return appelerApi<PageProjetsApi>(cheminProjets(), { signal })
}

/** Page des composants inventoriés d'un projet ; refuse un identifiant qui n'est pas un UUID ou une page invalide. */
export function fetchComposantsProjet(projetId: string, page: number, signal?: AbortSignal): Promise<PageComposantsApi> {
  const chemin = cheminComposants(projetId, page)
  if (!chemin) return Promise.reject(new ApiError('Identifiant de projet ou page invalide.', STATUT_REQUETE_INVALIDE))
  return appelerApi<PageComposantsApi>(chemin, { signal })
}
