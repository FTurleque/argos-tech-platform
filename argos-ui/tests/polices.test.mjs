// Polices embarquées des mises en page (ADR-0023 point 5, ADR-0021 point 6, ADR-0022 : CSP du desktop).
//
// Chaque feuille @fontsource importée par une mise en page appartient à un paquet déclaré dans
// package.json, enregistré dans NOTICE avec sa licence OFL-1.1, et dont l'avis figure dans
// public/licences-polices.txt (livré avec l'IHM). Aucune source ne charge une police depuis un
// service externe : les feuilles importées ne référencent que des fichiers locaux du paquet.
import { test } from 'node:test'
import assert from 'node:assert/strict'
import { existsSync, readdirSync, readFileSync, statSync } from 'node:fs'
import { dirname, join, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'

const RACINE = resolve(dirname(fileURLToPath(import.meta.url)), '..')
const SRC = join(RACINE, 'src')
const paquet = JSON.parse(readFileSync(join(RACINE, 'package.json'), 'utf8'))
const notice = readFileSync(join(RACINE, 'NOTICE'), 'utf8')
const avis = readFileSync(join(RACINE, 'public', 'licences-polices.txt'), 'utf8')

function fichiers(dossier) {
  return readdirSync(dossier).flatMap((nom) => {
    const chemin = join(dossier, nom)
    return statSync(chemin).isDirectory() ? fichiers(chemin) : [chemin]
  })
}

const sources = [...fichiers(SRC), join(RACINE, 'index.html')].filter((fichier) => /\.(ts|vue|css|html)$/.test(fichier))
const imports = sources.flatMap((fichier) =>
  [...readFileSync(fichier, 'utf8').matchAll(/import\s+'(@fontsource\/([a-z0-9-]+)\/[^']+)'/g)].map((correspondance) => ({
    fichier: fichier,
    feuille: correspondance[1],
    famille: correspondance[2],
  })),
)
const familles = [...new Set(imports.map((importPolice) => importPolice.famille))]

test('les quatre mises en page importent leurs polices embarquées', () => {
  assert.ok(familles.length >= 12, familles.join(', '))
})

test('chaque police importée est déclarée, enregistrée dans NOTICE en OFL-1.1 et livrée avec son avis', () => {
  for (const famille of familles) {
    const nom = `@fontsource/${famille}`
    const version = paquet.dependencies[nom]
    assert.ok(version, `${nom} absent des dependencies`)
    assert.match(notice, new RegExp(`^\\s+${nom.replace('/', '\\/')} ${version.replace(/^\^/, '')}\\s+OFL-1\\.1\\b`, 'm'), nom)
    assert.match(avis, new RegExp(`^${nom.replace('/', '\\/')} [0-9.]+ \\(OFL-1\\.1\\)`, 'm'), nom)
  }
  assert.match(avis, /SIL OPEN FONT LICENSE Version 1\.1/)
})

test('aucune dépendance @fontsource déclarée sans être utilisée ni enregistrée', () => {
  for (const nom of Object.keys(paquet.dependencies).filter((dependance) => dependance.startsWith('@fontsource/'))) {
    assert.ok(familles.includes(nom.slice('@fontsource/'.length)), `${nom} déclaré mais inutilisé`)
  }
})

test('aucun chargement de police depuis un service externe', () => {
  const EXTERNE = /fonts\.googleapis\.com|fonts\.gstatic\.com|use\.typekit|fonts\.bunny|@import\s+url\(\s*['"]?https?:/i
  for (const fichier of sources) {
    assert.doesNotMatch(readFileSync(fichier, 'utf8'), EXTERNE, fichier)
  }
})

test('les feuilles @font-face importées ne référencent que des fichiers du paquet', (contexte) => {
  const modules = join(RACINE, 'node_modules')
  if (!existsSync(join(modules, '@fontsource'))) {
    contexte.skip('dépendances non installées (npm ci)')
    return
  }
  for (const { feuille } of imports) {
    const css = readFileSync(join(modules, feuille), 'utf8')
    const urls = [...css.matchAll(/url\(([^)]+)\)/g)].map((correspondance) => correspondance[1].replace(/['"]/g, ''))
    assert.ok(urls.length > 0, feuille)
    for (const url of urls) assert.match(url, /^\.\/files\/[\w.-]+\.(woff2|woff)$/, `${feuille} : ${url}`)
    const licence = JSON.parse(readFileSync(join(modules, ...feuille.split('/').slice(0, 2), 'package.json'), 'utf8')).license
    assert.equal(licence, 'OFL-1.1', feuille)
  }
})
