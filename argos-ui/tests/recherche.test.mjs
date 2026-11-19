// Écran de recherche (RCH-01 à RCH-04) : paramètres de requête, période, vues enregistrées,
// et règles de sécurité de l'écran (aucun fetch hors du client, aucun v-html, liens filtrés).
import { test } from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { route } from './support/routes.mjs'
import {
  PERIODES,
  TAILLE_PAGE,
  annonceResultats,
  corpsNouvelleVue,
  criteresDuFormulaire,
  criteresVides,
  estUuid,
  formulaireDesCriteres,
  formulaireVide,
  libellePeriode,
  messageErreur,
  nombrePages,
  parametresPage,
  parametresRecherche,
  projetsConnus,
} from '../src/api/recherche-criteres.ts'

const P1 = '0b8e6c1a-2f4d-4c3e-9a1b-7d5e3f2a1c00'
const P2 = 'a1b2c3d4-e5f6-4a7b-8c9d-0e1f2a3b4c5d'
const source = (chemin) => readFileSync(new URL(chemin, import.meta.url), 'utf8')

test('un formulaire vide ne produit aucun critère, et une page 0 de 20 éléments', () => {
  const criteres = criteresDuFormulaire(formulaireVide())
  assert.deepEqual(criteres, {})
  assert.ok(criteresVides(criteres))
  assert.equal(parametresRecherche(criteres).toString(), `page=0&taille=${TAILLE_PAGE}`)
})

test('« Oracle » sur un projet et 30 jours : paramètres q, projet, horizon de l’API', () => {
  const f = { ...formulaireVide(), texte: '  Oracle ', projets: [P1], horizonJours: 30 }
  const criteres = criteresDuFormulaire(f)
  assert.deepEqual(criteres, { texte: 'Oracle', projets: [P1], horizonJours: 30 })
  const p = parametresRecherche(criteres, 2, 20)
  assert.equal(p.get('q'), 'Oracle')
  assert.deepEqual(p.getAll('projet'), [P1])
  assert.equal(p.get('horizon'), '30')
  assert.equal(p.get('page'), '2')
  assert.equal(p.get('taille'), '20')
  assert.equal(p.has('type'), false)
  assert.equal(p.has('gravite'), false)
})

test('les filtres répétables sont répétés, rognés et dédoublonnés ; les vides sont omis', () => {
  const f = {
    texte: '',
    projets: [P1, P2, P1, ' '],
    technologies: ' java, npm,, java ,',
    types: ['AVIS_SECURITE', 'ARTICLE'],
    gravite: 'HIGH',
    horizonJours: null,
    profils: ['securite-applicative'],
  }
  const criteres = criteresDuFormulaire(f)
  assert.deepEqual(criteres, {
    projets: [P1, P2],
    technologies: ['java', 'npm'],
    types: ['AVIS_SECURITE', 'ARTICLE'],
    gravite: 'HIGH',
    profils: ['securite-applicative'],
  })
  const p = parametresRecherche(criteres)
  assert.deepEqual(p.getAll('projet'), [P1, P2])
  assert.deepEqual(p.getAll('technologie'), ['java', 'npm'])
  assert.deepEqual(p.getAll('type'), ['AVIS_SECURITE', 'ARTICLE'])
  assert.deepEqual(p.getAll('profil'), ['securite-applicative'])
  assert.equal(p.get('gravite'), 'HIGH')
  assert.equal(p.has('q'), false)
  assert.equal(p.has('horizon'), false)
})

test('les mots sont encodés dans l’URL, jamais concaténés tels quels', () => {
  const p = parametresRecherche({ texte: '"log4j" or spring -test&page=9' })
  assert.equal(p.get('q'), '"log4j" or spring -test&page=9')
  assert.equal(p.getAll('page').length, 1)
  assert.match(p.toString(), /^q=%22log4j%22\+or\+spring\+-test%26page%3D9&page=0&taille=20$/)
})

test('période : « 30 derniers jours » est proposée et correspond à horizon=30', () => {
  const trente = PERIODES.find((p) => p.jours === 30)
  assert.equal(trente?.libelle, '30 derniers jours')
  assert.equal(PERIODES[0].jours, null)
  assert.equal(libellePeriode(30), '30 derniers jours')
  assert.equal(libellePeriode(null), 'Toute période')
  assert.equal(libellePeriode(undefined), 'Toute période')
  assert.equal(libellePeriode(45), '45 derniers jours')
})

test('pagination des vues rappelées et nombre de pages', () => {
  assert.equal(parametresPage(1).toString(), 'page=1&taille=20')
  assert.equal(nombrePages(0, 20), 0)
  assert.equal(nombrePages(20, 20), 1)
  assert.equal(nombrePages(21, 20), 2)
  assert.equal(nombrePages(5, 0), 0)
})

test('annonce des résultats pour les technologies d’assistance', () => {
  assert.equal(annonceResultats(0, 0, 20), 'Aucun résultat.')
  assert.equal(annonceResultats(1, 0, 20), '1 résultat.')
  assert.equal(annonceResultats(12, 0, 20), '12 résultats.')
  assert.equal(annonceResultats(45, 1, 20), '45 résultats, page 2 sur 3.')
})

test('vue enregistrée : corps NouvelleVue et aller-retour des critères', () => {
  const f = { ...formulaireVide(), texte: 'Oracle', projets: [P1], horizonJours: 30, types: ['AVIS_SECURITE'] }
  const criteres = criteresDuFormulaire(f)
  const corps = corpsNouvelleVue('  Oracle 30 jours ', criteres)
  assert.deepEqual(JSON.parse(JSON.stringify(corps)), {
    nom: 'Oracle 30 jours',
    criteres: { texte: 'Oracle', projets: [P1], types: ['AVIS_SECURITE'], horizonJours: 30 },
  })
  // La vue rappelée (VueEnregistree.criteres, attributs nuls omis par le serveur) préremplit le formulaire.
  const rappel = formulaireDesCriteres(corps.criteres)
  assert.deepEqual(criteresDuFormulaire(rappel), criteres)
})

test('une vue aux critères absents ou partiels donne un formulaire complet', () => {
  assert.deepEqual(formulaireDesCriteres(null), formulaireVide())
  const f = formulaireDesCriteres({ technologies: ['Oracle', 'java'], profils: ['expert-metier'], gravite: 'LOW' })
  assert.equal(f.technologies, 'Oracle, java')
  assert.deepEqual(f.profils, ['expert-metier'])
  assert.equal(f.gravite, 'LOW')
  assert.equal(f.horizonJours, null)
})

test('identifiants : seuls des UUID servent à construire une URL de vue ou un filtre projet', () => {
  assert.ok(estUuid(P1))
  assert.ok(estUuid(` ${P1.toUpperCase()} `))
  for (const v of ['', '../alertes', `${P1}/resultats`, '1234', null, undefined, 42]) {
    assert.equal(estUuid(v), false, String(v))
  }
})

test('projets du filtre : connus et cités, sans doublon, triés par nom, UUID seulement', () => {
  const tous = projetsConnus(
    [{ projetId: P2, projet: null }],
    [
      { projetId: P1, projet: 'Zeta' },
      { projetId: P2, projet: 'Alpha' },
      { projetId: 'pas-un-uuid', projet: 'X' },
    ],
  )
  assert.deepEqual(tous, [
    { projetId: P2, projet: 'Alpha' },
    { projetId: P1, projet: 'Zeta' },
  ])
})

test('messages d’erreur : non connecté, projet ou vue introuvable, refus du serveur', () => {
  assert.equal(messageErreur('recherche', 401, 'x'), 'Connectez-vous pour rechercher dans votre veille.')
  assert.equal(messageErreur('recherche', 404, 'Ressource introuvable.'), 'Projet introuvable ou hors de vos espaces.')
  assert.equal(messageErreur('vue', 404, 'Ressource introuvable.'), 'Vue introuvable.')
  assert.equal(messageErreur('recherche', 400, 'Type inconnu.'), 'Type inconnu.')
  assert.equal(messageErreur('enregistrement', 409, 'Nom déjà utilisé.'), 'Nom déjà utilisé.')
  assert.equal(messageErreur('liste', 500, 'boom'), 'Le serveur a répondu 500.')
  assert.equal(messageErreur('vue', 404, 'Message serveur'), 'Vue introuvable.')
  assert.match(messageErreur('suppression', 403, 'x'), /Accès refusé/)
  assert.match(messageErreur('recherche', 429, 'x'), /Trop de demandes/)
  assert.equal(messageErreur('liste', undefined, 'Serveur ARGOS injoignable'), 'Serveur ARGOS injoignable')
})

test('les appels de recherche passent par le point d’appel commun du client REST', () => {
  const recherche = source('../src/api/recherche.ts')
  assert.match(recherche, /import \{ appelerApi \} from '\.\/client'/)
  assert.doesNotMatch(recherche, /\bfetch\s*\(/)
  for (const chemin of ["'/api/v1/vues'", '/api/v1/recherche?', '/resultats?']) {
    assert.ok(recherche.includes(chemin), chemin)
  }
  assert.match(recherche, /methode: 'DELETE'/)
  assert.match(recherche, /methode: 'POST'/)
  const client = source('../src/api/client.ts')
  assert.equal(client.match(/\bfetch\s*\(/g)?.length, 1, 'un seul appel fetch dans client.ts')
})

test('écran de recherche : routes, lien de navigation, aucun v-html, liens sources filtrés, annonces', () => {
  assert.deepEqual(route('recherche'), { path: '/recherche', name: 'recherche', composant: 'RechercheView', publique: false })
  assert.deepEqual(route('vue-recherche'), { path: '/recherche/vues/:id', name: 'vue-recherche', composant: 'RechercheView', publique: false })
  const app = source('../src/App.vue')
  assert.match(app, /<RouterLink to="\/recherche">Recherche<\/RouterLink>/)
  const vue = source('../src/views/RechercheView.vue')
  assert.doesNotMatch(vue, /v-html/)
  assert.doesNotMatch(vue, /:href="element\.lien"/)
  assert.match(vue, /urlSure\(element\.lien\)/)
  assert.match(vue, /rel="noopener noreferrer"/)
  assert.match(vue, /role="search"/)
  assert.match(vue, /aria-live="polite"/)
  assert.doesNotMatch(vue, /localStorage|sessionStorage|document\.cookie/)
  for (const id of ['recherche-texte', 'recherche-periode', 'recherche-gravite', 'recherche-technologies', 'vue-nom']) {
    assert.match(vue, new RegExp(`<label for="${id}">`), `libellé de ${id}`)
    assert.match(vue, new RegExp(`id="${id}"`), `champ ${id}`)
  }
})

test('recherche : délai propre aux recherches, annulation des chargements dépassés, confirmation dans la page', () => {
  const recherche = source('../src/api/recherche.ts')
  assert.match(recherche, /export const DELAI_RECHERCHE_MS = 20_000/)
  assert.equal(recherche.match(/delaiMs: DELAI_RECHERCHE_MS/g)?.length, 2, 'recherche et résultats de vue')
  const client = source('../src/api/client.ts')
  assert.match(client, /'DELAI_DEPASSE'/)
  assert.match(client, /'ANNULE'/)
  const vue = source('../src/views/RechercheView.vue')
  assert.match(vue, /e\.motif === 'DELAI_DEPASSE'/)
  assert.match(vue, /chargementCourant\?\.abort\(\)/)
  assert.match(vue, /if \(signal\.aborted\) return/)
  // Suppression de la vue affichée : résultats et source vidés.
  assert.match(vue, /if \(affichee\) viderResultats\(\)/)
  assert.match(vue, /source\.value = null/)
  assert.doesNotMatch(vue, /window\.(confirm|alert|prompt)/)
  assert.match(vue, /role="alertdialog"/)
})
