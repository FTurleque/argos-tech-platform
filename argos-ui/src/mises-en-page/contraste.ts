// Contrastes des couleurs des mises en page (AFF-05, RGAA 4.1.2 critères 3.2 et 3.3, WCAG 2.1/2.2
// critères 1.4.3 et 1.4.11). Module pur, sans import : testé directement par `node --test`.
//
// Formules de luminance relative et de rapport de contraste du WCAG
// (https://www.w3.org/TR/WCAG22/#dfn-relative-luminance).

/** Usage d'un couple de couleurs, qui fixe le seuil minimal (niveau AA). */
export type UsageCouleur =
  /** Texte de taille courante : 4,5:1. */
  | 'texte'
  /** Texte d'au moins 24 px (18,66 px en gras) : 3:1. */
  | 'grand-texte'
  /** Composant d'interface ou contour de focus (indicateur non textuel) : 3:1. */
  | 'composant'

export const SEUILS: Readonly<Record<UsageCouleur, number>> = {
  texte: 4.5,
  'grand-texte': 3,
  composant: 3,
}

/** Couple de couleurs utilisé dans une mise en page, désigné par les noms de ses jetons. */
export interface PaireCouleurs {
  readonly premierPlan: string
  readonly fond: string
  readonly usage: UsageCouleur
  /** Où le couple est employé, pour le rapport d'audit. */
  readonly role: string
}

export interface ResultatContraste {
  readonly paire: PaireCouleurs
  readonly rapport: number
  readonly seuil: number
  readonly conforme: boolean
}

const HEX = /^#([0-9a-f]{3}|[0-9a-f]{6})$/i

/** Composantes [r, g, b] (0 à 255) d'une couleur `#rgb` ou `#rrggbb`. */
export function composantes(couleur: string): readonly [number, number, number] {
  const correspondance = HEX.exec(couleur.trim())
  if (!correspondance) throw new Error(`Couleur non reconnue : « ${couleur} » (attendu : #rgb ou #rrggbb)`)
  const hex = correspondance[1].length === 3 ? [...correspondance[1]].map((chiffre) => chiffre + chiffre).join('') : correspondance[1]
  return [0, 2, 4].map((position) => parseInt(hex.slice(position, position + 2), 16)) as unknown as readonly [number, number, number]
}

function lineaire(canal: number): number {
  const proportion = canal / 255
  return proportion <= 0.04045 ? proportion / 12.92 : ((proportion + 0.055) / 1.055) ** 2.4
}

/** Luminance relative WCAG (0 pour le noir, 1 pour le blanc). */
export function luminance(couleur: string): number {
  const [rouge, vert, bleu] = composantes(couleur)
  return 0.2126 * lineaire(rouge) + 0.7152 * lineaire(vert) + 0.0722 * lineaire(bleu)
}

/** Rapport de contraste WCAG entre deux couleurs, de 1 à 21. */
export function rapportContraste(premiere: string, seconde: string): number {
  const luminancePremiere = luminance(premiere)
  const luminanceSeconde = luminance(seconde)
  const [claire, foncee] = luminancePremiere >= luminanceSeconde
    ? [luminancePremiere, luminanceSeconde]
    : [luminanceSeconde, luminancePremiere]
  return (claire + 0.05) / (foncee + 0.05)
}

/** Rapport arrondi au centième inférieur, pour ne jamais afficher un seuil atteint par arrondi. */
export function arrondiInferieur(rapport: number): number {
  return Math.floor(rapport * 100) / 100
}

/**
 * Contrôle de chaque couple déclaré d'une palette.
 *
 * @throws si un couple cite un jeton absent de la palette
 */
export function controlerPalette(
  couleurs: Readonly<Record<string, string>>,
  paires: readonly PaireCouleurs[],
): ResultatContraste[] {
  return paires.map((paire) => {
    for (const jeton of [paire.premierPlan, paire.fond]) {
      if (!Object.hasOwn(couleurs, jeton)) throw new Error(`Jeton de couleur inconnu : « ${jeton} » (${paire.role})`)
    }
    const rapport = rapportContraste(couleurs[paire.premierPlan], couleurs[paire.fond])
    const seuil = SEUILS[paire.usage]
    return { paire, rapport, seuil, conforme: rapport >= seuil }
  })
}

/** Variables CSS (`--nom`) d'une palette, à poser en style sur la racine d'une mise en page. */
export function variablesCss(couleurs: Readonly<Record<string, string>>): Record<string, string> {
  return Object.fromEntries(Object.entries(couleurs).map(([nom, valeur]) => [`--${nom}`, valeur]))
}
