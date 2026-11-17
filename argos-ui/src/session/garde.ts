// Garde des vues de l'IHM (changement connecter-ihm-oidc : ACC-07-S1, ACC-07-S5, ACC-09-S4).
// Fonction pure : testée par node:test (tests/garde.test.mjs). La garde s'exécute avant que la vue
// ne soit créée : une vue refusée ne demande aucune donnée à l'API.

/** Décision de la garde : afficher la vue, ou rediriger vers une vue publique. */
export type DecisionGarde =
  | { type: 'afficher' }
  | { type: 'connexion'; retour: string }
  | { type: 'compte-non-cree' }

export function decisionGarde(p: {
  /** La vue est publique (état du serveur, connexion, refus). */
  publique: boolean
  /** Session ouverte (jetons valides en mémoire). */
  connecte: boolean
  /** Authentifié, mais l'API a répondu 403 INSCRIPTION_SUR_INVITATION. */
  compteNonCree: boolean
  /** Chemin complet demandé, rendu après la connexion. */
  chemin: string
}): DecisionGarde {
  if (p.publique) return { type: 'afficher' }
  if (p.connecte && p.compteNonCree) return { type: 'compte-non-cree' }
  if (!p.connecte) return { type: 'connexion', retour: p.chemin }
  return { type: 'afficher' }
}
