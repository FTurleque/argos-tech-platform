// Jeton porteur et classement des refus du client REST (changement connecter-ihm-oidc, ACC-09).
// Fonctions pures, sans dépendance : testées par node:test (tests/autorisation.test.mjs).
// Aucune règle métier : l'IHM présente les refus décidés par le serveur (arc42 §5.1).

/** Routes de /api/v1 publiques côté serveur (ACC-01-S2) : aucun jeton ne leur est envoyé. */
export const CHEMINS_PUBLICS: readonly string[] = ['/api/v1/system/info']

/** Code renvoyé par le serveur quand le compte n'est pas créé faute d'invitation (ACC-02, Q-2). */
export const CODE_COMPTE_NON_CREE = 'INSCRIPTION_SUR_INVITATION'

/** Vrai si l'appel vise /api/v1 et n'est pas une route publique. */
export function exigeJeton(chemin: string): boolean {
  const sansRequete = chemin.split(/[?#]/, 1)[0]
  if (sansRequete !== '/api/v1' && !sansRequete.startsWith('/api/v1/')) return false
  return !CHEMINS_PUBLICS.includes(sansRequete)
}

/**
 * En-têtes d'un appel : `Authorization: Bearer` ajouté aux seuls appels qui l'exigent.
 * Le jeton ne figure jamais dans l'adresse.
 */
export function entetesAvecJeton(
  chemin: string,
  entetes: Record<string, string>,
  jeton: string | null,
): Record<string, string> {
  if (!exigeJeton(chemin) || !jeton) return { ...entetes }
  return { ...entetes, Authorization: `Bearer ${jeton}` }
}

/** Refus présenté par l'IHM. */
export type Refus = 'NON_AUTHENTIFIE' | 'COMPTE_NON_CREE' | 'INTERDIT'

/**
 * Classe une réponse refusée : 401 relance la connexion, 403 affiche un refus, dont un écran
 * dédié quand le compte n'a pas été créé faute d'invitation. Null pour toute autre réponse.
 */
export function classerRefus(statut: number, corps: unknown): Refus | null {
  if (statut === 401) return 'NON_AUTHENTIFIE'
  if (statut !== 403) return null
  const code = corps && typeof corps === 'object' ? (corps as { code?: unknown }).code : undefined
  return code === CODE_COMPTE_NON_CREE ? 'COMPTE_NON_CREE' : 'INTERDIT'
}
