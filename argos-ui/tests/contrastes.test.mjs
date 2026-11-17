// Contrastes et feuilles de style des mises en page (AFF-05, RGAA 4.1.2 critères 3.2, 3.3 et 13.8 ;
// WCAG 1.4.3, 1.4.11, 2.3.3). Logique pure (src/mises-en-page/contraste.ts) et lecture des sources.
//
// Chaque couleur d'une mise en page est un jeton de sa palette (palette.ts) ; chaque couple texte/fond
// employé y est déclaré et doit atteindre 4,5:1 (texte) ou 3:1 (grand texte, composant, focus).
// Le contraste réellement rendu est en plus mesuré dans un navigateur par e2e/accessibilite/.
import { test } from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { dirname, join, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'
import {
  SEUILS,
  arrondiInferieur,
  composantes,
  controlerPalette,
  luminance,
  rapportContraste,
  variablesCss,
} from '../src/mises-en-page/contraste.ts'
import * as classique from '../src/mises-en-page/classique/palette.ts'
import * as journal from '../src/mises-en-page/journal/palette.ts'
import * as fantaisie from '../src/mises-en-page/fantaisie/palette.ts'
import * as gazette from '../src/mises-en-page/gazette/palette.ts'

const RACINE = resolve(dirname(fileURLToPath(import.meta.url)), '..')
const PALETTES = { classique, journal, fantaisie, gazette }
const COMPOSANTS = {
  classique: 'classique/MiseEnPageClassique.vue',
  journal: 'journal/MiseEnPageJournal.vue',
  fantaisie: 'fantaisie/MiseEnPageFantaisie.vue',
  gazette: 'gazette/MiseEnPageGazette.vue',
}
const style = (id) => {
  const source = readFileSync(join(RACINE, 'src', 'mises-en-page', COMPOSANTS[id]), 'utf8')
  return /<style scoped>([\s\S]*?)<\/style>/.exec(source)[1]
}

test('formule WCAG : bornes et valeurs de référence', () => {
  assert.equal(rapportContraste('#000000', '#ffffff'), 21)
  assert.equal(rapportContraste('#fff', '#fff'), 1)
  assert.equal(rapportContraste('#ffffff', '#000'), rapportContraste('#000', '#ffffff'))
  assert.equal(luminance('#ffffff'), 1)
  assert.equal(arrondiInferieur(rapportContraste('#777777', '#ffffff')), 4.47)
  assert.equal(arrondiInferieur(rapportContraste('#767676', '#ffffff')), 4.54)
  assert.deepEqual(composantes('#1D4f8F'), [29, 79, 143])
  assert.throws(() => composantes('rgb(0,0,0)'), /non reconnue/)
  assert.deepEqual(SEUILS, { texte: 4.5, 'grand-texte': 3, composant: 3 })
})

test('AFF-05-S1 : un couple sous le seuil est signalé, un jeton inconnu est refusé', () => {
  // Couple de la maquette « fantaisie » : sarcelle #1e6f6a sur rose #f7d9d2 (4,47:1).
  const [resultat] = controlerPalette({ a: '#1e6f6a', b: '#f7d9d2' }, [
    { premierPlan: 'a', fond: 'b', usage: 'texte', role: 'essai' },
  ])
  assert.equal(resultat.conforme, false)
  assert.equal(resultat.rapport.toFixed(2), '4.47')
  assert.throws(
    () => controlerPalette({ a: '#000' }, [{ premierPlan: 'a', fond: 'absent', usage: 'texte', role: 'essai' }]),
    /Jeton de couleur inconnu/,
  )
  assert.deepEqual(variablesCss({ encre: '#000' }), { '--encre': '#000' })
})

for (const [id, palette] of Object.entries(PALETTES)) {
  test(`${id} : chaque couple déclaré atteint le seuil AA`, () => {
    const ecarts = controlerPalette(palette.COULEURS, palette.PAIRES)
      .filter((r) => !r.conforme)
      .map((r) => `${r.paire.premierPlan} sur ${r.paire.fond} (${r.paire.role}) : ${arrondiInferieur(r.rapport)}:1 < ${r.seuil}:1`)
    assert.deepEqual(ecarts, [])
  })

  test(`${id} : toute couleur de la feuille de style est un jeton de la palette`, () => {
    const css = style(id)
    const sansCommentaires = css.replace(/\/\*[\s\S]*?\*\//g, '')
    assert.doesNotMatch(sansCommentaires, /#[0-9a-f]{3,8}\b/i, 'couleur hexadécimale en dur')
    assert.doesNotMatch(sansCommentaires, /\b(rgba?|hsla?|hwb|lab|lch|oklab|oklch|color)\(/i, 'couleur fonctionnelle en dur')
    const COULEUR_NOMMEE = /:[^;{}]*?(?<![\w-])(white|black|red|blue|green|gray|grey|yellow|orange|purple|pink|brown)(?![\w-])/i
    assert.doesNotMatch(sansCommentaires, COULEUR_NOMMEE, 'couleur nommée en dur')
    const jetons = new Set(Object.keys(palette.COULEURS))
    const inconnus = [...sansCommentaires.matchAll(/var\(--([\w-]+)\)/g)].map((m) => m[1]).filter((n) => !jetons.has(n))
    assert.deepEqual(inconnus, [], 'variable absente de la palette')
    for (const valeur of Object.values(palette.COULEURS)) {
      assert.match(valeur, /^#([0-9a-f]{3}|[0-9a-f]{6}|[0-9a-f]{8})$/i)
    }
  })

  test(`${id} : chaque jeton de premier plan ou de fond employé en texte figure dans un couple contrôlé`, () => {
    const css = style(id)
    const couverts = new Set([...palette.PAIRES.flatMap((p) => [p.premierPlan, p.fond]), ...(palette.FONDS_SANS_TEXTE ?? [])])
    const employes = [...css.matchAll(/(?:^|[\s;{])(?:color|background(?:-color)?)\s*:\s*var\(--([\w-]+)\)/gm)].map((m) => m[1])
    assert.deepEqual([...new Set(employes)].filter((j) => !couverts.has(j)), [])
  })

  test(`${id} : animations et transitions seulement sans préférence de mouvement réduit (RGAA 13.8)`, () => {
    const css = style(id).replace(/\/\*[\s\S]*?\*\//g, '')
    const horsBloc = css.replace(/@media\s*\(prefers-reduced-motion:\s*no-preference\)\s*\{(?:[^{}]*\{[^{}]*\})*[^{}]*\}/g, '')
    assert.doesNotMatch(horsBloc, /\b(transition|animation)\s*:/)
    assert.doesNotMatch(css, /@keyframes|scroll-behavior\s*:\s*smooth/)
  })

  test(`${id} : contour de focus visible sur les liens`, () => {
    assert.match(style(id), /:deep\(a:focus-visible\)\s*\{[^}]*outline:\s*3px solid var\(--focus\)/)
    assert.ok(palette.PAIRES.some((p) => p.premierPlan === 'focus' && p.usage === 'composant'))
  })
}
