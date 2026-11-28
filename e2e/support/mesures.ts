// Mesures d'accessibilité exécutées dans la page (page.evaluate) : contraste de chaque texte rendu d'une
// zone sur son fond effectif (RGAA 3.2, WCAG 1.4.3) et état de l'élément qui a le focus, avec le
// contraste de son contour (RGAA 3.3, 10.7, WCAG 1.4.11, 2.4.7). La zone est désignée par un sélecteur CSS
// (MISE_EN_PAGE pour la mise en page de la veille). Partagées par le banc d'essai
// (accessibilite/mises-en-page.spec.ts), les parcours sur la veille réelle (tests/veille.spec.ts) et l'audit des
// écrans (support/audit-accessibilite.ts). Chaque fonction est autonome : elle est sérialisée et exécutée dans le
// navigateur.

/** Sélecteur de la mise en page de la veille. */
export const MISE_EN_PAGE = '[data-mise-en-page]'

/** Mesure de contraste d'un texte rendu. */
export interface MesureContraste {
  texte: string
  rapport: number
  seuil: number
  decoratif: boolean
}

/** Contraste de tout texte visible de la zone désignée par `selecteur`, sur son fond effectif. */
export function mesurerContrastes(selecteur: string): MesureContraste[] {
  type Rgba = [number, number, number, number]
  const lire = (couleur: string): Rgba => {
    const correspondance = couleur.match(/rgba?\(([^)]+)\)/)
    if (!correspondance) return [0, 0, 0, 0]
    const composantes = correspondance[1].split(/[\s,/]+/).filter(Boolean).map(Number)
    return [composantes[0], composantes[1], composantes[2], composantes.length > 3 ? composantes[3] : 1]
  }
  const fusion = (dessus: Rgba, dessous: Rgba): Rgba => {
    const opacite = dessus[3] + dessous[3] * (1 - dessus[3])
    if (opacite === 0) return [0, 0, 0, 0]
    const canaux = [0, 1, 2].map((canal) => (dessus[canal] * dessus[3] + dessous[canal] * dessous[3] * (1 - dessus[3])) / opacite)
    return [canaux[0], canaux[1], canaux[2], opacite]
  }
  const lum = ([rouge, vert, bleu]: Rgba) => {
    const lineaire = (valeur: number) => {
      const proportion = valeur / 255
      return proportion <= 0.04045 ? proportion / 12.92 : ((proportion + 0.055) / 1.055) ** 2.4
    }
    return 0.2126 * lineaire(rouge) + 0.7152 * lineaire(vert) + 0.0722 * lineaire(bleu)
  }
  const rapport = (premiere: Rgba, seconde: Rgba) => {
    const [claire, sombre] = [lum(premiere), lum(seconde)].sort((gauche, droite) => droite - gauche)
    return (claire + 0.05) / (sombre + 0.05)
  }
  const fond = (depart: Element | null): Rgba => {
    const couches: Rgba[] = []
    for (let ancetre = depart; ancetre; ancetre = ancetre.parentElement) {
      const couleur = lire(getComputedStyle(ancetre).backgroundColor)
      if (couleur[3] > 0) couches.push(couleur)
      if (couleur[3] >= 1) break
    }
    return couches.reduceRight<Rgba>((dessous, dessus) => fusion(dessus, dessous), [255, 255, 255, 1])
  }
  const racine = document.querySelector(selecteur)
  if (!racine) throw new Error(`zone introuvable : ${selecteur}`)
  const mesures: { texte: string; rapport: number; seuil: number; decoratif: boolean }[] = []
  const marcheur = document.createTreeWalker(racine, NodeFilter.SHOW_TEXT)
  for (let noeud = marcheur.nextNode(); noeud; noeud = marcheur.nextNode()) {
    const texte = noeud.textContent?.trim()
    const parent = noeud.parentElement
    if (!texte || !parent) continue
    const cadre = parent.getBoundingClientRect()
    const style = getComputedStyle(parent)
    if (cadre.width * cadre.height < 4 || style.visibility === 'hidden' || style.display === 'none') continue
    const taille = parseFloat(style.fontSize)
    const gras = Number(style.fontWeight) >= 700
    const seuil = taille >= 24 || (gras && taille >= 18.66) ? 3 : 4.5
    const couleur = fusion(lire(style.color), fond(parent))
    mesures.push({
      texte: texte.slice(0, 50),
      rapport: Math.floor(rapport(couleur, fond(parent)) * 100) / 100,
      seuil,
      decoratif: !!parent.closest('[aria-hidden="true"]'),
    })
  }
  return mesures
}

/** Élément qui a le focus : position par rapport à la zone, lien visé, nom et contour de focus. */
export interface EtatFocus {
  /** Vrai si l'élément est dans la zone désignée. */
  dansZone: boolean
  href: string | null
  /** Texte de l'élément ou son attribut aria-label, pour nommer l'élément dans les messages. */
  nom: string
  style: string
  largeur: number
  /** Contraste du contour de focus sur le fond effectif de l'élément. */
  contraste: number
}

/** État de l'élément qui a le focus, ou null si le focus est sur le corps du document. */
export function etatFocus(selecteur: string): EtatFocus | null {
  const element = document.activeElement as HTMLElement | null
  if (!element || element === document.body) return null
  const style = getComputedStyle(element)
  const lire = (couleur: string): number[] => {
    const correspondance = couleur.match(/rgba?\(([^)]+)\)/)
    if (!correspondance) return [0, 0, 0, 0]
    const composantes = correspondance[1].split(/[\s,/]+/).filter(Boolean).map(Number)
    return [composantes[0], composantes[1], composantes[2], composantes.length > 3 ? composantes[3] : 1]
  }
  const fusion = (dessus: number[], dessous: number[]): number[] => {
    const opacite = dessus[3] + dessous[3] * (1 - dessus[3])
    if (opacite === 0) return [0, 0, 0, 0]
    const canaux = [0, 1, 2].map((canal) => (dessus[canal] * dessus[3] + dessous[canal] * dessous[3] * (1 - dessus[3])) / opacite)
    return [canaux[0], canaux[1], canaux[2], opacite]
  }
  const couches: number[][] = []
  for (let ancetre: Element | null = element; ancetre; ancetre = ancetre.parentElement) {
    const couleur = lire(getComputedStyle(ancetre).backgroundColor)
    if (couleur[3] > 0) couches.push(couleur)
    if (couleur[3] >= 1) break
  }
  const fond = couches.reduceRight((dessous, dessus) => fusion(dessus, dessous), [255, 255, 255, 1])
  const contour = fusion(lire(style.outlineColor), fond)
  const lum = (couleur: number[]) => {
    const lineaire = (valeur: number) => {
      const proportion = valeur / 255
      return proportion <= 0.04045 ? proportion / 12.92 : ((proportion + 0.055) / 1.055) ** 2.4
    }
    return 0.2126 * lineaire(couleur[0]) + 0.7152 * lineaire(couleur[1]) + 0.0722 * lineaire(couleur[2])
  }
  const [clair, sombre] = [lum(contour), lum(fond)].sort((gauche, droite) => droite - gauche)
  return {
    dansZone: !!element.closest(selecteur),
    href: element.getAttribute('href'),
    nom: (element.getAttribute('aria-label') ?? element.textContent ?? '').replace(/\s+/g, ' ').trim().slice(0, 50),
    style: style.outlineStyle,
    largeur: parseFloat(style.outlineWidth),
    contraste: (clair + 0.05) / (sombre + 0.05),
  }
}
