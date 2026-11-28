// Tests de bout en bout d'ARGOS (e2e/README.md). La base PostgreSQL jetable est démarrée par
// scripts/lancer.mjs (e2e/compose.yaml) ; Playwright démarre les sources simulées, puis le serveur
// (quarkus:dev, Keycloak des Dev Services sur 8180 avec le royaume de test) et le portail web (Vite, port
// 5173), ou l'image construite (ARGOS_E2E_IMAGE, e2e/compose.image.yaml), qui sert les deux. Un port déjà
// occupé fait échouer le lancement, sauf demande explicite de réutilisation (ARGOS_E2E_REUTILISER=1,
// support/pile.mjs). Le projet « desktop » pilote le client Electron argos-desktop
// (support/electron.ts) contre la même pile ; le projet « mcp » rejoue au niveau du protocole un
// assistant configuré par un .mcp.json (support/mcp.ts).
import { defineConfig, devices } from '@playwright/test'
import { URL_API, URL_IHM } from './support/environnement'
import { identificationBaseE2E } from './support/base-e2e.mjs'
import { cheminDocker } from './support/docker.mjs'
import { ligneServeurMaven } from './support/maven.mjs'
import { argumentsCompose, ENVIRONNEMENT_SERVEUR, IMAGE, PORT_SOURCES, REUTILISER } from './support/pile.mjs'

const DEUX_MINUTES = 120_000
const CINQ_MINUTES = 5 * 60_000

const SOURCES = `http://localhost:${PORT_SOURCES}`

/** Image construite (e2e/compose.image.yaml), au premier plan : Playwright l'arrête à la fin des tests. */
function serveurImage() {
  const ligne = [cheminDocker(), ...argumentsCompose(), 'up', '--no-log-prefix', 'argos']
    .map((argument) => `"${argument}"`).join(' ')
  return {
    command: ligne,
    url: `${URL_API}/q/health/ready`,
    timeout: CINQ_MINUTES,
    reuseExistingServer: false,
    stdout: 'ignore' as const,
    stderr: 'pipe' as const,
    env: { ...ENVIRONNEMENT_SERVEUR },
  }
}

/** Serveur de développement : Maven par son chemin absolu (support/maven.mjs), jamais par le PATH. */
function serveurDeveloppement() {
  return {
    command: ligneServeurMaven(['-B', '-q', '-f', '../argos-server/pom.xml', 'quarkus:dev',
      '-Dquarkus.console.enabled=false']),
    url: `${URL_API}/q/health/ready`,
    timeout: CINQ_MINUTES,
    reuseExistingServer: REUTILISER,
    stdout: 'ignore' as const,
    stderr: 'pipe' as const,
    env: {
      ...ENVIRONNEMENT_SERVEUR,
      _DEV_QUARKUS_DATASOURCE_JDBC_URL: 'jdbc:postgresql://localhost:55432/argos',
      ...identificationBaseE2E(),
      QUARKUS_HTTP_PORT: new URL(URL_API).port,
    },
  }
}

/** Portail web servi par Vite pour l'API de la pile. */
function portailVite() {
  return {
    command: 'npm --prefix ../argos-ui run dev -- --port 5173 --strictPort',
    url: URL_IHM,
    timeout: DEUX_MINUTES,
    reuseExistingServer: REUTILISER,
    stdout: 'ignore' as const,
    env: { VITE_ARGOS_API_URL: URL_API },
  }
}

export default defineConfig({
  testDir: './tests',
  outputDir: './test-results',
  // Les parcours partagent la même base et les mêmes comptes : exécution en série.
  workers: 1,
  fullyParallel: false,
  forbidOnly: true,
  retries: 0,
  timeout: DEUX_MINUTES,
  expect: { timeout: 15_000 },
  reporter: [
    ['list'],
    ['json', { outputFile: './test-results/rapport.json' }],
  ],
  use: {
    baseURL: URL_IHM,
    locale: 'fr-FR',
    trace: 'retain-on-failure',
    screenshot: 'only-on-failure',
    ...devices['Desktop Chrome'],
  },
  projects: [
    { name: 'preparation', testMatch: /preparation\.setup\.ts/ },
    { name: 'portail-web', dependencies: ['preparation'], testMatch: /.*\.spec\.ts/ },
    // Client desktop : IHM construite pour l'API et l'émetteur de test, puis parcours Electron.
    { name: 'construction-desktop', testDir: './desktop', testMatch: /construction\.setup\.ts/ },
    {
      name: 'desktop',
      testDir: './desktop',
      testMatch: /.*\.spec\.ts/,
      dependencies: ['preparation', 'construction-desktop'],
    },
    // Serveur MCP : client MCP du protocole (fetch), configuré par donnees/mcp.json ; navigateur
    // seulement pour l'approbation du code d'appareil sur Keycloak.
    { name: 'mcp', testDir: './mcp', testMatch: /.*\.spec\.ts/ },
  ],
  webServer: [
    {
      command: 'node support/sources-simulees.mjs',
      url: `${SOURCES}/__pret`,
      timeout: 30_000,
      reuseExistingServer: false,
      stdout: 'ignore',
      env: { ARGOS_E2E_PORT_SOURCES: String(PORT_SOURCES) },
    },
    ...(IMAGE ? [serveurImage()] : [serveurDeveloppement(), portailVite()]),
  ],
})
