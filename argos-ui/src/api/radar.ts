// Radar des sorties et référentiel des standards d'un espace (RAD-01 à RAD-06, RAD-09) : appels de
// `/api/v1/radar/espaces/{espaceId}/…` par le point d'appel commun `appelerApi`. Aucune règle métier ici
// (arc42 §5.1) : échéances, dettes, référentiel et suggestions sont calculés par le serveur, qui seul décide des
// droits (décision réservée à l'administrateur de l'espace, RAD-02).
import { appelerApi } from './client'
import { avecIdentifiants } from './identifiants'

const ESPACE = "Identifiant d'espace"

/** Horizons du radar acceptés par le serveur. */
export const HORIZONS = ['30j', '6m', '1a'] as const

export type Horizon = (typeof HORIZONS)[number]

/** Statut d'une technologie au référentiel, selon le principe d'un radar technologique. */
export type StatutStandard = 'ADOPT' | 'TRIAL' | 'ASSESS' | 'HOLD'

/** Échéance du radar (schéma `EcheanceRadar`) ; `nature` : `SORTIE`, `CORRECTIF` ou `FIN_DE_SUPPORT`. */
export interface EcheanceRadar {
  cle: string
  libelle: string | null
  version: string | null
  nature: string
  date: string
  source: string | null
  note: string | null
}

/** Fraîcheur du calendrier d'un produit suivi (schéma `EtatProduit`). */
export interface EtatProduit {
  produit: string
  etat: string
  derniereSynchro: string | null
  dernierSucces: string | null
  derniereErreur: string | null
}

/** Radar d'un horizon (schéma `Radar`). */
export interface Radar {
  horizon: string
  depuis: string
  jusqua: string
  echeances: EcheanceRadar[]
  sources: EtatProduit[]
}

/** Impact « dette de version » d'un projet de l'espace (schéma `DetteVersion`). */
export interface DetteVersion {
  id: string
  projetId: string
  projet: string | null
  cle: string
  version: string | null
  cycle: string | null
  finSupport: string | null
  versionStandard: string | null
  source: string | null
  creeLe: string
}

/** Entrée du référentiel de l'espace (schéma `EntreeReferentiel`). */
export interface EntreeReferentiel {
  id: string
  espaceId: string
  cle: string
  libelle: string | null
  statut: StatutStandard
  versionRecommandee: string | null
  responsable: string | null
  justification: string | null
  dateRevue: string | null
  produitCycleVie: string | null
  modifieLe: string
}

/** Suggestion de l'IA pour le référentiel (schéma `Suggestion`) ; `etat` : `EN_ATTENTE`, `ACCEPTEE` ou `REFUSEE`. */
export interface SuggestionReferentiel {
  id: string
  espaceId: string
  cle: string
  statut: StatutStandard
  versionRecommandee: string | null
  justification: string | null
  origine: string | null
  etat: string
  creeLe: string
  decideLe: string | null
}

/** Décision d'un administrateur sur une suggestion en attente. */
export type DecisionSuggestion = 'ACCEPTER' | 'REJETER'

/** Échéances de l'espace sur un horizon, et fraîcheur du calendrier. */
export function fetchEcheances(espaceId: string, horizon: Horizon, signal?: AbortSignal): Promise<Radar> {
  return avecIdentifiants(ESPACE, [espaceId], ([espace]) =>
    appelerApi<Radar>(`/api/v1/radar/espaces/${espace}/echeances?horizon=${encodeURIComponent(horizon)}`, { signal }),
  )
}

/** Dettes de version des projets de l'espace. */
export function fetchDettes(espaceId: string, signal?: AbortSignal): Promise<DetteVersion[]> {
  return avecIdentifiants(ESPACE, [espaceId], ([espace]) =>
    appelerApi<DetteVersion[]>(`/api/v1/radar/espaces/${espace}/dettes`, { signal }),
  )
}

/** Référentiel des standards de l'espace. */
export function fetchStandards(espaceId: string, signal?: AbortSignal): Promise<EntreeReferentiel[]> {
  return avecIdentifiants(ESPACE, [espaceId], ([espace]) =>
    appelerApi<EntreeReferentiel[]>(`/api/v1/radar/espaces/${espace}/standards`, { signal }),
  )
}

/** Suggestions de l'IA, en attente d'abord. */
export function fetchSuggestions(espaceId: string, signal?: AbortSignal): Promise<SuggestionReferentiel[]> {
  return avecIdentifiants(ESPACE, [espaceId], ([espace]) =>
    appelerApi<SuggestionReferentiel[]>(`/api/v1/radar/espaces/${espace}/suggestions`, { signal }),
  )
}

/** Accepte ou rejette une suggestion en attente ; renvoie les suggestions de l'espace après la décision. */
export function deciderSuggestion(
  espaceId: string,
  suggestionId: string,
  decision: DecisionSuggestion,
): Promise<SuggestionReferentiel[]> {
  return avecIdentifiants("Identifiant d'espace ou de suggestion", [espaceId, suggestionId], ([espace, suggestion]) =>
    appelerApi<SuggestionReferentiel[]>(`/api/v1/radar/espaces/${espace}/suggestions/${suggestion}/decision`, {
      methode: 'POST',
      corps: { decision },
    }),
  )
}
