'use strict';
// Configuration de sécurité de la fenêtre principale du client desktop (ADR-0022) : options de
// BrowserWindow, permissions accordées à la page, navigation et ouverture de liens.
// Module sans dépendance à Electron pour pouvoir être testé avec « node --test ».

// Taille de la fenêtre principale, en pixels : à l'ouverture, puis minimale pour garder l'IHM lisible.
const FENETRE_LARGEUR = 1200;
const FENETRE_HAUTEUR = 820;
const FENETRE_LARGEUR_MIN = 820;
const FENETRE_HAUTEUR_MIN = 560;
const TITRE_FENETRE = 'ARGOS';
// Seule permission que la page peut obtenir : les notifications système des alertes.
const PERMISSIONS_ACCEPTEES = new Set(['notifications']);
const SCHEMAS_WEB = new Set(['https:', 'http:']);
const ACTION_REFUS = 'deny';

/**
 * Options de la fenêtre principale : page isolée du processus principal (pas de Node, bac à sable,
 * contexte isolé), seul le preload fait le pont.
 * @param {{preload: string, icone: string}} chemins chemin du preload et de l'icône
 */
function optionsFenetre({ preload, icone }) {
  return {
    width: FENETRE_LARGEUR,
    height: FENETRE_HAUTEUR,
    minWidth: FENETRE_LARGEUR_MIN,
    minHeight: FENETRE_HAUTEUR_MIN,
    title: TITRE_FENETRE,
    icon: icone,
    show: false,
    autoHideMenuBar: true,
    webPreferences: {
      preload,
      contextIsolation: true,
      nodeIntegration: false,
      sandbox: true,
      // La lecture périodique des alertes tourne dans la page, même fenêtre masquée : sans cela,
      // Chromium espace les minuteries d'une page cachée et le délai de 30 s n'est plus tenu.
      backgroundThrottling: false,
    },
  };
}

/** Vrai si la permission demandée par la page est accordée. */
function permissionAccordee(permission) {
  return PERMISSIONS_ACCEPTEES.has(permission);
}

/** Vrai pour une URL http(s) bien formée, à ouvrir dans le navigateur du système. */
function isExternalWebUrl(url) {
  try {
    return SCHEMAS_WEB.has(new URL(url).protocol);
  } catch {
    return false;
  }
}

/**
 * Vrai si la fenêtre peut naviguer vers `url` : l'origine de l'IHM emballée, ou l'URL du serveur de
 * développement en mode développement.
 * @param {string} url destination demandée
 * @param {{dev: boolean, devUrl: string, origine: string}} contexte mode et origines admises
 */
function navigationAutorisee(url, { dev, devUrl, origine }) {
  return dev ? url.startsWith(devUrl) : url.startsWith(origine + '/');
}

/**
 * Gestionnaire de `will-navigate` : bloque toute navigation hors de l'IHM et ouvre les liens web
 * externes dans le navigateur du système.
 * @param {{dev: boolean, devUrl: string, origine: string, ouvrirExterne: (url: string) => unknown}} contexte
 */
function gestionnaireNavigation(contexte) {
  return (event, url) => {
    if (navigationAutorisee(url, contexte)) return;
    event.preventDefault();
    if (isExternalWebUrl(url)) contexte.ouvrirExterne(url);
  };
}

/**
 * Gestionnaire de `setWindowOpenHandler` : aucune fenêtre secondaire, les liens web externes
 * s'ouvrent dans le navigateur du système.
 * @param {(url: string) => unknown} ouvrirExterne
 * @returns {(details: {url: string}) => {action: 'deny'}}
 */
function gestionnaireOuverture(ouvrirExterne) {
  return ({ url }) => {
    if (isExternalWebUrl(url)) ouvrirExterne(url);
    return { action: ACTION_REFUS };
  };
}

/** Gestionnaire de `setPermissionRequestHandler` : seules les permissions acceptées sont accordées. */
function gestionnairePermissions(_webContents, permission, callback) {
  callback(permissionAccordee(permission));
}

module.exports = {
  FENETRE_LARGEUR,
  FENETRE_HAUTEUR,
  FENETRE_LARGEUR_MIN,
  FENETRE_HAUTEUR_MIN,
  TITRE_FENETRE,
  optionsFenetre,
  permissionAccordee,
  isExternalWebUrl,
  navigationAutorisee,
  gestionnaireNavigation,
  gestionnaireOuverture,
  gestionnairePermissions,
};
