// Lancement de Maven par la campagne de performance et par Playwright, sans consulter le PATH : chemin absolu
// pris dans ARGOS_E2E_MVN, puis dans l'installation désignée par MAVEN_HOME ou M2_HOME, puis parmi des
// emplacements d'installation fixes (même règle que support/docker.mjs). La campagne de performance lance Maven
// sans interpréteur de commandes trouvé par le PATH : le script directement hors de Windows ; sous Windows, mvn.cmd
// (script batch) par cmd.exe, lui-même appelé par son chemin absolu. Playwright exécute la commande de son serveur
// web par l'interpréteur de commandes du système : la ligne commence par le chemin absolu de Maven. Dans les deux
// cas, aucun argument ne contient de caractère interprété par cmd.exe ou par sh.
import { existsSync } from 'node:fs'
import { join } from 'node:path'
import { platform } from 'node:os'
import { fileURLToPath } from 'node:url'

const WINDOWS = 'win32'
const SCRIPT_WINDOWS = 'mvn.cmd'
const SCRIPT_UNIX = 'mvn'
export const CMD_WINDOWS = 'C:\\Windows\\System32\\cmd.exe'

/** Emplacements d'installation fixes, essayés dans l'ordre après les variables d'environnement. */
export const EMPLACEMENTS_MAVEN = Object.freeze([
  'C:/Program Files/Apache/Maven/bin/mvn.cmd',
  'C:/Program Files/Maven/bin/mvn.cmd',
  '/usr/bin/mvn',
  '/usr/local/bin/mvn',
  '/opt/homebrew/bin/mvn',
  '/usr/share/maven/bin/mvn',
  '/opt/maven/bin/mvn',
])

// Caractères qui changent le sens d'une ligne cmd.exe ou sh, même entre guillemets (guillemets, variables,
// substitution de commande, échappement, enchaînement).
const INTERPRETES = /["%!^&|<>$`\r\n]/
const A_CITER = /[\s()]/

export const MESSAGE_MAVEN_INTROUVABLE = 'Maven introuvable : installez-le, ou indiquez le chemin de mvn (mvn.cmd sous '
  + 'Windows) dans ARGOS_E2E_MVN, ou son dossier d\'installation dans MAVEN_HOME.'
// Script lancé par Playwright à la place de Maven introuvable : il échoue avec MESSAGE_MAVEN_INTROUVABLE.
const SCRIPT_MAVEN_INTROUVABLE = fileURLToPath(new URL('./maven-introuvable.mjs', import.meta.url))

/**
 * Chemin absolu de Maven, ou undefined s'il est introuvable.
 * @param {{ env?: Record<string, string | undefined>, existe?: (chemin: string) => boolean,
 *   plateforme?: string, emplacements?: readonly string[] }} [options]
 */
export function cheminMaven({ env = process.env, existe = existsSync, plateforme = platform(),
  emplacements = EMPLACEMENTS_MAVEN } = {}) {
  if (env.ARGOS_E2E_MVN) return existe(env.ARGOS_E2E_MVN) ? env.ARGOS_E2E_MVN : undefined
  const script = plateforme === WINDOWS ? SCRIPT_WINDOWS : SCRIPT_UNIX
  const installations = [env.MAVEN_HOME, env.M2_HOME].filter(Boolean).map((dossier) => join(dossier, 'bin', script))
  return [...installations, ...emplacements].find((chemin) => existe(chemin))
}

/** Chemin absolu de Maven ; lève une erreur explicite s'il est introuvable. */
export function cheminMavenExige(options) {
  const chemin = cheminMaven(options)
  if (!chemin) throw new Error(MESSAGE_MAVEN_INTROUVABLE)
  return chemin
}

function citer(argument) {
  if (INTERPRETES.test(argument)) {
    throw new Error(`argument refusé pour l'interpréteur de commandes (caractère interprété) : ${argument}`)
  }
  return A_CITER.test(argument) || argument === '' ? `"${argument}"` : argument
}

/** Ligne de commande « "mvn" arg… » pour un interpréteur de commandes, arguments cités et contrôlés. */
export function ligneMaven(chemin, args) {
  return [chemin, ...args].map(citer).join(' ')
}

/**
 * Ligne de commande du serveur web de Playwright. Maven est résolu au chargement de la configuration sans lever
 * d'erreur : sans Maven trouvé, la ligne lance un script qui échoue avec le message explicite, si bien que l'absence
 * de Maven n'arrête Playwright que lorsqu'il doit démarrer le serveur (un serveur déjà prêt est réutilisé).
 * @param {string[]} args arguments de Maven
 * @param {Parameters<typeof cheminMaven>[0]} [options]
 */
export function ligneServeurMaven(args, options) {
  const chemin = cheminMaven(options)
  return chemin ? ligneMaven(chemin, args) : ligneMaven(process.execPath, [SCRIPT_MAVEN_INTROUVABLE])
}

/**
 * Commande, arguments et options de spawn qui lancent Maven sans interpréteur trouvé par le PATH : le script
 * directement hors de Windows, cmd.exe par son chemin absolu pour un script batch sous Windows.
 * @param {string} chemin chemin absolu de Maven
 * @param {string[]} args arguments de Maven
 * @param {{ plateforme?: string }} [options]
 */
export function lancementMaven(chemin, args, { plateforme = platform() } = {}) {
  if (plateforme === WINDOWS && /\.(cmd|bat)$/i.test(chemin)) {
    return {
      commande: CMD_WINDOWS,
      args: ['/d', '/s', '/c', `"${ligneMaven(chemin, args)}"`],
      options: { windowsVerbatimArguments: true },
    }
  }
  return { commande: chemin, args, options: {} }
}
