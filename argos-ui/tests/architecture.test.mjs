// Test d'architecture de l'IHM (ADR-0023 point 1, exigence AFF-02).
//
// La couche mise en page (src/mises-en-page) ne fait aucun appel réseau et n'importe que :
//   - 'vue' ;
//   - les feuilles de polices embarquées `@fontsource/<famille>/latin-<graisse>[-italic].css`
//     (feuilles de style seulement, sans code ; licences dans argos-ui/NOTICE) ;
//   - ses propres fichiers, dont le modèle de données commun (src/mises-en-page/modele.ts) ;
//   - les mentions obligatoires (src/contenu/MentionsObligatoires.vue) de la couche contenu.
// Tout le reste, à commencer par le client API (src/api), lui est interdit.
// Chaque mise en page (<dossier>/MiseEnPage*.vue) affiche chaque élément avec MentionsObligatoires.
//
// Exécution : npm test (node --test, aucune dépendance).
import { test } from 'node:test'
import assert from 'node:assert/strict'
import { readdirSync, readFileSync, statSync } from 'node:fs'
import { dirname, join, relative, resolve, sep } from 'node:path'
import { fileURLToPath } from 'node:url'

const RACINE = resolve(dirname(fileURLToPath(import.meta.url)), '..')
const SRC = join(RACINE, 'src')
const MISES_EN_PAGE = join(SRC, 'mises-en-page')
const AUTORISES_CONTENU = new Set([join(SRC, 'contenu', 'MentionsObligatoires.vue')])
const POLICE_EMBARQUEE = /^@fontsource\/[a-z0-9-]+\/latin-[1-9]00(-italic)?\.css$/
const RESEAU = /\b(fetch\s*\(|XMLHttpRequest|WebSocket|EventSource|sendBeacon)/
const IMPORT = /(?:import|export)\s[^'"`]*?from\s*['"]([^'"]+)['"]|import\s*['"]([^'"]+)['"]|import\s*\(\s*['"]([^'"]+)['"]\s*\)/g

function fichiers(dossier) {
  return readdirSync(dossier).flatMap((nom) => {
    const chemin = join(dossier, nom)
    if (statSync(chemin).isDirectory()) return fichiers(chemin)
    return /\.(ts|vue|js|mjs)$/.test(nom) ? [chemin] : []
  })
}

function avecExtension(chemin) {
  if (/\.(ts|vue|js|mjs)$/.test(chemin)) return chemin
  return `${chemin}.ts`
}

/** Violations de la règle de couche pour un fichier de la couche mise en page. */
export function violations(chemin, source) {
  const erreurs = []
  for (const correspondance of source.matchAll(IMPORT)) {
    const specificateur = correspondance[1] ?? correspondance[2] ?? correspondance[3]
    if (specificateur === 'vue' || POLICE_EMBARQUEE.test(specificateur)) continue
    if (!specificateur.startsWith('.')) {
      erreurs.push(`${specificateur} : seuls 'vue' et les feuilles de polices @fontsource sont autorisés`)
      continue
    }
    const cible = avecExtension(resolve(dirname(chemin), specificateur))
    const dansCouche = cible.startsWith(MISES_EN_PAGE + sep)
    if (!dansCouche && !AUTORISES_CONTENU.has(cible)) {
      erreurs.push(`${specificateur} → ${relative(RACINE, cible)} : hors de la couche mise en page`)
    }
  }
  if (RESEAU.test(source)) {
    erreurs.push('appel réseau interdit dans une mise en page')
  }
  return erreurs
}

test('aucune mise en page n’importe le client API ni un autre module de la couche contenu', () => {
  const toutes = fichiers(MISES_EN_PAGE).flatMap((chemin) =>
    violations(chemin, readFileSync(chemin, 'utf8')).map((e) => `${relative(RACINE, chemin)} : ${e}`),
  )
  assert.deepEqual(toutes, [])
})

test('la règle détecte un import du client API et un appel réseau', () => {
  const chemin = join(MISES_EN_PAGE, 'exemple', 'MiseEnPageFautive.vue')
  const source = [
    "import { fetchPreferences } from '../../api/client'",
    "import { ELEMENTS_APERCU } from '../../contenu/apercu'",
    "import axios from 'axios'",
    "import '@fontsource/inter/index.js'",
    "const x = await import('../../api/client')",
    "fetch('/api/v1/veille')",
  ].join('\n')
  const erreurs = violations(chemin, source)
  assert.equal(erreurs.filter((e) => e.includes('src' + sep + 'api')).length, 2, erreurs.join('\n'))
  assert.ok(erreurs.some((e) => e.includes('apercu')), erreurs.join('\n'))
  assert.ok(erreurs.some((e) => e.startsWith('axios')), erreurs.join('\n'))
  assert.ok(erreurs.some((e) => e.includes('appel réseau')), erreurs.join('\n'))
})

test('chaque mise en page affiche chaque élément avec les mentions obligatoires', () => {
  const composants = fichiers(MISES_EN_PAGE).filter(
    (c) => dirname(c) !== MISES_EN_PAGE && /^MiseEnPage[A-Z]\w*\.vue$/.test(c.slice(dirname(c).length + 1)),
  )
  assert.ok(composants.length >= 4, 'les quatre mises en page initiales existent')
  for (const chemin of composants) {
    const source = readFileSync(chemin, 'utf8')
    const nom = relative(RACINE, chemin)
    assert.match(source, /import MentionsObligatoires from '\.\.\/\.\.\/contenu\/MentionsObligatoires\.vue'/, nom)
    assert.match(source, /<MentionsObligatoires :mentions="[a-z]+\.mentions"/, nom)
    assert.match(source, /data-element/, nom)
  }
})

test('le registre déclare les quatre mises en page initiales, et chaque dossier y figure', () => {
  const registre = readFileSync(join(MISES_EN_PAGE, 'registre.ts'), 'utf8')
  for (const id of ['classique', 'journal', 'fantaisie', 'gazette']) {
    assert.match(registre, new RegExp(`^\\s+${id}: `, 'm'), `${id} absent du registre`)
  }
  const dossiers = readdirSync(MISES_EN_PAGE).filter((n) => statSync(join(MISES_EN_PAGE, n)).isDirectory())
  for (const dossier of dossiers) {
    assert.match(registre, new RegExp(`^\\s+${dossier}: `, 'm'), `${dossier} n'est pas enregistrée`)
  }
})
