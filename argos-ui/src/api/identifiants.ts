// Identifiants placés dans le chemin des appels de l'API : seuls des UUID y entrent, encodés. Un identifiant invalide
// refuse l'appel avant tout envoi (aucune URL construite). Aucune règle métier ici (arc42 §5.1).
import { ApiError } from './client'
import { estUuid } from './recherche-criteres.ts'
import { STATUT_REQUETE_INVALIDE } from './statuts-http.ts'

/**
 * Appelle `appel` avec les identifiants encodés pour un chemin, s'ils sont tous des UUID ; sinon refuse par une
 * `ApiError` 400 dont le message nomme l'identifiant attendu (`libelle`, par exemple « Identifiant de projet »).
 */
export function avecIdentifiants<T>(
  libelle: string,
  identifiants: readonly unknown[],
  appel: (segments: string[]) => Promise<T>,
): Promise<T> {
  const segments: string[] = []
  for (const identifiant of identifiants) {
    if (!estUuid(identifiant)) return Promise.reject(new ApiError(`${libelle} invalide.`, STATUT_REQUETE_INVALIDE))
    segments.push(encodeURIComponent(identifiant.trim()))
  }
  return appel(segments)
}
