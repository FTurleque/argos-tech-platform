// Jeton porteur et refus du client REST (changement connecter-ihm-oidc : ACC-09).
import { test } from 'node:test'
import assert from 'node:assert/strict'
import { classerRefus, entetesAvecJeton, exigeJeton } from '../src/api/autorisation.ts'

const BASE = { Accept: 'application/json' }

test('ACC-09-S1 : le jeton est joint à tout appel de /api/v1, dans l’en-tête seulement', () => {
  for (const chemin of ['/api/v1/moi', '/api/v1/preferences', '/api/v1/alertes?canal=DESKTOP&limite=50', '/api/v1/alertes/1/remise']) {
    const entetes = entetesAvecJeton(chemin, BASE, 'jeton-1')
    assert.equal(entetes.Authorization, 'Bearer jeton-1', chemin)
    assert.equal(entetes.Accept, 'application/json')
    assert.doesNotMatch(chemin, /jeton-1/)
  }
})

test('ACC-09-S2 : l’identité du serveur et les adresses hors /api/v1 sont appelées sans jeton', () => {
  assert.equal(exigeJeton('/api/v1/system/info'), false)
  assert.equal(exigeJeton('/api/v1/system/info?x=1'), false)
  assert.equal(exigeJeton('/q/health/ready'), false)
  assert.equal(exigeJeton('/api/v10/x'), false)
  assert.equal(entetesAvecJeton('/api/v1/system/info', BASE, 'jeton-1').Authorization, undefined)
  assert.equal(entetesAvecJeton('/q/health', BASE, 'jeton-1').Authorization, undefined)
})

test('sans jeton disponible, aucun en-tête Authorization vide n’est envoyé', () => {
  assert.deepEqual(entetesAvecJeton('/api/v1/moi', BASE, null), BASE)
  // L'objet reçu n'est pas modifié.
  const entree = { ...BASE }
  entetesAvecJeton('/api/v1/moi', entree, 'j')
  assert.deepEqual(entree, BASE)
})

test('ACC-09-S3 à S5 : 401 relance la connexion, 403 affiche un refus, compte non créé à part', () => {
  assert.equal(classerRefus(401, null), 'NON_AUTHENTIFIE')
  assert.equal(classerRefus(403, { code: 'INSCRIPTION_SUR_INVITATION', message: '…' }), 'COMPTE_NON_CREE')
  assert.equal(classerRefus(403, { code: 'ROLE_INSUFFISANT' }), 'INTERDIT')
  assert.equal(classerRefus(403, null), 'INTERDIT')
  assert.equal(classerRefus(403, 'texte'), 'INTERDIT')
  assert.equal(classerRefus(404, null), null)
  assert.equal(classerRefus(500, null), null)
})
