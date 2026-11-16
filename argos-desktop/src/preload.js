'use strict';
// Pont minimal entre l'IHM et le processus principal (ADR-0022).
// L'IHM n'a pas accès à Node.js : elle ne voit que window.argosDesktop.

const { contextBridge, ipcRenderer } = require('electron');

contextBridge.exposeInMainWorld('argosDesktop', {
  platform: process.platform,
  /** Affiche une notification système. Renvoie true si elle a pu être affichée. */
  notify: (title, body) => ipcRenderer.invoke('argos:notify', { title, body }),
});
