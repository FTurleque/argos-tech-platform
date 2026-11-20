// Traitement des impacts dans l'IHM (COR-09, COR-10-S1, COR-12-S1 ; changement automatiser-chaine-veille, tâche 3.5) :
// état et niveau distincts, historique, actions exactement égales aux transitions renvoyées par le serveur (aucune
// pour un lecteur), motif demandé pour écarter, refus du serveur nommés, textes externes rendus comme texte. API
// simulée : ni navigateur ni réseau.
import { test } from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { dirname, join } from 'node:path'
import { fileURLToPath } from 'node:url'
import {
  cheminImpact,
  cheminImpactsSignal,
  cheminTransitions,
  demandeTransition,
  libelleEtat,
  libelleNiveau,
} from '../src/api/impacts-modele.ts'
import { appliquerTransition, impactAffiche, messageRefusTransition } from '../src/contenu/traitement-impact.ts'

const ICI = dirname(fileURLToPath(import.meta.url))
const IMPACT = '3f2504e0-4f89-41d3-9a0c-0305e82c3301'
const SIGNAL = '9b2d1c1e-1111-4a2b-8c3d-0305e82c3302'

function impact(extra = {}) {
  return {
    id: IMPACT,
    signalId: SIGNAL,
    signalSource: 'OSV',
    signalExternalId: 'GHSA-TEST',
    signalTitle: 'Faille de test',
    projectId: '5d1c1e2f-2222-4a2b-8c3d-0305e82c3303',
    projectName: 'Paie',
    componentPurl: 'pkg:npm/exemple@1.5.0',
    componentVersion: '1.5.0',
    level: 'A_VERIFIER',
    etat: 'OUVERT',
    historique: [],
    transitionsPossibles: ['CONFIRME', 'ECARTE', 'RESOLU'],
    ...extra,
  }
}

class ErreurApi extends Error {
  constructor(status, message) {
    super(message)
    this.status = status
  }
}

test('chemins : impacts d’un signal tous états confondus, impact, transitions ; identifiant non UUID refusé', () => {
  assert.equal(
    cheminImpactsSignal(SIGNAL),
    `/api/v1/signals/${SIGNAL}/impacts?etat=OUVERT&etat=CONFIRME&etat=ECARTE&etat=RESOLU&size=200`,
  )
  assert.equal(cheminImpact(IMPACT), `/api/v1/impacts/${IMPACT}`)
  assert.equal(cheminTransitions(IMPACT), `/api/v1/impacts/${IMPACT}/transitions`)
  assert.equal(cheminImpact('../ops/impacts/integrity'), null)
  assert.equal(cheminTransitions(''), null)
  assert.equal(cheminImpactsSignal(null), null)
})

test('état et niveau : deux axes nommés séparément (COR-09)', () => {
  const vue = impactAffiche(impact({ etat: 'CONFIRME', level: 'A_VERIFIER' }))
  assert.equal(vue.etat, 'confirmé')
  assert.equal(vue.niveau, 'à vérifier')
  assert.equal(libelleEtat('ECARTE'), 'écarté')
  assert.equal(libelleEtat('RESOLU'), 'résolu')
  assert.equal(libelleNiveau('EXACT'), 'exact')
  assert.equal(libelleEtat('INCONNU'), 'INCONNU')
})

test('actions : celles du serveur pour un contributeur, aucune pour un lecteur (COR-10-S1)', () => {
  const contributeur = impactAffiche(impact())
  assert.deepEqual(contributeur.actions.map((action) => action.libelle), ['Confirmer', 'Écarter', 'Marquer résolu'])
  assert.deepEqual(contributeur.actions.filter((action) => action.motifObligatoire).map((action) => action.vers), ['ECARTE'])
  assert.deepEqual(impactAffiche(impact({ transitionsPossibles: [] })).actions, [])
  assert.deepEqual(impactAffiche(impact({ transitionsPossibles: undefined })).actions, [])
  assert.deepEqual(impactAffiche(impact({ etat: 'ECARTE', transitionsPossibles: ['OUVERT'] })).actions.map((action) => action.libelle), [
    'Rouvrir',
  ])
})

test('historique : ordre du serveur, auteur, motif système nommé, commentaire (COR-12-S1)', () => {
  const vue = impactAffiche(
    impact({
      etat: 'RESOLU',
      historique: [
        { de: 'OUVERT', vers: 'CONFIRME', auteur: 'Jules', auteurCompteId: 'x', systeme: false, motif: null,
          commentaire: 'Vu en production', date: '2026-11-19T08:00:00Z' },
        { de: 'CONFIRME', vers: 'RESOLU', auteur: 'SYSTEME', auteurCompteId: null, systeme: true,
          motif: 'COMPOSANT_RETIRE', commentaire: null, date: '2026-11-19T09:00:00Z' },
        { de: 'RESOLU', vers: 'OUVERT', auteur: null, auteurCompteId: 'y', systeme: false, motif: null, commentaire: null,
          date: '2026-11-19T10:00:00Z' },
      ],
    }),
  )
  assert.equal(vue.etat, 'résolu')
  assert.deepEqual(vue.historique.map((ligne) => `${ligne.de}→${ligne.vers} par ${ligne.auteur}`), [
    'ouvert→confirmé par Jules',
    'confirmé→résolu par système',
    'résolu→ouvert par compte retiré',
  ])
  assert.equal(vue.historique[0].commentaire, 'Vu en production')
  assert.equal(vue.historique[1].motif, 'composant retiré de l’inventaire')
})

test('demande : motif obligatoire pour écarter, textes bornés, état inconnu refusé avant tout appel', () => {
  assert.deepEqual(demandeTransition('ECARTE', '  ', ''), { erreur: 'Indiquez le motif pour écarter cet impact.' })
  assert.deepEqual(demandeTransition('ECARTE', ' Code mort ', ''), { corps: { vers: 'ECARTE', motif: 'Code mort' } })
  assert.deepEqual(demandeTransition('CONFIRME', '', ' ok '), { corps: { vers: 'CONFIRME', commentaire: 'ok' } })
  assert.ok('erreur' in demandeTransition('CONFIRME', '', 'x'.repeat(1001)))
  assert.ok('erreur' in demandeTransition('CLOS', '', ''))
})

test('transition : impact renvoyé par le serveur affiché, demande invalide jamais envoyée', async () => {
  const appels = []
  const poster = async (id, corps) => {
    appels.push({ id, corps })
    return impact({ etat: 'CONFIRME', transitionsPossibles: ['ECARTE', 'RESOLU'] })
  }
  const issue = await appliquerTransition(IMPACT, 'CONFIRME', '', 'Confirmé', poster)
  assert.deepEqual(appels, [{ id: IMPACT, corps: { vers: 'CONFIRME', commentaire: 'Confirmé' } }])
  assert.equal(issue.impact.etat, 'confirmé')
  assert.deepEqual(issue.impact.actions.map((action) => action.vers), ['ECARTE', 'RESOLU'])

  const refusee = await appliquerTransition(IMPACT, 'ECARTE', '', '', poster)
  assert.equal(appels.length, 1, 'aucun appel sans motif')
  assert.equal(refusee.erreur, 'Indiquez le motif pour écarter cet impact.')
})

test('refus du serveur : 403, 404, 409 avec les transitions possibles, panne', async () => {
  assert.match(messageRefusTransition(new ErreurApi(403, 'interdit')), /rôle/)
  assert.equal(messageRefusTransition(new ErreurApi(404, 'x')), 'Impact introuvable.')
  const conflit = 'Transition vers CONFIRME impossible depuis l’état ECARTE. Transitions possibles : OUVERT.'
  assert.equal(messageRefusTransition(new ErreurApi(409, conflit)), conflit)
  assert.match(messageRefusTransition(new Error('réseau')), /Réessayez/)
  const issue = await appliquerTransition(IMPACT, 'CONFIRME', '', '', async () => {
    throw new ErreurApi(409, conflit)
  })
  assert.equal(issue.erreur, conflit)
})

test('vue : textes externes interpolés (jamais v-html), actions conditionnées aux transitions du serveur', () => {
  const source = readFileSync(join(ICI, '..', 'src', 'contenu', 'TraitementImpacts.vue'), 'utf8')
  assert.doesNotMatch(source, /v-html|innerHTML/)
  assert.match(source, /v-if="impact\.actions\.length > 0"/)
  assert.match(source, /\{\{ ligne\.commentaire \}\}/)
  const alertes = readFileSync(join(ICI, '..', 'src', 'views', 'AlertesView.vue'), 'utf8')
  assert.match(alertes, /<TraitementImpacts /)
  assert.doesNotMatch(alertes, /v-html/)
})
