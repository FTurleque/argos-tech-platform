// Mention du résumé produit par l'IA dans le détail d'une alerte (changement encadrer-appels-ia, IAG-05).
import { test } from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { MENTION_IA, resumeIaAffiche } from '../src/contenu/resume-ia.ts'

test('un résumé IA est toujours accompagné de la mention « produit par l\'IA »', () => {
  assert.deepEqual(resumeIaAffiche({ resumeIa: 'Faille critique.', mentionResumeIa: "produit par l'IA" }), {
    texte: 'Faille critique.',
    mention: "produit par l'IA",
  })
  assert.deepEqual(resumeIaAffiche({ resumeIa: 'Faille critique.' }), { texte: 'Faille critique.', mention: MENTION_IA })
  assert.deepEqual(resumeIaAffiche({ resumeIa: 'Faille critique.', mentionResumeIa: '  ' }), {
    texte: 'Faille critique.',
    mention: MENTION_IA,
  })
})

test('sans résumé, rien n\'est affiché', () => {
  assert.equal(resumeIaAffiche(null), null)
  assert.equal(resumeIaAffiche({ resumeIa: null }), null)
  assert.equal(resumeIaAffiche({ resumeIa: '   ' }), null)
})

test('la vue d\'une alerte affiche la mention et le texte brut du résumé, jamais du HTML', () => {
  const vue = readFileSync(new URL('../src/views/AlertesView.vue', import.meta.url), 'utf8')
  assert.match(vue, /resumeIaAffiche\(/)
  assert.match(vue, /data-mention="ia"/)
  assert.doesNotMatch(vue, /v-html/)
})
