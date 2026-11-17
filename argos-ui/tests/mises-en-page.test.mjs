// Rendu des quatre mises en page sur un même jeu de données (AFF-02) et contrôles d'accessibilité
// scriptés sur le HTML produit (AFF-05, RGAA 4.1.2 : thèmes 1 images, 6 liens, 8 éléments
// obligatoires, 9 structuration). Le HTML est produit par vue/server-renderer (tests/support/rendu-ssr.mjs),
// sans navigateur ni réseau. Les contrôles qui exigent un navigateur (contraste rendu, ordre de
// tabulation, largeur d'un téléphone, mouvement réduit) sont dans e2e/accessibilite/.
import { after, before, test } from 'node:test'
import assert from 'node:assert/strict'
import { ouvrirRendu } from './support/rendu-ssr.mjs'

const IDENTIFIANTS = ['classique', 'journal', 'fantaisie', 'gazette']
let rendu
const html = {}

before(async () => {
  rendu = await ouvrirRendu()
  for (const id of IDENTIFIANTS) html[id] = await rendu.rendre(id)
})
after(async () => {
  await rendu?.fermer()
})

const texte = (fragment) =>
  fragment
    .replace(/<!--[\s\S]*?-->/g, '')
    .replace(/<[^>]+>/g, '')
    .replace(/&#39;/g, "'")
    .replace(/&quot;/g, '"')
    .replace(/&lt;/g, '<')
    .replace(/&gt;/g, '>')
    .replace(/&amp;/g, '&')
    .replace(/\s+/g, ' ')
    .trim()

/** Fragments `<article … data-element …>…</article>` (les articles ne s'imbriquent pas). */
function articles(page) {
  return [...page.matchAll(/<article\b[^>]*\bdata-element\b[^>]*>[\s\S]*?<\/article>/g)].map((m) => m[0])
}

/** Faits restitués par un article, lus par leurs marqueurs `data-champ` et `data-mention`. */
function faits(article) {
  const champs = {}
  for (const m of article.matchAll(/data-(champ|mention)="([\w-]+)"[^>]*>([\s\S]*?)<\/(span|li|p|dd|h[1-6])>/g)) {
    const cle = `${m[1]}:${m[2]}`
    ;(champs[cle] ??= []).push(texte(m[3]))
  }
  champs.liens = [...article.matchAll(/href="([^"]*)"/g)].map((m) => m[1])
  return champs
}

/** Faits attendus d'un élément du modèle, indépendants de toute mise en page. */
function faitsAttendus(e) {
  const attendus = {
    'champ:type': [e.type.libelle],
    'champ:titre': [e.titre],
    'champ:rubrique': e.rubriques.map((r) => r.libelle),
    'mention:source': [e.mentions.source.nom + (e.mentions.source.url ? ' (nouvelle fenêtre)' : '')],
  }
  if (e.gravite) attendus['champ:gravite'] = [e.gravite.libelle]
  if (e.resume) attendus['champ:resume'] = [e.resume]
  if (e.action) attendus['champ:action'] = [e.action]
  if (e.projets.length) attendus['champ:projet'] = e.projets.map((p) => p.nom)
  else attendus['champ:sans-projet'] = ['aucun projet visible']
  const niveaux = e.projets.filter((p) => p.niveau).map((p) => p.niveau.libelle)
  if (niveaux.length) attendus['champ:niveau'] = niveaux
  if (e.mentions.niveauConfiance) attendus['mention:confiance'] = [e.mentions.niveauConfiance.libelle]
  if (e.mentions.resumeProduitParIA) attendus['mention:ia'] = ["produit par l'IA, à vérifier à la source"]
  return attendus
}

test('AFF-02 : chaque mise en page restitue tous les éléments, une fois chacun, dans l’ordre reçu', () => {
  const titres = rendu.veilleApercu.elements.map((e) => e.titre)
  for (const id of IDENTIFIANTS) {
    const lus = articles(html[id]).map((a) => faits(a)['champ:titre']?.[0])
    assert.deepEqual(lus, titres, id)
  }
})

test('AFF-02 : mêmes faits, mêmes liens et mêmes mentions dans les quatre mises en page', () => {
  const reference = articles(html.classique).map(faits)
  rendu.veilleApercu.elements.forEach((e, i) => {
    const attendus = faitsAttendus(e)
    for (const [cle, valeurs] of Object.entries(attendus)) {
      assert.deepEqual(reference[i][cle], valeurs, `classique, élément ${e.identifiant}, ${cle}`)
    }
  })
  for (const id of IDENTIFIANTS.slice(1)) {
    assert.deepEqual(articles(html[id]).map(faits), reference, `${id} diffère de classique`)
  }
})

test('AFF-02 : la mention « résumé produit par l’IA » figure sur chaque résumé IA, et seulement sur eux', () => {
  const attendu = rendu.veilleApercu.elements.filter((e) => e.mentions.resumeProduitParIA).length
  assert.ok(attendu > 0)
  for (const id of IDENTIFIANTS) {
    assert.equal(html[id].match(/data-mention="ia"/g)?.length ?? 0, attendu, id)
  }
})

test('le jeu d’exemple est annoncé comme tel dans chaque mise en page, et un jeu réel ne l’est pas', async () => {
  const reelle = { ...rendu.veilleApercu, exemple: false }
  for (const id of IDENTIFIANTS) {
    assert.match(html[id], /data-exemple[^>]*>Données d’exemple/, id)
    assert.doesNotMatch(await rendu.rendre(id, reelle), /data-exemple/, id)
  }
})

test('liens : seules les sources http(s) sont cliquables, en nouvelle fenêtre annoncée, avec rel="noopener noreferrer"', async () => {
  const hostile = {
    ...rendu.veilleApercu,
    elements: [
      {
        ...rendu.veilleApercu.elements[0],
        titre: '<script>alert(1)</script>',
        mentions: { ...rendu.veilleApercu.elements[0].mentions, source: { nom: 'piège', url: 'javascript:alert(1)' } },
      },
    ],
  }
  for (const id of IDENTIFIANTS) {
    const page = html[id]
    for (const lien of page.matchAll(/<a\b[^>]*>/g)) {
      assert.match(lien[0], /href="https?:\/\//, `${id} : ${lien[0]}`)
      assert.match(lien[0], /rel="noopener noreferrer"/, `${id} : ${lien[0]}`)
    }
    const piege = await rendu.rendre(id, hostile)
    assert.doesNotMatch(piege, /javascript:/, id)
    assert.doesNotMatch(piege, /<script>/, id)
    assert.match(piege, /&lt;script&gt;alert\(1\)&lt;\/script&gt;/, id)
  }
})

test('RGAA 6.1, 6.2 : chaque lien a un intitulé non vide', () => {
  for (const id of IDENTIFIANTS) {
    for (const m of html[id].matchAll(/<a\b[^>]*>([\s\S]*?)<\/a>/g)) {
      assert.ok(texte(m[1]).length > 0, `${id} : lien sans intitulé`)
    }
    assert.doesNotMatch(html[id], /<button\b/, `${id} : une mise en page ne porte aucune commande`)
  }
})

test('RGAA 9.1 : hiérarchie de titres sans saut, à partir du niveau demandé', async () => {
  for (const id of IDENTIFIANTS) {
    for (const niveau of [1, 2, 3]) {
      const page = niveau === 2 ? html[id] : await rendu.rendre(id, rendu.veilleApercu, niveau)
      const niveaux = [...page.matchAll(/<h([1-6])\b/g)].map((m) => Number(m[1]))
      assert.equal(niveaux[0], niveau, `${id} : premier titre`)
      assert.ok(niveaux.every((n) => n >= niveau), `${id} : titre au-dessus du niveau demandé`)
      niveaux.forEach((n, i) => {
        if (i > 0) assert.ok(n <= niveaux[i - 1] + 1, `${id} : saut de h${niveaux[i - 1]} à h${n}`)
      })
      const titresElements = articles(page).map((a) => Number(/<h([1-6])\b/.exec(a)[1]))
      assert.ok(titresElements.every((n) => n === niveau + 1), `${id} : titres des éléments au niveau ${niveau + 1}`)
    }
  }
})

test('RGAA 9.2, 9.3 : région nommée par le titre principal, articles nommés par leur titre, listes structurées', () => {
  for (const id of IDENTIFIANTS) {
    const page = html[id]
    const ids = new Set([...page.matchAll(/<h[1-6]\b[^>]*\bid="([^"]+)"/g)].map((m) => m[1]))
    const racine = /^<section\b[^>]*aria-labelledby="([^"]+)"/.exec(page)
    assert.ok(racine && ids.has(racine[1]), `${id} : la racine est une région nommée par un titre`)
    for (const a of articles(page)) {
      const nom = /aria-labelledby="([^"]+)"/.exec(a)
      assert.ok(nom && ids.has(nom[1]), `${id} : article sans nom accessible`)
    }
    const identifiants = [...page.matchAll(/\bid="([^"]+)"/g)].map((m) => m[1])
    assert.equal(new Set(identifiants).size, identifiants.length, `${id} : identifiants en double`)
    // Les éléments sont les items d'une liste ; aucune balise de liste n'a d'autre enfant que <li>.
    assert.equal(page.match(/<li\b[^>]*>(?:<!--\[-->)?<article\b/g)?.length, rendu.veilleApercu.elements.length, id)
    for (const liste of page.matchAll(/<(ul|ol)\b[^>]*>((?:<!--[\s\S]*?-->)*)<(\w+)/g)) {
      assert.equal(liste[3], 'li', `${id} : <${liste[1]}> contient <${liste[3]}>`)
    }
    assert.ok(page.match(/role="list"/g).length >= 2, `${id} : listes sans puce restituées comme listes`)
  }
})

test('RGAA 1.1, 1.2 : images et dessins décoratifs ignorés des technologies d’assistance', () => {
  for (const id of IDENTIFIANTS) {
    for (const svg of html[id].matchAll(/<svg\b[^>]*>/g)) {
      const decoratif = /aria-hidden="true"/.test(svg[0])
      const informatif = /role="img"/.test(svg[0]) && /aria-label="[^"]+"/.test(svg[0])
      assert.ok(decoratif || informatif, `${id} : ${svg[0]}`)
      if (decoratif) assert.match(svg[0], /focusable="false"/, `${id} : ${svg[0]}`)
    }
    for (const img of html[id].matchAll(/<img\b[^>]*>/g)) {
      assert.match(img[0], /\balt="/, `${id} : ${img[0]}`)
    }
    assert.doesNotMatch(html[id], /tabindex="[1-9]/, `${id} : ordre de tabulation forcé`)
  }
})

test('veille vide : aucune erreur, aucun élément, sommaire à zéro', async () => {
  const vide = { exemple: false, composeeLe: null, rubriques: [], elements: [] }
  for (const id of IDENTIFIANTS) {
    const page = await rendu.rendre(id, vide)
    assert.equal(articles(page).length, 0, id)
    assert.match(page, /date non communiquée/, id)
  }
})

test('sélecteur central : la mise en page demandée, ou « classique » pour un identifiant inconnu', async () => {
  for (const id of IDENTIFIANTS) {
    assert.match(await rendu.rendreSelecteur(id), new RegExp(`data-mise-en-page="${id}"`), id)
  }
  for (const inconnu of ['inconnue', '', 'constructor', '__proto__']) {
    assert.match(await rendu.rendreSelecteur(inconnu), /data-mise-en-page="classique"/, inconnu)
  }
})
