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
  constructor(message: string, readonly status?: number) {
    super(message)
    this.name = 'ApiError'
  }
}

async function getJson<T>(path: string, timeoutMs = 5000): Promise<T> {
  const controller = new AbortController()
  const timer = setTimeout(() => controller.abort(), timeoutMs)
  try {
    const response = await fetch(`${apiBaseUrl}${path}`, {
      headers: { Accept: 'application/json' },
      signal: controller.signal,
    })
    if (!response.ok) {
      throw new ApiError(`Le serveur a répondu ${response.status}`, response.status)
    }
    return (await response.json()) as T
  } catch (error) {
    if (error instanceof ApiError) throw error
    throw new ApiError(`Serveur ARGOS injoignable à ${apiBaseUrl}`)
  } finally {
    clearTimeout(timer)
  }
}

export function fetchSystemInfo(): Promise<SystemInfo> {
  return getJson<SystemInfo>('/api/v1/system/info')
}
