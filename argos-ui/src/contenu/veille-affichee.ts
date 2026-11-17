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

function projet(p: ElementVeilleProfils['projets'][number]): ProjetConcerne {
  return {
    nom: p.projet ?? p.projetId,
    niveau: p.niveau ? { code: p.niveau, libelle: NIVEAUX[p.niveau.toUpperCase()] ?? p.niveau.toLowerCase() } : null,
  }
}

function texte(valeur: string | null | undefined): string | null {
  const t = valeur?.trim()
  return t ? t : null
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
    rubriques: (element.rubriques ?? []).map((r) => ({ profil: r.profil, identifiant: r.identifiant, libelle: r.libelle })),
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
  const ajouter = (r: RubriqueAffichee): void => {
    const cle = `${r.profil}/${r.identifiant}`
    if (vues.has(cle)) return
    vues.add(cle)
    resultat.push(r)
  }
  for (const profil of veille.profilsActifs ?? []) {
    for (const r of catalogue.find((p) => p.identifiant === profil)?.rubriques ?? []) {
      ajouter({ profil, identifiant: r.identifiant, libelle: r.libelle })
    }
  }
  for (const element of veille.elements ?? []) {
    for (const r of element.rubriques ?? []) ajouter({ profil: r.profil, identifiant: r.identifiant, libelle: r.libelle })
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
    elements: (veille.elements ?? []).map((e) => elementAffiche(e, catalogue, abonnements)),
  }
}
