// Garde des vues (changement connecter-ihm-oidc : ACC-07-S1, ACC-07-S5, ACC-09-S4).
// Les routes réelles sont lues dans src/router.ts : seules l'état du serveur, la connexion et
// l'écran de refus sont publiques.
import { test } from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { decisionGarde } from '../src/session/garde.ts'

const ROUTEUR = readFileSync(new URL('../src/router.ts', import.meta.url), 'utf8')
const ROUTES = [...ROUTEUR.matchAll(/\{ path: '([^']+)', name: '([^']+)', component: \w+(, meta: \{ public: true \})? \}/g)]
  .map((m) => ({ path: m[1], name: m[2], publique: Boolean(m[3]) }))

test('seules l’état du serveur, la connexion et l’écran de refus sont publiques', () => {
  assert.ok(ROUTES.length >= 7, 'routes lues dans router.ts')
  assert.deepEqual(ROUTES.filter((r) => r.publique).map((r) => r.name).sort(), ['compte-non-cree', 'connexion', 'status'])
})

test('ACC-07-S1 : sans session, toute vue protégée mène à l’écran de connexion avec la vue en retour', () => {
  for (const route of ROUTES.filter((r) => !r.publique)) {
    assert.deepEqual(
      decisionGarde({ publique: false, connecte: false, compteNonCree: false, chemin: route.path }),
      { type: 'connexion', retour: route.path },
      route.name,
    )
  }
})

test('ACC-07-S5 : l’état du serveur s’affiche sans session', () => {
  assert.deepEqual(decisionGarde({ publique: true, connecte: false, compteNonCree: false, chemin: '/' }), { type: 'afficher' })
})

test('ACC-09-S4 : compte non créé → vues protégées inaccessibles, écran de refus', () => {
  assert.deepEqual(decisionGarde({ publique: false, connecte: true, compteNonCree: true, chemin: '/affichage' }), { type: 'compte-non-cree' })
  assert.deepEqual(decisionGarde({ publique: true, connecte: true, compteNonCree: true, chemin: '/compte-non-cree' }), { type: 'afficher' })
})

test('session ouverte et compte créé : la vue s’affiche', () => {
  assert.deepEqual(decisionGarde({ publique: false, connecte: true, compteNonCree: false, chemin: '/alertes' }), { type: 'afficher' })
})
