// Dépôts d'un projet dans la vue d'inventaire (INV-10-S1 ; décision J6 du porteur du projet, 2026-11-19 ; changement
// automatiser-chaine-veille, tâche 2.5) : dépôts de la synthèse (`repositories` de `GET …/inventory/summary`), dépôts
// d'un composant (`repositories` de `GET …/inventory/components`), colonne « Dépôts » réservée aux projets à plusieurs
// dépôts, valeurs reçues rendues comme texte. Lecture de l'API simulée : ni navigateur ni réseau.
import { test } from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import {
  afficherColonneDepots,
  depotsComposant,
  depotsInventaire,
  libelleDepotsComposant,
} from '../src/contenu/inventaire-depots.ts'
import { chargerInventaireProjet } from '../src/contenu/inventaire-projet.ts'
import { pageComposants } from '../src/contenu/inventaire-listes.ts'

const PROJET = '3f2504e0-4f89-41d3-9a0c-0305e82c3301'
const source = (chemin) => readFileSync(new URL(chemin, import.meta.url), 'utf8')

const FRONT = {
  id: 'd-front',
  provider: 'GITLAB',
  repository: 'groupe/front',
  branch: 'main',
  principal: true,
  origin: 'SBOM',
  sourceDate: '2026-11-17T00:00:00Z',
  importedAt: '2026-11-19T03:00:00Z',
  lastFailure: null,
}
const BACK = {
  id: 'd-back',
  provider: 'GITHUB',
  repository: 'org/back',
  branch: 'main',
  principal: false,
  origin: 'MANIFEST',
  sourceDate: null,
  importedAt: '2026-11-19T03:00:01Z',
  lastFailure: { reason: 'HTTP 503 de gitlab.exemple', receivedAt: '2026-11-19T03:00:02Z' },
}

function synthese(repositories) {
  return {
    projectId: PROJET,
    status: 'AVAILABLE',
    summary: {
      projectId: PROJET,
      origin: 'SBOM',
      sourceDate: '2026-11-17T00:00:00Z',
      importedAt: '2026-11-19T03:00:00Z',
      componentCount: 3,
      ignoredCount: 0,
      ignoredComponents: [],
      repositories,
    },
  }
}

test('INV-10-S1 : dépôts de la synthèse, principal d’abord, origine, dates et dernier échec', () => {
  assert.deepEqual(depotsInventaire(synthese([FRONT, BACK])), [
    {
      cle: 'd-front',
      forge: 'GitLab',
      depot: 'groupe/front',
      branche: 'main',
      principal: true,
      origine: 'SBOM',
      dateSource: '2026-11-17T00:00:00Z',
      importeLe: '2026-11-19T03:00:00Z',
      echec: null,
    },
    {
      cle: 'd-back',
      forge: 'GitHub',
      depot: 'org/back',
      branche: 'main',
      principal: false,
      origine: 'manifestes',
      dateSource: null,
      importeLe: '2026-11-19T03:00:01Z',
      echec: { motif: 'HTTP 503 de gitlab.exemple', date: '2026-11-19T03:00:02Z' },
    },
  ])
})

test('dépôt jamais importé, valeurs absentes ou inattendues nommées', () => {
  const [depot] = depotsInventaire(synthese([{ id: '', provider: 'AUTRE', repository: ' ', branch: null, principal: 'oui',
    origin: null, sourceDate: null, importedAt: null, lastFailure: { reason: '', receivedAt: null } }]))
  assert.equal(depot.cle, 'rang-0')
  assert.equal(depot.forge, 'forge non précisée')
  assert.equal(depot.depot, 'dépôt non renseigné')
  assert.equal(depot.principal, false)
  assert.equal(depot.origine, null)
  assert.equal(depot.echec, null)
})

test('aucun dépôt : projet sans dépôt, sans inventaire ou serveur antérieur', () => {
  assert.deepEqual(depotsInventaire(synthese([])), [])
  assert.deepEqual(depotsInventaire(synthese(undefined)), [])
  assert.deepEqual(depotsInventaire({ projectId: PROJET, status: 'NO_SBOM', summary: null }), [])
  assert.deepEqual(depotsInventaire(null), [])
})

test('chargement de l’écran : les dépôts accompagnent la synthèse', async () => {
  const etat = await chargerInventaireProjet(PROJET, async () => synthese([FRONT, BACK]))
  assert.equal(etat.erreur, '')
  assert.deepEqual(etat.depots.map((depot) => depot.depot), ['groupe/front', 'org/back'])
})

test('INV-10-S1 : composant présent dans deux dépôts, une seule ligne avec ses dépôts', () => {
  const page = pageComposants({
    projectId: PROJET,
    page: 0,
    size: 20,
    total: 1,
    items: [{ id: 'c-b', name: 'b', version: '2.0.0', purl: 'pkg:npm/b@2.0.0', transitive: false,
      repositories: [{ id: 'd-front', provider: 'GITLAB', repository: 'groupe/front' },
        { id: 'd-back', provider: 'GITHUB', repository: 'org/back' }] }],
  })
  assert.deepEqual(page.composants[0].depots, ['groupe/front', 'org/back'])
  assert.equal(libelleDepotsComposant(page.composants[0].depots), 'groupe/front, org/back')
  assert.equal(libelleDepotsComposant([]), 'aucun dépôt (import manuel)')
  assert.deepEqual(depotsComposant(null), [])
  assert.deepEqual(depotsComposant([{ id: 'x', provider: 'GITLAB', repository: null }]), [])
})

test('colonne « Dépôts » affichée seulement pour un projet à plusieurs dépôts', () => {
  const deux = depotsInventaire(synthese([FRONT, BACK]))
  assert.equal(afficherColonneDepots(deux), true)
  assert.equal(afficherColonneDepots(deux.slice(0, 1)), false)
  assert.equal(afficherColonneDepots([]), false)
})

test('écran : tableau des dépôts et colonne conditionnelle, valeurs rendues comme texte (aucun v-html)', () => {
  const vue = source('../src/views/InventaireView.vue')
  const tableau = source('../src/contenu/ComposantsInventories.vue')
  assert.match(vue, /Dépôts du projet/)
  assert.match(vue, /v-if="depots\.length > 0"/)
  assert.match(vue, /:afficher-depots="afficherColonneDepots\(depots\)"/)
  assert.match(tableau, /<th v-if="afficherDepots" scope="col">Dépôts<\/th>/)
  for (const texteSource of [vue, tableau, source('../src/contenu/inventaire-depots.ts')]) {
    assert.doesNotMatch(texteSource, /v-html|innerHTML/)
  }
})
