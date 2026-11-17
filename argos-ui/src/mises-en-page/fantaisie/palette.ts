// Palette de la mise en page « fantaisie » (maquette « La une fantaisie »).
// Toute couleur de la mise en page est un jeton de cette palette ; chaque couple texte/fond employé
// est déclaré dans PAIRES et contrôlé par tests/contrastes.test.mjs (AFF-05).
import type { PaireCouleurs } from '../contraste'

export const COULEURS = {
  lavande: '#e6dcf5',
  papier: '#fbf6ea',
  carte: '#ffffff',
  encre: '#2a1f3d',
  surEncre: '#fbf6ea',
  rouge: '#a8341f',
  sarcelle: '#17615c',
  discret: '#5c5070',
  violet: '#5b3a91',
  rose: '#f7d9d2',
  jaune: '#fbe9b0',
  menthe: '#d4ebe8',
  or: '#e8b931',
  focus: '#a8341f',
} as const

export const PAIRES: readonly PaireCouleurs[] = [
  { premierPlan: 'encre', fond: 'papier', usage: 'texte', role: 'texte courant de la page' },
  { premierPlan: 'encre', fond: 'carte', usage: 'texte', role: 'cartes blanches' },
  { premierPlan: 'encre', fond: 'menthe', usage: 'texte', role: 'cartes menthe' },
  { premierPlan: 'encre', fond: 'jaune', usage: 'texte', role: 'cartes jaunes, projets probables' },
  { premierPlan: 'encre', fond: 'rose', usage: 'texte', role: 'cartes roses, projets concernés' },
  { premierPlan: 'encre', fond: 'lavande', usage: 'texte', role: 'cartes lavande, rubriques' },
  { premierPlan: 'encre', fond: 'or', usage: 'texte', role: 'tampon décoratif de gravité' },
  { premierPlan: 'surEncre', fond: 'encre', usage: 'texte', role: 'bandeau « données d’exemple », rubrique active' },
  { premierPlan: 'rouge', fond: 'papier', usage: 'texte', role: 'devise, surtitres manuscrits' },
  { premierPlan: 'rouge', fond: 'carte', usage: 'texte', role: 'surtitres, liens vers la source (cartes blanches)' },
  { premierPlan: 'rouge', fond: 'menthe', usage: 'texte', role: 'surtitres (cartes menthe)' },
  { premierPlan: 'rouge', fond: 'jaune', usage: 'texte', role: 'surtitres (cartes jaunes)' },
  { premierPlan: 'rouge', fond: 'rose', usage: 'texte', role: 'surtitres (cartes roses)' },
  { premierPlan: 'rouge', fond: 'lavande', usage: 'texte', role: 'surtitres (cartes lavande)' },
  { premierPlan: 'sarcelle', fond: 'papier', usage: 'texte', role: 'griffonnage de tête' },
  { premierPlan: 'sarcelle', fond: 'carte', usage: 'texte', role: 'nature de l’élément' },
  { premierPlan: 'sarcelle', fond: 'lavande', usage: 'texte', role: 'nature de l’élément (cartes lavande)' },
  { premierPlan: 'sarcelle', fond: 'menthe', usage: 'texte', role: 'nature de l’élément (cartes menthe)' },
  { premierPlan: 'sarcelle', fond: 'jaune', usage: 'texte', role: 'nature de l’élément (cartes jaunes)' },
  { premierPlan: 'sarcelle', fond: 'rose', usage: 'texte', role: 'nature de l’élément (cartes roses)' },
  { premierPlan: 'discret', fond: 'papier', usage: 'texte', role: 'pied de page' },
  { premierPlan: 'discret', fond: 'carte', usage: 'texte', role: 'mentions obligatoires' },
  { premierPlan: 'violet', fond: 'lavande', usage: 'texte', role: 'nombre d’éléments par rubrique' },
  { premierPlan: 'focus', fond: 'papier', usage: 'composant', role: 'contour de focus' },
  { premierPlan: 'focus', fond: 'carte', usage: 'composant', role: 'contour de focus dans une carte' },
  { premierPlan: 'focus', fond: 'menthe', usage: 'composant', role: 'contour de focus (carte menthe)' },
  { premierPlan: 'focus', fond: 'jaune', usage: 'composant', role: 'contour de focus (carte jaune)' },
  { premierPlan: 'focus', fond: 'rose', usage: 'composant', role: 'contour de focus (carte rose)' },
  { premierPlan: 'focus', fond: 'lavande', usage: 'composant', role: 'contour de focus (carte lavande)' },
]
