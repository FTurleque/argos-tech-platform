// Aides de présentation communes aux mises en page (ADR-0023). Module pur, sans import d'exécution :
// testé directement par `node --test`.
//
// Ces fonctions mettent en forme ce que la couche contenu fournit ; elles ne filtrent, ne trient
// et n'écartent aucun élément (AFF-02) : l'union des profils, le dédoublonnage et les droits sont
// calculés par le serveur (arc42 §5.1).
import type { RubriqueAffichee, VeilleAffichee } from './modele'

export interface RubriqueComptee {
  readonly rubrique: RubriqueAffichee
  /** Nombre d'éléments de la veille rangés dans cette rubrique. */
  readonly nombre: number
}

/** Clé stable d'une rubrique : deux profils peuvent déclarer le même identifiant de rubrique. */
export function cleRubrique(rubrique: RubriqueAffichee): string {
  return `${rubrique.profil}/${rubrique.identifiant}`
}

/**
 * Sommaire : chaque rubrique de la veille, dans l'ordre fourni, avec le nombre d'éléments qui en
 * relèvent. Un élément rangé dans deux rubriques compte dans chacune ; il reste affiché une fois.
 */
export function sommaire(veille: VeilleAffichee): RubriqueComptee[] {
  return veille.rubriques.map((rubrique) => {
    const cle = cleRubrique(rubrique)
    const nombre = veille.elements.filter((element) => element.rubriques.some((rubrique) => cleRubrique(rubrique) === cle)).length
    return { rubrique, nombre }
  })
}

/** Date en toutes lettres (« 14 novembre 2026 ») ; la valeur brute si elle n'est pas une date. */
export function dateLongue(iso: string | null | undefined): string {
  if (!iso) return 'date non communiquée'
  const date = new Date(iso)
  return Number.isNaN(date.getTime())
    ? iso
    : date.toLocaleDateString('fr-FR', { day: 'numeric', month: 'long', year: 'numeric', timeZone: 'UTC' })
}

/** « 1 élément », « 3 éléments ». */
export function nombreElements(nombre: number): string {
  return `${nombre} ${nombre > 1 ? 'éléments' : 'élément'}`
}

/** Niveau de titre des éléments : un de plus que le titre principal, sans dépasser h6. */
export function niveauSuivant(niveau: number): number {
  return Math.min(6, niveau + 1)
}
