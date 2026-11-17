// Résumé produit par l'IA d'une alerte (changement encadrer-appels-ia, IAG-05) : tout résumé affiché
// porte la mention « produit par l'IA » et renvoie à ses sources, même si le serveur ne l'a pas fournie.
// Le texte est affiché comme texte brut (interpolation Vue), jamais comme HTML.

/** Mention obligatoire de tout contenu produit par un modèle (IAG-05, AFF-02). */
export const MENTION_IA = "produit par l'IA"

export interface ContenuAvecResumeIa {
  readonly resumeIa?: string | null
  readonly mentionResumeIa?: string | null
}

export interface ResumeIaAffiche {
  readonly texte: string
  readonly mention: string
}

/** Résumé à afficher avec sa mention, ou `null` s'il n'y a pas de résumé. */
export function resumeIaAffiche(contenu: ContenuAvecResumeIa | null | undefined): ResumeIaAffiche | null {
  const texte = contenu?.resumeIa?.trim()
  if (!texte) return null
  const mention = contenu?.mentionResumeIa?.trim()
  return { texte, mention: mention ? mention : MENTION_IA }
}
