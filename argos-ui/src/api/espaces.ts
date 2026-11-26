// Espaces de l'utilisateur et son rôle dans chacun (ACC-03) : `GET /api/v1/espaces` et `GET /api/v1/espaces/{id}`,
// par le point d'appel commun `appelerApi`. Aucune règle métier ici (arc42 §5.1) : l'appartenance et les droits sont
// décidés par le serveur ; le rôle sert seulement à ne pas proposer une commande que le serveur refuserait.
import { appelerApi } from './client'
import { avecIdentifiants } from './identifiants'

/** Rôle d'un compte dans un espace (énumération `Role` du serveur), dans l'ordre croissant des droits. */
export type RoleEspace = 'LECTEUR' | 'CONTRIBUTEUR' | 'ADMINISTRATEUR'

/** Espace vu par un de ses membres (schéma `EspaceDeCompte`). */
export interface EspaceDeCompte {
  id: string
  nom: string
  role: RoleEspace
  creeLe: string
}

/** Espaces dont l'utilisateur est membre, avec son rôle dans chacun. */
export function fetchEspaces(signal?: AbortSignal): Promise<EspaceDeCompte[]> {
  return appelerApi<EspaceDeCompte[]>('/api/v1/espaces', { signal })
}

/** Espace dont l'utilisateur est membre ; 404 pour un espace inconnu ou dont il n'est pas membre. */
export function fetchEspace(espaceId: string, signal?: AbortSignal): Promise<EspaceDeCompte> {
  return avecIdentifiants("Identifiant d'espace", [espaceId], ([espace]) =>
    appelerApi<EspaceDeCompte>(`/api/v1/espaces/${espace}`, { signal }),
  )
}
