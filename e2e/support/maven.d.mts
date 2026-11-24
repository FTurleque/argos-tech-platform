// Déclaration de support/maven.mjs, importé par playwright.config.ts.

export interface OptionsRecherche {
  env?: Record<string, string | undefined>
  existe?: (chemin: string) => boolean
  plateforme?: string
  emplacements?: readonly string[]
}

export const CMD_WINDOWS: string
export const EMPLACEMENTS_MAVEN: readonly string[]
export const MESSAGE_MAVEN_INTROUVABLE: string

/** Chemin absolu de Maven, ou undefined s'il est introuvable. */
export function cheminMaven(options?: OptionsRecherche): string | undefined

/** Chemin absolu de Maven ; lève une erreur explicite s'il est introuvable. */
export function cheminMavenExige(options?: OptionsRecherche): string

/** Ligne de commande « "mvn" arg… » pour un interpréteur de commandes, arguments cités et contrôlés. */
export function ligneMaven(chemin: string, args: string[]): string

/** Ligne de commande du serveur web de Playwright ; sans Maven trouvé, ligne d'un script qui échoue explicitement. */
export function ligneServeurMaven(args: string[], options?: OptionsRecherche): string

/** Commande, arguments et options de spawn qui lancent Maven sans interpréteur trouvé par le PATH. */
export function lancementMaven(chemin: string, args: string[], options?: { plateforme?: string }): {
  commande: string
  args: string[]
  options: { windowsVerbatimArguments?: boolean }
}
