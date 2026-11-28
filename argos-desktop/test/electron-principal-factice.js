'use strict';
// Doublure d'Electron pour les tests du processus principal (main.js, AUD-TST-10).
// Le main.js livré est chargé par le chargeur de modules de Node avec un `require('electron')`
// factice qui enregistre chaque appel : c'est le fichier livré qui est testé, pas une copie.
const Module = require('node:module');
const path = require('node:path');

const MAIN = path.join(__dirname, '..', 'src', 'main.js');
const PACKAGE_LIVRE = require('../package.json');
const VARIABLES_REGLAGES = ['ARGOS_API_URL', 'ARGOS_OIDC_URL', 'ARGOS_UI_DEV_URL'];
const DOSSIER_UTILISATEUR_ABSENT = path.join(__dirname, 'dossier-utilisateur-absent');

/** Contenu Web factice d'une fenêtre : écouteurs et gestionnaire d'ouverture retenus. */
function creerContenuWeb() {
  return {
    ecouteurs: {},
    gestionnaireOuverture: null,
    envoyes: [],
    on(evenement, ecouteur) { this.ecouteurs[evenement] = ecouteur; },
    setWindowOpenHandler(gestionnaire) { this.gestionnaireOuverture = gestionnaire; },
    send(canal, valeur) { this.envoyes.push({ canal, valeur }); },
  };
}

/** Fabrique la classe BrowserWindow factice qui inscrit chaque fenêtre dans le journal. */
function fabriqueFenetres(journal) {
  return class FenetreFactice {
    constructor(options) {
      this.options = options;
      this.ecouteurs = {};
      this.urlChargee = null;
      this.webContents = creerContenuWeb();
      journal.fenetres.push(this);
    }
    once(evenement, ecouteur) { this.ecouteurs[evenement] = ecouteur; }
    on(evenement, ecouteur) { this.ecouteurs[evenement] = ecouteur; }
    loadURL(url) { this.urlChargee = url; }
    show() { /* doublure d'Electron : sans effet dans les tests */ }
    hide() { /* doublure d'Electron : sans effet dans les tests */ }
  };
}

/** Fabrique la classe Tray factice. */
function fabriqueZoneNotification(journal) {
  return class ZoneNotificationFactice {
    constructor(image) {
      this.image = image;
      journal.zones.push(this);
    }
    setToolTip(infobulle) { this.infobulle = infobulle; }
    setContextMenu(menu) { this.menu = menu; }
    on() { /* doublure d'Electron : sans effet dans les tests */ }
  };
}

class NotificationFactice {
  static isSupported() { return false; }
  on() { /* doublure d'Electron : sans effet dans les tests */ }
  show() { /* doublure d'Electron : sans effet dans les tests */ }
}

/** Objet `app` factice : instance unique accordée, prêt immédiatement. */
function creerApplication(journal, empaquetee) {
  return {
    isPackaged: empaquetee,
    requestSingleInstanceLock: () => true,
    quit() { journal.quitte = true; },
    on(evenement, ecouteur) { journal.evenementsApplication[evenement] = ecouteur; },
    whenReady: () => journal.pret,
    setAppUserModelId(identifiant) { journal.identifiantApplication = identifiant; },
    getPath: (nom) => path.join(DOSSIER_UTILISATEUR_ABSENT, nom),
    setLoginItemSettings(reglages) { journal.demarrage.push(reglages); },
    getLoginItemSettings: () => ({ wasOpenedAtLogin: false }),
    setBadgeCount() {},
  };
}

/**
 * Electron factice et journal des appels reçus.
 * @param {{empaquetee: boolean, lireFichier: (url: string) => unknown}} options
 */
function creerElectronPrincipal({ empaquetee, lireFichier }) {
  const journal = {
    pret: Promise.resolve(),
    fenetres: [],
    zones: [],
    schemasPrivilegies: [],
    protocoles: new Map(),
    gestionnairePermissions: null,
    ouverturesExternes: [],
    canauxIpc: [],
    evenementsApplication: {},
    demarrage: [],
    quitte: false,
    identifiantApplication: null,
  };
  const electron = {
    app: creerApplication(journal, empaquetee),
    BrowserWindow: fabriqueFenetres(journal),
    Menu: { buildFromTemplate: (modele) => modele },
    Notification: NotificationFactice,
    Tray: fabriqueZoneNotification(journal),
    ipcMain: { handle(canal) { journal.canauxIpc.push(canal); } },
    nativeImage: { createFromPath: (chemin) => ({ chemin }) },
    net: { fetch: async (url) => lireFichier(url) },
    protocol: {
      registerSchemesAsPrivileged(schemas) { journal.schemasPrivilegies.push(...schemas); },
      handle(schema, gestionnaire) { journal.protocoles.set(schema, gestionnaire); },
    },
    session: {
      defaultSession: {
        setPermissionRequestHandler(gestionnaire) { journal.gestionnairePermissions = gestionnaire; },
      },
    },
    shell: { openExternal: async (url) => { journal.ouverturesExternes.push(url); } },
  };
  return { electron, journal };
}

/** Remplace temporairement les variables de réglage, les arguments et, si demandée, la plateforme du processus. */
function preparerProcessus(reglages) {
  const sauvegarde = sauvegarderProcessus();
  appliquerReglages(reglages);
  return () => restaurerProcessus(sauvegarde);
}

/** État du processus que les réglages modifient : variables, arguments, dossier des ressources, plateforme. */
function sauvegarderProcessus() {
  return {
    variables: Object.fromEntries(VARIABLES_REGLAGES.map((nom) => [nom, process.env[nom]])),
    argv: process.argv,
    ressources: Object.getOwnPropertyDescriptor(process, 'resourcesPath'),
    plateforme: Object.getOwnPropertyDescriptor(process, 'platform'),
  };
}

function appliquerReglages({ environnement, dev, dossierRessources, plateforme }) {
  if (plateforme) Object.defineProperty(process, 'platform', { value: plateforme, configurable: true });
  for (const nom of VARIABLES_REGLAGES) delete process.env[nom];
  Object.assign(process.env, environnement);
  process.argv = dev ? [...process.argv, '--dev'] : process.argv.filter((argument) => argument !== '--dev');
  if (dossierRessources) Object.defineProperty(process, 'resourcesPath', { value: dossierRessources, configurable: true });
}

function restaurerProcessus(sauvegarde) {
  for (const [nom, valeur] of Object.entries(sauvegarde.variables)) {
    if (valeur === undefined) delete process.env[nom];
    else process.env[nom] = valeur;
  }
  process.argv = sauvegarde.argv;
  if (sauvegarde.ressources) Object.defineProperty(process, 'resourcesPath', sauvegarde.ressources);
  else delete process.resourcesPath;
  if (sauvegarde.plateforme) Object.defineProperty(process, 'platform', sauvegarde.plateforme);
}

/** autoUpdater factice d'electron-updater : recherches et réglages inscrits dans le journal. */
function creerMiseAJourFactice(journal) {
  journal.miseAJour = { recherches: 0, ecouteurs: {}, autoDownload: false, autoInstallOnAppQuit: false };
  const miseAJour = journal.miseAJour;
  return {
    set autoDownload(valeur) { miseAJour.autoDownload = valeur; },
    set autoInstallOnAppQuit(valeur) { miseAJour.autoInstallOnAppQuit = valeur; },
    on(evenement, ecouteur) { miseAJour.ecouteurs[evenement] = ecouteur; },
    checkForUpdates() {
      miseAJour.recherches += 1;
      return Promise.resolve(undefined);
    },
  };
}

/**
 * Exécute main.js avec `electron`, `electron-updater` et `../package.json` remplacés ; le remplacement reste actif
 * jusqu'à l'appel de la fonction renvoyée, car electron-updater n'est chargé qu'au démarrage de l'application.
 */
function executerMain(electron, packageLivre, journal) {
  const chargementOrigine = Module._load;
  Module._load = function chargerDepuisMain(request, parent, ...reste) {
    if (parent && parent.filename === MAIN) {
      if (request === 'electron') return electron;
      if (request === 'electron-updater') return { autoUpdater: creerMiseAJourFactice(journal) };
      if (request === '../package.json') return packageLivre;
    }
    return chargementOrigine.call(this, request, parent, ...reste);
  };
  const retablir = () => {
    Module._load = chargementOrigine;
    delete require.cache[MAIN];
  };
  try {
    delete require.cache[MAIN];
    require(MAIN);
  } catch (erreur) {
    retablir();
    throw erreur;
  }
  return retablir;
}

/**
 * Charge le main.js livré avec un Electron factice, attend la fin de `app.whenReady()` et renvoie
 * le journal des appels.
 * @param {{
 *   environnement?: Record<string, string>, emballes?: {apiUrl?: string, oidcUrl?: string}, dev?: boolean,
 *   empaquetee?: boolean, dossierRessources?: string, lireFichier?: (url: string) => unknown, plateforme?: string,
 * }} [options]
 */
async function chargerMain(options = {}) {
  const { electron, journal } = creerElectronPrincipal({
    empaquetee: options.empaquetee ?? false,
    lireFichier: options.lireFichier ?? (() => ({ body: null })),
  });
  const restaurer = preparerProcessus({
    environnement: options.environnement ?? {},
    dev: options.dev ?? false,
    dossierRessources: options.dossierRessources,
    plateforme: options.plateforme,
  });
  let retablirChargement = () => {};
  try {
    retablirChargement = executerMain(electron, { ...PACKAGE_LIVRE, argos: options.emballes }, journal);
    await journal.pret;
    await new Promise((resoudre) => setImmediate(resoudre));
  } finally {
    retablirChargement();
    restaurer();
  }
  return journal;
}

module.exports = { chargerMain, MAIN };
