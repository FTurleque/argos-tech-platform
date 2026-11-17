// Choix du composant de mise en page et aides de présentation (ADR-0023, AFF-01, AFF-02, AFF-04).
// Modules purs de la couche mise en page : src/mises-en-page/selection.ts et presentation.ts.
import { test } from 'node:test'
import assert from 'node:assert/strict'
import { MISE_EN_PAGE_DE_REPLI, choisirComposant, bornerNiveauTitre } from '../src/mises-en-page/selection.ts'
import { cleRubrique, dateLongue, niveauSuivant, nombreElements, sommaire } from '../src/mises-en-page/presentation.ts'

const REGISTRE = { classique: 'C', journal: 'J', fantaisie: 'F', gazette: 'G' }

test('l’identifiant de la préférence choisit son composant', () => {
  for (const [id, composant] of Object.entries(REGISTRE)) {
    assert.deepEqual(choisirComposant(id, REGISTRE), { identifiant: id, composant, repli: false })
  }
})

test('identifiant inconnu, vide ou absent : mise en page de repli « classique »', () => {
  assert.equal(MISE_EN_PAGE_DE_REPLI, 'classique')
  for (const id of ['inconnue', '', null, undefined, 'Gazette']) {
    assert.deepEqual(choisirComposant(id, REGISTRE), { identifiant: 'classique', composant: 'C', repli: true }, String(id))
  }
})

test('une propriété héritée ne sélectionne jamais un composant', () => {
  for (const id of ['constructor', '__proto__', 'toString', 'hasOwnProperty']) {
    assert.equal(choisirComposant(id, REGISTRE).identifiant, 'classique', id)
  }
})

test('registre sans mise en page de repli : erreur explicite', () => {
  assert.throws(() => choisirComposant('autre', { journal: 'J' }), /ne contient pas « classique »/)
})

test('niveau du titre principal borné à [1, 5], 2 par défaut', () => {
  assert.equal(bornerNiveauTitre(undefined), 2)
  assert.equal(bornerNiveauTitre(Number.NaN), 2)
  assert.equal(bornerNiveauTitre(0), 1)
  assert.equal(bornerNiveauTitre(3.7), 3)
  assert.equal(bornerNiveauTitre(9), 5)
  assert.equal(niveauSuivant(5), 6)
  assert.equal(niveauSuivant(6), 6)
})

test('sommaire : chaque rubrique avec le nombre d’éléments qui en relèvent, sans retirer ni réordonner', () => {
  const securite = { profil: 'developpeur', identifiant: 'securite', libelle: 'Sécurité' }
  const homonyme = { profil: 'expert-metier', identifiant: 'securite', libelle: 'Sécurité des données' }
  const element = (identifiant, rubriques) => ({ identifiant, rubriques })
  const veille = {
    rubriques: [homonyme, securite],
    elements: [element('a', [securite]), element('b', [securite, homonyme]), element('c', [])],
  }
  assert.deepEqual(sommaire(veille), [
    { rubrique: homonyme, nombre: 1 },
    { rubrique: securite, nombre: 2 },
  ])
  assert.equal(cleRubrique(securite), 'developpeur/securite')
})

test('dates et nombres en français', () => {
  assert.equal(dateLongue('2026-11-15T07:00:00Z'), '15 novembre 2026')
  assert.equal(dateLongue('2026-12-31T23:30:00Z'), '31 décembre 2026')
  assert.equal(dateLongue(null), 'date non communiquée')
  assert.equal(dateLongue('pas une date'), 'pas une date')
  assert.equal(nombreElements(0), '0 élément')
  assert.equal(nombreElements(1), '1 élément')
  assert.equal(nombreElements(2), '2 éléments')
})
