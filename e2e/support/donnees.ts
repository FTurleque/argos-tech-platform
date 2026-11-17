// Jeu de données des parcours, injecté par l'API (jetons réels du royaume de test) et par SQL
// (signaux, projets et impacts, tels que les écrivent la collecte et la corrélation). Aucune source
// externe n'est appelée. Comptes : ceux du royaume de test du dépôt
// (argos-server/src/test/resources/keycloak/argos-realm.json), mot de passe = identifiant.
import { execFileSync } from 'node:child_process'
import { readFileSync } from 'node:fs'
import { dirname, join } from 'node:path'
import { fileURLToPath } from 'node:url'
import { cheminDocker } from './docker.mjs'
import { EMETTEUR, URL_API } from './environnement'

const ICI = dirname(fileURLToPath(import.meta.url))
const COMPOSE = join(ICI, '..', 'compose.yaml')

/** Client confidentiel argos-server du royaume de test (flux par mot de passe, réservé aux tests). */
const CLIENT_TEST = { id: 'argos-server', secret: 'secret-de-test' }

export const ESPACE_E2E = 'Espace E2E recherche'
export const SIGNAL_ATTENDU = 'Oracle Database : faille corrigée'
export const SIGNAUX_EXCLUS = ['Oracle Database : faille ancienne', 'Oracle Java : faille', 'PostgreSQL : faille']

export async function jeton(utilisateur: string): Promise<string> {
  const reponse = await fetch(`${EMETTEUR}/protocol/openid-connect/token`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
    body: new URLSearchParams({
      grant_type: 'password',
      client_id: CLIENT_TEST.id,
      client_secret: CLIENT_TEST.secret,
      username: utilisateur,
      password: utilisateur,
    }),
  })
  if (!reponse.ok) throw new Error(`jeton de ${utilisateur} : ${reponse.status}`)
  return ((await reponse.json()) as { access_token: string }).access_token
}

export async function api<T>(utilisateur: string, chemin: string, init: RequestInit = {}): Promise<T> {
  const reponse = await fetch(`${URL_API}${chemin}`, {
    ...init,
    headers: {
      Accept: 'application/json',
      ...(init.body ? { 'Content-Type': 'application/json' } : {}),
      Authorization: `Bearer ${await jeton(utilisateur)}`,
    },
  })
  if (!reponse.ok) throw new Error(`${init.method ?? 'GET'} ${chemin} (${utilisateur}) : ${reponse.status}`)
  return (reponse.status === 204 ? null : await reponse.json()) as T
}

/** Exécute un script SQL de e2e/donnees/ dans la base de test (PostgreSQL de e2e/compose.yaml). */
export function sql(fichier: string, variables: Record<string, string>): void {
  const args = ['compose', '-f', COMPOSE, 'exec', '-T', 'postgres', 'psql', '-U', 'argos', '-d', 'argos',
    '-v', 'ON_ERROR_STOP=1', '-q']
  for (const [nom, valeur] of Object.entries(variables)) args.push('-v', `${nom}=${valeur}`)
  execFileSync(cheminDocker(), args, {
    input: readFileSync(join(ICI, '..', 'donnees', fichier)),
    stdio: ['pipe', 'pipe', 'inherit'],
  })
}

/** Espace d'alice contenant les projets « Paie » et « Comptabilité » et les signaux « Oracle ». */
export async function preparerRecherche(): Promise<string> {
  // Première connexion d'alice : compte administrateur plateforme créé sans invitation.
  await api('alice', '/api/v1/moi')
  const espaces = await api<{ id: string; nom: string }[]>('alice', '/api/v1/espaces')
  const espace = espaces.find((e) => e.nom === ESPACE_E2E) ?? (await api<{ id: string }>('alice', '/api/v1/espaces', {
    method: 'POST',
    body: JSON.stringify({ nom: ESPACE_E2E }),
  }))
  sql('recherche.sql', { espace: espace.id })
  return espace.id
}
