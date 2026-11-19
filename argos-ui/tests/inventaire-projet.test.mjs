// Vue d'inventaire d'un projet (INV-01-S3, INV-02, INV-03 ; décision E4 du 2026-11-19) : synthèse de la
// réponse de `GET /api/v1/projects/{projectId}/inventory/summary` (décision G5), validation de l'identifiant et comportement du
// chargement (réponse, projet sans inventaire, erreur puis nouvelle tentative, annulation), avec une
// lecture de l'API simulée : ni navigateur ni réseau.
import { test } from 'node:test'
import assert from 'node:assert/strict'
import {
  chargerInventaireProjet,
  messageIdentifiantProjet,
  resumeInventaire,
} from '../src/contenu/inventaire-projet.ts'

const PROJET = '3f2504e0-4f89-41d3-9a0c-0305e82c3301'
const SANS_PURL = { name: 'outil-interne', group: 'fr.exemple', version: '1.0', reason: 'SANS_PURL' }

function disponible(extra = {}) {
  return {
    projectId: PROJET,
    status: 'AVAILABLE',
    summary: {
      projectId: PROJET,
      origin: 'SBOM',
      sourceDate: '2026-11-17T00:00:00Z',
      ageDays: 2,
      obsolete: false,
      importedAt: '2026-11-18T00:00:00Z',
      componentCount: 2,
      ignoredCount: 1,
      ignoredComponents: [SANS_PURL],
      ...extra,
    },
  }
}

class ErreurApi extends Error {
  constructor(status, motif) {
    super('refus')
    this.status = status
    this.motif = motif
  }
}

test('synthèse : origine, dates, âge, obsolescence et nombre de composants inventoriés', () => {
  assert.deepEqual(resumeInventaire(disponible()), {
    etat: 'DISPONIBLE',
    origine: 'SBOM',
    dateSource: '2026-11-17T00:00:00Z',
    importeLe: '2026-11-18T00:00:00Z',
    ageJours: 2,
    obsolete: false,
    composants: 2,
  })
  const manifestes = resumeInventaire(disponible({ origin: 'MANIFEST', ageDays: null, obsolete: true, componentCount: null }))
  assert.equal(manifestes.origine, 'manifestes')
  assert.equal(manifestes.ageJours, null)
  assert.equal(manifestes.obsolete, true)
  assert.equal(manifestes.composants, 0)
  assert.equal(resumeInventaire(disponible({ origin: 'AUTRE' })).origine, 'non précisée')
  assert.deepEqual(resumeInventaire({ projectId: PROJET, status: 'NO_SBOM', summary: null }), { etat: 'SANS_INVENTAIRE' })
  assert.deepEqual(resumeInventaire(null), { etat: 'SANS_INVENTAIRE' })
})

test('identifiant saisi : vide ou hors UUID refusé avant tout appel', () => {
  assert.equal(messageIdentifiantProjet(''), 'Saisissez l’identifiant du projet.')
  assert.equal(messageIdentifiantProjet('   '), 'Saisissez l’identifiant du projet.')
  assert.equal(messageIdentifiantProjet('../ops'), 'Identifiant de projet invalide : un UUID est attendu.')
  assert.equal(messageIdentifiantProjet(PROJET), '')
})

test('chargement : synthèse et composants ignorés du projet demandé', async () => {
  const appels = []
  const etat = await chargerInventaireProjet(PROJET, async (id, signal) => {
    appels.push({ id, signal })
    return disponible()
  })
  assert.equal(appels.length, 1)
  assert.equal(appels[0].id, PROJET)
  assert.equal(etat.erreur, '')
  assert.equal(etat.resume.etat, 'DISPONIBLE')
  assert.equal(etat.ignores.etat, 'DISPONIBLE')
  assert.deepEqual(etat.ignores.composants, [{ nom: 'outil-interne', groupe: 'fr.exemple', version: '1.0', motif: 'sans PURL' }])
})

test('chargement : projet sans inventaire, sans erreur', async () => {
  const etat = await chargerInventaireProjet(PROJET, async () => ({ projectId: PROJET, status: 'NO_SBOM', summary: null }))
  assert.deepEqual(etat, { resume: { etat: 'SANS_INVENTAIRE' }, ignores: { etat: 'SANS_INVENTAIRE' }, erreur: '' })
})

test('chargement : identifiant invalide, aucun appel', async () => {
  let appele = false
  const etat = await chargerInventaireProjet('pas-un-uuid', async () => {
    appele = true
    return disponible()
  })
  assert.equal(appele, false)
  assert.deepEqual(etat, { resume: null, ignores: null, erreur: 'Identifiant de projet invalide : un UUID est attendu.' })
})

test('chargement : erreur nommée, puis nouvelle tentative qui aboutit', async () => {
  let essais = 0
  const lire = async () => {
    essais += 1
    if (essais === 1) throw new ErreurApi(503)
    return disponible()
  }
  const echec = await chargerInventaireProjet(PROJET, lire)
  assert.deepEqual(echec, { resume: null, ignores: null, erreur: 'Inventaire indisponible pour le moment.' })
  const reprise = await chargerInventaireProjet(PROJET, lire)
  assert.equal(reprise.erreur, '')
  assert.equal(reprise.resume.etat, 'DISPONIBLE')
  assert.equal(essais, 2)
  const horsEspace = await chargerInventaireProjet(PROJET, async () => { throw new ErreurApi(403) })
  assert.equal(horsEspace.erreur, 'Inventaire introuvable ou hors de vos espaces.')
})

test('chargement annulé (projet affiché changé) : aucun état restitué', async () => {
  const annulation = new AbortController()
  const enCours = chargerInventaireProjet(PROJET, async () => {
    annulation.abort()
    return disponible()
  }, annulation.signal)
  assert.equal(await enCours, null, 'réponse arrivée après l’annulation ignorée')
  const rejet = await chargerInventaireProjet(PROJET, async () => { throw new ErreurApi(undefined, 'ANNULE') })
  assert.equal(rejet, null, 'appel annulé par le client REST ignoré')
})
