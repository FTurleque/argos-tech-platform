// Liens des mentions obligatoires : seules les URL http et https sont cliquables.
import { test } from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { urlSure } from '../src/contenu/liens.ts'

test('une URL http ou https est acceptée', () => {
  assert.equal(urlSure('https://osv.dev/vulnerability/GHSA-jfh8-c2jp-5v3q'), 'https://osv.dev/vulnerability/GHSA-jfh8-c2jp-5v3q')
  assert.equal(urlSure(' http://www.cert.ssi.gouv.fr/ '), 'http://www.cert.ssi.gouv.fr/')
})

test('une URL javascript, data, vbscript, file ou relative est refusée', () => {
  for (const url of [
    'javascript:alert(1)',
    'JavaScript:alert(1)',
    ' javascript:alert(1)',
    'java\tscript:alert(1)',
    'data:text/html,<script>alert(1)</script>',
    'vbscript:msgbox(1)',
    'file:///etc/passwd',
    '/avis/1',
    '//exemple.org/avis',
    '',
    null,
    undefined,
  ]) {
    assert.equal(urlSure(url), null, String(url))
  }
})

test('MentionsObligatoires ne lie jamais une URL de source sans la filtrer', () => {
  const composant = readFileSync(new URL('../src/contenu/MentionsObligatoires.vue', import.meta.url), 'utf8')
  assert.doesNotMatch(composant, /:href="mentions\.source\.url"/)
  assert.match(composant, /urlSure\(/)
})
