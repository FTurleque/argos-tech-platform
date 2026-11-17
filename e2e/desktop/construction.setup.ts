// Prépare le client desktop : binaire Electron présent, puis IHM argos-ui construite comme pour
// « npm --prefix argos-desktop start » (vue-tsc puis Vite, dans argos-ui/dist, chargé par main.js
// hors paquet), avec l'API et l'émetteur de la pile de test.
// Les outils sont lancés par le Node courant (process.execPath) sur leurs scripts JS installés dans
// argos-ui/node_modules, avec des arguments en tableau et sans shell : le PATH n'est pas consulté,
// comme pour Docker (support/docker.mjs) et Playwright (scripts/lancer.mjs).
import { spawnSync } from 'node:child_process'
import { existsSync } from 'node:fs'
import { join } from 'node:path'
import { test as setup } from '@playwright/test'
import { DOSSIER_IHM, verifierBinaireElectron } from '../support/electron'
import { EMETTEUR, URL_API } from '../support/environnement'

setup.setTimeout(5 * 60_000)

const VUE_TSC = join(DOSSIER_IHM, 'node_modules', 'vue-tsc', 'bin', 'vue-tsc.js')
const VITE = join(DOSSIER_IHM, 'node_modules', 'vite', 'bin', 'vite.js')

/** Exécute un script JS de argos-ui/node_modules par le Node courant ; lève une erreur en cas d'échec. */
function executerOutil(script: string, args: string[]): void {
  if (!existsSync(script)) throw new Error(`${script} absent : exécutez « npm --prefix argos-ui ci »`)
  const r = spawnSync(process.execPath, [script, ...args], {
    cwd: DOSSIER_IHM,
    stdio: 'pipe',
    encoding: 'utf8',
    env: { ...process.env, VITE_ARGOS_API_URL: URL_API, VITE_ARGOS_OIDC_ISSUER: EMETTEUR },
  })
  if (r.error) throw r.error
  if (r.status !== 0) throw new Error(`Construction de argos-ui en échec (${script}) :\n${r.stdout}\n${r.stderr}`)
}

setup('IHM construite pour le client desktop', () => {
  verifierBinaireElectron()
  // Même enchaînement que le script « build » de argos-ui : vue-tsc --noEmit && vite build.
  executerOutil(VUE_TSC, ['--noEmit'])
  executerOutil(VITE, ['build'])
  if (!existsSync(join(DOSSIER_IHM, 'dist', 'index.html'))) throw new Error('argos-ui/dist/index.html absent')
})
