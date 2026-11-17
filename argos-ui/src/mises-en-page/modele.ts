// Modèle de données des mises en page (ADR-0023 point 1, exigence AFF-02).
//
// Contrat unique entre la couche contenu et la couche mise en page : les quatre mises en page
// reçoivent exactement ce modèle et n'en reçoivent pas d'autre. Il ne contient que des éléments
// déjà autorisés, déjà filtrés par profils et garde-fous côté serveur, avec leurs mentions
// obligatoires et des libellés prêts à afficher. Une mise en page n'y ajoute aucun fait et n'en
// retire aucun : seule la forme change.
//
// Module de types seulement, sans import : utilisable par les tests `node --test`.

/**
 * Gravité d'un élément, fixée par la source ou par le moteur d'impact. `non-classee` porte une gravité
 * que la source exprime hors de cette échelle : son libellé reste celui de la source.
 */
export type CodeGravite = 'critique' | 'elevee' | 'moyenne' | 'faible' | 'non-classee'

/** Nature d'un élément de veille ; une mise en page peut s'en servir pour choisir un style. */
export type CodeNature =
  | 'avis-securite'
  | 'fiche-reglementaire'
  | 'nouvelle-version'
  | 'fin-de-support'
  | 'echeance'
  | 'actualite'

/** Valeur codée accompagnée de son libellé, fourni par la couche contenu. */
export interface Libelle<C extends string = string> {
  readonly code: C
  readonly libelle: string
}

/** Mentions qu'aucune mise en page ne peut omettre (dossier v2.4 §A.3, ADR-0013, ADR-0023 point 4). */
export interface MentionsElement {
  /** Source d'origine ; `url` n'est rendue cliquable que si elle est `http` ou `https`. */
  readonly source: { readonly nom: string; readonly url?: string | null }
  /** Date de publication par la source (ISO 8601), ou null si la source n'en donne pas. */
  readonly publieLe: string | null
  /** Niveau de confiance d'un impact, absent pour une simple information. */
  readonly niveauConfiance: Libelle | null
  /** Vrai si le résumé affiché a été produit par l'IA : la mention « résumé IA » est alors affichée. */
  readonly resumeProduitParIA: boolean
  /**
   * Mention de paternité et de licence exigée par la source (par exemple la Licence Ouverte des textes
   * de la DILA), telle que fournie par le serveur ; null si la source n'en exige pas.
   */
  readonly attribution: string | null
  /** Mention de validation humaine exigée par un garde-fou de profil (PRF-06-S2), texte fourni par le serveur. */
  readonly validation: string | null
  /** Textes des garde-fous des profils actifs qui s'appliquent à l'élément (PRF-06), dans l'ordre reçu. */
  readonly gardeFous: readonly string[]
  /**
   * Origine de l'élément : profils actifs dont il relève, puis abonnements personnels qui l'ont retenu
   * (décision Q-3), sous forme de libellés prêts à afficher ; vide pour un jeu d'exemple sans origine.
   */
  readonly origines: readonly string[]
}

/** Rubrique d'un profil actif (PRF-02). */
export interface RubriqueAffichee {
  readonly profil: string
  readonly identifiant: string
  readonly libelle: string
}

/** Projet concerné par un élément, avec le niveau de correspondance calculé sans IA (ADR-0013). */
export interface ProjetConcerne {
  readonly nom: string
  readonly niveau: Libelle | null
}

/** Élément de veille prêt à afficher. */
export interface ElementAffiche {
  readonly identifiant: string
  /** Titre venu de la source : donnée externe, toujours rendue comme texte. */
  readonly titre: string
  readonly resume: string | null
  readonly type: Libelle<CodeNature>
  readonly gravite: Libelle<CodeGravite> | null
  /** Rubriques des profils actifs dont relève l'élément, sans doublon (calcul serveur). */
  readonly rubriques: readonly RubriqueAffichee[]
  /** Projets visibles par l'utilisateur et concernés par l'élément (calcul serveur, sous droits). */
  readonly projets: readonly ProjetConcerne[]
  /** Action recommandée, ou null. */
  readonly action: string | null
  readonly mentions: MentionsElement
}

/** Veille à afficher : même modèle pour toutes les mises en page. */
export interface VeilleAffichee {
  /** Vrai pour un jeu de démonstration : chaque mise en page l'annonce comme exemple. */
  readonly exemple: boolean
  /** Date de composition de la veille (ISO 8601), ou null. */
  readonly composeeLe: string | null
  /** Rubriques des profils actifs, dans l'ordre fourni par le serveur. */
  readonly rubriques: readonly RubriqueAffichee[]
  /** Éléments, tous, dans l'ordre reçu : une mise en page n'en retire et n'en ajoute aucun. */
  readonly elements: readonly ElementAffiche[]
}

/** Propriétés communes à toutes les mises en page du catalogue. */
export interface ProprietesMiseEnPage {
  readonly veille: VeilleAffichee
  /**
   * Niveau du titre principal de la mise en page (1 à 5) ; les titres des éléments sont au niveau
   * suivant. Permet d'insérer la mise en page dans un écran qui a déjà ses propres titres.
   */
  readonly niveauTitre?: number
}
