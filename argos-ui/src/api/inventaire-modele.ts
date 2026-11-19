// Inventaire d'un projet tel que le restitue `GET /api/v1/projects/{projectId}/inventory` (INV-01-S3,
// décision D6 du 2026-11-18, décision E4 du 2026-11-19) : types de la réponse et chemin de l'appel. Module pur, sans appel réseau :
// testé directement par `node --test`. L'appel lui-même passe par `appelerApi` (`inventaire.ts`).
import { estUuid } from './recherche-criteres.ts'

/** Composant de la SBOM écarté de l'inventaire faute de PURL exploitable (schéma `IgnoredComponent`). */
export interface ComposantIgnoreApi {
  name: string | null
  group: string | null
  version: string | null
  /** `SANS_PURL` ou `PURL_INVALIDE`. */
  reason: string | null
}

/** Inventaire de référence (schéma `InventoryView`), réduit aux champs lus par l'IHM. */
export interface InventaireApi {
  projectId: string
  origin: string | null
  sourceDate: string | null
  /** Âge de la source en jours, `null` si la date de la source est inconnue. */
  ageDays?: number | null
  /** Vrai quand l'inventaire est jugé obsolète par le serveur (INV-03). */
  obsolete?: boolean
  importedAt: string | null
  /** Composants inventoriés (avec un PURL exploitable) ; seul leur nombre est lu par l'IHM. */
  components?: unknown[] | null
  /** Nombre total de composants écartés au dernier import. */
  ignoredCount: number
  /** Les premiers composants écartés, au plus {@link MAX_COMPOSANTS_IGNORES}. */
  ignoredComponents: ComposantIgnoreApi[] | null
}

/** Réponse de l'appel (schéma `ProjectInventory`) : `inventory` est absent quand `status` vaut `NO_SBOM`. */
export interface InventaireProjetApi {
  projectId: string
  status: 'AVAILABLE' | 'NO_SBOM' | string
  inventory: InventaireApi | null
}

/** Borne de la liste restituée par l'API (`ParsedInventory.MAX_SIGNALED`). */
export const MAX_COMPOSANTS_IGNORES = 100

/** Borne de chaque champ restitué par l'API (nom, groupe, version, motif). */
export const MAX_CARACTERES_CHAMP = 200

/** Chemin de l'inventaire d'un projet ; `null` si l'identifiant n'est pas un UUID (aucune URL construite). */
export function cheminInventaire(projetId: unknown): string | null {
  if (!estUuid(projetId)) return null
  return `/api/v1/projects/${encodeURIComponent(projetId.trim())}/inventory`
}
