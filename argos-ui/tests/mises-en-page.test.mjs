// Rendu des quatre mises en page sur un même jeu de données (AFF-02) et contrôles d'accessibilité
// scriptés sur le HTML produit (AFF-05, RGAA 4.1.2 : thèmes 1 images, 6 liens, 8 éléments
// obligatoires, 9 structuration). Le HTML est produit par vue/server-renderer (tests/support/rendu-ssr.mjs),
// sans navigateur ni réseau. Les contrôles qui exigent un navigateur (contraste rendu, ordre de
// tabulation, largeur d'un téléphone, mouvement réduit) sont dans e2e/accessibilite/.
import { after, before, test } from 'node:test'
import assert from 'node:assert/strict'
import { veilleAffichee } from '../src/contenu/veille-affichee.ts'
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
  return [...page.matchAll(/<article\b[^>]*\bdata-element\b[^>]*>[\s\S]*?<\/article>/g)].map((correspondance) => correspondance[0])
}

/** Faits restitués par un article, lus par leurs marqueurs `data-champ` et `data-mention`. */
function faits(article) {
  const champs = {}
  for (const correspondance of article.matchAll(/data-(champ|mention)="([\w-]+)"[^>]*>([\s\S]*?)<\/(span|li|p|dd|h[1-6])>/g)) {
    const cle = `${correspondance[1]}:${correspondance[2]}`
    ;(champs[cle] ??= []).push(texte(correspondance[3]))
  }
  champs.liens = [...article.matchAll(/href="([^"]*)"/g)].map((correspondance) => correspondance[1])
  return champs
}

/** Faits attendus d'un élément du modèle, indépendants de toute mise en page. */
function faitsAttendus(element) {
  const attendus = {
    'champ:type': [element.type.libelle],
    'champ:titre': [element.titre],
    'mention:source': [
      element.mentions.source.nom + (/^https?:\/\//i.test(element.mentions.source.url ?? '') ? ' (nouvelle fenêtre)' : ''),
    ],
  }
  if (element.rubriques.length) attendus['champ:rubrique'] = element.rubriques.map((rubrique) => rubrique.libelle)
  if (element.gravite) attendus['champ:gravite'] = [element.gravite.libelle]
  if (element.resume) attendus['champ:resume'] = [element.resume]
  if (element.action) attendus['champ:action'] = [element.action]
  if (element.projets.length) attendus['champ:projet'] = element.projets.map((projet) => projet.nom)
  else attendus['champ:sans-projet'] = ['aucun projet visible']
  const niveaux = element.projets.filter((projet) => projet.niveau).map((projet) => projet.niveau.libelle)
  if (niveaux.length) attendus['champ:niveau'] = niveaux
  if (element.mentions.niveauConfiance) attendus['mention:confiance'] = [element.mentions.niveauConfiance.libelle]
  if (element.mentions.resumeProduitParIA) attendus['mention:ia'] = ["produit par l'IA, à vérifier à la source"]
  if (element.mentions.validation) attendus['mention:validation'] = [element.mentions.validation]
  if (element.mentions.origines.length) attendus['mention:origine'] = [element.mentions.origines.join(' · ')]
  if (element.mentions.attribution) attendus['mention:attribution'] = [element.mentions.attribution]
  if (element.mentions.gardeFous.length) attendus['mention:garde-fou'] = [...element.mentions.gardeFous]
  return attendus
}

test('AFF-02 : chaque mise en page restitue tous les éléments, une fois chacun, dans l’ordre reçu', () => {
  const titres = rendu.veilleApercu.elements.map((element) => element.titre)
  for (const id of IDENTIFIANTS) {
    const lus = articles(html[id]).map((article) => faits(article)['champ:titre']?.[0])
    assert.deepEqual(lus, titres, id)
  }
})

test('AFF-02 : mêmes faits, mêmes liens et mêmes mentions dans les quatre mises en page', () => {
  const reference = articles(html.classique).map(faits)
  rendu.veilleApercu.elements.forEach((element, position) => {
    const attendus = faitsAttendus(element)
    for (const [cle, valeurs] of Object.entries(attendus)) {
      assert.deepEqual(reference[position][cle], valeurs, `classique, élément ${element.identifiant}, ${cle}`)
    }
  })
  for (const id of IDENTIFIANTS.slice(1)) {
    assert.deepEqual(articles(html[id]).map(faits), reference, `${id} diffère de classique`)
  }
})

test('AFF-02 : la veille réelle de /me/veille, transposée, garde ses faits et ses mentions dans les quatre mises en page', async () => {
  const fiche = {
    type: 'FICHE_REGLEMENTAIRE', id: 'f1', source: 'JORF', identifiant: 'JORFTEXT000001', titre: 'Décret <b>relatif</b> à la paie',
    resume: 'Résumé de la fiche.', gravite: null, date: '2026-11-02T00:00:00Z', dateEffet: '2027-01-01',
    lien: 'javascript:alert(1)', profils: ['expert-metier'],
    rubriques: [{ profil: 'expert-metier', identifiant: 'reglementation', libelle: 'Réglementation' }],
    gardeFous: ['Les fiches informent ; elles ne constituent pas un avis juridique.'],
    mentionValidation: "validée par l'expert métier", projets: [], abonnements: [],
    mentionIa: "produit par l'IA", mentionSource: 'Source : DILA, Licence Ouverte 2.0.',
  }
  const avis = {
    type: 'AVIS_SECURITE', id: 'a1', source: 'OSV', identifiant: 'GHSA-1', titre: 'Avis retenu par un abonnement',
    resume: null, gravite: 'CRITICAL', date: '2026-11-10T00:00:00Z', dateEffet: null, lien: 'https://osv.dev/GHSA-1',
    profils: [], rubriques: [], gardeFous: [], mentionValidation: null,
    projets: [{ projetId: 'p1', projet: 'Paie', niveau: 'EXACT' }, { projetId: 'p2', projet: 'Paie', niveau: 'PROBABLE' }],
    abonnements: ['abo'], mentionIa: null, mentionSource: null,
  }
  const veille = veilleAffichee({ profilsActifs: ['expert-metier'], items: [fiche, avis], page: 0, size: 50, total: 2 })
  const pages = {}
  for (const id of IDENTIFIANTS) pages[id] = await rendu.rendre(id, veille)
  const reference = articles(pages.classique).map(faits)
  veille.elements.forEach((element, position) => {
    for (const [cle, valeurs] of Object.entries(faitsAttendus(element))) {
      assert.deepEqual(reference[position][cle], valeurs, `classique, élément ${element.identifiant}, ${cle}`)
    }
  })
  assert.deepEqual(reference[0]['mention:attribution'], ['Source : DILA, Licence Ouverte 2.0.'])
  assert.deepEqual(reference[0]['mention:ia'], ["produit par l'IA, à vérifier à la source"])
  assert.deepEqual(reference[1]['mention:origine'], ['Abonnement personnel'])
  assert.deepEqual(reference[1]['champ:projet'], ['Paie', 'Paie'], 'deux projets de même nom restent affichés')
  for (const id of IDENTIFIANTS) {
    assert.deepEqual(articles(pages[id]).map(faits), reference, `${id} diffère de classique`)
    assert.doesNotMatch(pages[id], /data-exemple/, id)
    assert.doesNotMatch(pages[id], /<b>relatif/, `${id} : titre externe échappé`)
    assert.doesNotMatch(pages[id], /href="javascript:/, `${id} : lien non http refusé`)
  }
})

test('AFF-02 : la mention « résumé produit par l’IA » figure sur chaque résumé IA, et seulement sur eux', () => {
  const attendu = rendu.veilleApercu.elements.filter((element) => element.mentions.resumeProduitParIA).length
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
    for (const correspondance of html[id].matchAll(/<a\b[^>]*>([\s\S]*?)<\/a>/g)) {
      assert.ok(texte(correspondance[1]).length > 0, `${id} : lien sans intitulé`)
    }
    assert.doesNotMatch(html[id], /<button\b/, `${id} : une mise en page ne porte aucune commande`)
  }
})

test('RGAA 9.1 : hiérarchie de titres sans saut, à partir du niveau demandé', async () => {
  for (const id of IDENTIFIANTS) {
    for (const niveau of [1, 2, 3]) {
      const page = niveau === 2 ? html[id] : await rendu.rendre(id, rendu.veilleApercu, niveau)
      const niveaux = [...page.matchAll(/<h([1-6])\b/g)].map((correspondance) => Number(correspondance[1]))
      assert.equal(niveaux[0], niveau, `${id} : premier titre`)
      assert.ok(niveaux.every((niveauTitre) => niveauTitre >= niveau), `${id} : titre au-dessus du niveau demandé`)
      niveaux.forEach((niveauTitre, position) => {
        if (position > 0) {
          assert.ok(niveauTitre <= niveaux[position - 1] + 1, `${id} : saut de h${niveaux[position - 1]} à h${niveauTitre}`)
        }
      })
      const titresElements = articles(page).map((article) => Number(/<h([1-6])\b/.exec(article)[1]))
      assert.ok(titresElements.every((niveauTitre) => niveauTitre === niveau + 1), `${id} : titres des éléments au niveau ${niveau + 1}`)
    }
  }
})

test('RGAA 9.2, 9.3 : région nommée par le titre principal, articles nommés par leur titre, listes structurées', () => {
  for (const id of IDENTIFIANTS) {
    const page = html[id]
    const ids = new Set([...page.matchAll(/<h[1-6]\b[^>]*\bid="([^"]+)"/g)].map((correspondance) => correspondance[1]))
    const racine = /^<section\b[^>]*aria-labelledby="([^"]+)"/.exec(page)
    assert.ok(racine && ids.has(racine[1]), `${id} : la racine est une région nommée par un titre`)
    for (const article of articles(page)) {
      const nom = /aria-labelledby="([^"]+)"/.exec(article)
      assert.ok(nom && ids.has(nom[1]), `${id} : article sans nom accessible`)
    }
    const identifiants = [...page.matchAll(/\bid="([^"]+)"/g)].map((correspondance) => correspondance[1])
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
