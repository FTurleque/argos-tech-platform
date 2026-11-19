// Statuts HTTP partagés de l'IHM (décision H4 du porteur du projet, 2026-11-19) : valeurs des constantes,
// classements dérivés et absence de statut écrit en dur dans les comparaisons des écrans et modules de src/.
import { test } from 'node:test'
import assert from 'node:assert/strict'
import { readdirSync, readFileSync } from 'node:fs'
import { join } from 'node:path'
import { fileURLToPath } from 'node:url'
import {
  introuvableOuInterdit,
  nonAutorise,
  STATUT_CONFLIT,
  STATUT_INTERDIT,
  STATUT_INTROUVABLE,
  STATUT_NON_AUTHENTIFIE,
  STATUT_REQUETE_INVALIDE,
  STATUT_SANS_CONTENU,
  STATUT_TROP_DE_DEMANDES,
} from '../src/api/statuts-http.ts'

const SRC = fileURLToPath(new URL('../src/', import.meta.url))
const MODULE_PARTAGE = join(SRC, 'api', 'statuts-http.ts')

function fichiersSource(dossier) {
  return readdirSync(dossier, { withFileTypes: true }).flatMap((entree) => {
    const chemin = join(dossier, entree.name)
    if (entree.isDirectory()) return fichiersSource(chemin)
    return /\.(ts|vue)$/.test(entree.name) ? [chemin] : []
  })
}

test('constantes : valeurs de la RFC 9110', () => {
  assert.equal(STATUT_SANS_CONTENU, 204)
  assert.equal(STATUT_REQUETE_INVALIDE, 400)
  assert.equal(STATUT_NON_AUTHENTIFIE, 401)
  assert.equal(STATUT_INTERDIT, 403)
  assert.equal(STATUT_INTROUVABLE, 404)
  assert.equal(STATUT_CONFLIT, 409)
  assert.equal(STATUT_TROP_DE_DEMANDES, 429)
})

test('classements : introuvable ou interdit, non autorisé', () => {
  assert.equal(introuvableOuInterdit(403), true)
  assert.equal(introuvableOuInterdit(404), true)
  assert.equal(introuvableOuInterdit(401), false)
  assert.equal(introuvableOuInterdit(undefined), false)
  assert.equal(nonAutorise(401), true)
  assert.equal(nonAutorise(403), true)
  assert.equal(nonAutorise(404), false)
  assert.equal(nonAutorise(undefined), false)
})

// Formes repérées : égalité ou inégalité (===, !==, ==, !=), `case 404:`, liste de statuts
// (`[401, 403].includes(…)`) et comparaison d'ordre d'une variable dont le nom contient « status » ou
// « statut » (`status >= 400`).
const FORMES_STATUT_EN_DUR = [
  /[!=]==?\s*[1-5]\d\d\b|\b[1-5]\d\d\s*[!=]==?/,
  /\bcase\s+[1-5]\d\d\s*:/,
  /\[\s*[1-5]\d\d\s*(,\s*[1-5]\d\d\s*)+\]/,
  /statu[st]?\w*\s*[<>]=?\s*[1-5]\d\d\b|\b[1-5]\d\d\s*[<>]=?\s*[\w.]*statu/i,
]

test("garde-fou : les formes repérées couvrent égalité, case, liste et comparaison d'ordre", () => {
  const repere = (ligne) => FORMES_STATUT_EN_DUR.some((forme) => forme.test(ligne))
  for (const ligne of ['if (r.status === 404)', 'case 409:', '[401, 403].includes(s)', 'if (status >= 400)',
    'if (500 <= reponse.status)']) {
    assert.equal(repere(ligne), true, ligne)
  }
  for (const ligne of ['if (r.status === STATUT_INTROUVABLE)', 'texte.length > 200', 'const taille = 250']) {
    assert.equal(repere(ligne), false, ligne)
  }
})

test('aucun statut HTTP écrit en dur hors du module partagé', () => {
  for (const fichier of fichiersSource(SRC)) {
    if (fichier === MODULE_PARTAGE) continue
    readFileSync(fichier, 'utf8').split('\n').forEach((ligne, index) => {
      for (const forme of FORMES_STATUT_EN_DUR) {
        assert.doesNotMatch(ligne, forme, `${fichier}:${index + 1}`)
      }
    })
  }
})
