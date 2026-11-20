// Tests de la campagne de performance (PERF-01-S2, PERF-01-S3, PERF-02-S2, PERF-02-S3 ; changement
// automatiser-chaine-veille, tâche 3.6) : garde réseau, centiles, verdict et code de sortie, opérations exclues,
// rapport Markdown, campagne hors des commandes de test par défaut. Exécution : node --test e2e/performance/
import { test } from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { dirname, join } from 'node:path'
import { fileURLToPath } from 'node:url'
import {
  HoteNonLocal,
  OPERATIONS_EXCLUES,
  PARAMETRES_PAR_DEFAUT,
  SEUIL_P95_MS,
  centile,
  operationsRetenues,
  parametres,
  rapportMarkdown,
  resumerOperation,
  verdictGlobal,
  verifierHoteLocal,
} from './mesure.mjs'

const ICI = dirname(fileURLToPath(import.meta.url))
const E2E = join(ICI, '..')
const CONTEXTE = {
  projet: '3f2504e0-4f89-41d3-9a0c-0305e82c3301',
  signal: '3f2504e0-4f89-41d3-9a0c-0305e82c3302',
  impact: '3f2504e0-4f89-41d3-9a0c-0305e82c3303',
  paquet: 'pkg:maven/org.perf/lib-1',
  nomPaquet: 'org.perf:lib-1',
}

test('garde réseau : boucle locale admise, tout autre hôte refusé en le nommant (PERF-02-S3)', () => {
  for (const url of ['http://localhost:8090/api/v1/projects', 'http://127.0.0.1:8180/realms/argos', 'http://[::1]:8090/mcp']) {
    assert.doesNotThrow(() => verifierHoteLocal(url), url)
  }
  for (const [url, hote] of [
    ['https://api.osv.dev/v1/vulns/X', 'api.osv.dev'],
    ['http://localhost.exemple.com/', 'localhost.exemple.com'],
    ['http://10.0.0.5:8090/', '10.0.0.5'],
  ]) {
    assert.throws(() => verifierHoteLocal(url),
      (erreur) => erreur instanceof HoteNonLocal && erreur.hote === hote && erreur.message.includes(hote))
  }
  assert.throws(() => verifierHoteLocal('file:///etc/passwd'), HoteNonLocal)
  assert.throws(() => verifierHoteLocal('pas une url'), HoteNonLocal)
})

test('centiles : rang le plus proche, liste vide sans valeur', () => {
  const valeurs = Array.from({ length: 100 }, (_, rang) => rang + 1)
  assert.equal(centile(valeurs, 50), 50)
  assert.equal(centile(valeurs, 95), 95)
  assert.equal(centile(valeurs, 100), 100)
  assert.equal(centile([30, 10, 20], 95), 30)
  assert.equal(centile([7], 95), 7)
  assert.equal(centile([], 95), null)
  assert.throws(() => centile([1], 0))
})

test('verdict : p95 sous 500 ms dans la cible, à 500 ms ou plus hors cible, appel en échec invalide (PERF-01-S2)', () => {
  const operation = { id: 'GET /api/v1/projects', type: 'REST', libelle: 'projets' }
  const rapides = resumerOperation(operation, Array.from({ length: 100 }, () => 12))
  assert.equal(rapides.verdict, 'DANS_LA_CIBLE')
  assert.equal(rapides.appels, 100)
  const lentes = resumerOperation(operation, [...Array.from({ length: 94 }, () => 10), ...Array.from({ length: 6 }, () => SEUIL_P95_MS)])
  assert.equal(lentes.p95Ms, SEUIL_P95_MS)
  assert.equal(lentes.verdict, 'HORS_CIBLE')
  const enErreur = resumerOperation(operation, [10, 11], ['HTTP 500'])
  assert.equal(enErreur.verdict, 'EN_ERREUR')
  assert.equal(enErreur.premiereErreur, 'HTTP 500')

  assert.deepEqual(verdictGlobal([rapides]).codeSortie, 0)
  const hors = verdictGlobal([rapides, { ...lentes, id: 'lente' }])
  assert.equal(hors.codeSortie, 1)
  assert.deepEqual(hors.horsCible, ['lente'])
  const invalide = verdictGlobal([rapides, { ...enErreur, id: 'cassée' }])
  assert.equal(invalide.valide, false)
  assert.equal(invalide.codeSortie, 1)
  assert.equal(verdictGlobal([]).codeSortie, 1)
})

test('opérations : consultations REST et huit outils MCP moins explain_change, exclusions motivées (PERF-01-S3)', () => {
  const ops = operationsRetenues(CONTEXTE)
  const mcp = ops.filter((operation) => operation.type === 'MCP').map((operation) => operation.outil)
  assert.deepEqual(mcp.sort(), ['check_dependency', 'get_project_context', 'get_regulatory_changes', 'get_security_alerts',
    'get_technology_standard', 'search_signals', 'upcoming_releases'])
  const rest = ops.filter((operation) => operation.type === 'REST')
  assert.ok(rest.every((operation) => operation.chemin.startsWith('/api/v1/')))
  assert.ok(rest.some((operation) => operation.chemin === `/api/v1/impacts/${CONTEXTE.impact}`))
  assert.ok(!rest.some((operation) => /synchronizations|veille-web/.test(operation.chemin)),
    'aucune opération qui joint une source externe')
  assert.ok(OPERATIONS_EXCLUES.some((exclue) => exclue.id.includes('explain_change') && /IA/.test(exclue.motif)))
  assert.ok(OPERATIONS_EXCLUES.some((exclue) => exclue.id.includes('synchronizations')))
  assert.ok(OPERATIONS_EXCLUES.every((exclue) => exclue.motif.length > 0))
})

test('paramètres : valeurs par défaut (P-9), surcharge par l’environnement, valeur invalide refusée', () => {
  assert.deepEqual(parametres({}), { ...PARAMETRES_PAR_DEFAUT })
  assert.equal(parametres({ ARGOS_PERF_CONCURRENCE: '8' }).concurrence, 8)
  assert.throws(() => parametres({ ARGOS_PERF_APPELS: '0' }))
  assert.throws(() => parametres({ ARGOS_PERF_SIGNAUX: 'beaucoup' }))
})

test('rapport Markdown : date, révision, environnement, jeu de données, concurrence, mesures et exclusions (PERF-02-S1)', () => {
  const resultats = [resumerOperation({ id: 'GET /api/v1/projects', type: 'REST', libelle: 'projets' }, [5, 6, 7])]
  const markdown = rapportMarkdown({
    date: '2026-11-19T10:00:00.000Z',
    revision: 'abc1234',
    environnement: { description: 'pile de test locale', systeme: 'win32', node: 'v24' },
    jeuDeDonnees: { description: '20 projets × 100 composants' },
    parametres: { appelsParOperation: 200, concurrence: 4, echauffement: 20 },
    resultats,
    exclues: OPERATIONS_EXCLUES,
    verdict: verdictGlobal(resultats),
  })
  for (const attendu of ['2026-11-19T10:00:00.000Z', 'abc1234', 'pile de test locale', '20 projets × 100 composants',
    '4 clients simultanés', '| GET /api/v1/projects | REST | 3 |', 'dans la cible', 'Opérations exclues', 'Code de sortie | 0']) {
    assert.ok(markdown.includes(attendu), attendu)
  }
})

test('campagne hors des commandes de test par défaut (PERF-02-S2)', () => {
  const paquet = JSON.parse(readFileSync(join(E2E, 'package.json'), 'utf8'))
  assert.equal(paquet.scripts['test:performance'], 'node performance/mesurer-p95.mjs')
  for (const [nom, commande] of Object.entries(paquet.scripts)) {
    if (nom !== 'test:performance') assert.doesNotMatch(commande, /performance/, nom)
  }
  assert.deepEqual(paquet.devDependencies, { '@playwright/test': '1.61.1' }, 'aucune dépendance ajoutée')
  const config = readFileSync(join(E2E, 'playwright.config.ts'), 'utf8')
  assert.doesNotMatch(config, /performance/, 'Playwright ne parcourt pas e2e/performance/')
  assert.match(config, /testDir: '\.\/tests'/)
})
