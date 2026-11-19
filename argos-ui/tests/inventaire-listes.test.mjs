// Listes de la vue d'inventaire (INV-06 ; décisions F1 et F5 du porteur du projet, 2026-11-19) : chemins des
// appels, choix du projet parmi les projets visibles, page des composants inventoriés, chargements (erreur,
// annulation, page au-delà de la dernière), rendu côté serveur du tableau et de la pagination
// (tests/support/rendu-ssr.mjs, sans navigateur ni réseau) et règles de l'écran.
import { after, before, test } from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import {
  cheminComposants,
  cheminProjets,
  TAILLE_PAGE_COMPOSANTS,
  TAILLE_PAGE_MAX,
} from '../src/api/inventaire-modele.ts'
import {
  chargerComposants,
  chargerProjets,
  libellePage,
  listeProjets,
  messageErreurComposants,
  messageErreurProjets,
  pageComposants,
  syntheseComposants,
  syntheseProjets,
} from '../src/contenu/inventaire-listes.ts'
import { ouvrirRendu } from './support/rendu-ssr.mjs'

const source = (chemin) => readFileSync(new URL(chemin, import.meta.url), 'utf8')
const PROJET = '3f2504e0-4f89-41d3-9a0c-0305e82c3301'
const AUTRE = '9b2c7e10-1d2a-4c3b-8e4f-5a6b7c8d9e0f'
const ESPACE = '11111111-2222-4333-8444-555555555555'

class ErreurApi extends Error {
  constructor(status, motif) {
    super('refus')
    this.status = status
    this.motif = motif
  }
}

function composantApi(i, transitive = true) {
  const nom = `paquet-${String(i).padStart(2, '0')}`
  return { id: `id-${i}`, name: nom, version: `1.0.${i}`, purl: `pkg:npm/${nom}@1.0.${i}`, packageKey: `pkg:npm/${nom}`, transitive }
}

function pageApi(page, size, total, items) {
  return { projectId: PROJET, page, size, total, items }
}

let rendu
before(async () => {
  rendu = await ouvrirRendu()
})
after(async () => {
  await rendu?.fermer()
})

test('chemins : projets en une page de taille maximale ; composants par UUID et page entière seulement', () => {
  assert.equal(cheminProjets(), `/api/v1/projects?page=0&size=${TAILLE_PAGE_MAX}`)
  assert.equal(cheminComposants(PROJET, 0), `/api/v1/projects/${PROJET}/inventory/components?page=0&size=${TAILLE_PAGE_COMPOSANTS}`)
  assert.equal(cheminComposants(` ${PROJET} `, 3, 10), `/api/v1/projects/${PROJET}/inventory/components?page=3&size=10`)
  assert.equal(cheminComposants(PROJET, 0, 5000), `/api/v1/projects/${PROJET}/inventory/components?page=0&size=${TAILLE_PAGE_MAX}`)
  assert.equal(cheminComposants(PROJET, 0, 0), `/api/v1/projects/${PROJET}/inventory/components?page=0&size=1`)
  assert.equal(cheminComposants('../ops/sources', 0), null)
  assert.equal(cheminComposants(PROJET, -1), null)
  assert.equal(cheminComposants(PROJET, 1.5), null)
  assert.equal(cheminComposants(PROJET, '2'), null)
})

test('choix du projet : libellé nom puis espace, ordre du serveur, entrées sans UUID écartées', () => {
  const liste = listeProjets({
    page: 0,
    size: 200,
    total: 3,
    items: [
      { id: PROJET.toUpperCase(), workspaceId: ESPACE, workspaceName: 'Équipe A', name: 'Paie' },
      { id: AUTRE, workspaceId: ESPACE, workspaceName: null, name: '  ' },
      { id: '../x', workspaceId: ESPACE, workspaceName: 'Équipe A', name: 'Piège' },
    ],
  })
  assert.deepEqual(liste.options, [
    { id: PROJET, libelle: 'Paie — Équipe A' },
    { id: AUTRE, libelle: 'projet sans nom' },
  ])
  assert.equal(liste.total, 3)
  assert.equal(liste.tronquee, true)
})

test('choix du projet : synthèses (aucun projet, un, plusieurs, liste limitée) et messages d’erreur', () => {
  assert.equal(syntheseProjets(listeProjets({ items: [], total: 0 })), 'Aucun projet dans vos espaces.')
  assert.equal(syntheseProjets(listeProjets(null)), 'Aucun projet dans vos espaces.')
  const un = listeProjets({ items: [{ id: PROJET, name: 'Paie', workspaceName: 'A' }], total: 1 })
  assert.equal(syntheseProjets(un), '1 projet dans vos espaces.')
  const deux = listeProjets({ items: [{ id: PROJET, name: 'Paie' }, { id: AUTRE, name: 'RH' }], total: 2 })
  assert.equal(syntheseProjets(deux), '2 projets dans vos espaces.')
  const limite = listeProjets({ items: [{ id: PROJET, name: 'Paie' }, { id: AUTRE, name: 'RH' }], total: 250 })
  assert.equal(
    syntheseProjets(limite),
    '250 projets dans vos espaces ; les 2 premiers sont proposés, les autres se désignent par leur identifiant.',
  )
  assert.equal(messageErreurProjets(401), 'Connectez-vous pour consulter vos projets.')
  assert.equal(messageErreurProjets(503), 'Liste des projets indisponible pour le moment.')
})

test('choix du projet : chargement, erreur puis nouvelle tentative, annulation', async () => {
  let essais = 0
  const lire = async () => {
    essais += 1
    if (essais === 1) throw new ErreurApi(503)
    return { items: [{ id: PROJET, name: 'Paie', workspaceName: 'A' }], page: 0, size: 200, total: 1 }
  }
  assert.deepEqual(await chargerProjets(lire), { projets: null, erreur: 'Liste des projets indisponible pour le moment.' })
  const reprise = await chargerProjets(lire)
  assert.equal(reprise.erreur, '')
  assert.deepEqual(reprise.projets.options, [{ id: PROJET, libelle: 'Paie — A' }])
  const annulation = new AbortController()
  assert.equal(await chargerProjets(async () => { annulation.abort(); return { items: [] } }, annulation.signal), null)
  assert.equal(await chargerProjets(async () => { throw new ErreurApi(undefined, 'ANNULE') }), null)
})

test('composants : conversion d’une page, dépendance nommée, total et nombre de pages', () => {
  const p = pageComposants(pageApi(1, 20, 25, [composantApi(21, false), composantApi(22), { ...composantApi(23), transitive: null, version: null, purl: '' }]))
  assert.equal(p.page, 1)
  assert.equal(p.taille, 20)
  assert.equal(p.total, 25)
  assert.equal(p.pages, 2)
  assert.deepEqual(p.composants[0], { cle: 'id-21', nom: 'paquet-21', version: '1.0.21', purl: 'pkg:npm/paquet-21@1.0.21', dependance: 'directe' })
  assert.equal(p.composants[1].dependance, 'transitive')
  assert.equal(p.composants[2].dependance, 'inconnue')
  assert.equal(p.composants[2].version, null)
  assert.equal(p.composants[2].purl, null)
  assert.equal(syntheseComposants(p), '25 composants inventoriés ; composants 21 à 23 affichés.')
  assert.equal(libellePage(p), 'Page 2 sur 2')
  const longue = 'pkg:maven/' + 'x'.repeat(600) + '@1'
  assert.equal(pageComposants(pageApi(0, 20, 1, [{ ...composantApi(1), purl: longue }])).composants[0].purl, longue, 'PURL restitué entier')
})

test('composants : état vide, valeurs incohérentes du serveur et messages d’erreur', () => {
  const vide = pageComposants(pageApi(0, 20, 0, []))
  assert.deepEqual(vide, { composants: [], page: 0, taille: 20, total: 0, pages: 1 })
  assert.equal(syntheseComposants(vide), 'Aucun composant inventorié.')
  assert.equal(libellePage(vide), 'Page 1 sur 1')
  const un = pageComposants(pageApi(0, 20, 1, [composantApi(1)]))
  assert.equal(syntheseComposants(un), '1 composant inventorié ; composants 1 à 1 affichés.')
  const incoherent = pageComposants({ items: [composantApi(1), composantApi(2)], page: -2, size: 0, total: -1 })
  assert.equal(incoherent.page, 0)
  assert.equal(incoherent.taille, 1)
  assert.equal(incoherent.total, 2, 'total jamais inférieur aux composants reçus')
  assert.equal(pageComposants(undefined).total, 0)
  assert.equal(messageErreurComposants(401), 'Connectez-vous pour consulter les composants.')
  assert.equal(messageErreurComposants(404), 'Composants introuvables ou hors de vos espaces.')
  assert.equal(messageErreurComposants(403), 'Composants introuvables ou hors de vos espaces.')
  assert.equal(messageErreurComposants(undefined), 'Composants indisponibles pour le moment.')
})

test('composants : chargement de la page demandée, identifiant invalide sans appel', async () => {
  const appels = []
  const etat = await chargerComposants(PROJET, 1, async (id, page) => {
    appels.push([id, page])
    return pageApi(page, 20, 25, [composantApi(21)])
  })
  assert.deepEqual(appels, [[PROJET, 1]])
  assert.equal(etat.erreur, '')
  assert.equal(etat.page.page, 1)
  let appele = false
  const invalide = await chargerComposants('pas-un-uuid', 0, async () => { appele = true })
  assert.equal(appele, false)
  assert.deepEqual(invalide, { page: null, erreur: 'Identifiant de projet invalide : un UUID est attendu.' })
  const negative = []
  await chargerComposants(PROJET, -4, async (id, page) => { negative.push(page); return pageApi(0, 20, 0, []) })
  assert.deepEqual(negative, [0], 'page invalide ramenée à la première')
})

test('composants : page au-delà de la dernière remplacée par la dernière, en un seul appel de plus', async () => {
  const appels = []
  const etat = await chargerComposants(PROJET, 4, async (id, page) => {
    appels.push(page)
    return page === 4 ? pageApi(4, 20, 25, []) : pageApi(page, 20, 25, [composantApi(21)])
  })
  assert.deepEqual(appels, [4, 1])
  assert.equal(etat.page.page, 1)
})

test('composants : erreur nommée, puis nouvelle tentative ; annulation sans état', async () => {
  let essais = 0
  const lire = async (id, page) => {
    essais += 1
    if (essais === 1) throw new ErreurApi(404)
    return pageApi(page, 20, 1, [composantApi(1)])
  }
  assert.deepEqual(await chargerComposants(PROJET, 0, lire), { page: null, erreur: 'Composants introuvables ou hors de vos espaces.' })
  assert.equal((await chargerComposants(PROJET, 0, lire)).page.total, 1)
  const annulation = new AbortController()
  const enCours = chargerComposants(PROJET, 0, async () => { annulation.abort(); return pageApi(0, 20, 0, []) }, annulation.signal)
  assert.equal(await enCours, null)
  assert.equal(await chargerComposants(PROJET, 0, async () => { throw new ErreurApi(undefined, 'ANNULE') }), null)
})

test('rendu : tableau à en-têtes de colonne, légende avec la page, valeurs échappées, pagination au clavier', async () => {
  const hostile = { ...composantApi(2), name: '<img src=x onerror=alert(1)>', version: null }
  const page = pageComposants(pageApi(0, 2, 5, [composantApi(1, false), hostile]))
  const html = await rendu.rendreComposant('/src/contenu/ComposantsInventories.vue', { page, projet: 'Paie — Équipe A' })
  assert.match(html, /<table[^>]*tabindex="-1"/)
  assert.match(html, /<caption[^>]*>Composants inventoriés du projet Paie — Équipe A, page 1 sur 3<\/caption>/)
  const entetes = [...html.matchAll(/<th scope="col"[^>]*>([^<]*)<\/th>/g)].map((m) => m[1])
  assert.deepEqual(entetes, ['Nom', 'Version', 'PURL', 'Dépendance'])
  assert.equal([...html.matchAll(/data-composant-inventorie/g)].length, 2)
  assert.match(html, /<td[^>]*>paquet-01<\/td><td[^>]*>1\.0\.1<\/td><td[^>]*>pkg:npm\/paquet-01@1\.0\.1<\/td><td[^>]*>directe<\/td>/)
  assert.ok(!html.includes('<img'), 'valeur de la SBOM rendue comme texte')
  assert.match(html, /&lt;img src=x onerror=alert\(1\)&gt;/)
  assert.match(html, /non renseignée<\/td>/)
  assert.match(html, /<nav[^>]*aria-label="Pagination des composants inventoriés"/)
  // Première page : « précédente » reste focalisable mais signalée inactive ; aucun attribut disabled.
  assert.match(html, /<button type="button" class="secondary" aria-disabled="true"[^>]*> Page précédente <\/button>/)
  assert.match(html, /<button type="button" class="secondary"(?![^>]*aria-disabled)[^>]*> Page suivante <\/button>/)
  assert.doesNotMatch(html, /\sdisabled[\s=>]/)
  assert.match(html, /Page 1 sur 3/)
  assert.match(html, /5 composants inventoriés ; composants 1 à 2 affichés\./)
})

test('rendu : dernière page, page unique sans pagination et état vide sans tableau', async () => {
  const derniere = await rendu.rendreComposant('/src/contenu/ComposantsInventories.vue', {
    page: pageComposants(pageApi(2, 2, 5, [composantApi(5)])),
    projet: 'Paie',
  })
  assert.match(derniere, /aria-disabled="true"[^>]*> Page suivante/)
  assert.match(derniere, /<button type="button" class="secondary"(?![^>]*aria-disabled)[^>]*> Page précédente/)
  const unique = await rendu.rendreComposant('/src/contenu/ComposantsInventories.vue', {
    page: pageComposants(pageApi(0, 20, 1, [composantApi(1)])),
    projet: 'Paie',
  })
  assert.ok(!unique.includes('<nav'), 'aucune pagination pour une seule page')
  const vide = await rendu.rendreComposant('/src/contenu/ComposantsInventories.vue', {
    page: pageComposants(pageApi(0, 20, 0, [])),
    projet: 'Paie',
  })
  assert.ok(!vide.includes('<table'))
  assert.match(vide, /Aucun composant inventorié\./)
})

test('sécurité et accessibilité de l’écran : appels par appelerApi, aucun v-html, choix étiqueté, régions live', () => {
  const api = source('../src/api/inventaire.ts')
  assert.match(api, /appelerApi<PageProjetsApi>\(cheminProjets\(\)/)
  assert.match(api, /appelerApi<PageComposantsApi>\(chemin/)
  assert.doesNotMatch(api, /\bfetch\s*\(/)
  const composant = source('../src/contenu/ComposantsInventories.vue')
  const vue = source('../src/views/InventaireView.vue')
  for (const s of [composant, vue]) {
    assert.doesNotMatch(s, /v-html/)
    assert.doesNotMatch(s, /\bfetch\s*\(/)
  }
  assert.match(vue, /<label for="choix-projet">Projet<\/label>/)
  assert.match(vue, /<select\s+id="choix-projet"/)
  assert.match(vue, /<label for="identifiant-projet">Ou identifiant du projet<\/label>/)
  assert.match(vue, /<h2 id="titre-composants-inventories">Composants inventoriés<\/h2>/)
  assert.match(vue, /<output class="sr-only" aria-live="polite">\{\{ annonce \}\}<\/output>/)
  assert.match(vue, /chargerProjets\(fetchProjets, /)
  assert.match(vue, /chargerComposants\(projet, numero, fetchComposantsProjet, signal\)/)
  // Un projet chargé efface les messages de validation des deux formulaires.
  assert.match(vue, /erreurSaisie\.value = ''\r?\n\s*erreurChoix\.value = ''/)
  // Aucun changement de contexte à la sélection (WCAG 3.2.2) : la navigation part du bouton du formulaire.
  assert.doesNotMatch(vue, /@change=/)
})
