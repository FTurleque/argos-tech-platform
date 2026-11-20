// Abonnements actifs ou suspendus et suppression distincte (ABO-07-S4, ABO-08-S1, changement
// `automatiser-chaine-veille`, tâche 1.6) : état affiché tel que le serveur le renvoie, actions proposées,
// confirmation avant suppression, mise à jour de la liste, appels par le client REST commun, aucune règle de
// droits dans l'IHM.
import { test } from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import {
  actionsAbonnement,
  confirmationSuppression,
  libelleAction,
  libelleEtat,
  messageErreurAbonnement,
  nomAbonnement,
  remplacerAbonnement,
  retirerAbonnement,
} from '../src/contenu/abonnements.ts'

const source = (chemin) => readFileSync(new URL(chemin, import.meta.url), 'utf8')

function abonnement(id, scope, state, filters = [{ type: 'PACKAGE', value: 'pkg:cargo/hyper' }]) {
  return {
    id,
    scope,
    ownerWorkspaceId: scope === 'TEAM' ? 'e1' : null,
    ownerUserId: scope === 'PERSONAL' ? 'u1' : null,
    filters,
    channel: 'EMAIL',
    frequency: 'IMMEDIATE',
    mandatory: false,
    state,
    createdAt: '2026-11-19T08:00:00Z',
  }
}

test('ABO-07-S4 : chaque abonnement est présenté avec l’état renvoyé par le serveur', () => {
  assert.equal(libelleEtat('ACTIVE'), 'Actif')
  assert.equal(libelleEtat('SUSPENDED'), 'Suspendu')
  // Un état inconnu n'est jamais remplacé par un état supposé.
  assert.equal(libelleEtat('ARCHIVED'), 'ARCHIVED')
  assert.equal(nomAbonnement(abonnement('a', 'PERSONAL', 'ACTIVE')), 'Abonnement personnel — paquet : pkg:cargo/hyper')
  assert.equal(
    nomAbonnement(abonnement('t', 'TEAM', 'SUSPENDED', [{ type: 'TECHNOLOGY', value: 'java' }, { type: 'SEVERITY', value: 'HIGH' }])),
    "Abonnement d'équipe — technologie : java, gravité minimale : HIGH",
  )
  assert.equal(nomAbonnement(abonnement('v', 'PERSONAL', 'ACTIVE', [])), 'Abonnement personnel')
})

test('ABO-07 : suspendre un actif, réactiver un suspendu ; supprimer dans les deux cas (ABO-08)', () => {
  assert.deepEqual(actionsAbonnement(abonnement('a', 'PERSONAL', 'ACTIVE')), ['SUSPENDRE', 'SUPPRIMER'])
  assert.deepEqual(actionsAbonnement(abonnement('s', 'PERSONAL', 'SUSPENDED')), ['REACTIVER', 'SUPPRIMER'])
  assert.deepEqual(actionsAbonnement(abonnement('x', 'PERSONAL', 'ARCHIVED')), [])
  // Abonnement d'équipe : affiché avec son état, géré par les administrateurs de l'espace (PA-1).
  assert.deepEqual(actionsAbonnement(abonnement('t', 'TEAM', 'ACTIVE')), [])
  assert.equal(libelleAction('SUSPENDRE'), 'Suspendre')
  assert.equal(libelleAction('REACTIVER'), 'Réactiver')
  assert.equal(libelleAction('SUPPRIMER'), 'Supprimer')
})

test('ABO-08-S1 : la suppression est confirmée, définitive et distinguée de la suspension', () => {
  const question = confirmationSuppression(abonnement('s', 'PERSONAL', 'SUSPENDED'))
  assert.match(question, /définitivement/)
  assert.match(question, /pkg:cargo\/hyper/)
  assert.match(question, /ne pourra pas être réactivé/)
  assert.match(question, /suspendez-le/)
})

test('la liste suit la réponse du serveur : remplacement après changement d’état, retrait après suppression', () => {
  const liste = [abonnement('a', 'PERSONAL', 'ACTIVE'), abonnement('b', 'PERSONAL', 'ACTIVE')]
  const suspendu = { ...liste[0], state: 'SUSPENDED' }
  const apres = remplacerAbonnement(liste, suspendu)
  assert.deepEqual(apres.map((abonnement) => [abonnement.id, abonnement.state]), [['a', 'SUSPENDED'], ['b', 'ACTIVE']])
  assert.deepEqual(apres[0].filters, liste[0].filters, 'critères conservés')
  assert.equal(liste[0].state, 'ACTIVE', 'liste d’origine inchangée')
  assert.deepEqual(retirerAbonnement(apres, 'a').map((abonnement) => abonnement.id), ['b'])
})

test('messages d’erreur : droits décidés par le serveur, abonnement disparu, autres statuts', () => {
  assert.match(messageErreurAbonnement(403, 'x'), /administrateurs/)
  assert.match(messageErreurAbonnement(404, 'x'), /n’existe plus/)
  assert.equal(messageErreurAbonnement(401, 'x'), 'Connectez-vous pour gérer vos abonnements.')
  assert.equal(messageErreurAbonnement(500, 'x'), 'Le serveur a répondu 500.')
  assert.equal(messageErreurAbonnement(undefined, 'Serveur ARGOS injoignable'), 'Serveur ARGOS injoignable')
})

test('API : suspension, réactivation et suppression par le client REST commun', () => {
  const api = source('../src/api/veille.ts')
  assert.doesNotMatch(api, /\bfetch\s*\(/)
  assert.match(api, /`\/api\/v1\/subscriptions\/\$\{encodeURIComponent\(id\)\}\/suspension`, \{ methode: 'POST' \}/)
  assert.match(api, /`\/api\/v1\/subscriptions\/\$\{encodeURIComponent\(id\)\}\/reactivation`, \{ methode: 'POST' \}/)
  assert.match(api, /`\/api\/v1\/subscriptions\/\$\{encodeURIComponent\(id\)\}`, \{ methode: 'DELETE' \}/)
})

test('module de contenu pur et écran sans règle de droits ni rendu HTML', () => {
  const contenu = source('../src/contenu/abonnements.ts')
  for (const [declaration, module] of contenu.matchAll(/^import[\s\S]*?from '([^']+)'/gm)) {
    if (module === '../api/statuts-http.ts' || module === './veille-profils.ts') continue
    assert.match(declaration, /^import type /, `module pur : import de types seulement (${declaration})`)
  }
  assert.doesNotMatch(contenu, /\bfetch\s*\(|localStorage|sessionStorage/)
  const vue = source('../src/views/VeilleView.vue')
  assert.doesNotMatch(vue, /v-html/)
  assert.doesNotMatch(vue, /\bconfirm\s*\(/, 'confirmation dans la page, pas de boîte bloquante')
  assert.match(vue, /Confirmer la suppression/)
  assert.match(vue, /suppressionDemandee/)
  assert.match(vue, /libelleEtat\(abonnement\.state\)/)
  assert.match(vue, /actionsAbonnement\(abonnement\)/)
  assert.doesNotMatch(vue, /adminOf|ADMINISTRATEUR|ownerUserId\s*===/, 'aucune règle de droits dans l’IHM')
})
