// Pilotage du client desktop argos-desktop par Playwright (`_electron`, API expérimentale de
// @playwright/test). L'application est lancée telle qu'elle est livrée, hors paquet : processus
// principal de argos-desktop/src/main.js, IHM argos-ui construite dans argos-ui/dist et servie par
// le protocole interne app://argos avec sa CSP. Aucun code de production n'est modifié pour les
// tests : les doublures (navigateur du système, garde réseau) sont installées dans le processus
// principal par `electronApp.evaluate`, depuis le test.
import { existsSync } from 'node:fs'
import { dirname, join } from 'node:path'
import { fileURLToPath } from 'node:url'
import { _electron, expect, type ElectronApplication, type Page, type Response } from '@playwright/test'
import { EMETTEUR, HOTES_LOCAUX, URL_API } from './environnement'
import { saisirIdentifiants } from './fixtures'

const RACINE = join(dirname(fileURLToPath(import.meta.url)), '..', '..')
export const DOSSIER_DESKTOP = join(RACINE, 'argos-desktop')
export const DOSSIER_IHM = join(RACINE, 'argos-ui')

const BINAIRE_PAR_PLATEFORME: Record<string, string> = {
  win32: 'electron.exe',
  darwin: join('Electron.app', 'Contents', 'MacOS', 'Electron'),
  linux: 'electron',
}

/**
 * Binaire Electron de argos-desktop (paquet `electron`, version du package-lock). ARGOS_E2E_ELECTRON
 * désigne un autre binaire de la même version, par exemple celui du dépôt principal depuis un
 * worktree où `npm ci` n'a pas pu le télécharger.
 */
export const BINAIRE_ELECTRON = process.env.ARGOS_E2E_ELECTRON
  ?? join(DOSSIER_DESKTOP, 'node_modules', 'electron', 'dist', BINAIRE_PAR_PLATEFORME[process.platform] ?? 'electron')

/** Lève une erreur explicite si le binaire Electron est absent : le parcours desktop échoue, il n'est pas sauté. */
export function verifierBinaireElectron(): void {
  if (!existsSync(BINAIRE_ELECTRON)) {
    throw new Error(
      `Binaire Electron introuvable (${BINAIRE_ELECTRON}) : installez-le dans argos-desktop `
      + '(script d’installation du paquet electron) ou indiquez son chemin dans ARGOS_E2E_ELECTRON.',
    )
  }
}

/** Requête vue par la session du desktop (webRequest du processus principal). */
export interface RequeteDesktop {
  /** Adresse sans chaîne de requête ni fragment : aucun code ni jeton n'est conservé. */
  adresse: string
  protocole: string
  hote: string
  methode: string
  /** Type Chromium : mainFrame, subFrame, script, xhr… */
  type: string
  /** Vrai si la requête portait un en-tête `Authorization: Bearer` (la valeur n'est pas lue). */
  porteur: boolean
  /** Statut HTTP, ou null si la requête n'a pas abouti. */
  statut: number | null
  /** Vrai si la garde a annulé la requête (hôte hors de la boucle locale). */
  refusee: boolean
}

/** État de la fenêtre principale lu dans le processus principal. */
export interface EtatFenetre {
  visible: boolean
  reduite: boolean
  focus: boolean
  adresse: string
  /** Événements de la fenêtre observés depuis l'installation des doublures (show, restore, focus…). */
  evenements: string[]
}

export interface ApplicationDesktop {
  app: ElectronApplication
  /** Fenêtre principale (IHM servie par app://argos). */
  fenetre: Page
  /** Requêtes de la session depuis le rechargement qui suit l'installation de la garde. */
  journal(): Promise<RequeteDesktop[]>
  /** Adresses que l'application a demandé d'ouvrir dans le navigateur du système. */
  ouvertures(): Promise<string[]>
  etatFenetre(): Promise<EtatFenetre>
  fermer(): Promise<void>
}

export interface OptionsLancement {
  /** Dossier des données de l'application (userData : réglages du poste, verrou d'instance unique). */
  dossierDonnees: string
}

/**
 * Lance argos-desktop contre la pile locale (API URL_API, émetteur EMETTEUR), installe les doublures
 * dans le processus principal puis recharge la fenêtre pour que le journal couvre tout son chargement.
 * Le dossier de données propre au lancement isole le verrou d'instance unique et les réglages d'un
 * ARGOS ouvert sur le poste.
 */
export async function lancerDesktop(options: OptionsLancement): Promise<ApplicationDesktop> {
  verifierBinaireElectron()
  const env: Record<string, string> = {}
  for (const [cle, valeur] of Object.entries(process.env)) {
    if (valeur !== undefined && cle !== 'ELECTRON_RUN_AS_NODE' && cle !== 'ARGOS_UI_DEV_URL') env[cle] = valeur
  }
  const app = await _electron.launch({
    executablePath: BINAIRE_ELECTRON,
    args: [DOSSIER_DESKTOP, `--user-data-dir=${options.dossierDonnees}`],
    cwd: DOSSIER_DESKTOP,
    env: { ...env, ARGOS_API_URL: URL_API, ARGOS_OIDC_URL: EMETTEUR },
  })
  try {
    await installerDoublures(app)
    const fenetre = await app.firstWindow()
    await fenetre.waitForLoadState('domcontentloaded')
    await app.evaluate(({ BrowserWindow }) => {
      const premiere = BrowserWindow.getAllWindows()[0]
      ;(globalThis as unknown as DoubluresArgos).__argosE2E.observerFenetre(premiere)
    })
    await fenetre.reload()
    await expect(fenetre).toHaveURL(/^app:\/\/argos\/index\.html/)
    return {
      app,
      fenetre,
      journal: () => app.evaluate(() => (globalThis as unknown as DoubluresArgos).__argosE2E.journal.map((r) => ({ ...r }))),
      ouvertures: () => app.evaluate(() => [...(globalThis as unknown as DoubluresArgos).__argosE2E.ouvertures]),
      etatFenetre: () => app.evaluate(({ BrowserWindow }) => {
        const f = BrowserWindow.getAllWindows()[0]
        return {
          visible: f.isVisible(),
          reduite: f.isMinimized(),
          focus: f.isFocused(),
          adresse: f.webContents.getURL(),
          evenements: [...(globalThis as unknown as DoubluresArgos).__argosE2E.evenements],
        }
      }),
      fermer: () => app.close(),
    }
  } catch (erreur) {
    await app.close()
    throw erreur
  }
}

/** Partie de BrowserWindow utilisée par les doublures. */
interface FenetreObservable {
  on(evenement: string, ecouteur: () => void): unknown
}

/** État partagé des doublures, dans le processus principal. */
interface DoubluresArgos {
  __argosE2E: {
    journal: RequeteDesktop[]
    ouvertures: string[]
    evenements: string[]
    observerFenetre(fenetre: FenetreObservable): void
  }
}

/**
 * Doublures du processus principal :
 * - navigateur du système : `shell.openExternal` enregistre l'adresse au lieu d'ouvrir le navigateur
 *   du poste ; le test l'ouvre dans une page Chromium pilotée par Playwright ;
 * - garde réseau : toute requête de la session vers un hôte hors de la boucle locale est annulée et
 *   journalisée ; les autres sont journalisées sans chaîne de requête ni valeur d'en-tête.
 */
async function installerDoublures(app: ElectronApplication): Promise<void> {
  await app.evaluate(({ session, shell }, hotesLocaux) => {
    const etat = {
      journal: [] as RequeteDesktop[],
      ouvertures: [] as string[],
      evenements: [] as string[],
      observerFenetre(fenetre: FenetreObservable) {
        for (const nom of ['show', 'hide', 'minimize', 'restore', 'focus', 'blur'] as const) {
          fenetre.on(nom, () => etat.evenements.push(nom))
        }
      },
    }
    ;(globalThis as unknown as DoubluresArgos).__argosE2E = etat
    shell.openExternal = async (url: string) => {
      etat.ouvertures.push(url)
    }
    const parId = new Map<number, RequeteDesktop>()
    const locaux = new Set(hotesLocaux)
    const web = session.defaultSession.webRequest
    web.onBeforeRequest((details, callback) => {
      const url = new URL(details.url)
      const reseau = ['http:', 'https:', 'ws:', 'wss:'].includes(url.protocol)
      const refusee = reseau && !locaux.has(url.hostname)
      const requete: RequeteDesktop = {
        adresse: `${url.protocol}//${url.host}${url.pathname}`,
        protocole: url.protocol,
        hote: url.host,
        methode: details.method,
        type: details.resourceType,
        porteur: false,
        statut: null,
        refusee,
      }
      parId.set(details.id, requete)
      etat.journal.push(requete)
      callback({ cancel: refusee })
    })
    web.onBeforeSendHeaders((details, callback) => {
      const requete = parId.get(details.id)
      const autorisation = Object.entries(details.requestHeaders)
        .find(([nom]) => nom.toLowerCase() === 'authorization')?.[1]
      if (requete) requete.porteur = typeof autorisation === 'string' && autorisation.startsWith('Bearer ')
      callback({ requestHeaders: details.requestHeaders })
    })
    web.onCompleted((details) => {
      const requete = parId.get(details.id)
      if (requete) requete.statut = details.statusCode
    })
  }, [...HOTES_LOCAUX])
}

/** Réduit la fenêtre principale, comme lorsque la personne passe dans son navigateur. */
export async function reduireFenetre(desktop: ApplicationDesktop): Promise<void> {
  await desktop.app.evaluate(({ BrowserWindow }) => BrowserWindow.getAllWindows()[0].minimize())
  await expect.poll(async () => (await desktop.etatFenetre()).reduite).toBe(true)
}

/** Attend la prochaine adresse ouverte dans le « navigateur du système » et la renvoie. */
export async function attendreOuverture(desktop: ApplicationDesktop, deja = 0): Promise<string> {
  await expect.poll(async () => (await desktop.ouvertures()).length, { timeout: 15_000 }).toBeGreaterThan(deja)
  return (await desktop.ouvertures())[deja]
}

export interface ConnexionDesktop {
  /** Adresse d'autorisation reçue par le navigateur du système. */
  urlAutorisation: URL
  /** Réponse de l'écoute locale 127.0.0.1 à la redirection du fournisseur. */
  retour: Response
}

/**
 * Connexion d'un compte du royaume de test depuis l'écran de connexion du desktop : clic sur
 * « Se connecter » dans la fenêtre, adresse d'autorisation reçue par la doublure du navigateur du
 * système, saisie des identifiants sur Keycloak dans `navigateur`, puis redirection suivie jusqu'à
 * l'écoute locale du desktop. `avantRetour` s'exécute une fois l'adresse reçue, avant la saisie.
 */
export async function connecterCompte(
  desktop: ApplicationDesktop,
  navigateur: Page,
  utilisateur: string,
  avantRetour?: () => Promise<void>,
): Promise<ConnexionDesktop> {
  const deja = (await desktop.ouvertures()).length
  await expect(desktop.fenetre.getByRole('heading', { name: 'Connexion' })).toBeVisible()
  await desktop.fenetre.getByRole('button', { name: 'Se connecter' }).click()
  const urlAutorisation = new URL(await attendreOuverture(desktop, deja))
  if (avantRetour) await avantRetour()
  const retour = navigateur.waitForResponse((r) => /^http:\/\/127\.0\.0\.1:\d+\/argos\/retour\?/.test(r.url()))
  await navigateur.goto(urlAutorisation.toString())
  await saisirIdentifiants(navigateur, utilisateur)
  return { urlAutorisation, retour: await retour }
}
