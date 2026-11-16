'use strict';
// Pont minimal entre l'IHM et le processus principal (ADR-0022, changement notifier-sur-le-poste).
// L'IHM n'a pas accès à Node.js : elle ne voit que window.argosDesktop, limité aux notifications,
// au compteur de la zone de notification et aux réglages du poste. Seules des valeurs simples
// traversent le pont ; aucun objet de l'IHM n'est transmis tel quel.

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
   * Notifie une alerte : seuls son identifiant, son titre, sa gravité et son nombre de projets sont
   * transmis ; le processus principal construit le texte affiché (NOT-05).
   */
  notifyAlert: (alerte) => ipcRenderer.invoke('argos:alert', {
    id: String(alerte?.id ?? ''),
    titre: String(alerte?.titre ?? ''),
    gravite: alerte?.gravite == null ? null : String(alerte.gravite),
    projets: Number(alerte?.projets),
  }),
  /** Nombre d'alertes ouvertes affiché dans la zone de notification (NOT-03). */
  setAlertCount: (n) => ipcRenderer.invoke('argos:alert-count', Number(n)),
  /** Appelé avec l'identifiant de l'alerte quand l'utilisateur clique sur sa notification (NOT-02). */
  onOpenAlert: (callback) => ecouter('argos:open-alert', (id) => callback(String(id))),
  /** Réglages du poste : notifications du poste (NOT-04) et démarrage avec la session (Q-2). */
  getSettings: () => ipcRenderer.invoke('argos:settings-get'),
  setSettings: (reglages) => ipcRenderer.invoke('argos:settings-set', {
    notificationsPoste: reglages?.notificationsPoste,
    demarrageSession: reglages?.demarrageSession,
  }),
  /** Appelé quand les réglages changent depuis le menu de la zone de notification. */
  onSettingsChanged: (callback) => ecouter('argos:settings', callback),
});
