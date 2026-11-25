// Contrôle du bundle de production de l'IHM (ACC-11-S3, design.md I-1 et D6) : construit comme l'étage « ui » du
// Dockerfile (VITE_ARGOS_API_URL vide, IHM servie par le serveur) et échoue si une adresse d'émetteur figée y
// figure. L'émetteur est une donnée de l'instance, lue au démarrage dans la réponse publique du serveur.
// Le build écrit dans un dossier temporaire : argos-ui/dist, chargé par le client desktop, n'est pas touché.
import { test } from 'node:test'
import assert from 'node:assert/strict'
import { execFileSync } from 'node:child_process'
import { mkdtempSync, readdirSync, readFileSync, rmSync, statSync } from 'node:fs'
import { tmpdir } from 'node:os'
import { dirname, join, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'

const RACINE = resolve(dirname(fileURLToPath(import.meta.url)), '..')
const VITE = join(RACINE, 'node_modules', 'vite', 'bin', 'vite.js')
const DELAI_BUILD_MS = 180_000
const EMETTEURS_FIGES = ['localhost:8180', '127.0.0.1:8180']

function fichiers(dossier) {
  return readdirSync(dossier).flatMap((nom) => {
    const chemin = join(dossier, nom)
    return statSync(chemin).isDirectory() ? fichiers(chemin) : [chemin]
  })
}

/** Fichiers du bundle qui contiennent l'une des adresses, avec l'adresse trouvée. */
export function occurrences(dossier, adresses) {
  const trouvees = []
  for (const chemin of fichiers(dossier)) {
    const contenu = readFileSync(chemin, 'utf8')
    for (const adresse of adresses) {
      if (contenu.includes(adresse)) trouvees.push(`${chemin} : ${adresse}`)
    }
  }
  return trouvees
}

test('le bundle de production ne contient aucun émetteur OIDC figé', { timeout: DELAI_BUILD_MS }, () => {
  const sortie = mkdtempSync(join(tmpdir(), 'argos-bundle-'))
  try {
    execFileSync(process.execPath, [VITE, 'build', '--outDir', sortie, '--emptyOutDir'], {
      cwd: RACINE,
      env: { ...process.env, VITE_ARGOS_API_URL: '' },
      stdio: 'pipe',
    })
    assert.ok(fichiers(sortie).some((chemin) => chemin.endsWith('.js')), 'le build produit au moins un script')
    assert.deepEqual(occurrences(sortie, EMETTEURS_FIGES), [], 'aucun émetteur figé dans le bundle')
  } finally {
    rmSync(sortie, { recursive: true, force: true })
  }
})

test("le contrôle détecte un émetteur figé dans un bundle d'une ancienne révision", () => {
  const sortie = mkdtempSync(join(tmpdir(), 'argos-bundle-fige-'))
  try {
    execFileSync(process.execPath, ['-e',
      `require('node:fs').writeFileSync(process.argv[1] + '/index.js', 'x="http://localhost:8180/realms/argos"')`,
      sortie])
    assert.equal(occurrences(sortie, EMETTEURS_FIGES).length, 1)
  } finally {
    rmSync(sortie, { recursive: true, force: true })
  }
})
