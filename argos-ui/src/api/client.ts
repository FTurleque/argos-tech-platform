// Client minimal de l'API REST ARGOS (/api/v1). Aucune règle métier ici (arc42 §5.1).

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

export class ApiError extends Error {
  constructor(message: string, readonly status?: number, readonly code?: string) {
    super(message)
    this.name = 'ApiError'
  }
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
}

/**
 * Point d'appel unique de l'API ARGOS : tous les appels de l'IHM y passent, de sorte que l'en-tête
 * d'authentification ne s'ajoute qu'ici. Réponse 204 : `undefined`. Refus : `ApiError` avec le
 * statut, et le message et le code du corps `Erreur` quand le serveur en renvoie un.
 */
export async function appelerApi<T>(chemin: string, options: OptionsAppel = {}): Promise<T> {
  const { methode = 'GET', corps, delaiMs = 5000 } = options
  const controller = new AbortController()
  const timer = setTimeout(() => controller.abort(), delaiMs)
  const headers: Record<string, string> = { Accept: 'application/json' }
  if (corps !== undefined) headers['Content-Type'] = 'application/json'
  try {
    const response = await fetch(`${apiBaseUrl}${chemin}`, {
      method: methode,
      headers,
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
      throw new ApiError(erreur?.message ?? `Le serveur a répondu ${response.status}`, response.status, erreur?.code)
    }
    if (response.status === 204) return undefined as T
    return (await response.json()) as T
  } catch (error) {
    if (error instanceof ApiError) throw error
    throw new ApiError(`Serveur ARGOS injoignable à ${apiBaseUrl}`)
  } finally {
    clearTimeout(timer)
  }
}

function getJson<T>(path: string, timeoutMs = 5000): Promise<T> {
  return appelerApi<T>(path, { delaiMs: timeoutMs })
}

export function fetchSystemInfo(): Promise<SystemInfo> {
  return getJson<SystemInfo>('/api/v1/system/info')
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

function putJson<T>(path: string, body: unknown, timeoutMs = 5000): Promise<T> {
  return appelerApi<T>(path, { methode: 'PUT', corps: body, delaiMs: timeoutMs })
}

export function fetchPreferences(): Promise<PreferencesEffectives> {
  return getJson<PreferencesEffectives>('/api/v1/preferences')
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

function postJson<T>(path: string, timeoutMs = 5000): Promise<T> {
  return appelerApi<T>(path, { methode: 'POST', delaiMs: timeoutMs })
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
