// Choix du composant de mise en page d'après l'identifiant de la préférence (ADR-0023, AFF-04).
// Module pur, sans import : testé directement par `node --test`.
//
// La préférence est résolue par la couche contenu (`contenu/resolution.ts`) ; ce choix n'est que
// le dernier rempart de la couche mise en page : un identifiant inconnu de cette IHM (version plus
// ancienne que le catalogue, valeur vide) affiche la mise en page de repli au lieu de rien.

/** Mise en page affichée quand l'identifiant demandé n'est pas connu de cette IHM. */
export const MISE_EN_PAGE_DE_REPLI = 'classique'

export interface ComposantChoisi<T> {
  readonly identifiant: string
  readonly composant: T
  /** Vrai si l'identifiant demandé était inconnu et que la mise en page de repli s'applique. */
  readonly repli: boolean
}

/** Bornes du niveau de titre principal : le titre d'un élément reste un titre HTML (h2 à h6). */
export const NIVEAU_TITRE_MIN = 1
export const NIVEAU_TITRE_MAX = 5

/**
 * Composant à afficher pour `identifiant`.
 *
 * Seules les clés propres du registre comptent : un identifiant comme `constructor` ou
 * `__proto__` ne sélectionne jamais une propriété héritée.
 */
export function choisirComposant<T>(
  identifiant: string | null | undefined,
  registre: Readonly<Record<string, T>>,
): ComposantChoisi<T> {
  if (typeof identifiant === 'string' && Object.hasOwn(registre, identifiant)) {
    return { identifiant, composant: registre[identifiant] as T, repli: false }
  }
  if (!Object.hasOwn(registre, MISE_EN_PAGE_DE_REPLI)) {
    throw new Error(`Le registre des mises en page ne contient pas « ${MISE_EN_PAGE_DE_REPLI} »`)
  }
  return { identifiant: MISE_EN_PAGE_DE_REPLI, composant: registre[MISE_EN_PAGE_DE_REPLI] as T, repli: true }
}

/** Niveau de titre principal borné à [1, 5] ; 2 par défaut. */
export function bornerNiveauTitre(niveau: number | null | undefined): number {
  if (typeof niveau !== 'number' || !Number.isFinite(niveau)) return 2
  return Math.min(NIVEAU_TITRE_MAX, Math.max(NIVEAU_TITRE_MIN, Math.trunc(niveau)))
}
