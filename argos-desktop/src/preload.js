'use strict';
// Pont minimal entre l'IHM et le processus principal (ADR-0022, changement notifier-sur-le-poste).
// L'IHM n'a pas accès à Node.js : elle ne voit que window.argosDesktop, limité aux notifications,
// au compteur de la zone de notification, aux réglages du poste et à l'ouverture de la connexion
// OIDC dans le navigateur du système (connecter-ihm-oidc). Seules des valeurs simples
// traversent le pont ; aucun objet de l'IHM n'est transmis tel quel.
// Preload sandboxé : seul le module electron peut être chargé ; les noms de canaux sont donc
// écrits ici et doivent rester ceux de canaux.js (vérifié par test/preload.test.js).

const { contextBridge, ipcRenderer } = require('electron');

/** Abonne `callback` au canal et renvoie de quoi se désabonner. */
function ecouter(canal, callback) {
  if (typeof callback !== 'function') return () => {};
  const listener = (_event, valeur) => callback(valeur);
  ipcRenderer.on(canal, listener);
  return () => ipcRenderer.removeListener(canal, listener);
}

contextBridge.exposeInMainWorld('argosDesktop', {
  platform: process.platform,
  /** Affiche une notification système. Renvoie true si elle a pu être affichée. */
  notify: (title, body) => ipcRenderer.invoke('argos:notify', { title, body }),
  /**
   * Notifie une alerte : seuls son identifiant, sa gravité et son nombre de projets sont transmis ;
   * le processus principal construit le texte affiché, sous un titre générique (NOT-05).
   */
  notifyAlert: (alerte) => ipcRenderer.invoke('argos:alert', {
    id: String(alerte?.id ?? ''),
    gravite: alerte?.gravite == null ? null : String(alerte.gravite),
    projets: Number(alerte?.projets),
  }),
  /** Nombre d'alertes ouvertes affiché dans la zone de notification (NOT-03). */
  setAlertCount: (n) => ipcRenderer.invoke('argos:alert-count', Number(n)),
  /** Appelé avec l'identifiant de l'alerte quand l'utilisateur clique sur sa notification (NOT-02). */
  onOpenAlert: (callback) => ecouter(
    'argos:open-alert',
    typeof callback === 'function' ? (id) => callback(String(id)) : null,
  ),
  /** Réglages du poste : notifications du poste (NOT-04) et démarrage avec la session (Q-2). */
  getSettings: () => ipcRenderer.invoke('argos:settings-get'),
  setSettings: (reglages) => ipcRenderer.invoke('argos:settings-set', {
    notificationsPoste: reglages?.notificationsPoste,
    demarrageSession: reglages?.demarrageSession,
  }),
  /** Appelé quand les réglages changent depuis le menu de la zone de notification. */
  onSettingsChanged: (callback) => ecouter('argos:settings', callback),
  /**
   * Connexion OIDC (connecter-ihm-oidc, ACC-10) : ouvre l'écoute locale 127.0.0.1 et renvoie
   * l'adresse de redirection, puis ouvre l'URL d'autorisation dans le navigateur du système et
   * renvoie la chaîne de requête du retour. Aucun jeton ne traverse le pont.
   */
  preparerConnexion: () => ipcRenderer.invoke('argos:login-prepare'),
  autoriserConnexion: (url) => ipcRenderer.invoke('argos:login-authorize', String(url ?? '')),
});
