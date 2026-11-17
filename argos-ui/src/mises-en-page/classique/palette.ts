// Palette de la mise en page « classique » (maquette « Fil de veille de l'équipe »).
// Toute couleur de la mise en page est un jeton de cette palette ; chaque couple texte/fond employé
// est déclaré dans PAIRES et contrôlé par tests/contrastes.test.mjs (AFF-05).
import type { PaireCouleurs } from '../contraste'

export const COULEURS = {
  fond: '#f4f6f9',
  carte: '#ffffff',
  encre: '#1c2430',
  encreDouce: '#3a4554',
  discret: '#5b6676',
  marine: '#16365c',
  surMarine: '#ffffff',
  surMarineDoux: '#c9d6e6',
  lien: '#1f7a68',
  bordure: '#dde3ea',
  focus: '#16365c',
  puce: '#eef1f5',
  securiteFond: '#fde8e7',
  securiteTexte: '#9b1c14',
  vigilanceFond: '#fff1e0',
  vigilanceTexte: '#8a4b00',
  versionFond: '#e6eef8',
  versionTexte: '#1d4f8f',
  reglementFond: '#efe9f8',
  reglementTexte: '#5b3a91',
  rubriqueFond: '#e6f2ef',
} as const

export const PAIRES: readonly PaireCouleurs[] = [
  { premierPlan: 'encre', fond: 'carte', usage: 'texte', role: 'texte des éléments' },
  { premierPlan: 'encre', fond: 'fond', usage: 'texte', role: 'sommaire des rubriques' },
  { premierPlan: 'encreDouce', fond: 'carte', usage: 'texte', role: 'résumé, action' },
  { premierPlan: 'discret', fond: 'carte', usage: 'texte', role: 'libellés « Concerne », mentions' },
  { premierPlan: 'discret', fond: 'fond', usage: 'texte', role: 'sous-titre du fil' },
  { premierPlan: 'marine', fond: 'carte', usage: 'texte', role: 'titres des éléments' },
  { premierPlan: 'marine', fond: 'fond', usage: 'grand-texte', role: 'titre « Fil de veille »' },
  { premierPlan: 'surMarine', fond: 'marine', usage: 'texte', role: 'bandeau de tête' },
  { premierPlan: 'surMarineDoux', fond: 'marine', usage: 'texte', role: 'date du bandeau' },
  { premierPlan: 'lien', fond: 'carte', usage: 'texte', role: 'lien vers la source' },
  { premierPlan: 'focus', fond: 'carte', usage: 'composant', role: 'contour de focus' },
  { premierPlan: 'surMarine', fond: 'marine', usage: 'composant', role: 'contour de focus sur le bandeau' },
  { premierPlan: 'encre', fond: 'puce', usage: 'texte', role: 'projets concernés' },
  { premierPlan: 'securiteTexte', fond: 'securiteFond', usage: 'texte', role: 'étiquette avis de sécurité, gravité critique' },
  { premierPlan: 'vigilanceTexte', fond: 'vigilanceFond', usage: 'texte', role: 'étiquette fin de support, échéance ; bandeau « exemple »' },
  { premierPlan: 'versionTexte', fond: 'versionFond', usage: 'texte', role: 'étiquette nouvelle version' },
  { premierPlan: 'reglementTexte', fond: 'reglementFond', usage: 'texte', role: 'étiquette fiche réglementaire' },
  { premierPlan: 'encreDouce', fond: 'puce', usage: 'texte', role: 'étiquette actualité' },
  { premierPlan: 'marine', fond: 'rubriqueFond', usage: 'texte', role: 'rubriques de l’élément' },
]
