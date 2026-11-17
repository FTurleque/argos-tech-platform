// Mesures d'accessibilité exécutées dans la page (page.evaluate) : contraste de chaque texte rendu d'une
// mise en page sur son fond effectif (RGAA 3.2, WCAG 1.4.3) et état de l'élément qui a le focus, avec le
// contraste de son contour (RGAA 3.3, 10.7, WCAG 1.4.11, 2.4.7). Partagées par le banc d'essai
// (accessibilite/mises-en-page.spec.ts) et par les parcours sur la veille réelle (tests/veille.spec.ts).
// Chaque fonction est autonome : elle est sérialisée et exécutée dans le navigateur.

/** Mesure de contraste d'un texte rendu. */
export interface MesureContraste {
  texte: string
  rapport: number
  seuil: number
  decoratif: boolean
}

/** Écarts de contraste de tout texte visible de la mise en page, sur son fond effectif. */
export function mesurerContrastes(): MesureContraste[] {
  type Rgba = [number, number, number, number]
  const lire = (c: string): Rgba => {
    const m = c.match(/rgba?\(([^)]+)\)/)
    if (!m) return [0, 0, 0, 0]
    const p = m[1].split(/[\s,/]+/).filter(Boolean).map(Number)
    return [p[0], p[1], p[2], p.length > 3 ? p[3] : 1]
  }
  const fusion = (dessus: Rgba, dessous: Rgba): Rgba => {
    const a = dessus[3] + dessous[3] * (1 - dessus[3])
    if (a === 0) return [0, 0, 0, 0]
    const c = [0, 1, 2].map((i) => (dessus[i] * dessus[3] + dessous[i] * dessous[3] * (1 - dessus[3])) / a)
    return [c[0], c[1], c[2], a]
  }
  const lum = ([r, g, b]: Rgba) => {
    const f = (v: number) => {
      const s = v / 255
      return s <= 0.04045 ? s / 12.92 : ((s + 0.055) / 1.055) ** 2.4
    }
    return 0.2126 * f(r) + 0.7152 * f(g) + 0.0722 * f(b)
  }
  const rapport = (a: Rgba, b: Rgba) => {
    const [x, y] = [lum(a), lum(b)].sort((p, q) => q - p)
    return (x + 0.05) / (y + 0.05)
  }
  const fond = (el: Element | null): Rgba => {
    const couches: Rgba[] = []
    for (let e = el; e; e = e.parentElement) {
      const c = lire(getComputedStyle(e).backgroundColor)
      if (c[3] > 0) couches.push(c)
      if (c[3] >= 1) break
    }
    return couches.reduceRight<Rgba>((dessous, dessus) => fusion(dessus, dessous), [255, 255, 255, 1])
  }
  const racine = document.querySelector('[data-mise-en-page]')!
  const mesures: { texte: string; rapport: number; seuil: number; decoratif: boolean }[] = []
  const marcheur = document.createTreeWalker(racine, NodeFilter.SHOW_TEXT)
  for (let n = marcheur.nextNode(); n; n = marcheur.nextNode()) {
    const texte = n.textContent?.trim()
    const el = n.parentElement
    if (!texte || !el) continue
    const r = el.getBoundingClientRect()
    const s = getComputedStyle(el)
    if (r.width * r.height < 4 || s.visibility === 'hidden' || s.display === 'none') continue
    const taille = parseFloat(s.fontSize)
    const gras = Number(s.fontWeight) >= 700
    const seuil = taille >= 24 || (gras && taille >= 18.66) ? 3 : 4.5
    const couleur = fusion(lire(s.color), fond(el))
    mesures.push({
      texte: texte.slice(0, 50),
      rapport: Math.floor(rapport(couleur, fond(el)) * 100) / 100,
      seuil,
      decoratif: !!el.closest('[aria-hidden="true"]'),
    })
  }
  return mesures
}

/** Élément qui a le focus : position par rapport à la mise en page, lien visé et contour de focus. */
export interface EtatFocus {
  /** Vrai si l'élément est dans la mise en page (`[data-mise-en-page]`). */
  dansMiseEnPage: boolean
  href: string | null
  style: string
  largeur: number
  /** Contraste du contour de focus sur le fond effectif de l'élément. */
  contraste: number
}

/** État de l'élément qui a le focus, ou null si le focus est sur le corps du document. */
export function etatFocus(): EtatFocus | null {
  const el = document.activeElement as HTMLElement | null
  if (!el || el === document.body) return null
  const s = getComputedStyle(el)
  const lire = (c: string): number[] => {
    const m = c.match(/rgba?\(([^)]+)\)/)
    if (!m) return [0, 0, 0, 0]
    const p = m[1].split(/[\s,/]+/).filter(Boolean).map(Number)
    return [p[0], p[1], p[2], p.length > 3 ? p[3] : 1]
  }
  const fusion = (dessus: number[], dessous: number[]): number[] => {
    const a = dessus[3] + dessous[3] * (1 - dessus[3])
    if (a === 0) return [0, 0, 0, 0]
    const c = [0, 1, 2].map((i) => (dessus[i] * dessus[3] + dessous[i] * dessous[3] * (1 - dessus[3])) / a)
    return [c[0], c[1], c[2], a]
  }
  const couches: number[][] = []
  for (let e: Element | null = el; e; e = e.parentElement) {
    const c = lire(getComputedStyle(e).backgroundColor)
    if (c[3] > 0) couches.push(c)
    if (c[3] >= 1) break
  }
  const fond = couches.reduceRight((dessous, dessus) => fusion(dessus, dessous), [255, 255, 255, 1])
  const contour = fusion(lire(s.outlineColor), fond)
  const lum = (c: number[]) => {
    const f = (v: number) => {
      const x = v / 255
      return x <= 0.04045 ? x / 12.92 : ((x + 0.055) / 1.055) ** 2.4
    }
    return 0.2126 * f(c[0]) + 0.7152 * f(c[1]) + 0.0722 * f(c[2])
  }
  const [clair, sombre] = [lum(contour), lum(fond)].sort((x, y) => y - x)
  return {
    dansMiseEnPage: !!el.closest('[data-mise-en-page]'),
    href: el.getAttribute('href'),
    style: s.outlineStyle,
    largeur: parseFloat(s.outlineWidth),
    contraste: (clair + 0.05) / (sombre + 0.05),
  }
}
