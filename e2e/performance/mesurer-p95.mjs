#!/usr/bin/env node
// Campagne de mesure de la latence p95 de l'API REST et du serveur MCP sur la pile de test locale (PERF-01, PERF-02 ;
// décision J8 du porteur du projet, 2026-11-19 ; changement automatiser-chaine-veille, tâches 3.6 et 3.7).
// Usage : npm --prefix e2e run test:performance
//
// 1. démarre la base PostgreSQL jetable de e2e/compose.yaml et le serveur (quarkus:dev, port 8090, Keycloak des Dev
//    Services) comme scripts/lancer.mjs et playwright.config.ts, ou réutilise un serveur prêt ;
// 2. crée par l'API l'espace « Espace performance » d'alice et charge le jeu de données de référence
//    (donnees-performance.sql, volume en paramètres) ;
// 3. appelle chaque opération retenue (mesure.mjs) : échauffement puis N appels avec C clients simultanés ;
// 4. écrit le rapport JSON dans e2e/resultats/performance/ et le rapport Markdown dans
//    docs/architecture/quality/mesure-performance-api-mcp.md ;
// 5. arrête ce qu'elle a démarré, et seulement cela (base ou serveur déjà en marche laissés en place). Code de sortie : 0 si tous les p95 sont sous 500 ms, 1 sinon ou si la campagne est
//    invalide (appel en échec, requête hors de la boucle locale).
// Node.js seul (fetch, node:perf_hooks) : aucune dépendance (ADR-0021). Jamais lancée par `npm --prefix e2e test`.
import { execFileSync, spawn, spawnSync } from 'node:child_process'
import { existsSync, mkdirSync, readFileSync, writeFileSync } from 'node:fs'
import { arch, cpus, platform, release, totalmem } from 'node:os'
import { dirname, join } from 'node:path'
import { performance } from 'node:perf_hooks'
import { fileURLToPath } from 'node:url'
import { cheminDocker } from '../support/docker.mjs'
import {
  OPERATIONS_EXCLUES,
  operationsRetenues,
  parametres,
  rapportMarkdown,
  resumerOperation,
  verdictGlobal,
  verifierHoteLocal,
} from './mesure.mjs'

const ICI = dirname(fileURLToPath(import.meta.url))
const E2E = join(ICI, '..')
const RACINE = join(E2E, '..')
const COMPOSE = ['compose', '-f', join(E2E, 'compose.yaml')]
const URL_API = process.env.ARGOS_E2E_URL_API ?? 'http://localhost:8090'
const EMETTEUR = process.env.ARGOS_E2E_EMETTEUR ?? 'http://localhost:8180/realms/argos'
const CLIENT_TEST = { id: 'argos-server', secret: 'secret-de-test' }
const VERSION_MCP = '2026-07-28'
const ESPACE = 'Espace performance'

// Tâches planifiées qui joindraient une source externe : désactivées (aucun appel à Internet), comme les parcours.
const SANS_COLLECTE_EXTERNE = {
  ARGOS_REGLEMENTAIRE_COLLECTE_CRON: 'off',
  ARGOS_ARTISTE_COLLECTE_CRON: 'off',
  ARGOS_RADAR_CRON: 'off',
  ARGOS_ACTIVITE_CADENCE: 'off',
  ARGOS_VEILLE_WEB_CADENCE: 'off',
  // Synchronisation horaire des sources d'avis (J1) et import quotidien des inventaires (J3).
  ARGOS_INGESTION_PLANIFICATION_CADENCE: 'off',
  ARGOS_INVENTORY_IMPORT_CRON: 'off',
  // Aucune passerelle IA, quelle que soit la configuration du poste (cible « hors IA »).
  ARGOS_IA_ACTIVE: 'false',
}

/** fetch sous la garde réseau : toute URL hors de la boucle locale fait échouer la campagne (PERF-02-S3). */
async function requete(url, init = {}) {
  verifierHoteLocal(url)
  return fetch(url, { ...init, redirect: 'manual' })
}

function attendre(ms) {
  return new Promise((resoudre) => setTimeout(resoudre, ms))
}

async function pret() {
  try {
    const r = await requete(`${URL_API}/q/health/ready`)
    return r.ok
  } catch (e) {
    if (e?.name === 'HoteNonLocal') throw e
    return false
  }
}

// Exécutables appelés par leur chemin absolu, pris parmi des emplacements fixes : la campagne ne dépend pas
// du PATH (même règle que support/docker.mjs). ARGOS_E2E_GIT désigne un autre emplacement de Git.
const EMPLACEMENTS_TASKKILL = ['C:\\Windows\\System32\\taskkill.exe']
const EMPLACEMENTS_GIT = ['C:/Program Files/Git/cmd/git.exe', '/usr/bin/git', '/usr/local/bin/git', '/opt/homebrew/bin/git']

function cheminGit() {
  return process.env.ARGOS_E2E_GIT ?? EMPLACEMENTS_GIT.find((chemin) => existsSync(chemin))
}

function executer(commande, args, options = {}) {
  const execution = spawnSync(commande, args, { cwd: E2E, stdio: 'inherit', ...options })
  if (execution.error) throw execution.error
  return execution.status ?? 1
}

/** Arrête le serveur démarré par la campagne et ses processus enfants (mvn → java). */
function arreter(processus) {
  if (!processus || processus.exitCode !== null) return
  const taskkill = EMPLACEMENTS_TASKKILL.find((chemin) => existsSync(chemin))
  if (platform() === 'win32' && taskkill) spawnSync(taskkill, ['/pid', String(processus.pid), '/T', '/F'], { stdio: 'ignore' })
  else if (platform() === 'win32') processus.kill()
  else {
    try {
      process.kill(-processus.pid, 'SIGTERM')
    } catch {
      processus.kill('SIGTERM')
    }
  }
}

async function demarrerServeur() {
  if (await pret()) return null
  const env = {
    ...process.env,
    ...SANS_COLLECTE_EXTERNE,
    _DEV_QUARKUS_DATASOURCE_JDBC_URL: 'jdbc:postgresql://localhost:55432/argos',
    QUARKUS_HTTP_PORT: new URL(URL_API).port,
    // Le débit MCP par compte (60 par minute en développement) limiterait la campagne : borne relevée comme en test.
    ARGOS_MCP_DEBIT_REQUETES_PAR_MINUTE: '1000000',
  }
  const mvn = platform() === 'win32' ? 'mvn.cmd' : 'mvn'
  const serveur = spawn(mvn, ['-B', '-q', '-f', join(RACINE, 'argos-server', 'pom.xml'), 'quarkus:dev',
    '-Dquarkus.console.enabled=false'], { cwd: E2E, env, stdio: ['ignore', 'ignore', 'inherit'],
    detached: platform() !== 'win32', shell: platform() === 'win32' })
  const limite = Date.now() + 5 * 60_000
  while (Date.now() < limite) {
    if (serveur.exitCode !== null) throw new Error(`le serveur s'est arrêté (code ${serveur.exitCode})`)
    if (await pret()) return serveur
    await attendre(2000)
  }
  arreter(serveur)
  throw new Error('serveur non prêt après 5 minutes')
}

async function jeton(utilisateur) {
  const r = await requete(`${EMETTEUR}/protocol/openid-connect/token`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
    body: new URLSearchParams({ grant_type: 'password', client_id: CLIENT_TEST.id, client_secret: CLIENT_TEST.secret,
      username: utilisateur, password: utilisateur }),
  })
  if (!r.ok) throw new Error(`jeton de ${utilisateur} : ${r.status}`)
  return (await r.json()).access_token
}

async function api(token, chemin, init = {}) {
  const r = await requete(`${URL_API}${chemin}`, {
    ...init,
    headers: { Accept: 'application/json', ...(init.body ? { 'Content-Type': 'application/json' } : {}),
      Authorization: `Bearer ${token}` },
  })
  if (!r.ok) throw new Error(`${init.method ?? 'GET'} ${chemin} : ${r.status}`)
  return r.status === 204 ? null : r.json()
}

/** Espace d'alice, jeu de données SQL et abonnement personnel ; renvoie les identifiants de référence. */
async function preparer(token, p) {
  await api(token, '/api/v1/moi')
  const espaces = await api(token, '/api/v1/espaces')
  const espace = espaces.find((e) => e.nom === ESPACE)
    ?? (await api(token, '/api/v1/espaces', { method: 'POST', body: JSON.stringify({ nom: ESPACE }) }))
  execFileSync(cheminDocker(), [...COMPOSE, 'exec', '-T', 'postgres', 'psql', '-U', 'argos', '-d', 'argos',
    '-v', 'ON_ERROR_STOP=1', '-q', '-v', `espace=${espace.id}`, '-v', `projets=${p.projets}`,
    '-v', `composants=${p.composantsParProjet}`, '-v', `signaux=${p.signaux}`],
  { cwd: E2E, input: readFileSync(join(ICI, 'donnees-performance.sql')), stdio: ['pipe', 'pipe', 'inherit'] })
  const abonnements = await api(token, '/api/v1/subscriptions')
  if (abonnements.length === 0) {
    await api(token, '/api/v1/subscriptions', { method: 'POST', body: JSON.stringify({ scope: 'PERSONAL',
      filters: [{ type: 'PACKAGE', value: 'pkg:maven/org.perf/lib-1' }], channel: 'desktop', frequency: 'daily',
      mandatory: false }) })
  }
  const projets = await api(token, '/api/v1/projects?size=200')
  const projet = (projets.items ?? projets).find((x) => x.name === 'Projet performance 001')
  if (!projet) throw new Error('projet de référence absent : jeu de données non chargé')
  const recherche = await api(token, '/api/v1/recherche?q=PERF-2099-00001&type=AVIS_SECURITE')
  const signal = (recherche.elements ?? recherche.items ?? [])[0]?.id
  if (!signal) throw new Error('signal de référence introuvable par la recherche')
  const impacts = await api(token, `/api/v1/signals/${signal}/impacts`)
  const impact = impacts.items[0]?.id
  if (!impact) throw new Error('impact de référence absent')
  return { projet: projet.id, signal, impact, paquet: 'pkg:maven/org.perf/lib-1', nomPaquet: 'org.perf:lib-1' }
}

let idMcp = 0

/** Un appel d'outil MCP (JSON-RPC sur HTTP sans état) ; erreur si la réponse n'est pas un résultat. */
async function appelMcp(token, outil, args) {
  const corps = { jsonrpc: '2.0', id: ++idMcp, method: 'tools/call', params: { name: outil, arguments: args,
    _meta: { 'io.modelcontextprotocol/protocolVersion': VERSION_MCP,
      'io.modelcontextprotocol/clientInfo': { name: 'argos-performance', version: '1' },
      'io.modelcontextprotocol/clientCapabilities': {} } } }
  const r = await requete(`${URL_API}/mcp`, {
    method: 'POST',
    headers: { Authorization: `Bearer ${token}`, 'Content-Type': 'application/json',
      Accept: 'application/json, text/event-stream', 'MCP-Protocol-Version': VERSION_MCP, 'Mcp-Method': 'tools/call',
      'Mcp-Name': outil },
    body: JSON.stringify(corps),
  })
  const texte = await r.text()
  if (!r.ok) return `HTTP ${r.status}`
  const json = texte.includes('data:') ? texte.split('\n').filter((l) => l.startsWith('data:')).pop().slice(5) : texte
  const message = JSON.parse(json)
  if (message.error) return `JSON-RPC ${message.error.code} ${message.error.message}`
  if (message.result?.isError) return `outil en erreur : ${JSON.stringify(message.result.content).slice(0, 200)}`
  return null
}

async function appelRest(token, chemin) {
  const r = await requete(`${URL_API}${chemin}`, { headers: { Accept: 'application/json', Authorization: `Bearer ${token}` } })
  await r.arrayBuffer()
  return r.ok ? null : `HTTP ${r.status}`
}

/** Mesure une opération : échauffement, puis `appels` appels répartis sur `concurrence` clients. */
async function mesurer(operation, token, p) {
  const appeler = operation.type === 'MCP'
    ? () => appelMcp(token, operation.outil, operation.args)
    : () => appelRest(token, operation.chemin)
  for (let i = 0; i < p.echauffement; i++) await appeler()
  const durees = []
  const erreurs = []
  let restant = p.appelsParOperation
  async function client() {
    while (restant > 0) {
      restant--
      const debut = performance.now()
      let erreur
      try {
        erreur = await appeler()
      } catch (e) {
        if (e?.name === 'HoteNonLocal') throw e
        erreur = String(e?.message ?? e)
      }
      durees.push(performance.now() - debut)
      if (erreur) erreurs.push(erreur)
    }
  }
  await Promise.all(Array.from({ length: p.concurrence }, client))
  return resumerOperation(operation, durees, erreurs)
}

function revision() {
  const git = cheminGit()
  if (!git) return 'inconnue'
  try {
    return execFileSync(git, ['rev-parse', '--short', 'HEAD'], { cwd: RACINE }).toString().trim()
  } catch {
    return 'inconnue'
  }
}

async function principal() {
  const p = parametres(process.env)
  verifierHoteLocal(URL_API)
  verifierHoteLocal(EMETTEUR)
  let baseDemarree = false
  let serveur = null
  try {
    // Une base déjà en marche (pile lancée par ailleurs) est réutilisée et laissée en place.
    const enMarche = spawnSync(cheminDocker(), [...COMPOSE, 'ps', '-q', 'postgres'], { cwd: E2E }).stdout?.toString().trim()
    if (executer(cheminDocker(), [...COMPOSE, 'up', '-d', '--wait']) !== 0) throw new Error('base de test indisponible : Docker est-il démarré ?')
    baseDemarree = !enMarche
    serveur = await demarrerServeur()
    const token = await jeton('alice')
    const contexte = await preparer(token, p)
    const resultats = []
    for (const operation of operationsRetenues(contexte)) {
      const r = await mesurer(operation, token, p)
      console.log(`${r.id.padEnd(48)} p50 ${String(r.p50Ms).padStart(7)} ms  p95 ${String(r.p95Ms).padStart(7)} ms  ${r.verdict}`)
      resultats.push(r)
    }
    const date = new Date().toISOString()
    const rapport = {
      date,
      revision: revision(),
      environnement: {
        description: `pile de test locale (PostgreSQL de e2e/compose.yaml, quarkus:dev ${URL_API}, Keycloak des Dev Services)`,
        systeme: `${platform()} ${release()} ${arch()}, ${cpus().length} cœurs, ${Math.round(totalmem() / 2 ** 30)} Gio`,
        node: process.version,
        serveurReutilise: serveur === null,
      },
      jeuDeDonnees: {
        description: `${p.projets} projets × ${p.composantsParProjet} composants, ${p.signaux} avis, ${p.projets * p.signaux} impacts (un espace)`,
        projets: p.projets,
        composantsParProjet: p.composantsParProjet,
        signaux: p.signaux,
        impacts: p.projets * p.signaux,
      },
      parametres: p,
      resultats,
      exclues: OPERATIONS_EXCLUES,
      verdict: verdictGlobal(resultats),
    }
    const dossier = join(E2E, 'resultats', 'performance')
    mkdirSync(dossier, { recursive: true })
    const jour = date.slice(0, 10)
    writeFileSync(join(dossier, `rapport-${jour}.json`), `${JSON.stringify(rapport, null, 2)}\n`)
    writeFileSync(join(RACINE, 'docs', 'architecture', 'quality', 'mesure-performance-api-mcp.md'), rapportMarkdown(rapport))
    console.log(`rapport : e2e/resultats/performance/rapport-${jour}.json ; code de sortie ${rapport.verdict.codeSortie}`)
    return rapport.verdict.codeSortie
  } finally {
    arreter(serveur)
    if (baseDemarree && !process.env.ARGOS_E2E_GARDER_BASE) executer(cheminDocker(), [...COMPOSE, 'down', '--volumes'])
  }
}

principal().then((code) => process.exit(code), (e) => {
  console.error(`campagne de performance en échec : ${e?.message ?? e}`)
  process.exit(1)
})
