// Outillage des tests de composants (Vitest, happy-dom) : API simulée derrière `fetch` et montage d'une vue par son
// adresse, avec le routeur de l'IHM en mémoire (`createMemoryHistory`). Aucun réseau, aucun navigateur réel.
import { flushPromises, mount, type VueWrapper } from '@vue/test-utils'
import { vi } from 'vitest'
import { defineComponent, h as creerNoeud } from 'vue'
import { createMemoryHistory, createRouter, RouterView, type Router } from 'vue-router'
import { apiBaseUrl } from '../../src/api/client'
import { ROUTES_IHM } from '../../src/router'

const STATUT_OK = 200
const STATUT_SANS_CONTENU = 204
const STATUT_INTROUVABLE = 404

/** Appel reçu par l'API simulée. */
export interface AppelApi {
  methode: string
  /** Chemin et requête, sans l'origine du serveur. */
  chemin: string
  corps: unknown
  entetes: Record<string, string>
}

/** Réponse de l'API simulée : statut (200 par défaut) et corps JSON (aucun pour un 204). */
export interface ReponseSimulee {
  statut?: number
  corps?: unknown
}

/** Réponse fixe, ou calculée à partir de l'appel. */
export type Repondeur = ReponseSimulee | ((appel: AppelApi) => ReponseSimulee)

/** Réponses indexées par « MÉTHODE chemin » ; une clé sans requête vaut pour toutes les requêtes du chemin. */
export type TableApi = Record<string, Repondeur>

/** Corps d'erreur du serveur (schéma `Erreur`). */
export function erreurApi(statut: number, message: string, code = 'ERREUR'): ReponseSimulee {
  return { statut, corps: { code, message, attribut: null } }
}

/** En-têtes de l'appel, noms en minuscules (`appelerApi` les passe en objet simple). */
function lireEntetes(entetes: HeadersInit | undefined): Record<string, string> {
  const paires = Object.entries((entetes ?? {}) as Record<string, string>)
  return Object.fromEntries(paires.map(([nom, valeur]) => [nom.toLowerCase(), valeur]))
}

function reponseHttp(reponse: ReponseSimulee): Response {
  const statut = reponse.statut ?? STATUT_OK
  if (statut === STATUT_SANS_CONTENU) return new Response(null, { status: statut })
  return new Response(JSON.stringify(reponse.corps ?? null), { status: statut, headers: { 'Content-Type': 'application/json' } })
}

function repondre(table: TableApi, appel: AppelApi): ReponseSimulee {
  const sansRequete = appel.chemin.split('?', 1)[0]
  const repondeur = table[`${appel.methode} ${appel.chemin}`] ?? table[`${appel.methode} ${sansRequete}`]
  if (repondeur === undefined) return erreurApi(STATUT_INTROUVABLE, `Route non simulée : ${appel.methode} ${appel.chemin}`)
  return typeof repondeur === 'function' ? repondeur(appel) : repondeur
}

/** Remplace `fetch` par l'API simulée ; renvoie la liste des appels reçus, dans l'ordre. */
export function simulerApi(table: TableApi): AppelApi[] {
  const appels: AppelApi[] = []
  vi.stubGlobal('fetch', async (adresse: string, init: RequestInit = {}) => {
    if (init.signal?.aborted) throw new DOMException('Appel annulé', 'AbortError')
    const appel: AppelApi = {
      methode: init.method ?? 'GET',
      chemin: String(adresse).slice(apiBaseUrl.length),
      corps: typeof init.body === 'string' ? JSON.parse(init.body) : undefined,
      entetes: lireEntetes(init.headers),
    }
    appels.push(appel)
    return reponseHttp(repondre(table, appel))
  })
  return appels
}

/** Appels reçus pour une méthode et un chemin (requête comprise ou non). */
export function appelsVers(appels: readonly AppelApi[], methode: string, chemin: string): AppelApi[] {
  return appels.filter((appel) => appel.methode === methode && (appel.chemin === chemin || appel.chemin.split('?', 1)[0] === chemin))
}

/** Vue montée et routeur en mémoire. */
export interface VueMontee {
  wrapper: VueWrapper
  router: Router
}

const Racine = defineComponent({ render: () => creerNoeud(RouterView) })

/** Monte la vue d'une adresse de l'IHM (routes réelles, sans garde de session) et attend ses premiers appels. */
export async function monter(adresse: string): Promise<VueMontee> {
  const router = createRouter({ history: createMemoryHistory(), routes: [...ROUTES_IHM] })
  await router.push(adresse)
  await router.isReady()
  const wrapper = mount(Racine, { global: { plugins: [router] }, attachTo: document.body })
  await attendre()
  return { wrapper, router }
}

/** Laisse aboutir les promesses en attente (appels simulés, rendus). */
export async function attendre(): Promise<void> {
  for (let tour = 0; tour < 5; tour += 1) await flushPromises()
}

/** Fichier choisi dans un champ `<input type="file">`, comme le ferait l'utilisateur. */
export async function choisirFichiers(wrapper: VueWrapper, selecteur: string, fichiers: File[]): Promise<void> {
  const champ = wrapper.get(selecteur)
  Object.defineProperty(champ.element, 'files', { value: fichiers, configurable: true })
  await champ.trigger('change')
}

/** Texte des éléments d'un sélecteur, espaces normalisés. */
export function textes(wrapper: VueWrapper, selecteur: string): string[] {
  return wrapper.findAll(selecteur).map((element) => element.text().replace(/\s+/g, ' ').trim())
}
