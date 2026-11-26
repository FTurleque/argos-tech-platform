// Administration des projets et alimentation de leur inventaire (INV-12, INV-13, PIN-06) : création d'un projet,
// rattachement et détachement d'un dépôt (administrateur de l'espace), import d'une SBOM ou de manifestes, relance de
// l'import depuis les dépôts et collecte immédiate de l'activité (contributeur), par le point d'appel commun
// `appelerApi`. Aucune règle métier ici (arc42 §5.1) : droits, validation des saisies, verrou du projet et issue de
// chaque import sont décidés par le serveur ; ce module transmet les saisies et restitue les réponses.
import { appelerApi } from './client'
import { avecIdentifiants } from './identifiants'
import type { ComposantIgnoreApi } from './inventaire-modele.ts'

/** Délai des opérations qui lisent une forge ou analysent un document (imports, relance, collecte). */
const DELAI_TRAITEMENT_MS = 30_000

const PROJET = 'Identifiant de projet'
const DEPOT = 'Identifiant de dépôt'

/** Forges acceptées pour un dépôt. */
export type Forge = 'GITHUB' | 'GITLAB'

/** Dépôt à rattacher à un projet (schéma `Depot`) ; la branche vaut `main` côté serveur quand elle est absente. */
export interface Depot {
  forge: Forge
  depot: string
  branche?: string
  cheminSbom?: string
  jobSbom?: string
  manifestes?: string[]
}

/** Demande de création d'un projet dans un espace dont l'appelant est administrateur. */
export interface NouveauProjet {
  espaceId: string
  nom: string
  depot?: Depot
}

/** Projet créé. */
export interface ProjetCree {
  id: string
  espaceId: string
  nom: string
}

/** Dépôt rattaché ; `principal` : premier dépôt du projet. */
export interface DepotRattache {
  id: string
  principal: boolean
}

/** Issue d'un import d'inventaire (énumération `StatutImport` du serveur). */
export type StatutImport = 'IMPORTED' | 'REJECTED' | 'KEPT' | 'NONE' | 'ERROR' | 'DEFERRED'

/** Résultat d'un import (schéma `ImportResult`). */
export interface ResultatImport {
  status: StatutImport
  origin: string | null
  components: number
  ignored: number
  reason: string | null
  /** Composants écartés faute de PURL exploitable (schéma `IgnoredComponent`, partagé avec la synthèse). */
  ignoredComponents: ComposantIgnoreApi[] | null
}

/** Manifeste de dépendances transmis tel quel (chemin dans le dépôt et contenu texte). */
export interface Manifeste {
  chemin: string
  contenu: string
}

/** Résultat de la relance pour un dépôt du projet. */
export interface ResultatDepot {
  repositoryId: string
  provider: string | null
  repository: string | null
  result: ResultatImport
}

/** Résultat de la relance de l'import depuis les dépôts du projet. */
export interface ResultatRelance {
  projectId: string
  depots: ResultatDepot[]
}

/** Issue d'une collecte immédiate de l'activité (énumération `CollecteActivite.Issue` du serveur). */
export type IssueCollecte = 'A_JOUR' | 'REPORTEE_QUOTA' | 'ECHEC' | 'NON_ECHUE' | 'SANS_DEPOT'

/** Réponse de la collecte immédiate. */
export interface ResultatCollecte {
  issue: IssueCollecte
}

/** Crée un projet ; deux appels créent deux projets (opération non idempotente). */
export function creerProjet(demande: NouveauProjet): Promise<ProjetCree> {
  return appelerApi<ProjetCree>('/api/v1/projects', { methode: 'POST', corps: demande })
}

/** Rattache un dépôt au projet ; 409 quand ce dépôt (forge, identifiant, branche) y est déjà rattaché. */
export function rattacherDepot(projetId: string, depot: Depot): Promise<DepotRattache> {
  return avecIdentifiants(PROJET, [projetId], ([projet]) =>
    appelerApi<DepotRattache>(`/api/v1/projects/${projet}/repositories`, { methode: 'POST', corps: depot }),
  )
}

/** Détache un dépôt du projet (réponse sans contenu). */
export function detacherDepot(projetId: string, depotId: string): Promise<void> {
  return avecIdentifiants(`${PROJET} ou ${DEPOT.toLowerCase()}`, [projetId, depotId], ([projet, depot]) =>
    appelerApi<void>(`/api/v1/projects/${projet}/repositories/${depot}`, { methode: 'DELETE' }),
  )
}

/** Importe une SBOM CycloneDX : le document JSON choisi est envoyé tel quel comme corps. */
export function importerSbom(projetId: string, document: unknown): Promise<ResultatImport> {
  return avecIdentifiants(PROJET, [projetId], ([projet]) =>
    appelerApi<ResultatImport>(`/api/v1/projects/${projet}/inventory/sbom`, {
      methode: 'POST',
      corps: document,
      delaiMs: DELAI_TRAITEMENT_MS,
    }),
  )
}

/** Importe des manifestes de dépendances (contenu texte de chaque fichier). */
export function importerManifestes(projetId: string, manifestes: readonly Manifeste[]): Promise<ResultatImport> {
  return avecIdentifiants(PROJET, [projetId], ([projet]) =>
    appelerApi<ResultatImport>(`/api/v1/projects/${projet}/inventory/manifests`, {
      methode: 'POST',
      corps: { manifestes },
      delaiMs: DELAI_TRAITEMENT_MS,
    }),
  )
}

/** Relance l'import depuis chaque dépôt du projet ; 409 pour un projet sans dépôt. */
export function relancerImport(projetId: string): Promise<ResultatRelance> {
  return avecIdentifiants(PROJET, [projetId], ([projet]) =>
    appelerApi<ResultatRelance>(`/api/v1/projects/${projet}/inventory/refresh`, {
      methode: 'POST',
      delaiMs: DELAI_TRAITEMENT_MS,
    }),
  )
}

/** Collecte immédiatement l'activité du projet, avec les règles et quotas de la collecte planifiée. */
export function collecterActivite(projetId: string): Promise<ResultatCollecte> {
  return avecIdentifiants(PROJET, [projetId], ([projet]) =>
    appelerApi<ResultatCollecte>(`/api/v1/projects/${projet}/activite/collecte`, {
      methode: 'POST',
      delaiMs: DELAI_TRAITEMENT_MS,
    }),
  )
}
