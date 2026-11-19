// Inventaire d'un projet (INV-01-S3, INV-06) : appels de `GET /api/v1/projects/{projectId}/inventory`, de
// `GET /api/v1/projects` (projets visibles, décision F1 du 2026-11-19) et de
// `GET /api/v1/projects/{projectId}/inventory/components` (composants paginés, décision F5 du 2026-11-19) par le
// point d'appel commun `appelerApi` du client REST. Aucune règle métier ici (arc42 §5.1) : les composants écartés,
// leur nombre, la borne de la liste, la visibilité des projets, le tri et les bornes des pages sont calculés par
// le serveur.
import { ApiError, appelerApi } from './client'
import {
  cheminComposants,
  cheminInventaire,
  cheminProjets,
  type InventaireProjetApi,
  type PageComposantsApi,
  type PageProjetsApi,
} from './inventaire-modele.ts'

export type {
  ComposantApi,
  ComposantIgnoreApi,
  InventaireApi,
  InventaireProjetApi,
  PageComposantsApi,
  PageProjetsApi,
  ProjetApi,
} from './inventaire-modele.ts'

/** Inventaire d'un projet visible de l'appelant ; refuse un identifiant qui n'est pas un UUID. */
export function fetchInventaireProjet(projetId: string, signal?: AbortSignal): Promise<InventaireProjetApi> {
  const chemin = cheminInventaire(projetId)
  if (!chemin) return Promise.reject(new ApiError('Identifiant de projet invalide.', 400))
  return appelerApi<InventaireProjetApi>(chemin, { signal })
}

/** Projets des espaces de l'appelant (première page, taille maximale acceptée par l'API). */
export function fetchProjets(signal?: AbortSignal): Promise<PageProjetsApi> {
  return appelerApi<PageProjetsApi>(cheminProjets(), { signal })
}

/** Page des composants inventoriés d'un projet ; refuse un identifiant qui n'est pas un UUID ou une page invalide. */
export function fetchComposantsProjet(projetId: string, page: number, signal?: AbortSignal): Promise<PageComposantsApi> {
  const chemin = cheminComposants(projetId, page)
  if (!chemin) return Promise.reject(new ApiError('Identifiant de projet ou page invalide.', 400))
  return appelerApi<PageComposantsApi>(chemin, { signal })
}
