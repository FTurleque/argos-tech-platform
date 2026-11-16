'use strict';
// Client desktop ARGOS (ADR-0022) : emballe l'IHM argos-ui sans y ajouter de logique métier.
// Apports propres au desktop : icône et compteur dans la zone de notification, notifications
// système des alertes, réglages du poste (changement notifier-sur-le-poste).
// Fermer la fenêtre la masque seulement, pour que les alertes continuent d'arriver ; quitter
// l'application arrête tout : aucun service ne tourne en arrière-plan (Q-1).

const path = require('node:path');
const { pathToFileURL } = require('node:url');
const fs = require('node:fs');
const {
  app, BrowserWindow, Menu, Notification, Tray, ipcMain, nativeImage, net, protocol, session, shell,
} = require('electron');
const { SCHEME, ORIGIN, contentSecurityPolicy, resolveUiFile, mimeType } = require('./ui-protocol');
const { libelleCompteur } = require('./notifications');
const preferences = require('./preferences');
const { creerPoste } = require('./poste');

const APP_ID = 'io.argos.desktop';
const DEV = process.argv.includes('--dev');
const DEV_URL = process.env.ARGOS_UI_DEV_URL || 'http://localhost:5173';
// URL du serveur ARGOS : sert uniquement à la politique de sécurité du contenu.
// L'IHM lit la sienne au moment du build (VITE_ARGOS_API_URL).
const API_URL = process.env.ARGOS_API_URL || 'http://localhost:8080';
const UI_DIR = app.isPackaged
  ? path.join(process.resourcesPath, 'ui')
  : path.join(__dirname, '..', '..', 'argos-ui', 'dist');
const ASSETS = path.join(__dirname, '..', 'assets');
// Argument ajouté au lancement automatique avec la session : la fenêtre reste alors masquée.
const ARG_DEMARRAGE = '--demarrage-session';
// Le démarrage avec la session n'est proposé que là où Electron sait le configurer.
const DEMARRAGE_DISPONIBLE = process.platform === 'win32' || process.platform === 'darwin';

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
  if (!mainWindow) return;
  if (mainWindow.isMinimized()) mainWindow.restore();
  mainWindow.show();
  mainWindow.focus();
}

function isExternalWebUrl(url) {
  try {
    const { protocol: p } = new URL(url);
    return p === 'https:' || p === 'http:';
  } catch {
    return false;
  }
}

function registerUiProtocol() {
  const csp = contentSecurityPolicy(API_URL);
  protocol.handle(SCHEME, async (request) => {
    const file = resolveUiFile(request.url, UI_DIR);
    if (!file || !fs.existsSync(file)) {
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
    width: 1200,
    height: 820,
    minWidth: 820,
    minHeight: 560,
    title: 'ARGOS',
    icon: path.join(ASSETS, 'icon.png'),
    show: false,
    autoHideMenuBar: true,
    webPreferences: {
      preload: path.join(__dirname, 'preload.js'),
      contextIsolation: true,
      nodeIntegration: false,
      sandbox: true,
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
    preferences.ecrire(fs, fichierReglages, reglages);
  } catch (error) {
    console.error('Réglages du poste non enregistrés :', error.message);
  }
  appliquerDemarrage();
  construireMenu();
  if (mainWindow) mainWindow.webContents.send('argos:settings', vueReglages());
}

function vueReglages() {
  return { ...reglages, demarrageSessionDisponible: DEMARRAGE_DISPONIBLE };
}

function ouvrirAlerte(id) {
  showMainWindow();
  if (mainWindow) mainWindow.webContents.send('argos:open-alert', id);
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

/** Seule la fenêtre principale peut parler au processus principal. */
function depuisFenetrePrincipale(event) {
  return Boolean(mainWindow) && event.sender === mainWindow.webContents;
}

ipcMain.handle('argos:notify', (event, payload) => {
  if (!depuisFenetrePrincipale(event)) return false;
  const title = typeof payload?.title === 'string' ? payload.title.slice(0, 120) : '';
  const body = typeof payload?.body === 'string' ? payload.body.slice(0, 400) : '';
  if (!title || !Notification.isSupported()) return false;
  const notification = new Notification({ title, body, icon: path.join(ASSETS, 'icon.png') });
  notification.on('click', showMainWindow);
  notification.show();
  return true;
});

// Alerte reçue par l'IHM : notification limitée au titre, à la gravité et au nombre de projets.
ipcMain.handle('argos:alert', (event, payload) => {
  if (!depuisFenetrePrincipale(event)) return false;
  return poste.alerte(payload).affichee;
});

ipcMain.handle('argos:alert-count', (event, n) => {
  if (!depuisFenetrePrincipale(event)) return false;
  return poste.definirCompteur(n);
});

ipcMain.handle('argos:settings-get', (event) => {
  if (!depuisFenetrePrincipale(event)) return null;
  return vueReglages();
});

ipcMain.handle('argos:settings-set', (event, demande) => {
  if (!depuisFenetrePrincipale(event)) return null;
  return poste.changerReglages(demande) ? vueReglages() : null;
});

app.on('second-instance', showMainWindow);
app.on('before-quit', () => { quitting = true; });
// La fenêtre se masque au lieu de se fermer : l'application reste dans la zone de notification.
app.on('window-all-closed', () => {});

app.whenReady().then(() => {
  app.setAppUserModelId(APP_ID); // nécessaire aux notifications sous Windows
  fichierReglages = path.join(app.getPath('userData'), 'reglages-poste.json');
  reglages = preferences.lire(fs, fichierReglages);
  appliquerDemarrage(); // désactivé par défaut (Q-2)
  session.defaultSession.setPermissionRequestHandler((_wc, permission, callback) => {
    callback(permission === 'notifications');
  });
  if (!DEV) registerUiProtocol();
  createWindow();
  createTray();
  app.on('activate', showMainWindow);
});
