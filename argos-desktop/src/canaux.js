'use strict';
// Canaux entre l'IHM et le processus principal (ADR-0022, changement notifier-sur-le-poste).
// Module sans dépendance directe à Electron : main.js lui fournit ipcMain et la fenêtre
// principale ; les tests lui fournissent des doublures. Le preload (preload.js) est l'autre
// extrémité de ces canaux.

/** Noms des canaux, partagés avec preload.js (qui ne peut pas importer ce module : sandbox). */
const CANAUX = Object.freeze({
  NOTIFIER: 'argos:notify',
  ALERTE: 'argos:alert',
  COMPTEUR: 'argos:alert-count',
  REGLAGES_LIRE: 'argos:settings-get',
  REGLAGES_ECRIRE: 'argos:settings-set',
  OUVRIR_ALERTE: 'argos:open-alert',
  REGLAGES_CHANGES: 'argos:settings',
  CONNEXION_PREPARER: 'argos:login-prepare',
  CONNEXION_AUTORISER: 'argos:login-authorize',
});

const TITRE_MAX = 120;
const CORPS_MAX = 400;

/**
 * Notification libre (`notify`) bornée : titre obligatoire de 120 caractères au plus, corps de
 * 400 au plus. Renvoie null si la demande est invalide.
 */
function bornerNotification(payload) {
  const title = typeof payload?.title === 'string' ? payload.title.slice(0, TITRE_MAX) : '';
  const body = typeof payload?.body === 'string' ? payload.body.slice(0, CORPS_MAX) : '';
  return title ? { title, body } : null;
}

/** Vrai si le message vient de la fenêtre principale ; tout autre émetteur est refusé. */
function depuisFenetre(fenetre, event) {
  return Boolean(fenetre) && Boolean(event) && event.sender === fenetre.webContents;
}

/** Remet la fenêtre au premier plan, même masquée ou réduite. */
function afficherFenetre(fenetre) {
  if (!fenetre) return;
  if (fenetre.isMinimized()) fenetre.restore();
  fenetre.show();
  fenetre.focus();
}

/** Clic sur la notification d'une alerte : fenêtre au premier plan sur cette alerte (NOT-02). */
function ouvrirAlerte(fenetre, id) {
  afficherFenetre(fenetre);
  if (fenetre) fenetre.webContents.send(CANAUX.OUVRIR_ALERTE, id);
}

/**
 * Déclare les canaux du processus principal.
 * @param {{handle: Function}} ipcMain
 * @param {object} dependances dépendances
 * @param {() => object|null} dependances.fenetre fenêtre principale courante
 * @param {ReturnType<typeof import('./poste').creerPoste>} dependances.poste comportement du poste
 * @param {(n: {title: string, body: string}) => boolean} dependances.notifier notification libre déjà bornée
 * @param {() => object} dependances.vueReglages réglages présentés à l'IHM
 * @param {ReturnType<typeof import('./connexion').creerConnexion>} dependances.connexion connexion OIDC (ACC-10)
 */
function enregistrerCanaux(ipcMain, dependances) {
  const garde = (refus, traitement) => (event, valeur) => (
    depuisFenetre(dependances.fenetre(), event) ? traitement(valeur) : refus
  );

  ipcMain.handle(CANAUX.NOTIFIER, garde(false, (payload) => {
    const notification = bornerNotification(payload);
    return notification ? dependances.notifier(notification) : false;
  }));
  // Alerte reçue par l'IHM : notification limitée à un titre générique, à la gravité et au
  // nombre de projets (NOT-05).
  ipcMain.handle(CANAUX.ALERTE, garde(false, (payload) => dependances.poste.alerte(payload).affichee));
  ipcMain.handle(CANAUX.COMPTEUR, garde(false, (nombre) => dependances.poste.definirCompteur(nombre)));
  ipcMain.handle(CANAUX.REGLAGES_LIRE, garde(null, () => dependances.vueReglages()));
  ipcMain.handle(CANAUX.REGLAGES_ECRIRE, garde(null, (demande) => (
    dependances.poste.changerReglages(demande) ? dependances.vueReglages() : null
  )));
  // Connexion OIDC (changement connecter-ihm-oidc, ACC-10) : écoute locale puis navigateur du système.
  ipcMain.handle(CANAUX.CONNEXION_PREPARER, garde(null, () => dependances.connexion.preparer()));
  ipcMain.handle(CANAUX.CONNEXION_AUTORISER, garde(null, (url) => (
    typeof url === 'string' ? dependances.connexion.autoriser(url) : null
  )));
}

module.exports = {
  CANAUX, bornerNotification, depuisFenetre, afficherFenetre, ouvrirAlerte, enregistrerCanaux,
};
