// Contrat du client de l'IHM (AUD-ARC-12 ; décision du porteur du projet, 2026-11-26) : chaque chemin d'API écrit
// dans src/api existe dans l'instantané OpenAPI src/api/openapi.json, et chaque appel direct à `appelerApi`,
// `getJson` ou `postJson` utilise une méthode que le serveur déclare pour ce chemin. L'instantané est comparé au
// document servi par /q/openapi par le test serveur ContratClientIhmTest : une évolution de l'API qui casserait un
// appel de l'IHM fait échouer l'une des deux suites.
import { test } from 'node:test'
import assert from 'node:assert/strict'
import { readdirSync, readFileSync } from 'node:fs'

const DOSSIER_API = new URL('../src/api/', import.meta.url)
const CONTRAT = JSON.parse(readFileSync(new URL('openapi.json', DOSSIER_API), 'utf8'))
const METHODES_HTTP = ['get', 'post', 'put', 'delete', 'patch']
/** Préfixe commun des chemins de l'API, cité seul pour désigner toute l'API. */
const PREFIXE_API = '/api/v1'

/** Littéraux (chaîne ou gabarit) qui commencent par /api/v1, avec leur position dans le source. */
const LITTERAL_API = /(['`])(\/api\/v1[^'`]*)\1/g
/** Appel direct : fonction, chemin littéral, puis options éventuelles jusqu'à la parenthèse fermante. */
const APPEL_DIRECT = /\b(appelerApi|getJson|postJson)\s*(?:<[^>(]*>)?\(\s*(['`])(\/api\/v1[^'`]*)\2([^)]*)\)/g

/** Chemin du source ramené à un gabarit : requête retirée, expressions `${…}` remplacées par un paramètre. */
function gabarit(chemin) {
  return chemin.replace(/\$\{[^}]*\}/g, '{p}').split('?')[0]
}

/** Expression régulière d'un chemin OpenAPI, chaque `{nom}` acceptant un segment. */
function motifOpenApi(chemin) {
  const echappe = chemin.replace(/[.*+?^$()|[\]\\]/g, '\\$&').replace(/\\?\{[^}]+\\?\}/g, '[^/]+')
  return new RegExp(`^${echappe}$`)
}

const CHEMINS_CONTRAT = Object.entries(CONTRAT.paths).map(([chemin, operations]) => ({
  chemin,
  motif: motifOpenApi(chemin),
  methodes: METHODES_HTTP.filter((methode) => methode in operations),
}))

/** Entrées du contrat dont le chemin correspond au gabarit (un préfixe terminé par « / » vaut pour ses suites). */
function correspondances(chemin) {
  const concret = chemin.replace(/\{p\}/g, 'parametre')
  if (concret === PREFIXE_API || concret.endsWith('/')) {
    return CHEMINS_CONTRAT.filter((entree) => entree.chemin.startsWith(chemin.replace(/\{p\}.*$/, '')))
  }
  return CHEMINS_CONTRAT.filter((entree) => entree.motif.test(concret))
}

/** Source sans commentaires : un chemin cité dans un commentaire n'est pas un appel. */
function sansCommentaires(texte) {
  return texte.replace(/\/\*[\s\S]*?\*\//g, '').replace(/^\s*\/\/.*$/gm, '').replace(/\s\/\/ .*$/gm, '')
}

function sourcesApi() {
  return readdirSync(DOSSIER_API)
    .filter((fichier) => fichier.endsWith('.ts'))
    .map((fichier) => ({ fichier, texte: sansCommentaires(readFileSync(new URL(fichier, DOSSIER_API), 'utf8')) }))
}

function methodeDe(fonction, options) {
  if (fonction === 'getJson') return 'get'
  if (fonction === 'postJson') return 'post'
  const declaree = /methode:\s*'(\w+)'/.exec(options)
  return declaree ? declaree[1].toLowerCase() : 'get'
}

test("l'instantané du contrat déclare des chemins", () => {
  assert.ok(CHEMINS_CONTRAT.length > 20, 'instantané OpenAPI vide ou illisible')
})

test('chaque chemin d’API du client existe dans le contrat', () => {
  const absents = []
  let nombre = 0
  for (const { fichier, texte } of sourcesApi()) {
    for (const [, , chemin] of texte.matchAll(LITTERAL_API)) {
      nombre++
      if (correspondances(gabarit(chemin)).length === 0) absents.push(`${fichier} : ${chemin}`)
    }
  }
  assert.ok(nombre > 20, 'aucun chemin d’API trouvé dans src/api')
  assert.deepEqual(absents, [], 'chemins absents du contrat OpenAPI')
})

test('chaque appel direct utilise une méthode déclarée pour son chemin', () => {
  const ecarts = []
  for (const { fichier, texte } of sourcesApi()) {
    for (const [, fonction, , chemin, options] of texte.matchAll(APPEL_DIRECT)) {
      const methode = methodeDe(fonction, options)
      const entrees = correspondances(gabarit(chemin))
      if (!entrees.some((entree) => entree.methodes.includes(methode))) {
        ecarts.push(`${fichier} : ${methode.toUpperCase()} ${chemin}`)
      }
    }
  }
  assert.deepEqual(ecarts, [], 'méthodes non déclarées par le contrat')
})

test('un chemin inconnu du contrat est détecté', () => {
  assert.equal(correspondances(gabarit('/api/v1/inexistant/${id}')).length, 0)
  assert.ok(correspondances(gabarit('/api/v1/projects/${projet}/inventory/sbom')).length > 0)
})
