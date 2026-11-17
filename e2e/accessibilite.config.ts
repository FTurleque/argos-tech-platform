// Audit d'accessibilité scripté des quatre mises en page (personnaliser-profils-et-affichage, tâche 2.6 ;
// AFF-05, RGAA 4.1.2). Sans serveur ARGOS, sans base ni Keycloak : Playwright démarre Vite sur la boucle
// locale et ouvre le banc d'essai argos-ui/banc/index.html, qui affiche chaque mise en page sur le jeu
// d'exemple. Lancement : npm --prefix e2e run test:accessibilite
import { defineConfig, devices } from '@playwright/test'

export const URL_BANC = process.env.ARGOS_E2E_URL_BANC ?? 'http://127.0.0.1:5174'

export default defineConfig({
  testDir: './accessibilite',
  outputDir: './test-results/accessibilite',
  workers: 1,
  fullyParallel: false,
  forbidOnly: true,
  retries: 0,
  timeout: 60_000,
  reporter: [['list'], ['json', { outputFile: './test-results/accessibilite.json' }]],
  use: {
    baseURL: URL_BANC,
    locale: 'fr-FR',
    trace: 'retain-on-failure',
    screenshot: 'only-on-failure',
    ...devices['Desktop Chrome'],
  },
  webServer: {
    command: 'npm --prefix ../argos-ui run dev -- --host 127.0.0.1 --port 5174 --strictPort',
    url: `${URL_BANC}/banc/index.html`,
    timeout: 120_000,
    reuseExistingServer: true,
    stdout: 'ignore',
    stderr: 'pipe',
  },
})
