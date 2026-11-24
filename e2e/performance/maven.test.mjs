// Tests de la résolution et du lancement de Maven (support/maven.mjs), utilisés par la campagne de
// performance (mesurer-p95.mjs) et par playwright.config.ts. Exécution : node --test "e2e/performance/*.test.mjs".
import { test } from 'node:test'
import assert from 'node:assert/strict'
import { spawnSync } from 'node:child_process'
import { join } from 'node:path'
import {
  CMD_WINDOWS, cheminMaven, cheminMavenExige, lancementMaven, ligneMaven, ligneServeurMaven,
} from '../support/maven.mjs'

const MAVEN_WINDOWS = 'C:/Outils/apache maven/bin/mvn.cmd'
const MAVEN_UNIX = '/usr/local/bin/mvn'
const presents = (...chemins) => (chemin) => chemins.includes(chemin)

test('ARGOS_E2E_MVN désigne Maven en priorité', () => {
  const env = { ARGOS_E2E_MVN: MAVEN_WINDOWS, MAVEN_HOME: '/opt/maven' }
  assert.equal(cheminMaven({ env, existe: presents(MAVEN_WINDOWS, MAVEN_UNIX) }), MAVEN_WINDOWS)
})

test('ARGOS_E2E_MVN vers un fichier absent ne retombe pas sur un autre emplacement', () => {
  const env = { ARGOS_E2E_MVN: '/absent/mvn' }
  assert.equal(cheminMaven({ env, existe: presents(MAVEN_UNIX) }), undefined)
})

test('MAVEN_HOME désigne le script de la plateforme dans bin/', () => {
  const dossier = 'C:/Outils/maven'
  const attendu = join(dossier, 'bin', 'mvn.cmd')
  assert.equal(cheminMaven({ env: { MAVEN_HOME: dossier }, plateforme: 'win32', existe: presents(attendu) }), attendu)
})

test('sans variable, le premier emplacement fixe présent est retenu', () => {
  assert.equal(cheminMaven({ env: {}, plateforme: 'linux', existe: presents(MAVEN_UNIX, '/opt/maven/bin/mvn') }), MAVEN_UNIX)
})

test('Maven introuvable : undefined, puis erreur explicite', () => {
  const options = { env: {}, existe: () => false }
  assert.equal(cheminMaven(options), undefined)
  assert.throws(() => cheminMavenExige(options), /Maven introuvable : .*ARGOS_E2E_MVN/)
})

test('hors de Windows, le script est lancé directement, sans interpréteur', () => {
  const lancement = lancementMaven(MAVEN_UNIX, ['-B', 'quarkus:dev'], { plateforme: 'linux' })
  assert.deepEqual(lancement, { commande: MAVEN_UNIX, args: ['-B', 'quarkus:dev'], options: {} })
})

test('sous Windows, mvn.cmd est lancé par cmd.exe désigné par son chemin absolu', () => {
  const lancement = lancementMaven(MAVEN_WINDOWS, ['-B', '-f', 'N:/dépôt argos/pom.xml', 'quarkus:dev'], { plateforme: 'win32' })
  assert.equal(lancement.commande, CMD_WINDOWS)
  const ligne = '"C:/Outils/apache maven/bin/mvn.cmd" -B -f "N:/dépôt argos/pom.xml" quarkus:dev'
  assert.deepEqual(lancement.args, ['/d', '/s', '/c', `"${ligne}"`])
  assert.deepEqual(lancement.options, { windowsVerbatimArguments: true })
})

test('un argument contenant un caractère interprété par cmd.exe ou sh est refusé', () => {
  for (const argument of ['a&b', 'x|y', '%PATH%', 'guillemet"', 'a^b', '!v!', '$(id)', '`id`']) {
    assert.throws(() => ligneMaven(MAVEN_WINDOWS, [argument]), /argument refusé pour l'interpréteur de commandes/, argument)
  }
})

test('serveur de Playwright : chemin absolu de Maven, ou script qui échoue explicitement sans Maven', () => {
  assert.equal(ligneServeurMaven(['-B'], { env: {}, plateforme: 'linux', existe: presents(MAVEN_UNIX) }), `${MAVEN_UNIX} -B`)
  const sansMaven = ligneServeurMaven(['-B'], { env: {}, existe: () => false })
  assert.match(sansMaven, /maven-introuvable\.mjs"?$/)
  const execution = spawnSync(sansMaven, { shell: true, encoding: 'utf8' })
  assert.equal(execution.status, 1)
  assert.match(execution.stderr, /Maven introuvable/)
})
