// Modèles de vue produits par la couche contenu et consommés par la couche mise en page
// (ADR-0023 point 1, exigence AFF-02).
//
// Une mise en page ne reçoit que ces modèles : des éléments déjà autorisés, déjà filtrés
// par profils et garde-fous côté serveur, accompagnés de leurs mentions obligatoires.

/** Mentions qu'aucune mise en page ne peut omettre (dossier v2.4 §A.3, ADR-0013). */
export interface MentionsElement {
  /** Source d'origine de l'information. */
  readonly source: { readonly nom: string; readonly url?: string }
  /** Date de publication par la source, au format ISO 8601. */
  readonly publieLe: string
  /** Niveau de confiance d'un impact, absent pour une simple information. */
  readonly niveauConfiance: { readonly code: string; readonly libelle: string } | null
  /** Vrai si le résumé affiché a été produit par l'IA. */
  readonly resumeProduitParIA: boolean
  /** Validation humaine exigée par un garde-fou de profil (PRF-06-S2), si elle a eu lieu. */
  readonly validation: { readonly par: string; readonly le: string } | null
}

/** Rubrique d'un profil actif dont relève l'élément (PRF-02). */
export interface RubriqueElement {
  readonly profil: string
  readonly identifiant: string
  readonly libelle: string
}

/** Élément de veille prêt à afficher. */
export interface ElementVeille {
  readonly identifiant: string
  readonly titre: string
  readonly resume: string
  readonly gravite: 'critique' | 'elevee' | 'moyenne' | 'faible' | null
  readonly rubriques: readonly RubriqueElement[]
  readonly mentions: MentionsElement
}

/** Propriétés communes à toutes les mises en page du catalogue. */
export interface ProprietesMiseEnPage {
  /** Éléments à restituer, tous, dans l'ordre reçu. */
  readonly elements: readonly ElementVeille[]
}
