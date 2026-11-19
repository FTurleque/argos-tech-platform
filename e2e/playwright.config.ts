// Tests de bout en bout d'ARGOS (e2e/README.md). La base PostgreSQL jetable est démarrée par
// scripts/lancer.mjs (e2e/compose.yaml) ; Playwright démarre le serveur (quarkus:dev, Keycloak des
// Dev Services sur 8180 avec le royaume de test) et le portail web (Vite, port 5173), ou réutilise
// ceux qui tournent déjà. Le projet « desktop » pilote le client Electron argos-desktop
// (support/electron.ts) contre la même pile ; le projet « mcp » rejoue au niveau du protocole un
// assistant configuré par un .mcp.json (support/mcp.ts).
import { defineConfig, devices } from '@playwright/test'
import { URL_API, URL_IHM } from './support/environnement'

const DEUX_MINUTES = 120_000

// Tâches planifiées qui joindraient une source externe : désactivées (aucun appel à Internet).
const SANS_COLLECTE_EXTERNE = {
  ARGOS_REGLEMENTAIRE_COLLECTE_CRON: 'off',
  ARGOS_ARTISTE_COLLECTE_CRON: 'off',
  ARGOS_RADAR_CRON: 'off',
  ARGOS_ACTIVITE_CADENCE: 'off',
  ARGOS_VEILLE_WEB_CADENCE: 'off',
  ARGOS_INGESTION_PLANIFICATION_CADENCE: 'off',
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
      command: 'mvn -B -q -f ../argos-server/pom.xml quarkus:dev -Dquarkus.console.enabled=false',
      url: `${URL_API}/q/health/ready`,
      timeout: 5 * 60_000,
      reuseExistingServer: true,
      stdout: 'ignore',
      stderr: 'pipe',
      env: {
        ...SANS_COLLECTE_EXTERNE,
        _DEV_QUARKUS_DATASOURCE_JDBC_URL: 'jdbc:postgresql://localhost:55432/argos',
        QUARKUS_HTTP_PORT: new URL(URL_API).port,
      },
    },
    {
      command: 'npm --prefix ../argos-ui run dev -- --port 5173 --strictPort',
      url: URL_IHM,
      timeout: DEUX_MINUTES,
      reuseExistingServer: true,
      stdout: 'ignore',
      env: { VITE_ARGOS_API_URL: URL_API },
    },
  ],
})
