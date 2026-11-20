'use strict';
// Doublures des objets Electron pour les tests du pont (preload.js) et des canaux (canaux.js).
// Le preload réel est chargé par le chargeur de modules de Node, avec un `require('electron')`
// factice et tout autre module refusé : c'est le fichier livré qui est testé, pas une copie.
const Module = require('node:module');
const path = require('node:path');

const PRELOAD = path.join(__dirname, '..', 'src', 'preload.js');

/**
 * Bus IPC en mémoire : relie un ipcRenderer factice (côté IHM) à un ipcMain factice (côté
 * processus principal). Les valeurs sont copiées par clonage structuré, comme entre les
 * processus d'Electron : aucun objet n'est partagé par référence.
 */
function creerBus() {
  const gestionnaires = new Map();
  const ecouteursIhm = new Map();
  const appels = [];
  const webContents = {
    envoyes: [],
    send(canal, valeur) {
      this.envoyes.push({ canal, valeur });
      for (const listener of ecouteursIhm.get(canal) ?? []) listener({}, structuredClone(valeur));
    },
  };
  const ipcMain = {
    handle(canal, gestionnaire) {
      if (gestionnaires.has(canal)) throw new Error(`canal déjà déclaré : ${canal}`);
      gestionnaires.set(canal, gestionnaire);
    },
  };
  /** Appel depuis un émetteur donné (par défaut la fenêtre principale). */
  async function invoquer(canal, valeur, sender = webContents) {
    appels.push({ canal, valeur });
    const gestionnaire = gestionnaires.get(canal);
    if (!gestionnaire) throw new Error(`aucun gestionnaire pour ${canal}`);
    return gestionnaire({ sender }, structuredClone(valeur));
  }
  const ipcRenderer = {
    invoke: (canal, valeur) => invoquer(canal, valeur),
    on(canal, listener) {
      if (!ecouteursIhm.has(canal)) ecouteursIhm.set(canal, new Set());
      ecouteursIhm.get(canal).add(listener);
    },
    removeListener(canal, listener) {
      ecouteursIhm.get(canal)?.delete(listener);
    },
    ecouteurs: (canal) => ecouteursIhm.get(canal)?.size ?? 0,
  };
  return { ipcMain, ipcRenderer, webContents, invoquer, appels, gestionnaires };
}

/** Fenêtre principale factice : visible ou masquée, réduite ou non. */
function creerFenetre(webContents, { visible = false, reduite = false } = {}) {
  return {
    webContents,
    visible,
    reduite,
    auPremierPlan: false,
    isMinimized() { return this.reduite; },
    restore() { this.reduite = false; },
    show() { this.visible = true; },
    focus() { this.auPremierPlan = true; },
  };
}

/**
 * Exécute le preload livré avec un Electron factice et renvoie l'objet exposé à l'IHM sous
 * `window.argosDesktop`.
 */
function chargerPreload(ipcRenderer, platform = 'win32') {
  const exposes = {};
  const electron = {
    contextBridge: {
      exposeInMainWorld(nom, api) { exposes[nom] = api; },
    },
    ipcRenderer,
  };
  const chargementOrigine = Module._load;
  const plateformeOrigine = Object.getOwnPropertyDescriptor(process, 'platform');
  Module._load = function chargerDepuisPreload(request, parent, ...reste) {
    if (parent && parent.filename === PRELOAD) {
      if (request === 'electron') return electron;
      throw new Error(`module interdit dans un preload sandboxé : ${request}`);
    }
    return chargementOrigine.call(this, request, parent, ...reste);
  };
  Object.defineProperty(process, 'platform', { value: platform, configurable: true });
  try {
    delete require.cache[PRELOAD];
    require(PRELOAD);
  } finally {
    Module._load = chargementOrigine;
    Object.defineProperty(process, 'platform', plateformeOrigine);
    delete require.cache[PRELOAD];
  }
  return exposes;
}

/** Notification système factice. */
function fabriqueNotifications() {
  const creees = [];
  class NotificationFactice {
    constructor(options) {
      this.options = options;
      this.ecouteurs = {};
      this.affichee = false;
      creees.push(this);
    }
    on(evenement, ecouteur) { this.ecouteurs[evenement] = ecouteur; }
    show() { this.affichee = true; }
    cliquer() { this.ecouteurs.click?.(); }
  }
  return { creees, creer: (options) => new NotificationFactice(options) };
}

module.exports = { creerBus, creerFenetre, chargerPreload, fabriqueNotifications, PRELOAD };
