// « Ma veille » et choix des profils (PRF-01, PRF-02, décision Q-3) : présentation des éléments restitués
// par le serveur, origine profil ou abonnement personnel, et règles de sécurité des écrans (aucun fetch hors
// du client, aucun v-html, liens filtrés, aucune règle métier dans l'IHM).
import { test } from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { route } from './support/routes.mjs'
import {
  basculerProfil,
  dateLisible,
  libelleAbonnement,
  libelleGravite,
  libelleProfil,
  libelleType,
  messageErreurVeille,
  originesElement,
} from '../src/contenu/veille-profils.ts'

const source = (chemin) => readFileSync(new URL(chemin, import.meta.url), 'utf8')

const ABO = '7f6e5d4c-3b2a-4190-8f7e-6d5c4b3a2910'
const CATALOGUE = [
  { identifiant: 'developpeur', libelle: 'Développeur', description: '', version: '1.0.0', rubriques: [], gardeFous: [] },
  { identifiant: 'expert-metier', libelle: 'Expert métier', description: '', version: '1.0.0', rubriques: [], gardeFous: [] },
]

function element(id, rubriques, profils, abonnements = []) {
  return {
    type: 'AVIS_SECURITE',
    id,
    source: 'OSV',
    identifiant: id,
    titre: `Titre ${id}`,
    resume: null,
    gravite: 'HIGH',
    date: '2026-11-10T00:00:00Z',
    dateEffet: null,
    lien: null,
    profils,
    rubriques,
    gardeFous: [],
    mentionValidation: null,
    projets: [],
    abonnements,
  }
}

const SECURITE = { profil: 'developpeur', identifiant: 'securite', libelle: 'Sécurité' }

test('un profil absent du catalogue est nommé par son identifiant', () => {
  assert.equal(libelleProfil('expert-metier', CATALOGUE), 'Expert métier')
  assert.equal(libelleProfil('inconnu', CATALOGUE), 'inconnu')
})

test('origine : profils puis abonnements personnels, nommés par leurs critères', () => {
  const abonnements = [
    {
      id: ABO, scope: 'PERSONAL', ownerWorkspaceId: null, ownerUserId: 'u', state: 'ACTIVE', channel: 'IN_APP',
      frequency: 'DAILY', mandatory: false, createdAt: '2026-11-17T00:00:00Z',
      filters: [{ type: 'TECHNOLOGY', value: 'rust' }, { type: 'PACKAGE', value: 'pkg:cargo/hyper' }],
    },
  ]
  const commun = element('commun', [SECURITE], ['developpeur'], [ABO, 'autre'])
  assert.deepEqual(originesElement(commun, CATALOGUE, abonnements), [
    'Profil Développeur',
    'Abonnement personnel (technologie : rust, paquet : pkg:cargo/hyper)',
    'Abonnement personnel',
  ])
  assert.deepEqual(originesElement(element('p', [SECURITE], ['developpeur'])), ['Profil developpeur'])
  assert.equal(libelleAbonnement(undefined), 'Abonnement personnel')
})

test('un élément sans champ abonnements (serveur antérieur) n’a que l’origine de ses profils', () => {
  const e = element('a', [SECURITE], ['developpeur'])
  delete e.abonnements
  assert.deepEqual(originesElement(e, CATALOGUE), ['Profil Développeur'])
})

test('libellés de type, de gravité et de date', () => {
  assert.equal(libelleType('AVIS_SECURITE'), 'Avis de sécurité')
  assert.equal(libelleType('FICHE_REGLEMENTAIRE'), 'Fiche réglementaire')
  assert.equal(libelleType('AUTRE'), 'AUTRE')
  assert.equal(libelleGravite('CRITICAL'), 'critique')
  assert.equal(libelleGravite('high'), 'élevée')
  assert.equal(libelleGravite(null), null)
  assert.equal(dateLisible('2027-01-01'), '1 janvier 2027')
  assert.equal(dateLisible('pas une date'), 'pas une date')
  assert.equal(dateLisible(null), null)
})

test('basculer un profil donne l’ensemble complet demandé ; le serveur seul en juge la validité', () => {
  assert.deepEqual(basculerProfil(['developpeur'], 'expert-metier'), ['developpeur', 'expert-metier'])
  assert.deepEqual(basculerProfil(['developpeur', 'expert-metier'], 'developpeur'), ['expert-metier'])
  // Le dernier profil peut être demandé en retrait : le refus AU_MOINS_UN_PROFIL vient du serveur (INV-1).
  assert.deepEqual(basculerProfil(['developpeur'], 'developpeur'), [])
})

test('messages d’erreur : refus du serveur affiché tel quel, autres statuts traduits', () => {
  assert.equal(messageErreurVeille('choix', 400, 'Au moins un profil doit rester actif.'), 'Au moins un profil doit rester actif.')
  assert.equal(messageErreurVeille('veille', 401, 'x'), 'Connectez-vous pour consulter votre veille.')
  assert.equal(messageErreurVeille('choix', 401, 'x'), 'Connectez-vous pour choisir vos profils.')
  assert.match(messageErreurVeille('veille', 403, 'x'), /Accès refusé/)
  assert.equal(messageErreurVeille('veille', 500, 'x'), 'Le serveur a répondu 500.')
  assert.equal(messageErreurVeille('veille', undefined, 'Serveur ARGOS injoignable'), 'Serveur ARGOS injoignable')
})

test('module API de la veille : appels par appelerApi, points d’accès existants seulement', () => {
  const api = source('../src/api/veille.ts')
  assert.match(api, /import \{ appelerApi, type PreferencesEffectives \} from '\.\/client'/)
  assert.doesNotMatch(api, /\bfetch\s*\(/)
  for (const chemin of ["'/api/v1/me/veille'", "'/api/v1/catalogue/profils'", "'/api/v1/preferences'",
    "'/api/v1/preferences/profils'", "'/api/v1/subscriptions'"]) {
    assert.ok(api.includes(chemin), chemin)
  }
  assert.match(api, /methode: 'PUT'/)
  const contenu = source('../src/contenu/veille-profils.ts')
  const imports = [...contenu.matchAll(/^import[\s\S]*?from '([^']+)'/gm)]
  assert.ok(imports.length > 0)
  for (const [declaration, module] of imports) {
    // Seule valeur importée : les statuts HTTP partagés, module pur sans appel réseau (H4).
    if (module === '../api/statuts-http.ts') continue
    assert.match(declaration, /^import type /, `module pur : import de types seulement (${declaration})`)
  }
})

test('écrans : routes, lien de navigation, aucun v-html, « Ma veille » dans la mise en page choisie', () => {
  assert.deepEqual(route('veille'), { path: '/veille', name: 'veille', composant: 'VeilleView', publique: false })
  assert.deepEqual(route('profils-veille'), {
    path: '/veille/profils',
    name: 'profils-veille',
    composant: 'ProfilsVeilleView',
    publique: false,
  })
  assert.match(source('../src/App.vue'), /<RouterLink to="\/veille">Ma veille<\/RouterLink>/)
  for (const fichier of ['../src/views/VeilleView.vue', '../src/views/ProfilsVeilleView.vue']) {
    const vue = source(fichier)
    assert.doesNotMatch(vue, /v-html/, fichier)
    assert.doesNotMatch(vue, /\bfetch\s*\(/, fichier)
    assert.doesNotMatch(vue, /localStorage|sessionStorage|document\.cookie/, fichier)
  }
  // « Ma veille » passe par le sélecteur central des mises en page ; les liens de source sont rendus par
  // MentionsObligatoires (urlSure, rel="noopener noreferrer"), vérifié par tests/mises-en-page.test.mjs.
  const veille = source('../src/views/VeilleView.vue')
  assert.match(veille, /import MiseEnPage from '\.\.\/mises-en-page\/MiseEnPage\.vue'/)
  assert.match(veille, /<MiseEnPage\s/)
  assert.match(veille, /veilleAffichee\(/)
  assert.doesNotMatch(veille, /:href=/)
  assert.match(veille, /role="alert"/)
  const profils = source('../src/views/ProfilsVeilleView.vue')
  assert.match(profils, /:aria-pressed=/)
  assert.match(profils, /aria-live="polite"/)
  assert.doesNotMatch(profils, /length\s*===\s*1/, 'aucune règle « au moins un profil » dans l’IHM')
})
