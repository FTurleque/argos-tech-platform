#!/usr/bin/env node
// Lance les tests de bout en bout : base PostgreSQL jetable (e2e/compose.yaml), puis Playwright, qui
// démarre le serveur (quarkus:dev et son Keycloak de test) et le portail web, ou réutilise ceux qui
// tournent déjà. La base est arrêtée et effacée à la fin, que les tests réussissent ou non.
// Usage : npm --prefix e2e test [-- <options de playwright test>]
import { spawnSync } from 'node:child_process'
import { dirname, join } from 'node:path'
import { fileURLToPath } from 'node:url'
import { cheminDocker } from '../support/docker.mjs'

const E2E = join(dirname(fileURLToPath(import.meta.url)), '..')
const COMPOSE = ['compose', '-f', join(E2E, 'compose.yaml')]
const PLAYWRIGHT = join(E2E, 'node_modules', '@playwright', 'test', 'cli.js')

function executer(commande, args) {
  const resultat = spawnSync(commande, args, { cwd: E2E, stdio: 'inherit' })
  if (resultat.error) throw resultat.error
  return resultat.status ?? 1
}

if (executer(cheminDocker(), [...COMPOSE, 'up', '-d', '--wait']) !== 0) {
  console.error('Base de test indisponible : Docker est-il démarré ?')
  process.exit(1)
}
let statut = 1
try {
  statut = executer(process.execPath, [PLAYWRIGHT, 'test', ...process.argv.slice(2)])
} finally {
  if (!process.env.ARGOS_E2E_GARDER_BASE) executer(cheminDocker(), [...COMPOSE, 'down', '--volumes'])
}
process.exit(statut)
