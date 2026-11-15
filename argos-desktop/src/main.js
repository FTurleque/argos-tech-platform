'use strict';
// Client desktop ARGOS (ADR-0022) : emballe l'IHM argos-ui sans y ajouter de logique métier.
// Apports propres au desktop : icône dans la zone de notification et notifications système.
// Fermer la fenêtre la masque seulement, pour que les alertes continuent d'arriver.

const path = require('node:path');
const { pathToFileURL } = require('node:url');
const fs = require('node:fs');
const {
  app, BrowserWindow, Menu, Notification, Tray, ipcMain, nativeImage, net, protocol, session, shell,
} = require('electron');
const { SCHEME, HOST, ORIGIN, contentSecurityPolicy, resolveUiFile, mimeType } = require('./ui-protocol');

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

let mainWindow = null;
let tray = null;
let quitting = false;

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

  mainWindow.once('ready-to-show', () => mainWindow.show());

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

function createTray() {
  tray = new Tray(nativeImage.createFromPath(path.join(ASSETS, 'tray.png')));
  tray.setToolTip('ARGOS');
  tray.setContextMenu(Menu.buildFromTemplate([
    { label: 'Ouvrir ARGOS', click: showMainWindow },
    { type: 'separator' },
    { label: 'Quitter ARGOS', click: () => { quitting = true; app.quit(); } },
  ]));
  tray.on('click', showMainWindow);
}

ipcMain.handle('argos:notify', (event, payload) => {
  // Seule la fenêtre principale peut demander une notification.
  if (!mainWindow || event.sender !== mainWindow.webContents) return false;
  const title = typeof payload?.title === 'string' ? payload.title.slice(0, 120) : '';
  const body = typeof payload?.body === 'string' ? payload.body.slice(0, 400) : '';
  if (!title || !Notification.isSupported()) return false;
  const notification = new Notification({ title, body, icon: path.join(ASSETS, 'icon.png') });
  notification.on('click', showMainWindow);
  notification.show();
  return true;
});

app.on('second-instance', showMainWindow);
app.on('before-quit', () => { quitting = true; });
// La fenêtre se masque au lieu de se fermer : l'application reste dans la zone de notification.
app.on('window-all-closed', () => {});

app.whenReady().then(() => {
  app.setAppUserModelId(APP_ID); // nécessaire aux notifications sous Windows
  session.defaultSession.setPermissionRequestHandler((_wc, permission, callback) => {
    callback(permission === 'notifications');
  });
  if (!DEV) registerUiProtocol();
  createWindow();
  createTray();
  app.on('activate', showMainWindow);
});
