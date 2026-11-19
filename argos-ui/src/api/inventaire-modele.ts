// Synthèse de l'inventaire d'un projet telle que la restitue `GET /api/v1/projects/{projectId}/inventory/summary`
// (INV-01-S3, INV-06, INV-10 ; décision D6 du 2026-11-18, décisions E4, G5 et J6 du 2026-11-19), projets visibles
// (`GET /api/v1/projects`, INV-06, décision F1 du 2026-11-19) et composants inventoriés paginés
// (`GET /api/v1/projects/{projectId}/inventory/components`, INV-06, décision F5 du 2026-11-19) : types des réponses
// et chemins des appels. Module pur, sans appel réseau : testé directement par `node --test`. Les appels eux-mêmes
// passent par `appelerApi` (`inventaire.ts`).
import { estUuid } from './recherche-criteres.ts'

/** Composant de la SBOM écarté de l'inventaire faute de PURL exploitable (schéma `IgnoredComponent`). */
export interface ComposantIgnoreApi {
  name: string | null
  group: string | null
  version: string | null
  /** `SANS_PURL` ou `PURL_INVALIDE`. */
  reason: string | null
}

/** Synthèse de l'inventaire de référence, sans la liste des composants (schéma `InventorySummary`). */
export interface SyntheseInventaireApi {
  projectId: string
  origin: string | null
  sourceDate: string | null
  /** Âge de la source en jours, `null` si la date de la source est inconnue. */
  ageDays?: number | null
  /** Vrai quand l'inventaire est jugé obsolète par le serveur (INV-03). */
  obsolete?: boolean
  importedAt: string | null
  /** Nombre de composants inventoriés (avec un PURL exploitable). */
  componentCount: number
  /** Nombre total de composants écartés au dernier import. */
  ignoredCount: number
  /** Les premiers composants écartés, au plus {@link MAX_COMPOSANTS_IGNORES}. */
  ignoredComponents: ComposantIgnoreApi[] | null
  /** Dépôts du projet, le principal d'abord (INV-10) ; absent d'un serveur antérieur, vide sans dépôt. */
  repositories?: DepotInventaireApi[] | null
}

/** Dernier échec d'import d'un dépôt, postérieur à son dernier import réussi (schéma `RepositoryFailure`). */
export interface EchecDepotApi {
  reason: string | null
  receivedAt: string | null
}

/** Dépôt d'un projet et son dernier import réussi (schéma `RepositorySummary`, INV-10-S1). */
export interface DepotInventaireApi {
  id: string
  /** `GITLAB` ou `GITHUB`. */
  provider: string | null
  repository: string | null
  branch: string | null
  principal: boolean
  /** `SBOM` ou `MANIFEST`, `null` tant qu'aucun import n'a réussi. */
  origin: string | null
  sourceDate: string | null
  importedAt: string | null
  lastFailure: EchecDepotApi | null
}

/** Dépôt dans lequel figure un composant (schéma `ComponentRepository`). */
export interface DepotComposantApi {
  id: string
  provider: string | null
  repository: string | null
}

/** Réponse de l'appel (schéma `ProjectInventorySummary`) : `summary` est absent quand `status` vaut `NO_SBOM`. */
export interface SyntheseInventaireProjetApi {
  projectId: string
  status: 'AVAILABLE' | 'NO_SBOM' | string
  summary: SyntheseInventaireApi | null
}

/** Borne de la liste restituée par l'API (`ParsedInventory.MAX_SIGNALED`). */
export const MAX_COMPOSANTS_IGNORES = 100

/** Borne de chaque champ restitué par l'API (nom, groupe, version, motif). */
export const MAX_CARACTERES_CHAMP = 200

/**
 * Chemin de l'inventaire d'un projet, base des chemins de la synthèse et des composants ; `null` si l'identifiant
 * n'est pas un UUID (aucune URL construite).
 */
export function cheminInventaire(projetId: unknown): string | null {
  if (!estUuid(projetId)) return null
  return `/api/v1/projects/${encodeURIComponent(projetId.trim())}/inventory`
}

/** Chemin de la synthèse de l'inventaire d'un projet ; `null` si l'identifiant n'est pas un UUID. */
export function cheminSyntheseInventaire(projetId: unknown): string | null {
  const base = cheminInventaire(projetId)
  return base ? `${base}/summary` : null
}

/** Projet visible de l'appelant (schéma `ProjectItem`). */
export interface ProjetApi {
  id: string
  workspaceId: string | null
  /** Nom de l'espace du projet, `null` s'il n'est pas lisible. */
  workspaceName: string | null
  name: string | null
}

/** Page de projets (schéma `ProjectPage`). */
export interface PageProjetsApi {
  items: ProjetApi[] | null
  page: number
  size: number
  /** Nombre total de projets visibles. */
  total: number
}

/** Composant inventorié (schéma `ComponentItem`) ; `transitive` vaut `null` si le lien est inconnu. */
export interface ComposantApi {
  id: string
  name: string | null
  version: string | null
  purl: string | null
  packageKey?: string | null
  transitive: boolean | null
  /** Dépôts du projet qui contiennent le composant (INV-10) ; absent d'un serveur antérieur. */
  repositories?: DepotComposantApi[] | null
}

/** Page de composants inventoriés (schéma `ComponentPage`). */
export interface PageComposantsApi {
  projectId: string
  items: ComposantApi[] | null
  page: number
  size: number
  /** Nombre total de composants inventoriés du projet. */
  total: number
}

/** Taille maximale d'une page acceptée par l'API (`InventoryQueries.MAX_PAGE_SIZE`). */
export const TAILLE_PAGE_MAX = 200

/** Taille des pages de composants demandées par la vue d'inventaire. */
export const TAILLE_PAGE_COMPOSANTS = 20

/** Chemin de la liste des projets visibles : première page, de la taille maximale acceptée par l'API. */
export function cheminProjets(): string {
  return `/api/v1/projects?page=0&size=${TAILLE_PAGE_MAX}`
}

/**
 * Chemin d'une page des composants inventoriés d'un projet ; `null` si l'identifiant n'est pas un UUID ou si la
 * page n'est pas un entier positif ou nul (aucune URL construite).
 */
export function cheminComposants(projetId: unknown, page: unknown, taille: number = TAILLE_PAGE_COMPOSANTS): string | null {
  const base = cheminInventaire(projetId)
  if (!base || typeof page !== 'number' || !Number.isInteger(page) || page < 0) return null
  const tailleBornee = Math.min(Math.max(Math.trunc(taille), 1), TAILLE_PAGE_MAX)
  return `${base}/components?page=${page}&size=${tailleBornee}`
}
