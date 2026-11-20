// Garde des vues de l'IHM (changement connecter-ihm-oidc : ACC-07-S1, ACC-07-S5, ACC-08-S3, ACC-09-S4).
// Fonction pure : testée par node:test (tests/garde.test.mjs). La garde s'exécute avant que la vue
// ne soit créée : une vue refusée ne demande aucune donnée à l'API.

/** Décision de la garde : afficher la vue, ou rediriger vers une vue publique. */
export type DecisionGarde =
  | { type: 'afficher' }
  | { type: 'connexion'; retour: string }
  | { type: 'compte-non-cree' }

export function decisionGarde(parametres: {
  /** La vue est publique (état du serveur, connexion, refus). */
  publique: boolean
  /** Session ouverte (jetons valides en mémoire). */
  connecte: boolean
  /** Authentifié, mais l'API a répondu 403 INSCRIPTION_SUR_INVITATION. */
  compteNonCree: boolean
  /** Chemin complet demandé, rendu après la connexion. */
  chemin: string
}): DecisionGarde {
  if (parametres.publique) return { type: 'afficher' }
  if (parametres.connecte && parametres.compteNonCree) return { type: 'compte-non-cree' }
  if (!parametres.connecte) return { type: 'connexion', retour: parametres.chemin }
  return { type: 'afficher' }
}

/**
 * Vue à afficher quand la session expire pendant l'affichage d'une vue (renouvellement refusé,
 * réponse 401) : une vue protégée cède la place à l'écran de connexion, qui présente l'état
 * « session expirée » et garde la vue en retour (ACC-08-S3, ACC-09-S3) ; une vue publique reste.
 */
export function decisionExpiration(parametres: { publique: boolean; chemin: string }): DecisionGarde {
  return decisionGarde({ publique: parametres.publique, connecte: false, compteNonCree: false, chemin: parametres.chemin })
}
