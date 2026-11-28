#!/usr/bin/env node
// Lance les tests de bout en bout : base PostgreSQL jetable (e2e/compose.yaml), puis Playwright, qui
// démarre le serveur (quarkus:dev et son Keycloak de test) et le portail web. Sur l'image construite
// (ARGOS_E2E_IMAGE, support/pile.mjs), le Keycloak de test démarre avec la base (e2e/compose.image.yaml) et
// Playwright démarre l'image. La pile est arrêtée et effacée à la fin, que les tests réussissent ou non.
// Usage : [ARGOS_E2E_IMAGE=argos:e2e] npm --prefix e2e test [-- <options de playwright test>]
import { spawnSync } from 'node:child_process'
import { dirname, join } from 'node:path'
import { fileURLToPath } from 'node:url'
import { cheminDocker } from '../support/docker.mjs'
import { argumentsCompose, IMAGE } from '../support/pile.mjs'

const E2E = join(dirname(fileURLToPath(import.meta.url)), '..')
const COMPOSE = argumentsCompose()
/** Services démarrés avant Playwright : la base, et le Keycloak de test sur l'image construite. */
const SERVICES = IMAGE ? ['postgres', 'keycloak'] : ['postgres']
const PLAYWRIGHT = join(E2E, 'node_modules', '@playwright', 'test', 'cli.js')

function executer(commande, args) {
  const resultat = spawnSync(commande, args, { cwd: E2E, stdio: 'inherit' })
  if (resultat.error) throw resultat.error
  return resultat.status ?? 1
}

if (executer(cheminDocker(), [...COMPOSE, 'up', '-d', '--wait', ...SERVICES]) !== 0) {
  console.error('Pile de test indisponible : Docker est-il démarré ?')
  process.exit(1)
}
let statut = 1
try {
  statut = executer(process.execPath, [PLAYWRIGHT, 'test', ...process.argv.slice(2)])
} finally {
  if (!process.env.ARGOS_E2E_GARDER_BASE) executer(cheminDocker(), [...COMPOSE, 'down', '--volumes'])
}
process.exit(statut)
