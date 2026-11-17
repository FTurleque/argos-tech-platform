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

// ------------------------------------------------------------------ « Ma veille »
// Parcours de personnaliser-profils-et-affichage (tâches 2.4, 2.6, 3.4, 3.9, 5.2) : deux espaces.
// Espace A (ines, administratrice et experte « paie » ; jules, lecteur) ; espace B (karim). Les avis,
// impacts et fiches sont écrits par donnees/veille.sql ; l'abonnement personnel de jules par l'API.

export const ESPACE_VEILLE_A = 'Espace E2E veille A'
export const ESPACE_VEILLE_B = 'Espace E2E veille B'
const ESPACE_ACCUEIL = 'Espace E2E accueil'

/** Titres du jeu de données/veille.sql (textes fictifs). */
export const VEILLE = {
  /** Avis qui touche « Paie E2E veille » (espace A), retenu aussi par l'abonnement personnel de jules. */
  avisCommun: "Moteur de paie : faille d'injection corrigée",
  /** Avis qui ne touche que « RH confidentiel » (espace B), retenu par l'abonnement personnel de jules. */
  avisAbonne: "Bibliothèque d'export : fuite de données",
  /** Avis qui ne touche que « RH confidentiel » (espace B), retenu par aucun abonnement de jules. */
  avisEspaceB: "Export RH : faille réservée à l'espace B",
  /** Fiche validée de l'espace A, résumé produit par l'IA. */
  ficheIa: 'Décret E2E relatif au calcul de la paie des agents',
  /** Fiche validée de l'espace A, résumé relu par l'expert. */
  ficheExpert: 'Arrêté E2E fixant le barème des indemnités',
  /** Fiche de l'espace A en attente de validation : jamais restituée. */
  ficheEnAttente: 'Arrêté E2E en attente de validation',
  projetA: 'Paie E2E veille',
  projetB: 'RH confidentiel',
  idProjetB: '0e2e0000-0000-4000-8000-000000000102',
} as const

/** Critères PACKAGE de l'abonnement personnel de jules (paquets affectés par les avis V1 et V2). */
const PAQUETS_ABONNEMENT = ['pkg:npm/e2e-veille-commun', 'pkg:npm/e2e-veille-abonne']

async function statut(utilisateur: string, chemin: string): Promise<number> {
  const reponse = await fetch(`${URL_API}${chemin}`, { headers: { Authorization: `Bearer ${await jeton(utilisateur)}` } })
  return reponse.status
}

interface Espace {
  id: string
  nom: string
}

async function espaceNomme(utilisateur: string, nom: string): Promise<Espace> {
  const espaces = await api<Espace[]>(utilisateur, '/api/v1/espaces')
  return espaces.find((e) => e.nom === nom) ?? (await api<Espace>(utilisateur, '/api/v1/espaces', {
    method: 'POST',
    body: JSON.stringify({ nom }),
  }))
}

/** Compte créé sur invitation (inscription sur invitation, ACC-08) : alice invite dans un espace d'accueil. */
async function assurerCompte(utilisateur: string): Promise<void> {
  if ((await statut(utilisateur, '/api/v1/moi')) === 200) return
  const accueil = await espaceNomme('alice', ESPACE_ACCUEIL)
  await api('alice', `/api/v1/espaces/${accueil.id}/invitations`, {
    method: 'POST',
    body: JSON.stringify({ email: `${utilisateur}@argos.test`, role: 'LECTEUR' }),
  })
  await api(utilisateur, '/api/v1/moi')
}

/** Rend l'utilisateur membre de l'espace (invitation puis acceptation), s'il ne l'est pas déjà. */
async function assurerMembre(admin: string, espace: Espace, utilisateur: string, role: string): Promise<void> {
  if ((await statut(utilisateur, '/api/v1/moi')) === 200) {
    const siens = await api<Espace[]>(utilisateur, '/api/v1/espaces')
    if (siens.some((e) => e.id === espace.id)) return
  }
  const invitation = await api<{ id: string }>(admin, `/api/v1/espaces/${espace.id}/invitations`, {
    method: 'POST',
    body: JSON.stringify({ email: `${utilisateur}@argos.test`, role }),
  })
  await api(utilisateur, '/api/v1/moi')
  await api(utilisateur, `/api/v1/invitations/${invitation.id}/acceptation`, { method: 'POST' })
}

interface Abonnement {
  id: string
  scope: string
  filters: { type: string; value: string }[]
}

/** Espaces A et B, comptes, avis, impacts, fiches et abonnement personnel de jules. */
export async function preparerVeille(): Promise<void> {
  await api('alice', '/api/v1/moi')
  await assurerCompte('ines')
  await assurerCompte('karim')
  const a = await espaceNomme('ines', ESPACE_VEILLE_A)
  const b = await espaceNomme('karim', ESPACE_VEILLE_B)
  await assurerMembre('ines', a, 'jules', 'LECTEUR')
  const ines = await api<{ id: string }>('ines', '/api/v1/moi')
  sql('veille.sql', { espace_a: a.id, espace_b: b.id, ines: ines.id })

  const abonnements = await api<Abonnement[]>('jules', '/api/v1/subscriptions')
  const existant = abonnements.some(
    (s) => s.scope === 'PERSONAL' && PAQUETS_ABONNEMENT.every((p) => s.filters.some((f) => f.type === 'PACKAGE' && f.value === p)),
  )
  if (!existant) {
    await api('jules', '/api/v1/subscriptions', {
      method: 'POST',
      body: JSON.stringify({
        scope: 'PERSONAL',
        filters: PAQUETS_ABONNEMENT.map((value) => ({ type: 'PACKAGE', value })),
        channel: 'in_app',
        frequency: 'daily',
        mandatory: false,
      }),
    })
  }
}

/** Fixe les profils actifs et la mise en page d'un utilisateur (état initial connu d'un parcours). */
export async function fixerPreferences(utilisateur: string, profils: string[], miseEnPage: string): Promise<void> {
  await api(utilisateur, '/api/v1/preferences/profils', { method: 'PUT', body: JSON.stringify({ profilsActifs: profils }) })
  await api(utilisateur, '/api/v1/preferences/mise-en-page', { method: 'PUT', body: JSON.stringify({ miseEnPage }) })
}

/** Élément de `GET /api/v1/me/veille` (champs lus par les parcours). */
export interface ElementVeille {
  type: string
  titre: string
  lien: string | null
  profils: string[]
  rubriques: { profil: string; identifiant: string; libelle: string }[]
  projets: { projetId: string; projet: string }[]
  abonnements: string[]
  mentionIa: string | null
  mentionSource: string | null
  mentionValidation: string | null
}

export async function veilleDe(utilisateur: string): Promise<{ profilsActifs: string[]; elements: ElementVeille[] }> {
  return api(utilisateur, '/api/v1/me/veille')
}
