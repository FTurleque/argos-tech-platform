// Pilotage du client desktop argos-desktop par Playwright (`_electron`, API expérimentale de
// @playwright/test). L'application est lancée telle qu'elle est livrée, hors paquet : processus
// principal de argos-desktop/src/main.js, IHM argos-ui construite dans argos-ui/dist et servie par
// le protocole interne app://argos avec sa CSP. Aucun code de production n'est modifié pour les
// tests : les doublures (navigateur du système, garde réseau, coupure du serveur, observation des
// notifications et de la zone de notification) sont installées dans le processus principal par
// `electronApp.evaluate`, depuis le test.
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
  /** Vrai si la requête a été annulée par une coupure simulée du serveur (`couper`). */
  coupee: boolean
}

/** Notification système créée par le processus principal (objet `Notification` réel d'Electron). */
export interface NotificationObservee {
  titre: string
  corps: string
}

/** Refus simulé des notifications par le système (`refuserNotificationsSysteme`). */
export type RefusSysteme = 'non-supportees' | 'reglages-windows'

/** Message de l'événement `failed` d'Electron sous Windows quand les réglages refusent la notification. */
const REFUS_REGLAGES_WINDOWS =
  'Notification failed - Settings prevent the notification from being delivered. (HRESULT: -2143420143)'

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
  /** Notifications affichées (`show`) depuis l'installation des doublures, dans l'ordre. */
  notifications(): Promise<NotificationObservee[]>
  /** Émet l'événement `click` de la notification `rang` (0 pour la première), comme le système au clic. */
  cliquerNotification(rang: number): Promise<void>
  /** Infobulles successives de l'icône de la zone de notification (`Tray.setToolTip`). */
  infobulles(): Promise<string[]>
  /** Émet l'événement `click` de l'icône de la zone de notification, comme le système au clic. */
  cliquerZoneNotification(): Promise<void>
  /**
   * Refus simulé des notifications par le système : `non-supportees`, `Notification.isSupported`
   * renvoie faux ; `reglages-windows`, l'appel au système est remplacé par l'événement `failed` que
   * Windows fait émettre quand ses réglages refusent la notification.
   */
  refuserNotificationsSysteme(mode: RefusSysteme): Promise<void>
  /** Fin du refus simulé : `isSupported` et `show` d'origine. */
  accepterNotificationsSysteme(): Promise<void>
  /** Notifications demandées puis refusées par les réglages de Windows simulés, dans l'ordre. */
  notificationsRefusees(): Promise<NotificationObservee[]>
  /**
   * Coupure simulée : toute requête de la session vers `hote` (hôte et port, par exemple
   * localhost:8090) est annulée, comme si le serveur était injoignable ; le serveur reste en marche.
   */
  couper(hote: string): Promise<void>
  /** Fin de la coupure simulée. */
  retablir(): Promise<void>
  fermer(): Promise<void>
}

export interface OptionsLancement {
  /** Dossier des données de l'application (userData : réglages du poste, verrou d'instance unique). */
  dossierDonnees: string
}

/** Variables d'environnement du test qui changeraient le mode de lancement du desktop : non transmises. */
const VARIABLES_EXCLUES = new Set(['ELECTRON_RUN_AS_NODE', 'ARGOS_UI_DEV_URL'])

/** Environnement du processus desktop : celui du test, pointé sur l'API et l'émetteur de la pile locale. */
function environnementDesktop(): Record<string, string> {
  const variables: Record<string, string> = {}
  for (const [cle, valeur] of Object.entries(process.env)) {
    if (valeur !== undefined && !VARIABLES_EXCLUES.has(cle)) variables[cle] = valeur
  }
  return { ...variables, ARGOS_API_URL: URL_API, ARGOS_OIDC_URL: EMETTEUR }
}

/**
 * Installe les doublures, observe la fenêtre principale puis la recharge pour que le journal couvre
 * tout son chargement ; renvoie la fenêtre servie par app://argos.
 */
async function preparerFenetre(app: ElectronApplication): Promise<Page> {
  await installerDoublures(app)
  const fenetre = await app.firstWindow()
  await fenetre.waitForLoadState('domcontentloaded')
  await app.evaluate(({ BrowserWindow }) => {
    const premiere = BrowserWindow.getAllWindows()[0]
    ;globalThis.__argosE2E.observerFenetre(premiere)
  })
  await fenetre.reload()
  await expect(fenetre).toHaveURL(/^app:\/\/argos\/index\.html/)
  return fenetre
}

/** Lecture du journal réseau, des ouvertures du navigateur du système et de l'état de la fenêtre. */
function observationsSession(app: ElectronApplication): Pick<ApplicationDesktop, 'journal' | 'ouvertures' | 'etatFenetre'> {
  return {
    journal: () => app.evaluate(() => globalThis.__argosE2E.journal.map((requete) => ({ ...requete }))),
    ouvertures: () => app.evaluate(() => [...globalThis.__argosE2E.ouvertures]),
    etatFenetre: () => app.evaluate(({ BrowserWindow }) => {
      const fenetrePrincipale = BrowserWindow.getAllWindows()[0]
      return {
        visible: fenetrePrincipale.isVisible(),
        reduite: fenetrePrincipale.isMinimized(),
        focus: fenetrePrincipale.isFocused(),
        adresse: fenetrePrincipale.webContents.getURL(),
        evenements: [...globalThis.__argosE2E.evenements],
      }
    }),
  }
}

/** Observation et clic des notifications système. */
function commandesNotifications(app: ElectronApplication): Pick<ApplicationDesktop, 'notifications' | 'cliquerNotification'> {
  return {
    notifications: () => app.evaluate(() => globalThis.__argosE2E.notifications
      .map((notification) => ({ titre: notification.title, corps: notification.body }))),
    cliquerNotification: (rang) => app.evaluate((_electron, position) => {
      const notification = globalThis.__argosE2E.notifications[position]
      if (!notification) throw new Error(`notification ${position} absente`)
      notification.emit('click')
    }, rang),
  }
}

/** Infobulles et clic de l'icône de la zone de notification. */
function commandesZoneNotification(app: ElectronApplication): Pick<ApplicationDesktop, 'infobulles' | 'cliquerZoneNotification'> {
  return {
    infobulles: () => app.evaluate(() => [...globalThis.__argosE2E.infobulles]),
    cliquerZoneNotification: () => app.evaluate(() => {
      const icone = globalThis.__argosE2E.icone
      if (!icone) throw new Error('icône de la zone de notification non observée')
      icone.emit('click')
    }),
  }
}

/** Refus simulé des notifications par le système et lecture des notifications refusées. */
function commandesRefusSysteme(
  app: ElectronApplication,
): Pick<ApplicationDesktop, 'refuserNotificationsSysteme' | 'accepterNotificationsSysteme' | 'notificationsRefusees'> {
  return {
    refuserNotificationsSysteme: (mode) => app.evaluate((_electron, refus) => {
      globalThis.__argosE2E.refus = refus
    }, mode),
    accepterNotificationsSysteme: () => app.evaluate(() => {
      globalThis.__argosE2E.refus = null
    }),
    notificationsRefusees: () => app.evaluate(() => globalThis.__argosE2E.refusees
      .map((notification) => ({ titre: notification.title, corps: notification.body }))),
  }
}

/** Coupure simulée du serveur et son rétablissement. */
function commandesCoupure(app: ElectronApplication): Pick<ApplicationDesktop, 'couper' | 'retablir'> {
  return {
    couper: (hote) => app.evaluate((_electron, hoteCoupe) => {
      globalThis.__argosE2E.coupures.add(hoteCoupe)
    }, hote),
    retablir: () => app.evaluate(() => {
      globalThis.__argosE2E.coupures.clear()
    }),
  }
}

/**
 * Lance argos-desktop contre la pile locale (API URL_API, émetteur EMETTEUR), installe les doublures
 * dans le processus principal puis recharge la fenêtre pour que le journal couvre tout son chargement.
 * Le dossier de données propre au lancement isole le verrou d'instance unique et les réglages d'un
 * ARGOS ouvert sur le poste.
 */
export async function lancerDesktop(options: OptionsLancement): Promise<ApplicationDesktop> {
  verifierBinaireElectron()
  const app = await _electron.launch({
    executablePath: BINAIRE_ELECTRON,
    args: [DOSSIER_DESKTOP, `--user-data-dir=${options.dossierDonnees}`],
    cwd: DOSSIER_DESKTOP,
    env: environnementDesktop(),
  })
  try {
    const fenetre = await preparerFenetre(app)
    return {
      app,
      fenetre,
      ...observationsSession(app),
      ...commandesNotifications(app),
      ...commandesZoneNotification(app),
      ...commandesRefusSysteme(app),
      ...commandesCoupure(app),
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

/** Partie des détails d'une requête de `session.webRequest` lue par la garde réseau. */
interface DetailsRequete {
  id: number
  url: string
  method: string
  resourceType: string
}

/** Détails d'une requête avant l'envoi de ses en-têtes. */
interface DetailsEnvoiEntetes extends DetailsRequete {
  requestHeaders: Record<string, string>
}

/** Détails d'une requête terminée. */
interface DetailsRequeteTerminee extends DetailsRequete {
  statusCode: number
}

/** Partie de Notification utilisée par les doublures. */
interface NotificationElectron {
  title: string
  body: string
  emit(evenement: string, ...args: unknown[]): boolean
}

/**
 * État partagé des doublures, dans le processus principal, porté par `globalThis.__argosE2E`.
 * Les fonctions passées à `app.evaluate` sont sérialisées puis exécutées dans ce processus : elles ne
 * peuvent appeler aucune fonction du test, si bien que le typage passe par cette déclaration globale.
 */
declare global {
  var __argosE2E: DoubluresArgos
}

/** Champs de l'état partagé des doublures. */
interface DoubluresArgos {
  journal: RequeteDesktop[]
  ouvertures: string[]
  evenements: string[]
  /** Objets Notification affichés ; la référence gardée permet aussi d'émettre leur `click`. */
  notifications: NotificationElectron[]
  /** Notifications refusées par les réglages de Windows simulés (`failed` émis, système non appelé). */
  refusees: NotificationElectron[]
  /** Refus simulé en cours, ou null. */
  refus: RefusSysteme | null
  infobulles: string[]
  /** Icône de la zone de notification, gardée au premier appel de `setToolTip` observé. */
  icone: { emit(evenement: string): boolean } | null
  /** Hôtes (avec port) dont les requêtes sont annulées : coupure simulée du serveur. */
  coupures: Set<string>
  observerFenetre(fenetre: FenetreObservable): void
}

/**
 * Doublures du processus principal :
 * - navigateur du système : `shell.openExternal` enregistre l'adresse au lieu d'ouvrir le navigateur
 *   du poste ; le test l'ouvre dans une page Chromium pilotée par Playwright ;
 * - garde réseau : toute requête de la session vers un hôte hors de la boucle locale est annulée et
 *   journalisée ; les autres sont journalisées sans chaîne de requête ni valeur d'en-tête ;
 * - coupure du serveur : les requêtes vers un hôte coupé (`couper`) sont annulées, comme si le serveur
 *   était injoignable ;
 * - notifications : `Notification.prototype.show` est enveloppé pour garder chaque notification
 *   affichée (titre, corps, objet réel), puis appelle le `show` d'origine : la notification système
 *   est bien demandée au système ;
 * - refus du système (`refuserNotificationsSysteme`) : `Notification.isSupported` enveloppé renvoie
 *   faux en mode `non-supportees` ; en mode `reglages-windows`, `show` émet `failed` au lieu d'appeler
 *   le système ;
 * - zone de notification : `Tray.prototype.setToolTip` est enveloppé pour garder chaque infobulle,
 *   puis appelle l'original (Electron ne permet pas de relire l'infobulle).
 *
 * Chaque doublure est installée par son propre `app.evaluate` et lit l'état partagé : l'état est
 * donc créé en premier, puis la garde réseau, la plus sensible, avant les autres doublures.
 */
async function installerDoublures(app: ElectronApplication): Promise<void> {
  await installerEtatDoublures(app)
  await installerGardeReseau(app)
  await doublerNavigateurSysteme(app)
  await doublerNotifications(app)
  await doublerZoneNotification(app)
}

/** Crée l'état partagé `globalThis.__argosE2E`, vide, avec l'observation des événements de fenêtre. */
async function installerEtatDoublures(app: ElectronApplication): Promise<void> {
  await app.evaluate(() => {
    const evenements: string[] = []
    const observerFenetre = (fenetre: FenetreObservable) => {
      for (const nom of ['show', 'hide', 'minimize', 'restore', 'focus', 'blur']) fenetre.on(nom, () => evenements.push(nom))
    }
    globalThis.__argosE2E = {
      journal: [],
      ouvertures: [],
      evenements,
      notifications: [],
      refusees: [],
      refus: null,
      infobulles: [],
      icone: null,
      coupures: new Set<string>(),
      observerFenetre,
    }
  })
}

/** `shell.openExternal` enregistre l'adresse au lieu d'ouvrir le navigateur du poste. */
async function doublerNavigateurSysteme(app: ElectronApplication): Promise<void> {
  await app.evaluate(({ shell }) => {
    shell.openExternal = async (url: string) => {
      globalThis.__argosE2E.ouvertures.push(url)
    }
  })
}

/** Enveloppe `Notification.isSupported` et `Notification.prototype.show` : observation et refus simulé. */
async function doublerNotifications(app: ElectronApplication): Promise<void> {
  await app.evaluate(({ Notification }, refusWindows) => {
    const etat = globalThis.__argosE2E
    const isSupported = Notification.isSupported
    Notification.isSupported = () => etat.refus !== 'non-supportees' && isSupported.call(Notification)
    const show = Notification.prototype.show
    Notification.prototype.show = function (this: InstanceType<typeof Notification>) {
      const notification = this as unknown as NotificationElectron
      if (etat.refus === 'reglages-windows') {
        etat.refusees.push(notification)
        notification.emit('failed', { preventDefault: () => undefined }, refusWindows)
        return
      }
      etat.notifications.push(notification)
      return show.call(this)
    }
  }, REFUS_REGLAGES_WINDOWS)
}

/** Enveloppe `Tray.prototype.setToolTip` pour garder chaque infobulle et l'icône qui la porte. */
async function doublerZoneNotification(app: ElectronApplication): Promise<void> {
  await app.evaluate(({ Tray }) => {
    const etat = globalThis.__argosE2E
    const setToolTip = Tray.prototype.setToolTip
    Tray.prototype.setToolTip = function (this: InstanceType<typeof Tray>, texte: string) {
      etat.infobulles.push(texte)
      etat.icone = this as unknown as { emit(evenement: string): boolean }
      return setToolTip.call(this, texte)
    }
  })
}

/**
 * Garde réseau de la session : journalise chaque requête sans chaîne de requête ni valeur d'en-tête,
 * annule celles vers un hôte hors de la boucle locale ou vers un hôte coupé, puis complète l'entrée
 * du journal par la présence du jeton porteur et par le statut HTTP.
 */
async function installerGardeReseau(app: ElectronApplication): Promise<void> {
  await app.evaluate(({ session }, hotesLocaux) => {
    const etat = globalThis.__argosE2E
    const parId = new Map<number, RequeteDesktop>()
    const locaux = new Set(hotesLocaux)
    const protocolesReseau = new Set(['http:', 'https:', 'ws:', 'wss:'])
    const decrireRequete = (details: DetailsRequete): RequeteDesktop => {
      const url = new URL(details.url)
      const reseau = protocolesReseau.has(url.protocol)
      const refusee = reseau && !locaux.has(url.hostname)
      return {
        adresse: `${url.protocol}//${url.host}${url.pathname}`,
        protocole: url.protocol,
        hote: url.host,
        methode: details.method,
        type: details.resourceType,
        porteur: false,
        statut: null,
        refusee,
        coupee: reseau && !refusee && etat.coupures.has(url.host),
      }
    }
    const web = session.defaultSession.webRequest
    web.onBeforeRequest((details: DetailsRequete, callback: (reponse: { cancel: boolean }) => void) => {
      const requete = decrireRequete(details)
      parId.set(details.id, requete)
      etat.journal.push(requete)
      callback({ cancel: requete.refusee || requete.coupee })
    })
    web.onBeforeSendHeaders((details: DetailsEnvoiEntetes,
      callback: (reponse: { requestHeaders: Record<string, string> }) => void) => {
      const requete = parId.get(details.id)
      const autorisation = Object.entries(details.requestHeaders)
        .find(([nom]) => nom.toLowerCase() === 'authorization')?.[1]
      if (requete) requete.porteur = typeof autorisation === 'string' && autorisation.startsWith('Bearer ')
      callback({ requestHeaders: details.requestHeaders })
    })
    web.onCompleted((details: DetailsRequeteTerminee) => {
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

/**
 * Masque la fenêtre principale comme le bouton de fermeture : main.js la cache au lieu de la fermer,
 * et l'application reste dans la zone de notification.
 */
export async function masquerFenetre(desktop: ApplicationDesktop): Promise<void> {
  await desktop.app.evaluate(({ BrowserWindow }) => BrowserWindow.getAllWindows()[0].close())
  await expect.poll(async () => (await desktop.etatFenetre()).visible).toBe(false)
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
  const retour = navigateur.waitForResponse((reponse) => /^http:\/\/127\.0\.0\.1:\d+\/argos\/retour\?/.test(reponse.url()))
  await navigateur.goto(urlAutorisation.toString())
  await saisirIdentifiants(navigateur, utilisateur)
  return { urlAutorisation, retour: await retour }
}

/**
 * Ouvre une vue protégée du desktop avec le compte donné : la vue demandée sans session mène à
 * l'écran de connexion, la connexion passe par le navigateur du système (`connecterCompte`), puis
 * la fenêtre revient sur la vue demandée avec la session ouverte.
 */
export async function ouvrirVueConnectee(
  desktop: ApplicationDesktop,
  navigateur: Page,
  utilisateur: string,
  vue: string,
): Promise<void> {
  const { fenetre } = desktop
  await fenetre.evaluate((chemin) => { window.location.hash = `#${chemin}` }, vue)
  await expect(fenetre).toHaveURL((url) => url.hash === `#/connexion?retour=${vue}`)
  const { retour } = await connecterCompte(desktop, navigateur, utilisateur)
  expect(retour.status()).toBe(200)
  await expect(fenetre.getByRole('button', { name: 'Se déconnecter' })).toBeVisible()
  await expect(fenetre).toHaveURL((url) => url.hash === `#${vue}`)
}

/** Vrai quand le journal contient une requête GET aboutie (200) sur ce chemin de l'API, avec le jeton porteur. */
export async function appelApiAuthentifie(desktop: ApplicationDesktop, chemin: string | RegExp): Promise<boolean> {
  return (await desktop.journal()).some((requete) => {
    if (requete.methode !== 'GET' || !requete.porteur || requete.statut !== 200 || !requete.adresse.startsWith(`${URL_API}/`)) return false
    const relatif = requete.adresse.slice(URL_API.length)
    return typeof chemin === 'string' ? relatif === chemin : chemin.test(relatif)
  })
}

/** Journal de la session : aucune requête vers un hôte hors de la boucle locale, aucun document hors de app://argos. */
export async function verifierJournalLocal(desktop: ApplicationDesktop): Promise<void> {
  const journal = await desktop.journal()
  expect(journal.filter((requete) => requete.refusee).map((requete) => requete.adresse), 'hôtes hors de la boucle locale').toEqual([])
  for (const requete of journal.filter((candidate) => candidate.type === 'mainFrame' || candidate.type === 'subFrame')) {
    expect(requete.protocole, requete.adresse).toBe('app:')
  }
}
