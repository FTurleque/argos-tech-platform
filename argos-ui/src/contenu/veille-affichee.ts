// Transposition de la veille restituée par le serveur (`GET /api/v1/me/veille`, schéma `VeilleProfils`)
// vers le modèle commun des mises en page (`mises-en-page/modele.ts`, ADR-0023 point 1, AFF-02).
// Module pur de la couche contenu, sans appel réseau ni dépendance à Vue : testé par `node --test`.
//
// Conversion de forme uniquement (arc42 §5.1) : chaque élément reçu donne exactement un élément affiché,
// dans l'ordre reçu ; aucun n'est retiré, ajouté, trié ni fusionné. L'union des profils actifs et des
// abonnements personnels, le dédoublonnage, les garde-fous et les droits sont calculés par le serveur.
// Les mentions reçues (validation, garde-fous, « produit par l'IA », paternité de la source) sont toutes
// transmises. Le catalogue des profils et la liste des abonnements ne servent qu'à nommer.
import type { Abonnement, ElementVeilleProfils, ProfilCatalogue, VeilleProfils } from '../api/veille'
import type {
  CodeGravite,
  CodeNature,
  ElementAffiche,
  Libelle,
  ProjetConcerne,
  RubriqueAffichee,
  VeilleAffichee,
} from '../mises-en-page/modele'
import { libelleGravite, libelleType, originesElement } from './veille-profils.ts'

const NATURES: Readonly<Record<string, CodeNature>> = {
  AVIS_SECURITE: 'avis-securite',
  FICHE_REGLEMENTAIRE: 'fiche-reglementaire',
}

/** Nature affichée d'un type d'élément du serveur ; un type inconnu garde son libellé, sous la nature `actualite`. */
export function nature(type: string): Libelle<CodeNature> {
  return { code: NATURES[type] ?? 'actualite', libelle: libelleType(type) }
}

const GRAVITES: Readonly<Record<string, CodeGravite>> = {
  CRITICAL: 'critique',
  HIGH: 'elevee',
  MEDIUM: 'moyenne',
  MODERATE: 'moyenne',
  LOW: 'faible',
}

/** Gravité affichée ; une gravité hors de l'échelle garde le libellé de la source (`non-classee`). */
export function gravite(valeur: string | null | undefined): Libelle<CodeGravite> | null {
  const libelle = libelleGravite(valeur)
  if (!valeur || !libelle) return null
  return { code: GRAVITES[valeur.toUpperCase()] ?? 'non-classee', libelle }
}

const NIVEAUX: Readonly<Record<string, string>> = {
  EXACT: 'correspondance exacte',
  PROBABLE: 'correspondance probable',
  POSSIBLE: 'correspondance possible',
}

function projet(concerne: ElementVeilleProfils['projets'][number]): ProjetConcerne {
  return {
    nom: concerne.projet ?? concerne.projetId,
    niveau: concerne.niveau
      ? { code: concerne.niveau, libelle: NIVEAUX[concerne.niveau.toUpperCase()] ?? concerne.niveau.toLowerCase() }
      : null,
  }
}

function texte(valeur: string | null | undefined): string | null {
  const nettoye = valeur?.trim()
  return nettoye || null
}

/** Élément affiché correspondant à un élément de la veille ; aucune information reçue n'est écartée. */
export function elementAffiche(
  element: ElementVeilleProfils,
  catalogue: readonly ProfilCatalogue[] = [],
  abonnements: readonly Abonnement[] = [],
): ElementAffiche {
  const resume = texte(element.resume)
  return {
    identifiant: `${element.type}:${element.id}`,
    titre: element.titre ?? element.identifiant ?? element.id,
    resume,
    type: nature(element.type),
    gravite: gravite(element.gravite),
    rubriques: (element.rubriques ?? []).map((rubrique) => (
      { profil: rubrique.profil, identifiant: rubrique.identifiant, libelle: rubrique.libelle }
    )),
    projets: (element.projets ?? []).map(projet),
    action: null,
    mentions: {
      source: { nom: element.source ?? 'source non précisée', url: element.lien },
      publieLe: element.date,
      niveauConfiance: null,
      resumeProduitParIA: resume !== null && texte(element.mentionIa) !== null,
      attribution: texte(element.mentionSource),
      validation: texte(element.mentionValidation),
      gardeFous: [...(element.gardeFous ?? [])],
      origines: originesElement(element, catalogue, abonnements),
    },
  }
}

/**
 * Rubriques du sommaire : celles des profils actifs d'après le catalogue, dans l'ordre des profils actifs
 * restitués par le serveur, puis celles que portent les éléments et que le catalogue ne connaît pas, dans
 * l'ordre de première apparition. Sans doublon (clé profil et identifiant).
 */
export function rubriquesDuSommaire(
  veille: VeilleProfils,
  catalogue: readonly ProfilCatalogue[] = [],
): RubriqueAffichee[] {
  const vues = new Set<string>()
  const resultat: RubriqueAffichee[] = []
  const ajouter = (rubrique: RubriqueAffichee): void => {
    const cle = `${rubrique.profil}/${rubrique.identifiant}`
    if (vues.has(cle)) return
    vues.add(cle)
    resultat.push(rubrique)
  }
  for (const profil of veille.profilsActifs ?? []) {
    for (const rubrique of catalogue.find((profilCatalogue) => profilCatalogue.identifiant === profil)?.rubriques ?? []) {
      ajouter({ profil, identifiant: rubrique.identifiant, libelle: rubrique.libelle })
    }
  }
  for (const element of veille.items ?? []) {
    for (const rubrique of element.rubriques ?? []) {
      ajouter({ profil: rubrique.profil, identifiant: rubrique.identifiant, libelle: rubrique.libelle })
    }
  }
  return resultat
}

/** Veille réelle de l'utilisateur au format des mises en page (`exemple` vaut toujours faux). */
export function veilleAffichee(
  veille: VeilleProfils,
  catalogue: readonly ProfilCatalogue[] = [],
  abonnements: readonly Abonnement[] = [],
  composeeLe: string | null = null,
): VeilleAffichee {
  return {
    exemple: false,
    composeeLe,
    rubriques: rubriquesDuSommaire(veille, catalogue),
    elements: (veille.items ?? []).map((element) => elementAffiche(element, catalogue, abonnements)),
  }
}

/** Position d'une page de la veille (PRF-09) : numéro de page, nombre de pages, libellés du total et de la position. */
export interface PositionVeille {
  page: number
  /** Nombre de pages (au moins 1). */
  pages: number
  /** Nombre total d'éléments de la veille, toutes pages confondues. */
  synthese: string
  /** « Page n sur m ». */
  libelle: string
}

/** Position de la page reçue, d'après la page, la taille et le total établis par le serveur. */
export function positionVeille(veille: VeilleProfils): PositionVeille {
  const total = Math.max(0, veille.total ?? 0)
  const taille = Math.max(1, veille.size ?? 1)
  const pages = Math.max(1, Math.ceil(total / taille))
  const page = Math.min(Math.max(0, veille.page ?? 0), pages - 1)
  return {
    page,
    pages,
    synthese: total > 1 ? `${total} éléments dans votre veille` : `${total} élément dans votre veille`,
    libelle: `Page ${page + 1} sur ${pages}`,
  }
}
