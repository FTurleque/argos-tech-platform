// Choix de la mise en page à afficher (DF-AFF-1, exigence AFF-04).
// Module pur, sans import : testé directement par `node --test`.

/** Mise en page appliquée sans choix, après un retrait ou en cas d'erreur (AFF-04). */
export const MISE_EN_PAGE_PAR_DEFAUT = 'classique'

/** Ce que renvoie le serveur dans `GET /api/v1/preferences`, réduit à ce qui sert ici. */
export interface PreferencesServeur {
  readonly miseEnPage: string
  readonly message: string | null
}

export interface MiseEnPageResolue {
  readonly identifiant: string
  /** Information à montrer à l'utilisateur, ou null. */
  readonly message: string | null
}

/**
 * Mise en page à afficher.
 *
 * @param preferences préférences renvoyées par le serveur, ou null s'il est injoignable
 *                    ou si l'utilisateur n'est pas authentifié
 * @param disponibles identifiants des mises en page que cette IHM sait afficher
 */
export function resoudreMiseEnPage(
  preferences: PreferencesServeur | null,
  disponibles: readonly string[],
): MiseEnPageResolue {
  if (preferences === null) {
    return { identifiant: MISE_EN_PAGE_PAR_DEFAUT, message: null }
  }
  if (!disponibles.includes(preferences.miseEnPage)) {
    return {
      identifiant: MISE_EN_PAGE_PAR_DEFAUT,
      message: `La mise en page « ${preferences.miseEnPage} » n'est pas disponible dans cette version : la mise en page « ${MISE_EN_PAGE_PAR_DEFAUT} » s'applique.`,
    }
  }
  return { identifiant: preferences.miseEnPage, message: preferences.message }
}
