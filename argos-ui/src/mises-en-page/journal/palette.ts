// Palette de la mise en page « journal » (maquettes « La une du jour » et « L'article d'alerte »).
// Toute couleur de la mise en page est un jeton de cette palette ; chaque couple texte/fond employé
// est déclaré dans PAIRES et contrôlé par tests/contrastes.test.mjs (AFF-05).
import type { PaireCouleurs } from '../contraste'

export const COULEURS = {
  table: '#e9e2d3',
  papier: '#f4efe4',
  encart: '#ebe4d4',
  encre: '#1a1a1a',
  encreDouce: '#2e2a26',
  gris: '#4a4540',
  discret: '#6b645b',
  rouge: '#a3201b',
  filet: '#c9bfa9',
  focus: '#a3201b',
  ombre: '#3c280a2e',
} as const

/** Fonds qui ne portent aucun texte : cadre autour de la feuille (contrôlé dans le navigateur par e2e/accessibilite/). */
export const FONDS_SANS_TEXTE: readonly (keyof typeof COULEURS)[] = ['table']

export const PAIRES: readonly PaireCouleurs[] = [
  { premierPlan: 'encre', fond: 'papier', usage: 'texte', role: 'titres, texte courant' },
  { premierPlan: 'encreDouce', fond: 'papier', usage: 'texte', role: 'chapô, résumés' },
  { premierPlan: 'gris', fond: 'papier', usage: 'texte', role: 'ligne d’édition, devise, surtitres' },
  { premierPlan: 'discret', fond: 'papier', usage: 'texte', role: 'mentions obligatoires (petit corps)' },
  { premierPlan: 'rouge', fond: 'papier', usage: 'texte', role: 'surtitre de gravité, lien vers la source' },
  { premierPlan: 'encre', fond: 'encart', usage: 'texte', role: 'encart « Rubriques »' },
  { premierPlan: 'gris', fond: 'encart', usage: 'texte', role: 'nombre d’éléments par rubrique' },
  { premierPlan: 'rouge', fond: 'encart', usage: 'texte', role: 'bandeau « données d’exemple »' },
  { premierPlan: 'focus', fond: 'papier', usage: 'composant', role: 'contour de focus' },
]
