// Palette de la mise en page « gazette » (maquette « La une de la gazette »).
// Toute couleur de la mise en page est un jeton de cette palette ; chaque couple texte/fond employé
// est déclaré dans PAIRES et contrôlé par tests/contrastes.test.mjs (AFF-05).
import type { PaireCouleurs } from '../contraste'

export const COULEURS = {
  table: '#3b3226',
  papier: '#e9dcbf',
  papierClair: '#f3ead6',
  encre: '#1d1a14',
  rouge: '#8e1b12',
  discret: '#4f4636',
  focus: '#8e1b12',
  ombre: '#00000080',
} as const

/** Fonds qui ne portent aucun texte : cadre autour de la feuille (contrôlé dans le navigateur par e2e/accessibilite/). */
export const FONDS_SANS_TEXTE: readonly (keyof typeof COULEURS)[] = ['table']

export const PAIRES: readonly PaireCouleurs[] = [
  { premierPlan: 'encre', fond: 'papier', usage: 'texte', role: 'texte courant, titres' },
  { premierPlan: 'encre', fond: 'papierClair', usage: 'texte', role: 'encadré « Dans ce numéro », signalement' },
  { premierPlan: 'papier', fond: 'encre', usage: 'texte', role: 'étiquette « données d’exemple »' },
  { premierPlan: 'rouge', fond: 'papier', usage: 'texte', role: 'gravité, liens vers la source' },
  { premierPlan: 'rouge', fond: 'papierClair', usage: 'texte', role: 'remède dans le signalement' },
  { premierPlan: 'discret', fond: 'papier', usage: 'texte', role: 'mentions obligatoires (petit corps)' },
  { premierPlan: 'discret', fond: 'papierClair', usage: 'texte', role: 'mentions dans le signalement' },
  { premierPlan: 'focus', fond: 'papier', usage: 'composant', role: 'contour de focus' },
  { premierPlan: 'focus', fond: 'papierClair', usage: 'composant', role: 'contour de focus dans le signalement' },
]
