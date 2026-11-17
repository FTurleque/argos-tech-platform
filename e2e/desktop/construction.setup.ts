// Prépare le client desktop : binaire Electron présent, puis IHM argos-ui construite comme pour
// « npm --prefix argos-desktop start » (vue-tsc et Vite, dans argos-ui/dist, chargé par main.js
// hors paquet), avec l'API et l'émetteur de la pile de test.
import { spawnSync } from 'node:child_process'
import { existsSync } from 'node:fs'
import { join } from 'node:path'
import { test as setup } from '@playwright/test'
import { DOSSIER_IHM, verifierBinaireElectron } from '../support/electron'
import { EMETTEUR, URL_API } from '../support/environnement'

setup.setTimeout(5 * 60_000)

setup('IHM construite pour le client desktop', () => {
  verifierBinaireElectron()
  // Commande entière passée au shell (npm est un script npm.cmd sous Windows) ; chemin entre guillemets.
  const r = spawnSync(`npm --prefix "${DOSSIER_IHM}" run build`, {
    stdio: 'pipe',
    encoding: 'utf8',
    shell: true,
    env: { ...process.env, VITE_ARGOS_API_URL: URL_API, VITE_ARGOS_OIDC_ISSUER: EMETTEUR },
  })
  if (r.error) throw r.error
  if (r.status !== 0) throw new Error(`Construction de argos-ui en échec :\n${r.stdout}\n${r.stderr}`)
  if (!existsSync(join(DOSSIER_IHM, 'dist', 'index.html'))) throw new Error('argos-ui/dist/index.html absent')
})
