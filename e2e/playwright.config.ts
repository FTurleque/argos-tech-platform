// Tests de bout en bout d'ARGOS (e2e/README.md). La base PostgreSQL jetable est démarrée par
// scripts/lancer.mjs (e2e/compose.yaml) ; Playwright démarre le serveur (quarkus:dev, Keycloak des
// Dev Services sur 8180 avec le royaume de test) et le portail web (Vite, port 5173), ou réutilise
// ceux qui tournent déjà. Le projet « desktop » pilote le client Electron argos-desktop
// (support/electron.ts) contre la même pile ; le projet « mcp » rejoue au niveau du protocole un
// assistant configuré par un .mcp.json (support/mcp.ts).
import { defineConfig, devices } from '@playwright/test'
import { URL_API, URL_IHM } from './support/environnement'
import { ligneServeurMaven } from './support/maven.mjs'

const DEUX_MINUTES = 120_000
// Rôles de la base e2e (e2e/compose.yaml, deploiement/postgres/initialiser-bases.sh) : connexion et migrations.
const ROLE_CONNEXION_E2E = 'argos_connexion'
const ROLE_PROPRIETAIRE_E2E = 'argos'

// Tâches planifiées qui joindraient une source externe : désactivées (aucun appel à Internet).
const SANS_COLLECTE_EXTERNE = {
  ARGOS_REGLEMENTAIRE_COLLECTE_CRON: 'off',
  ARGOS_ARTISTE_COLLECTE_CRON: 'off',
  ARGOS_RADAR_CRON: 'off',
  ARGOS_ACTIVITE_CADENCE: 'off',
  ARGOS_VEILLE_WEB_CADENCE: 'off',
}

// Chaîne automatisée (automatiser-chaine-veille, tâche 4.3 ; [PROPOSITION] PI-1) : la synchronisation des sources
// d'avis (J1) et l'import des inventaires (J3) restent actifs, rapprochés à 5 secondes, et n'interrogent que les
// sources et forges simulées de support/sources-simulees.mjs ; jetons de forge fictifs.
const PORT_SOURCES = Number(process.env.ARGOS_E2E_PORT_SOURCES ?? 8099)
const SOURCES = `http://localhost:${PORT_SOURCES}`
const CHAINE_SIMULEE = {
  ARGOS_INGESTION_PLANIFICATION_CADENCE: '5s',
  ARGOS_INVENTORY_IMPORT_CRON: '*/5 * * * * ?',
  ARGOS_SOURCES_OSV_EXPORT_URL: `${SOURCES}/osv-export`,
  ARGOS_SOURCES_OSV_API_URL: `${SOURCES}/osv-api`,
  ARGOS_SOURCES_OSV_ECOSYSTEMS: 'Maven',
  ARGOS_SOURCES_NVD_URL: `${SOURCES}/nvd`,
  ARGOS_SOURCES_KEV_URL: `${SOURCES}/kev.json`,
  ARGOS_SOURCES_EPSS_URL: `${SOURCES}/epss`,
  ARGOS_SOURCES_CERTFR_FEED_URL: `${SOURCES}/certfr/feed/`,
  ARGOS_SOURCES_CERTFR_BASE_URL: `${SOURCES}/certfr/`,
  ARGOS_SCM_GITLAB_URL: `${SOURCES}/gitlab`,
  ARGOS_SCM_GITHUB_URL: `${SOURCES}/github`,
  ARGOS_GITLAB_TOKEN: 'jeton-fictif-e2e',
  ARGOS_GITHUB_TOKEN: 'jeton-fictif-e2e',
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
    {
      // Maven par son chemin absolu (support/maven.mjs), jamais par le PATH.
      command: ligneServeurMaven(['-B', '-q', '-f', '../argos-server/pom.xml', 'quarkus:dev',
        '-Dquarkus.console.enabled=false']),
      url: `${URL_API}/q/health/ready`,
      timeout: 5 * 60_000,
      reuseExistingServer: true,
      stdout: 'ignore',
      stderr: 'pipe',
      env: {
        ...SANS_COLLECTE_EXTERNE,
        ...CHAINE_SIMULEE,
        _DEV_QUARKUS_DATASOURCE_JDBC_URL: 'jdbc:postgresql://localhost:55432/argos',
        // Base jetable en authentification « trust » (e2e/compose.yaml) : Quarkus exige ces deux mots de passe
        // (connexion et migrations), que la base ne vérifie pas ; le nom de chaque rôle tient lieu de valeur.
        ARGOS_DB_PASSWORD: ROLE_CONNEXION_E2E,
        ARGOS_DB_OWNER_PASSWORD: ROLE_PROPRIETAIRE_E2E,
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
