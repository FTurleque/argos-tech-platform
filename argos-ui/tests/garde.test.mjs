// Garde des vues (changement connecter-ihm-oidc : ACC-07-S1, ACC-07-S5, ACC-09-S4).
// Les routes réelles sont lues dans src/router.ts : seules l'état du serveur, la connexion et
// l'écran de refus sont publiques.
import { test } from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { decisionExpiration, decisionGarde } from '../src/session/garde.ts'
import { ROUTES } from './support/routes.mjs'

test('seules l’état du serveur, la connexion et l’écran de refus sont publiques', () => {
  assert.equal(ROUTES.length, 13, 'routes lues dans router.ts')
  assert.deepEqual(ROUTES.filter((r) => r.publique).map((r) => r.name).sort(), ['compte-non-cree', 'connexion', 'status'])
  const routeur = readFileSync(new URL('../src/router.ts', import.meta.url), 'utf8')
  assert.match(routeur, /publique \? \{ path, name, component, meta: \{ public: true \} \} : \{ path, name, component \}/,
    'seules les vues publiques portent meta.public')
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

test('ACC-08-S3 : une session expirée sur une vue protégée ouvre la connexion, la vue en retour', () => {
  for (const route of ROUTES) {
    const attendu = route.publique ? { type: 'afficher' } : { type: 'connexion', retour: route.path }
    assert.deepEqual(decisionExpiration({ publique: route.publique, chemin: route.path }), attendu, route.name)
  }
})

test('ACC-08-S3 : le passage à l’état expiré déclenche la décision dans installerSession', () => {
  // Constat E2E : un renouvellement refusé laissait la vue protégée affichée, sans l'état expiré.
  const session = readFileSync(new URL('../src/session/session.ts', import.meta.url), 'utf8')
  const suivi = session.indexOf("if (etat !== 'EXPIREE') return")
  assert.ok(suivi > session.indexOf('export function installerSession'), 'suivi de l’état branché par installerSession')
  assert.ok(session.indexOf('decisionExpiration(', suivi) > suivi, 'décision de la garde appliquée à la vue courante')
})
