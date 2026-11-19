// Client minimal de l'API REST ARGOS (/api/v1). Aucune règle métier ici (arc42 §5.1).

import { classerRefus, entetesAvecJeton, type Refus } from './autorisation'
import { STATUT_SANS_CONTENU } from './statuts-http'

const DEFAULT_API_URL = 'http://localhost:8080'

/**
 * URL du serveur ARGOS.
 * - variable absente : serveur local de développement (Vite sur 5173, Quarkus sur 8080) ;
 * - variable vide : IHM servie par le serveur lui-même (image Docker), même adresse ;
 * - variable renseignée : serveur indiqué (client desktop, autre hébergement).
 */
function resolveApiBaseUrl(configured: string | undefined): string {
  if (configured === undefined) return DEFAULT_API_URL
  if (configured === '') return window.location.origin
  return configured.replace(/\/$/, '')
}

export const apiBaseUrl: string = resolveApiBaseUrl(import.meta.env.VITE_ARGOS_API_URL)

export interface SystemInfo {
  application: string
  version: string
  apiVersion: string
}

/** Échec d'un appel sans réponse du serveur : délai dépassé ou annulation par l'appelant. */
export type MotifSansReponse = 'DELAI_DEPASSE' | 'ANNULE'

export class ApiError extends Error {
  constructor(
    message: string,
    readonly status?: number,
    readonly code?: string,
    readonly motif?: MotifSansReponse,
  ) {
    super(message)
    this.name = 'ApiError'
  }
}

// --- Jeton porteur et refus (ACC-09) -----------------------------------------

/** Branchement de la session : fournisseur du jeton et réaction aux refus. */
export interface AutorisationClient {
  jeton: () => string | null
  surRefus: (refus: Refus, chemin: string) => void
}

let autorisation: AutorisationClient = { jeton: () => null, surRefus: () => {} }

/** Branche le client sur la session de l'IHM (appelé une fois au démarrage, avant tout appel). */
export function configurerAutorisation(a: AutorisationClient): void {
  autorisation = a
}

/** Corps d'une demande refusée (schéma `Erreur`). */
interface ErreurApi {
  code?: string
  message?: string
  attribut?: string | null
}

/** Options d'un appel à l'API ARGOS. */
export interface OptionsAppel {
  methode?: 'GET' | 'POST' | 'PUT' | 'DELETE'
  /** Corps JSON de la demande ; absent : aucun corps. */
  corps?: unknown
  delaiMs?: number
  /** Annulation par l'appelant (réponse devenue sans objet) : l'appel lève `ApiError` avec `annule`. */
  signal?: AbortSignal
}

/**
 * Point d'appel unique de l'API ARGOS : tous les appels de l'IHM y passent. Le jeton porteur
 * s'ajoute ici, sur tout /api/v1 sauf l'identité du serveur ; un 401 relance la connexion et un 403
 * affiche le refus, par le rappel de la session. Réponse 204 : `undefined`. Refus : `ApiError` avec
 * le statut, et le message et le code du corps `Erreur` quand le serveur en renvoie un.
 */
export async function appelerApi<T>(chemin: string, options: OptionsAppel = {}): Promise<T> {
  const { methode = 'GET', corps, delaiMs = 5000, signal } = options
  const controller = new AbortController()
  let delaiDepasse = false
  const timer = setTimeout(() => {
    delaiDepasse = true
    controller.abort()
  }, delaiMs)
  const annuler = (): void => controller.abort()
  signal?.addEventListener('abort', annuler)
  const headers: Record<string, string> = { Accept: 'application/json' }
  if (corps !== undefined) headers['Content-Type'] = 'application/json'
  try {
    if (signal?.aborted) controller.abort()
    const response = await fetch(`${apiBaseUrl}${chemin}`, {
      method: methode,
      headers: entetesAvecJeton(chemin, headers, autorisation.jeton()),
      body: corps === undefined ? undefined : JSON.stringify(corps),
      signal: controller.signal,
    })
    if (!response.ok) {
      let erreur: ErreurApi | null = null
      try {
        erreur = (await response.json()) as ErreurApi | null
      } catch {
        // corps absent ou non JSON : message générique
      }
      const refus = classerRefus(response.status, erreur)
      if (refus) autorisation.surRefus(refus, chemin)
      const message = typeof erreur?.message === 'string' ? erreur.message : `Le serveur a répondu ${response.status}`
      throw new ApiError(message, response.status, typeof erreur?.code === 'string' ? erreur.code : undefined)
    }
    if (response.status === STATUT_SANS_CONTENU) return undefined as T
    return (await response.json()) as T
  } catch (error) {
    if (error instanceof ApiError) throw error
    if (delaiDepasse) {
      throw new ApiError(`Le serveur ARGOS n'a pas répondu dans le délai imparti (${apiBaseUrl}).`, undefined, undefined, 'DELAI_DEPASSE')
    }
    if (signal?.aborted) throw new ApiError('Appel annulé.', undefined, undefined, 'ANNULE')
    throw new ApiError(`Serveur ARGOS injoignable à ${apiBaseUrl}`)
  } finally {
    clearTimeout(timer)
    signal?.removeEventListener('abort', annuler)
  }
}

function getJson<T>(path: string): Promise<T> {
  return appelerApi<T>(path)
}

function putJson<T>(path: string, body: unknown): Promise<T> {
  return appelerApi<T>(path, { methode: 'PUT', corps: body })
}

function postJson<T>(path: string): Promise<T> {
  return appelerApi<T>(path, { methode: 'POST' })
}

/** Identité du serveur : publique, appelée sans jeton (ACC-01-S2). */
export function fetchSystemInfo(): Promise<SystemInfo> {
  return getJson<SystemInfo>('/api/v1/system/info')
}

// --- Compte courant (ACC-02) -------------------------------------------------

/** Compte courant (schéma `CompteCourant` de `GET /api/v1/moi`). */
export interface CompteCourant {
  id: string
  nomAffiche: string | null
  email: string | null
  adminPlateforme: boolean
  creeLe: string
}

/** Compte de l'utilisateur connecté ; 403 `INSCRIPTION_SUR_INVITATION` quand aucun compte n'est créé. */
export function fetchMoi(): Promise<CompteCourant> {
  return getJson<CompteCourant>('/api/v1/moi')
}

// --- Préférences et catalogue (AFF-01, AFF-03, PRF-01) ---------------------

/** Réponse de `GET /api/v1/preferences` (schéma OpenAPI `PreferencesEffectives`). */
export interface PreferencesEffectives {
  miseEnPage: string
  miseEnPageChoisie: string | null
  miseEnPageIndisponible: boolean
  message: string | null
  profilsActifs: string[]
  profilsParDefaut: boolean
  profilsIndisponibles: string[]
}

/** Élément de `GET /api/v1/catalogue/mises-en-page` (schéma `MiseEnPageCatalogue`). */
export interface MiseEnPageCatalogue {
  identifiant: string
  libelle: string
  description: string
  parDefaut: boolean
}

export function fetchMisesEnPage(): Promise<MiseEnPageCatalogue[]> {
  return getJson<MiseEnPageCatalogue[]>('/api/v1/catalogue/mises-en-page')
}

export function choisirMiseEnPage(miseEnPage: string): Promise<PreferencesEffectives> {
  return putJson<PreferencesEffectives>('/api/v1/preferences/mise-en-page', { miseEnPage })
}

// --- Alertes du poste (ALR-06, NOT-01 à NOT-06) -----------------------------

/** Projet cité par une alerte (schéma `ContenuAlerte.Projet`). */
export interface ProjetAlerte {
  id: string
  nom: string | null
  niveau: string
}

/** Source d'un élément d'alerte (schéma `ContenuAlerte.Source`). */
export interface SourceAlerte {
  nom: string | null
  identifiant: string | null
  url: string | null
}

/** Élément d'une alerte (schéma `ContenuAlerte.Element`). */
export interface ElementAlerteApi {
  signalId: string
  titre: string
  gravite: string | null
  projets: ProjetAlerte[]
  sources: SourceAlerte[]
  lien: string | null
}

/** Alerte ou résumé de l'appelant (schéma `AlerteVue` de `GET /api/v1/alertes`). */
export interface AlerteVue {
  id: string
  nature: string
  canal: string
  etat: string
  titre: string
  gravite: string | null
  contenu: {
    elements: ElementAlerteApi[]
    resumeIa: string | null
    etatResumeIa: string
    /** « produit par l'IA » dès qu'un résumé est présent (IAG-05). */
    mentionResumeIa?: string | null
  } | null
  tentatives: number
  creeLe: string
  envoyeLe: string | null
}

/** Alertes du canal DESKTOP encore à remettre au poste (au plus 200, contrat `AlertesResource`). */
export function fetchAlertesPosteEnAttente(): Promise<AlerteVue[]> {
  return getJson<AlerteVue[]>('/api/v1/alertes?canal=DESKTOP&etat=EN_ATTENTE&limite=200')
}

/** Alertes du canal DESKTOP de l'appelant, des plus récentes aux plus anciennes. */
export function fetchAlertesPoste(): Promise<AlerteVue[]> {
  return getJson<AlerteVue[]>('/api/v1/alertes?canal=DESKTOP&limite=50')
}

export function fetchAlerte(id: string): Promise<AlerteVue> {
  return getJson<AlerteVue>(`/api/v1/alertes/${encodeURIComponent(id)}`)
}

/** Confirme la remise d'une alerte DESKTOP au poste. */
export function confirmerRemise(id: string): Promise<AlerteVue> {
  return postJson<AlerteVue>(`/api/v1/alertes/${encodeURIComponent(id)}/remise`)
}
