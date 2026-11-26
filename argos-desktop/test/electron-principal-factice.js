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
    show() {}
    hide() {}
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
    on() {}
  };
}

class NotificationFactice {
  static isSupported() { return false; }
  on() {}
  show() {}
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

/** Remplace temporairement les variables de réglage et les arguments du processus. */
function preparerProcessus({ environnement, dev, dossierRessources }) {
  const sauvegarde = {
    variables: Object.fromEntries(VARIABLES_REGLAGES.map((nom) => [nom, process.env[nom]])),
    argv: process.argv,
    ressources: Object.getOwnPropertyDescriptor(process, 'resourcesPath'),
  };
  for (const nom of VARIABLES_REGLAGES) delete process.env[nom];
  Object.assign(process.env, environnement);
  process.argv = dev ? [...process.argv, '--dev'] : process.argv.filter((argument) => argument !== '--dev');
  if (dossierRessources) Object.defineProperty(process, 'resourcesPath', { value: dossierRessources, configurable: true });
  return () => {
    for (const [nom, valeur] of Object.entries(sauvegarde.variables)) {
      if (valeur === undefined) delete process.env[nom];
      else process.env[nom] = valeur;
    }
    process.argv = sauvegarde.argv;
    if (sauvegarde.ressources) Object.defineProperty(process, 'resourcesPath', sauvegarde.ressources);
    else delete process.resourcesPath;
  };
}

/** Exécute main.js avec `electron` et `../package.json` remplacés. */
function executerMain(electron, packageLivre) {
  const chargementOrigine = Module._load;
  Module._load = function chargerDepuisMain(request, parent, ...reste) {
    if (parent && parent.filename === MAIN) {
      if (request === 'electron') return electron;
      if (request === '../package.json') return packageLivre;
    }
    return chargementOrigine.call(this, request, parent, ...reste);
  };
  try {
    delete require.cache[MAIN];
    require(MAIN);
  } finally {
    Module._load = chargementOrigine;
    delete require.cache[MAIN];
  }
}

/**
 * Charge le main.js livré avec un Electron factice, attend la fin de `app.whenReady()` et renvoie
 * le journal des appels.
 * @param {{
 *   environnement?: Record<string, string>, emballes?: {apiUrl?: string, oidcUrl?: string}, dev?: boolean,
 *   empaquetee?: boolean, dossierRessources?: string, lireFichier?: (url: string) => unknown,
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
  });
  try {
    executerMain(electron, { ...PACKAGE_LIVRE, argos: options.emballes });
    await journal.pret;
    await new Promise((resoudre) => setImmediate(resoudre));
  } finally {
    restaurer();
  }
  return journal;
}

module.exports = { chargerMain, MAIN };
