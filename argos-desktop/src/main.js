'use strict';
// Client desktop ARGOS (ADR-0022) : emballe l'IHM argos-ui sans y ajouter de logique métier.
// Apports propres au desktop : icône et compteur dans la zone de notification, notifications
// système des alertes, réglages du poste (changement notifier-sur-le-poste).
// Fermer la fenêtre la masque seulement, pour que les alertes continuent d'arriver ; quitter
// l'application arrête tout : aucun service ne tourne en arrière-plan (Q-1).

const path = require('node:path');
const { pathToFileURL } = require('node:url');
const systemeFichiers = require('node:fs');
const {
  app, BrowserWindow, Menu, Notification, Tray, ipcMain, nativeImage, net, protocol, session, shell,
} = require('electron');
const { SCHEME, ORIGIN, contentSecurityPolicy, resolveUiFile, mimeType } = require('./ui-protocol');
const { libelleCompteur } = require('./notifications');
const preferences = require('./preferences');
const { creerPoste } = require('./poste');
const canaux = require('./canaux');
const { creerConnexion } = require('./connexion');
const http = require('node:http');

const APP_ID = 'io.argos.desktop';
const DEV = process.argv.includes('--dev');
const DEV_URL = process.env.ARGOS_UI_DEV_URL || 'http://localhost:5173';
// Réglages fixés à l'emballage : champ `argos` du package.json livré (`apiUrl`, `oidcUrl`), renseigné par
// `electron-builder -c.extraMetadata.argos.apiUrl=…` ; la variable d'environnement garde la priorité.
const REGLAGES_EMBALLES = require('../package.json').argos || {};
// URL du serveur ARGOS : sert uniquement à la politique de sécurité du contenu.
// L'IHM lit la sienne au moment du build (VITE_ARGOS_API_URL).
const API_URL = process.env.ARGOS_API_URL || REGLAGES_EMBALLES.apiUrl || 'http://localhost:8080';
// Émetteur OIDC (royaume Keycloak) : origine ajoutée à la CSP et seule destination acceptée pour
// l'autorisation ouverte dans le navigateur du système (changement connecter-ihm-oidc, ACC-10).
// L'IHM lit le sien au moment du build (VITE_ARGOS_OIDC_ISSUER) ; les deux doivent coïncider.
const OIDC_URL = process.env.ARGOS_OIDC_URL || REGLAGES_EMBALLES.oidcUrl || 'http://localhost:8180/realms/argos';
const UI_DIR = app.isPackaged
  ? path.join(process.resourcesPath, 'ui')
  : path.join(__dirname, '..', '..', 'argos-ui', 'dist');
const ASSETS = path.join(__dirname, '..', 'assets');
// Argument ajouté au lancement automatique avec la session : la fenêtre reste alors masquée.
const ARG_DEMARRAGE = '--demarrage-session';
// Le démarrage avec la session n'est proposé que là où Electron sait le configurer.
const DEMARRAGE_DISPONIBLE = process.platform === 'win32' || process.platform === 'darwin';
// Taille de la fenêtre principale, en pixels : à l'ouverture, puis minimale pour garder l'IHM lisible.
const FENETRE_LARGEUR = 1200;
const FENETRE_HAUTEUR = 820;
const FENETRE_LARGEUR_MIN = 820;
const FENETRE_HAUTEUR_MIN = 560;

let mainWindow = null;
let tray = null;
let quitting = false;
let fichierReglages = null;
let reglages = { ...preferences.DEFAUTS };

protocol.registerSchemesAsPrivileged([
  { scheme: SCHEME, privileges: { standard: true, secure: true, supportFetchAPI: true } },
]);

// Une seule instance : un second lancement remet la fenêtre existante au premier plan.
if (!app.requestSingleInstanceLock()) {
  app.quit();
}

function showMainWindow() {
  canaux.afficherFenetre(mainWindow);
}

function isExternalWebUrl(url) {
  try {
    const { protocol: schema } = new URL(url);
    return schema === 'https:' || schema === 'http:';
  } catch {
    return false;
  }
}

function registerUiProtocol() {
  const csp = contentSecurityPolicy(API_URL, OIDC_URL);
  protocol.handle(SCHEME, async (request) => {
    const file = resolveUiFile(request.url, UI_DIR);
    if (!file || !systemeFichiers.existsSync(file)) {
      return new Response('Introuvable', { status: 404 });
    }
    const response = await net.fetch(pathToFileURL(file).toString());
    return new Response(response.body, {
      status: 200,
      headers: { 'Content-Type': mimeType(file), 'Content-Security-Policy': csp },
    });
  });
}

function createWindow() {
  mainWindow = new BrowserWindow({
    width: FENETRE_LARGEUR,
    height: FENETRE_HAUTEUR,
    minWidth: FENETRE_LARGEUR_MIN,
    minHeight: FENETRE_HAUTEUR_MIN,
    title: 'ARGOS',
    icon: path.join(ASSETS, 'icon.png'),
    show: false,
    autoHideMenuBar: true,
    webPreferences: {
      preload: path.join(__dirname, 'preload.js'),
      contextIsolation: true,
      nodeIntegration: false,
      sandbox: true,
      // La lecture périodique des alertes tourne dans la page, même fenêtre masquée : sans cela,
      // Chromium espace les minuteries d'une page cachée et le délai de 30 s n'est plus tenu.
      backgroundThrottling: false,
    },
  });

  mainWindow.once('ready-to-show', () => {
    if (!lanceAvecLaSession()) mainWindow.show();
  });

  // Les liens externes s'ouvrent dans le navigateur du système, jamais dans l'application.
  mainWindow.webContents.setWindowOpenHandler(({ url }) => {
    if (isExternalWebUrl(url)) shell.openExternal(url);
    return { action: 'deny' };
  });
  mainWindow.webContents.on('will-navigate', (event, url) => {
    const allowed = DEV ? url.startsWith(DEV_URL) : url.startsWith(ORIGIN + '/');
    if (!allowed) {
      event.preventDefault();
      if (isExternalWebUrl(url)) shell.openExternal(url);
    }
  });

  mainWindow.on('close', (event) => {
    if (!quitting) {
      event.preventDefault();
      mainWindow.hide();
    }
  });

  mainWindow.loadURL(DEV ? DEV_URL : `${ORIGIN}/index.html`);
}

function lanceAvecLaSession() {
  if (process.argv.includes(ARG_DEMARRAGE)) return true;
  return process.platform === 'darwin' && app.getLoginItemSettings().wasOpenedAtLogin === true;
}

function appliquerDemarrage() {
  if (!DEMARRAGE_DISPONIBLE) return;
  app.setLoginItemSettings({ openAtLogin: reglages.demarrageSession, args: [ARG_DEMARRAGE] });
}

function enregistrerReglages(nouveaux) {
  reglages = nouveaux;
  try {
    preferences.ecrire(systemeFichiers, fichierReglages, reglages);
  } catch (error) {
    console.error('Réglages du poste non enregistrés :', error.message);
  }
  appliquerDemarrage();
  construireMenu();
  if (mainWindow) mainWindow.webContents.send(canaux.CANAUX.REGLAGES_CHANGES, vueReglages());
}

function vueReglages() {
  return { ...reglages, demarrageSessionDisponible: DEMARRAGE_DISPONIBLE };
}

function ouvrirAlerte(id) {
  canaux.ouvrirAlerte(mainWindow, id);
}

const poste = creerPoste({
  notificationsSupportees: () => Notification.isSupported(),
  creerNotification: ({ title, body }) => new Notification({ title, body, icon: path.join(ASSETS, 'icon.png') }),
  reglages: () => reglages,
  enregistrerReglages,
  afficherCompteur: () => {
    if (tray) tray.setToolTip(poste.infobulle());
    construireMenu();
    app.setBadgeCount(poste.compteur()); // macOS et Linux (Unity) ; sans effet ailleurs
  },
  ouvrirAlerte,
  demarrageDisponible: DEMARRAGE_DISPONIBLE,
});

function construireMenu() {
  if (!tray) return;
  tray.setContextMenu(Menu.buildFromTemplate([
    { label: 'Ouvrir ARGOS', click: showMainWindow },
    { label: libelleCompteur(poste.compteur()), enabled: false },
    { type: 'separator' },
    {
      label: 'Notifications du poste',
      type: 'checkbox',
      checked: reglages.notificationsPoste,
      click: (item) => poste.changerReglages({ notificationsPoste: item.checked }),
    },
    {
      label: 'Démarrer avec la session',
      type: 'checkbox',
      checked: reglages.demarrageSession,
      enabled: DEMARRAGE_DISPONIBLE,
      click: (item) => poste.changerReglages({ demarrageSession: item.checked }),
    },
    { type: 'separator' },
    { label: 'Quitter ARGOS', click: () => { quitting = true; app.quit(); } },
  ]));
}

function createTray() {
  tray = new Tray(nativeImage.createFromPath(path.join(ASSETS, 'tray.png')));
  tray.setToolTip(poste.infobulle());
  construireMenu();
  tray.on('click', showMainWindow);
}

// Connexion OIDC : navigateur du système et redirection vers 127.0.0.1 (RFC 8252, ADR-0022 point 6).
const connexion = creerConnexion({
  http,
  ouvrirNavigateur: (url) => shell.openExternal(url),
  emetteur: OIDC_URL,
  afficherFenetre: showMainWindow,
});

// Seule la fenêtre principale peut parler au processus principal (canaux.js).
canaux.enregistrerCanaux(ipcMain, {
  connexion,
  fenetre: () => mainWindow,
  poste,
  notifier: ({ title, body }) => {
    if (!Notification.isSupported()) return false;
    const notification = new Notification({ title, body, icon: path.join(ASSETS, 'icon.png') });
    notification.on('click', showMainWindow);
    notification.show();
    return true;
  },
  vueReglages,
});

app.on('second-instance', showMainWindow);
app.on('before-quit', () => { quitting = true; connexion.fermer(); });
// La fenêtre se masque au lieu de se fermer : l'application reste dans la zone de notification.
app.on('window-all-closed', () => {});

app.whenReady().then(() => {
  app.setAppUserModelId(APP_ID); // nécessaire aux notifications sous Windows
  fichierReglages = path.join(app.getPath('userData'), 'reglages-poste.json');
  reglages = preferences.lire(systemeFichiers, fichierReglages);
  if (!DEMARRAGE_DISPONIBLE) reglages = { ...reglages, demarrageSession: false };
  appliquerDemarrage(); // désactivé par défaut (Q-2)
  session.defaultSession.setPermissionRequestHandler((_wc, permission, callback) => {
    callback(permission === 'notifications');
  });
  if (!DEV) registerUiProtocol();
  createWindow();
  createTray();
  app.on('activate', showMainWindow);
});
