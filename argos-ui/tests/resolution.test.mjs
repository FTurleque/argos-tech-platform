// Choix de la mise en page affichée (DF-AFF-1, AFF-04). Module pur de la couche contenu.
import { test } from 'node:test'
import assert from 'node:assert/strict'
import { MISE_EN_PAGE_PAR_DEFAUT, resoudreMiseEnPage } from '../src/contenu/resolution.ts'

const CONNUES = ['classique', 'journal', 'fantaisie', 'gazette']

test('serveur injoignable ou utilisateur non authentifié : classique', () => {
  assert.deepEqual(resoudreMiseEnPage(null, CONNUES), { identifiant: 'classique', message: null })
  assert.equal(MISE_EN_PAGE_PAR_DEFAUT, 'classique')
})

test('la mise en page renvoyée par le serveur est appliquée avec son message', () => {
  assert.deepEqual(resoudreMiseEnPage({ miseEnPage: 'gazette', message: null }, CONNUES), {
    identifiant: 'gazette',
    message: null,
  })
  const retiree = { miseEnPage: 'classique', message: 'Votre mise en page « fantaisie » n’est plus disponible' }
  assert.deepEqual(resoudreMiseEnPage(retiree, CONNUES), { identifiant: 'classique', message: retiree.message })
})

test('mise en page publiée côté serveur mais inconnue de cette IHM : classique et message', () => {
  const resolue = resoudreMiseEnPage({ miseEnPage: 'aquarelle', message: null }, CONNUES)
  assert.equal(resolue.identifiant, 'classique')
  assert.match(resolue.message, /aquarelle/)
})
