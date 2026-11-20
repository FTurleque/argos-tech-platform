// Composants ignorés faute de PURL (INV-01-S3, décisions E4 et G5 du 2026-11-19) : conversion de la réponse de
// `GET /api/v1/projects/{projectId}/inventory/summary`, chemins des appels, rendu côté serveur de la liste
// (tests/support/rendu-ssr.mjs, sans navigateur ni réseau) et règles de sécurité de l'écran.
import { after, before, test } from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import {
  cheminInventaire,
  cheminSyntheseInventaire,
  MAX_CARACTERES_CHAMP,
  MAX_COMPOSANTS_IGNORES,
} from '../src/api/inventaire-modele.ts'
import {
  composantsIgnores,
  libelleMotif,
  messageErreurInventaire,
  syntheseComposantsIgnores,
} from '../src/contenu/composants-ignores.ts'
import { route } from './support/routes.mjs'
import { ouvrirRendu } from './support/rendu-ssr.mjs'

const source = (chemin) => readFileSync(new URL(chemin, import.meta.url), 'utf8')
const PROJET = '3f2504e0-4f89-41d3-9a0c-0305e82c3301'

function reponse(ignoredCount, ignoredComponents) {
  return {
    projectId: PROJET,
    status: 'AVAILABLE',
    summary: {
      projectId: PROJET,
      origin: 'SBOM',
      sourceDate: '2026-11-17T00:00:00Z',
      importedAt: '2026-11-18T00:00:00Z',
      componentCount: 0,
      ignoredCount,
      ignoredComponents,
    },
  }
}

const LOG4J = { name: 'log4j-core', group: 'org.apache.logging.log4j', version: '2.14.1', reason: 'SANS_PURL' }

let rendu
before(async () => {
  rendu = await ouvrirRendu()
})
after(async () => {
  await rendu?.fermer()
})

test('chemin de l’inventaire : UUID seulement, aucune URL construite avec une saisie arbitraire', () => {
  assert.equal(cheminInventaire(PROJET), `/api/v1/projects/${PROJET}/inventory`)
  assert.equal(cheminInventaire(` ${PROJET} `), `/api/v1/projects/${PROJET}/inventory`)
  assert.equal(cheminInventaire('../ops/sources'), null)
  assert.equal(cheminInventaire(`${PROJET}/failures`), null)
  assert.equal(cheminInventaire(null), null)
  assert.equal(cheminSyntheseInventaire(PROJET), `/api/v1/projects/${PROJET}/inventory/summary`)
  assert.equal(cheminSyntheseInventaire(` ${PROJET} `), `/api/v1/projects/${PROJET}/inventory/summary`)
  assert.equal(cheminSyntheseInventaire('../ops/sources'), null)
  assert.equal(cheminSyntheseInventaire(`${PROJET}/failures`), null)
  assert.equal(cheminSyntheseInventaire(undefined), null)
})

test('conversion : nom, groupe, version et motif de chaque composant ignoré', () => {
  const vue = composantsIgnores(reponse(2, [LOG4J, { name: 'outil', group: null, version: '', reason: 'PURL_INVALIDE' }]))
  assert.deepEqual(vue, {
    etat: 'DISPONIBLE',
    total: 2,
    tronque: false,
    composants: [
      { nom: 'log4j-core', groupe: 'org.apache.logging.log4j', version: '2.14.1', motif: 'sans PURL' },
      { nom: 'outil', groupe: null, version: null, motif: 'PURL invalide' },
    ],
  })
  assert.equal(syntheseComposantsIgnores(vue), "2 composants écartés de l'inventaire au dernier import, faute de PURL exploitable.")
})

test('conversion : état vide et projet sans inventaire', () => {
  const vide = composantsIgnores(reponse(0, []))
  assert.deepEqual(vide, { etat: 'DISPONIBLE', total: 0, composants: [], tronque: false })
  assert.equal(syntheseComposantsIgnores(vide), 'Aucun composant ignoré au dernier import.')
  assert.deepEqual(composantsIgnores(reponse(0, null)), vide, 'liste absente : état vide')
  const sansSbom = composantsIgnores({ projectId: PROJET, status: 'NO_SBOM', summary: null })
  assert.deepEqual(sansSbom, { etat: 'SANS_INVENTAIRE' })
  assert.equal(syntheseComposantsIgnores(sansSbom), 'Aucun inventaire importé pour ce projet.')
  assert.deepEqual(composantsIgnores(undefined), { etat: 'SANS_INVENTAIRE' })
})

test('conversion : liste et champs bornés comme l’API, total déclaré conservé', () => {
  const longue = Array.from({ length: MAX_COMPOSANTS_IGNORES + 5 }, (_, rang) => ({ ...LOG4J, name: `c${rang}` }))
  const vue = composantsIgnores(reponse(250, longue))
  assert.equal(vue.composants.length, MAX_COMPOSANTS_IGNORES)
  assert.equal(vue.total, 250)
  assert.equal(vue.tronque, true)
  assert.equal(
    syntheseComposantsIgnores(vue),
    "250 composants écartés de l'inventaire au dernier import, faute de PURL exploitable ; les 100 premiers sont listés.",
  )
  const champ = composantsIgnores(reponse(1, [{ ...LOG4J, name: 'x'.repeat(500) }]))
  assert.equal(champ.composants[0].nom.length, MAX_CARACTERES_CHAMP)
  const unSeul = composantsIgnores(reponse(1, [LOG4J]))
  assert.equal(syntheseComposantsIgnores(unSeul), "1 composant écarté de l'inventaire au dernier import, faute de PURL exploitable.")
  assert.equal(composantsIgnores(reponse(-3, [LOG4J])).total, 1, 'total jamais inférieur à la liste')
})

test('libellés des motifs et messages d’erreur', () => {
  assert.equal(libelleMotif('SANS_PURL'), 'sans PURL')
  assert.equal(libelleMotif('PURL_INVALIDE'), 'PURL invalide')
  assert.equal(libelleMotif('AUTRE'), 'AUTRE')
  assert.equal(libelleMotif(null), 'non précisé')
  assert.equal(messageErreurInventaire(401), 'Connectez-vous pour consulter l’inventaire.')
  assert.equal(messageErreurInventaire(404), 'Inventaire introuvable ou hors de vos espaces.')
  assert.equal(messageErreurInventaire(403), 'Inventaire introuvable ou hors de vos espaces.')
  assert.equal(messageErreurInventaire(undefined), 'Inventaire indisponible pour le moment.')
})

test('rendu : tableau à en-têtes de colonne, valeurs échappées, champ absent nommé', async () => {
  const hostile = { name: '<img src=x onerror=alert(1)>', group: null, version: null, reason: 'SANS_PURL' }
  const html = await rendu.rendreComposant('/src/contenu/ComposantsIgnores.vue', {
    vue: composantsIgnores(reponse(2, [LOG4J, hostile])),
    projet: 'Paie',
  })
  assert.match(html, /<table[\s>]/)
  assert.match(html, /<caption[^>]*>Composants ignorés du projet Paie<\/caption>/)
  const entetes = [...html.matchAll(/<th scope="col"[^>]*>([^<]*)<\/th>/g)].map((correspondance) => correspondance[1])
  assert.deepEqual(entetes, ['Nom', 'Groupe', 'Version', 'Motif'])
  assert.equal([...html.matchAll(/data-composant-ignore/g)].length, 2)
  assert.match(html, /<td[^>]*>log4j-core<\/td><td[^>]*>org\.apache\.logging\.log4j<\/td><td[^>]*>2\.14\.1<\/td><td[^>]*>sans PURL<\/td>/)
  assert.ok(!html.includes('<img'), 'valeur de la SBOM rendue comme texte')
  assert.match(html, /&lt;img src=x onerror=alert\(1\)&gt;/)
  assert.match(html, /non renseigné<\/td><td[^>]*>non renseignée<\/td>/)
  assert.match(html, /2 composants écartés/)
})

test('rendu : état vide et projet sans inventaire, sans tableau', async () => {
  const vide = await rendu.rendreComposant('/src/contenu/ComposantsIgnores.vue', { vue: composantsIgnores(reponse(0, [])), projet: 'Paie' })
  assert.ok(!vide.includes('<table'))
  assert.match(vide, /Aucun composant ignoré au dernier import\./)
  const sans = await rendu.rendreComposant('/src/contenu/ComposantsIgnores.vue', { vue: { etat: 'SANS_INVENTAIRE' }, projet: 'Paie' })
  assert.ok(!sans.includes('<table'))
  assert.match(sans, /Aucun inventaire importé pour ce projet\./)
})

test('sécurité de l’écran : appel par appelerApi, aucun v-html, liste rattachée à la vue d’inventaire', () => {
  const api = source('../src/api/inventaire.ts')
  assert.match(api, /appelerApi<SyntheseInventaireProjetApi>\(chemin/)
  assert.doesNotMatch(api, /\bfetch\s*\(/)
  // Décision G5 : la vue ne lit plus l'inventaire complet, seulement la synthèse et la page de composants.
  assert.doesNotMatch(api, /cheminInventaire\b/)
  assert.doesNotMatch(api, /\bInventaireProjetApi\b/)
  const composant = source('../src/contenu/ComposantsIgnores.vue')
  const vue = source('../src/views/InventaireView.vue')
  for (const texteSource of [composant, vue]) {
    assert.doesNotMatch(texteSource, /v-html/)
    assert.doesNotMatch(texteSource, /\bfetch\s*\(/)
  }
  assert.doesNotMatch(composant, /:key="i"/, 'clé de ligne différente du seul rang')
  assert.match(vue, /<h2 id="titre-composants-ignores">Composants ignorés<\/h2>/)
  assert.match(vue, /chargerInventaireProjet\(id\.value, fetchSyntheseInventaire, signal\)/)
  assert.deepEqual(route('inventaire'), { path: '/inventaire', name: 'inventaire', composant: 'InventaireView', publique: false })
  assert.deepEqual(route('inventaire-projet'),
    { path: '/inventaire/:id', name: 'inventaire-projet', composant: 'InventaireView', publique: false })
  // Entrée de navigation commune au portail web et au client desktop (sans condition isDesktop).
  assert.match(source('../src/App.vue'), /\n\s*<RouterLink to="\/inventaire">Inventaire<\/RouterLink>/)
})
