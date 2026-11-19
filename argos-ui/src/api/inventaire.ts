// Inventaire d'un projet (INV-01-S3) : appel de `GET /api/v1/projects/{projectId}/inventory` par le point
// d'appel commun `appelerApi` du client REST. Aucune règle métier ici (arc42 §5.1) : les composants écartés,
// leur nombre et la borne de la liste sont calculés par le serveur.
import { ApiError, appelerApi } from './client'
import { cheminInventaire, type InventaireProjetApi } from './inventaire-modele.ts'

export type { ComposantIgnoreApi, InventaireApi, InventaireProjetApi } from './inventaire-modele.ts'

/** Inventaire d'un projet visible de l'appelant ; refuse un identifiant qui n'est pas un UUID. */
export function fetchInventaireProjet(projetId: string, signal?: AbortSignal): Promise<InventaireProjetApi> {
  const chemin = cheminInventaire(projetId)
  if (!chemin) return Promise.reject(new ApiError('Identifiant de projet invalide.', 400))
  return appelerApi<InventaireProjetApi>(chemin, { signal })
}
