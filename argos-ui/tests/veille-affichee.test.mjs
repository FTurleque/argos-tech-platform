// Transposition de `GET /api/v1/me/veille` vers le modèle des mises en page (AFF-02, PRF-02, décision Q-3,
// ADR-0023) : conversion de forme seulement, chaque élément reçu affiché une fois, dans l'ordre reçu, avec
// toutes ses mentions (validation, garde-fous, « produit par l'IA », paternité de la source, origine).
import { test } from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { elementAffiche, gravite, nature, rubriquesDuSommaire, veilleAffichee } from '../src/contenu/veille-affichee.ts'

const ABO = '7f6e5d4c-3b2a-4190-8f7e-6d5c4b3a2910'
const SECURITE = { profil: 'developpeur', identifiant: 'securite', libelle: 'Sécurité' }
const REGLEMENTATION = { profil: 'expert-metier', identifiant: 'reglementation', libelle: 'Réglementation' }
const CATALOGUE = [
  {
    identifiant: 'developpeur', libelle: 'Développeur', description: '', version: '1.0.0', gardeFous: [],
    rubriques: [{ identifiant: 'securite', libelle: 'Sécurité' }, { identifiant: 'versions', libelle: 'Nouvelles versions' }],
  },
  {
    identifiant: 'expert-metier', libelle: 'Expert métier', description: '', version: '1.0.0', gardeFous: [],
    rubriques: [{ identifiant: 'reglementation', libelle: 'Réglementation' }],
  },
]
const ABONNEMENTS = [
  {
    id: ABO, scope: 'PERSONAL', ownerWorkspaceId: null, ownerUserId: 'u', state: 'ACTIVE', channel: 'IN_APP',
    frequency: 'DAILY', mandatory: false, createdAt: '2026-11-17T00:00:00Z', filters: [{ type: 'TECHNOLOGY', value: 'rust' }],
  },
]

function avis(id, extra = {}) {
  return {
    type: 'AVIS_SECURITE', id, source: 'OSV', identifiant: `GHSA-${id}`, titre: `Avis ${id}`, resume: null,
    gravite: 'HIGH', date: '2026-11-10T00:00:00Z', dateEffet: null, lien: null, profils: ['developpeur'],
    rubriques: [SECURITE], gardeFous: [], mentionValidation: null,
    projets: [{ projetId: 'p1', projet: 'Projet 1', niveau: 'EXACT' }], abonnements: [], mentionIa: null, mentionSource: null,
    ...extra,
  }
}

const FICHE = {
  type: 'FICHE_REGLEMENTAIRE', id: 'f1', source: 'JORF', identifiant: 'JORFTEXT000001', titre: 'Décret relatif à la paie',
  resume: 'Résumé de la fiche.', gravite: null, date: '2026-11-02T00:00:00Z', dateEffet: '2027-01-01',
  lien: 'https://www.legifrance.gouv.fr/jorf/id/JORFTEXT000001', profils: ['expert-metier'], rubriques: [REGLEMENTATION],
  gardeFous: ['Les fiches informent ; elles ne constituent pas un avis juridique.'],
  mentionValidation: "validée par l'expert métier", projets: [], abonnements: [],
  mentionIa: "produit par l'IA", mentionSource: 'Source : DILA, Licence Ouverte 2.0.',
}

const VEILLE = {
  profilsActifs: ['developpeur', 'expert-metier'],
  elements: [
    avis('a', { abonnements: [ABO] }),
    avis('seul-abonnement', { profils: [], rubriques: [], projets: [], abonnements: [ABO], gravite: 'UNKNOWN' }),
    FICHE,
  ],
}

test('chaque élément reçu donne un élément affiché, dans l’ordre reçu, sans retrait ni ajout', () => {
  const v = veilleAffichee(VEILLE, CATALOGUE, ABONNEMENTS, '2026-11-17T08:00:00Z')
  assert.equal(v.exemple, false, 'une veille réelle n’est jamais annoncée comme exemple')
  assert.equal(v.composeeLe, '2026-11-17T08:00:00Z')
  assert.deepEqual(v.elements.map((e) => e.identifiant), ['AVIS_SECURITE:a', 'AVIS_SECURITE:seul-abonnement', 'FICHE_REGLEMENTAIRE:f1'])
  assert.equal(new Set(v.elements.map((e) => e.identifiant)).size, v.elements.length)
})

test('les mentions de la fiche sont toutes transmises : IA, licence de la source, validation, garde-fous', () => {
  const e = elementAffiche(FICHE, CATALOGUE, ABONNEMENTS)
  assert.equal(e.resume, 'Résumé de la fiche.')
  assert.equal(e.mentions.resumeProduitParIA, true)
  assert.equal(e.mentions.attribution, 'Source : DILA, Licence Ouverte 2.0.')
  assert.equal(e.mentions.validation, "validée par l'expert métier")
  assert.deepEqual(e.mentions.gardeFous, FICHE.gardeFous)
  assert.deepEqual(e.mentions.source, { nom: 'JORF', url: FICHE.lien })
  assert.equal(e.mentions.publieLe, FICHE.date)
  assert.deepEqual(e.mentions.origines, ['Profil Expert métier'])
  assert.deepEqual(e.type, { code: 'fiche-reglementaire', libelle: 'Fiche réglementaire' })
  assert.deepEqual(e.rubriques, [REGLEMENTATION])
})

test('mention IA : seulement si le serveur la donne et qu’un résumé est affiché', () => {
  assert.equal(elementAffiche({ ...FICHE, mentionIa: null }).mentions.resumeProduitParIA, false)
  assert.equal(elementAffiche({ ...FICHE, resume: '  ' }).mentions.resumeProduitParIA, false)
  assert.equal(elementAffiche({ ...FICHE, resume: '  ' }).resume, null)
  // Réponse d'un serveur antérieur, sans les champs : aucune mention inventée.
  const ancien = { ...FICHE }
  delete ancien.mentionIa
  delete ancien.mentionSource
  assert.equal(elementAffiche(ancien).mentions.resumeProduitParIA, false)
  assert.equal(elementAffiche(ancien).mentions.attribution, null)
})

test('élément retenu par un abonnement personnel seul : sans rubrique, origine nommée par ses critères', () => {
  const e = elementAffiche(VEILLE.elements[1], CATALOGUE, ABONNEMENTS)
  assert.deepEqual(e.rubriques, [])
  assert.deepEqual(e.mentions.origines, ['Abonnement personnel (technologie : rust)'])
  assert.deepEqual(e.projets, [])
  assert.deepEqual(e.gravite, { code: 'non-classee', libelle: 'unknown' }, 'gravité hors échelle : libellé de la source gardé')
})

test('élément d’un profil et d’un abonnement : origines profil puis abonnement, projets et niveaux', () => {
  const e = elementAffiche(VEILLE.elements[0], CATALOGUE, ABONNEMENTS)
  assert.deepEqual(e.mentions.origines, ['Profil Développeur', 'Abonnement personnel (technologie : rust)'])
  assert.deepEqual(e.projets, [{ nom: 'Projet 1', niveau: { code: 'EXACT', libelle: 'correspondance exacte' } }])
  assert.deepEqual(e.gravite, { code: 'elevee', libelle: 'élevée' })
  assert.deepEqual(e.type, { code: 'avis-securite', libelle: 'Avis de sécurité' })
  assert.equal(e.mentions.source.nom, 'OSV')
  assert.equal(e.mentions.source.url, null)
  assert.equal(e.titre, 'Avis a')
  assert.equal(e.action, null)
})

test('repli de forme : titre absent, projet sans nom, niveau inconnu, type inconnu', () => {
  const e = elementAffiche(avis('x', { titre: null, projets: [{ projetId: 'p9', projet: null, niveau: 'A_VERIFIER' }] }))
  assert.equal(e.titre, 'GHSA-x')
  assert.deepEqual(e.projets, [{ nom: 'p9', niveau: { code: 'A_VERIFIER', libelle: 'a_verifier' } }])
  assert.deepEqual(nature('AUTRE'), { code: 'actualite', libelle: 'AUTRE' })
  assert.equal(gravite(null), null)
  assert.deepEqual(gravite('critical'), { code: 'critique', libelle: 'critique' })
})

test('sommaire : rubriques des profils actifs d’après le catalogue, puis celles des éléments, sans doublon', () => {
  assert.deepEqual(rubriquesDuSommaire(VEILLE, CATALOGUE).map((r) => `${r.profil}/${r.identifiant}`), [
    'developpeur/securite', 'developpeur/versions', 'expert-metier/reglementation',
  ])
  // Sans catalogue : rubriques portées par les éléments, dans l'ordre de première apparition.
  assert.deepEqual(rubriquesDuSommaire(VEILLE).map((r) => r.identifiant), ['securite', 'reglementation'])
  assert.deepEqual(veilleAffichee({ profilsActifs: [], elements: [] }).elements, [])
})

test('module pur : imports de types, sauf les libellés de la couche contenu ; ni réseau ni Vue', () => {
  const source = readFileSync(new URL('../src/contenu/veille-affichee.ts', import.meta.url), 'utf8')
  const imports = [...source.matchAll(/^import[\s\S]*?from '([^']+)'/gm)]
  for (const [declaration, cible] of imports) {
    if (cible === './veille-profils.ts') continue
    assert.match(declaration, /^import type /, declaration)
  }
  assert.doesNotMatch(source, /\bfetch\s*\(|appelerApi|from 'vue'/)
  assert.doesNotMatch(source, /\.(filter|sort)\(/, 'aucun élément filtré ni trié')
})
