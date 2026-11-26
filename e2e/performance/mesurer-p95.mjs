#!/usr/bin/env node
// Campagne de mesure de la latence p95 de l'API REST et du serveur MCP sur la pile de test locale (PERF-01, PERF-02 ;
// décision J8 du porteur du projet, 2026-11-19 ; changement automatiser-chaine-veille, tâches 3.6 et 3.7).
// Usage : npm --prefix e2e run test:performance
//
// 1. démarre la base PostgreSQL jetable de e2e/compose.yaml et le serveur (quarkus:dev, port 8090, Keycloak des Dev
//    Services) comme scripts/lancer.mjs et playwright.config.ts, ou réutilise un serveur prêt ;
// 2. crée par l'API l'espace « Espace performance » d'alice et le petit espace de bob, charge le jeu de données de
//    référence (donnees-performance.sql, volume en paramètres, espace volumineux compris) et les abonnements ;
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
import { identificationBaseE2E } from '../support/base-e2e.mjs'
import { cheminDocker } from '../support/docker.mjs'
import { cheminMavenExige, lancementMaven } from '../support/maven.mjs'
import {
  OPERATIONS_EXCLUES,
  UTILISATEUR_PETIT_ESPACE,
  UTILISATEUR_REFERENCE,
  espacesDuJeu,
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
const PETIT_ESPACE = 'Petit espace performance'
const PAQUET_REFERENCE = 'pkg:maven/org.perf/lib-1'
// Abonnements PERSONAL de chaque compte (PRF-07-S3, PERF-02-S4) : par paquet et par technologie.
const ABONNEMENTS = {
  [UTILISATEUR_REFERENCE]: [{ type: 'PACKAGE', value: PAQUET_REFERENCE }, { type: 'TECHNOLOGY', value: 'java' }],
  [UTILISATEUR_PETIT_ESPACE]: [{ type: 'PACKAGE', value: 'pkg:maven/org.perf/lib-2' }, { type: 'TECHNOLOGY', value: 'java' }],
}

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

function attendre(duree) {
  return new Promise((resoudre) => setTimeout(resoudre, duree))
}

async function pret() {
  try {
    const reponse = await requete(`${URL_API}/q/health/ready`)
    return reponse.ok
  } catch (echec) {
    if (echec?.name === 'HoteNonLocal') throw echec
    return false
  }
}

// Exécutables appelés par leur chemin absolu, pris parmi des emplacements fixes : la campagne ne dépend pas
// du PATH (même règle que support/docker.mjs). ARGOS_E2E_GIT désigne un autre emplacement de Git ; Maven est résolu
// par support/maven.mjs (ARGOS_E2E_MVN, MAVEN_HOME, emplacements fixes) et lancé sans interpréteur trouvé par le PATH.
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
    ...identificationBaseE2E(),
    _DEV_QUARKUS_DATASOURCE_JDBC_URL: 'jdbc:postgresql://localhost:55432/argos',
    QUARKUS_HTTP_PORT: new URL(URL_API).port,
    // Le débit MCP par compte (60 par minute en développement) limiterait la campagne : borne relevée comme en test.
    ARGOS_MCP_DEBIT_REQUETES_PAR_MINUTE: '1000000',
    // Un seul compte envoie les 200 appels de « Ma veille » (PRF-07-S1) et des recherches (60 par minute et par compte
    // en développement, PRF-08, RCH-06) : bornes relevées de même.
    ARGOS_VEILLE_DEBIT_REQUETES_PAR_MINUTE: '1000000',
    ARGOS_RECHERCHE_DEBIT_REQUETES_PAR_MINUTE: '1000000',
  }
  const lancement = lancementMaven(cheminMavenExige(), ['-B', '-q', '-f', join(RACINE, 'argos-server', 'pom.xml'),
    'quarkus:dev', '-Dquarkus.console.enabled=false'])
  const serveur = spawn(lancement.commande, lancement.args, { cwd: E2E, env, stdio: ['ignore', 'ignore', 'inherit'],
    detached: platform() !== 'win32', ...lancement.options })
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
  const reponse = await requete(`${EMETTEUR}/protocol/openid-connect/token`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
    body: new URLSearchParams({ grant_type: 'password', client_id: CLIENT_TEST.id, client_secret: CLIENT_TEST.secret,
      username: utilisateur, password: utilisateur }),
  })
  if (!reponse.ok) throw new Error(`jeton de ${utilisateur} : ${reponse.status}`)
  return (await reponse.json()).access_token
}

async function api(token, chemin, init = {}) {
  const reponse = await requete(`${URL_API}${chemin}`, {
    ...init,
    headers: { Accept: 'application/json', ...(init.body ? { 'Content-Type': 'application/json' } : {}),
      Authorization: `Bearer ${token}` },
  })
  if (!reponse.ok) throw new Error(`${init.method ?? 'GET'} ${chemin} : ${reponse.status}`)
  return reponse.status === 204 ? null : reponse.json()
}

/** Espace du compte, créé par l'API s'il n'existe pas encore. */
async function espaceDe(token, nom) {
  await api(token, '/api/v1/moi')
  const espaces = await api(token, '/api/v1/espaces')
  return espaces.find((candidat) => candidat.nom === nom)
    ?? (await api(token, '/api/v1/espaces', { method: 'POST', body: JSON.stringify({ nom }) }))
}

/**
 * Petit espace créé par alice, dont bob devient membre par invitation (l'inscription se fait sur invitation) : bob
 * n'est membre que de cet espace.
 */
async function petitEspaceDeBob(jetons) {
  const espace = await espaceDe(jetons[UTILISATEUR_REFERENCE], PETIT_ESPACE)
  const moi = await requete(`${URL_API}/api/v1/moi`, { headers: { Accept: 'application/json',
    Authorization: `Bearer ${jetons[UTILISATEUR_PETIT_ESPACE]}` } })
  await moi.arrayBuffer()
  const espacesDeBob = moi.ok ? await api(jetons[UTILISATEUR_PETIT_ESPACE], '/api/v1/espaces') : []
  if (!espacesDeBob.some((candidat) => candidat.id === espace.id)) {
    const invitation = await api(jetons[UTILISATEUR_REFERENCE], `/api/v1/espaces/${espace.id}/invitations`, {
      method: 'POST', body: JSON.stringify({ email: `${UTILISATEUR_PETIT_ESPACE}@argos.test`, role: 'LECTEUR' }) })
    await api(jetons[UTILISATEUR_PETIT_ESPACE], `/api/v1/invitations/${invitation.id}/acceptation`, { method: 'POST' })
  }
  return espace
}

/** Abonnements PERSONAL du compte, un par filtre, créés s'il n'en a aucun. */
async function abonner(token, filtres) {
  const abonnements = await api(token, '/api/v1/subscriptions')
  if (abonnements.length > 0) return
  for (const filtre of filtres) {
    await api(token, '/api/v1/subscriptions', { method: 'POST', body: JSON.stringify({ scope: 'PERSONAL',
      filters: [filtre], channel: 'desktop', frequency: 'daily', mandatory: false }) })
  }
}

function psql(args, entree) {
  return execFileSync(cheminDocker(), [...COMPOSE, 'exec', '-T', 'postgres', 'psql', '-U', 'argos', '-d', 'argos',
    '-v', 'ON_ERROR_STOP=1', '-q', ...args], { cwd: E2E, input: entree, stdio: ['pipe', 'pipe', 'inherit'] })
}

/** Impacts de chaque espace par état, lus en base (PERF-02-S4). */
function impactsParEspace() {
  const lignes = psql(['-At', '-c', `SELECT e.nom, i.state, count(*) FROM project_impact i JOIN espace e
    ON e.id = i.workspace_id GROUP BY e.nom, i.state ORDER BY e.nom, i.state`]).toString()
  return espacesDuJeu(lignes)
}

/**
 * Espaces d'alice et de bob, jeu de données SQL (espace volumineux compris) et abonnements personnels ; renvoie les
 * identifiants de référence et la répartition des impacts par espace.
 */
async function preparer(jetons, reglages) {
  const espace = await espaceDe(jetons[UTILISATEUR_REFERENCE], ESPACE)
  const petitEspace = await petitEspaceDeBob(jetons)
  psql(['-v', `espace=${espace.id}`, '-v', `projets=${reglages.projets}`, '-v', `composants=${reglages.composantsParProjet}`,
    '-v', `signaux=${reglages.signaux}`, '-v', `espace_petit=${petitEspace.id}`,
    '-v', `projets_volumineux=${reglages.projetsVolumineux}`, '-v', `impacts_petit=${reglages.impactsPetitEspace}`],
  readFileSync(join(ICI, 'donnees-performance.sql')))
  for (const [utilisateur, filtres] of Object.entries(ABONNEMENTS)) await abonner(jetons[utilisateur], filtres)
  const token = jetons[UTILISATEUR_REFERENCE]
  const projets = await api(token, '/api/v1/projects?size=200')
  const projet = (projets.items ?? projets).find((candidat) => candidat.name === 'Projet performance 001')
  if (!projet) throw new Error('projet de référence absent : jeu de données non chargé')
  const recherche = await api(token, '/api/v1/recherche?q=PERF-2099-00001&type=AVIS_SECURITE')
  const signal = (recherche.elements ?? recherche.items ?? [])[0]?.id
  if (!signal) throw new Error('signal de référence introuvable par la recherche')
  const impacts = await api(token, `/api/v1/signals/${signal}/impacts`)
  const impact = impacts.items[0]?.id
  if (!impact) throw new Error('impact de référence absent')
  return { projet: projet.id, signal, impact, paquet: PAQUET_REFERENCE, nomPaquet: 'org.perf:lib-1',
    espaces: impactsParEspace() }
}

let idMcp = 0

/** Un appel d'outil MCP (JSON-RPC sur HTTP sans état) ; erreur si la réponse n'est pas un résultat. */
async function appelMcp(token, outil, args) {
  const corps = { jsonrpc: '2.0', id: ++idMcp, method: 'tools/call', params: { name: outil, arguments: args,
    _meta: { 'io.modelcontextprotocol/protocolVersion': VERSION_MCP,
      'io.modelcontextprotocol/clientInfo': { name: 'argos-performance', version: '1' },
      'io.modelcontextprotocol/clientCapabilities': {} } } }
  const reponse = await requete(`${URL_API}/mcp`, {
    method: 'POST',
    headers: { Authorization: `Bearer ${token}`, 'Content-Type': 'application/json',
      Accept: 'application/json, text/event-stream', 'MCP-Protocol-Version': VERSION_MCP, 'Mcp-Method': 'tools/call',
      'Mcp-Name': outil },
    body: JSON.stringify(corps),
  })
  const texte = await reponse.text()
  if (!reponse.ok) return `HTTP ${reponse.status}`
  const json = texte.includes('data:') ? texte.split('\n').filter((ligne) => ligne.startsWith('data:')).pop().slice(5) : texte
  const message = JSON.parse(json)
  if (message.error) return `JSON-RPC ${message.error.code} ${message.error.message}`
  if (message.result?.isError) return `outil en erreur : ${JSON.stringify(message.result.content).slice(0, 200)}`
  return null
}

async function appelRest(token, chemin) {
  const reponse = await requete(`${URL_API}${chemin}`, { headers: { Accept: 'application/json', Authorization: `Bearer ${token}` } })
  await reponse.arrayBuffer()
  return reponse.ok ? null : `HTTP ${reponse.status}`
}

/** Mesure une opération : échauffement, puis `appels` appels répartis sur `concurrence` clients. */
async function mesurer(operation, jetons, reglages) {
  const token = jetons[operation.utilisateur]
  const appeler = operation.type === 'MCP'
    ? () => appelMcp(token, operation.outil, operation.args)
    : () => appelRest(token, operation.chemin)
  for (let tour = 0; tour < reglages.echauffement; tour++) await appeler()
  const durees = []
  const erreurs = []
  let restant = reglages.appelsParOperation
  async function client() {
    while (restant > 0) {
      restant--
      const debut = performance.now()
      let erreur
      try {
        erreur = await appeler()
      } catch (echec) {
        if (echec?.name === 'HoteNonLocal') throw echec
        erreur = String(echec?.message ?? echec)
      }
      durees.push(performance.now() - debut)
      if (erreur) erreurs.push(erreur)
    }
  }
  await Promise.all(Array.from({ length: reglages.concurrence }, client))
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
  const reglages = parametres(process.env)
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
    const jetons = {
      [UTILISATEUR_REFERENCE]: await jeton(UTILISATEUR_REFERENCE),
      [UTILISATEUR_PETIT_ESPACE]: await jeton(UTILISATEUR_PETIT_ESPACE),
    }
    const contexte = await preparer(jetons, reglages)
    const resultats = []
    for (const operation of operationsRetenues(contexte)) {
      const resultat = await mesurer(operation, jetons, reglages)
      console.log(`${resultat.id.padEnd(48)} p50 ${String(resultat.p50Ms).padStart(7)} ms  `
        + `p95 ${String(resultat.p95Ms).padStart(7)} ms  ${resultat.verdict}`)
      resultats.push(resultat)
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
        description: `${reglages.projets} projets × ${reglages.composantsParProjet} composants, ${reglages.signaux} avis, `
          + `${reglages.projets * reglages.signaux} impacts actifs (espace d’${UTILISATEUR_REFERENCE}) ; `
          + `${reglages.projetsVolumineux * reglages.signaux} impacts résolus ou écartés (espace volumineux) ; `
          + `${reglages.impactsPetitEspace} impacts actifs (petit espace de ${UTILISATEUR_PETIT_ESPACE}) ; `
          + 'abonnements PERSONAL par paquet et par technologie',
        espaces: contexte.espaces,
        projets: reglages.projets,
        composantsParProjet: reglages.composantsParProjet,
        signaux: reglages.signaux,
        impacts: contexte.espaces.reduce((total, espace) => total
          + Object.values(espace.etats).reduce((somme, nombre) => somme + nombre, 0), 0),
      },
      parametres: reglages,
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

let codeSortie
try {
  codeSortie = await principal()
} catch (echec) {
  console.error(`campagne de performance en échec : ${echec?.message ?? echec}`)
  codeSortie = 1
}
process.exit(codeSortie)
