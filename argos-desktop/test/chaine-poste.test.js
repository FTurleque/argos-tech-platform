'use strict';
// Chaîne complète du poste sans binaire Electron (notifier-sur-le-poste : NOT-01-S1, NOT-01-S2,
// NOT-02-S1, NOT-03-S1) : pont window.argosDesktop livré (preload.js) → bus IPC en mémoire →
// canaux du processus principal (canaux.js) → comportement du poste (poste.js) → notification
// système factice → clic → fenêtre au premier plan → IHM avertie de l'alerte à afficher.
// Seuls les objets Electron (ipc, fenêtre, notification, zone de notification) sont simulés ;
// le parcours dans l'application Electron réelle reste à faire (tâche 3.2).
const test = require('node:test');
const assert = require('node:assert/strict');
const { enregistrerCanaux, ouvrirAlerte } = require('../src/canaux');
const { creerPoste } = require('../src/poste');
const preferences = require('../src/preferences');
const { creerBus, creerFenetre, chargerPreload, fabriqueNotifications } = require('./electron-factice');

const ID = '3f2b8c1e-4d5a-4b6c-8d7e-9f0a1b2c3d4e';

function application({ supportees = true, notificationsPoste = true } = {}) {
  const bus = creerBus();
  const fenetre = creerFenetre(bus.webContents, { visible: false }); // masquée dans la zone de notification
  const notifications = fabriqueNotifications();
  const zone = { infobulles: [] };
  let reglages = { ...preferences.DEFAUTS, notificationsPoste };
  const poste = creerPoste({
    notificationsSupportees: () => supportees,
    creerNotification: notifications.creer,
    reglages: () => reglages,
    enregistrerReglages: (r) => { reglages = r; },
    afficherCompteur: () => zone.infobulles.push(poste.infobulle()),
    ouvrirAlerte: (id) => ouvrirAlerte(fenetre, id),
  });
  enregistrerCanaux(bus.ipcMain, {
    fenetre: () => fenetre,
    poste,
    notifier: () => false,
    vueReglages: () => ({ ...reglages, demarrageSessionDisponible: true }),
  });
  const { argosDesktop } = chargerPreload(bus.ipcRenderer);
  return { bus, fenetre, notifications, zone, poste, argosDesktop };
}

test('NOT-01-S1 : fenêtre masquée, une alerte émise par l’IHM affiche une notification système', async () => {
  const app = application();
  const affichee = await app.argosDesktop.notifyAlert({
    id: ID, gravite: 'CRITICAL', projets: 3, titre: 'Prototype pollution in lodash',
  });
  assert.equal(affichee, true);
  assert.equal(app.fenetre.visible, false, 'la fenêtre reste masquée');
  assert.equal(app.notifications.creees.length, 1);
  assert.ok(app.notifications.creees[0].affichee);
  assert.deepEqual(app.notifications.creees[0].options, {
    title: 'Nouvelle alerte ARGOS',
    body: 'Gravité critique · 3 projets',
  });
});

test('NOT-01-S2 : notifications refusées par le système, aucune notification mais l’alerte est comptée', async () => {
  const app = application({ supportees: false });
  assert.equal(await app.argosDesktop.notifyAlert({ id: ID, gravite: 'HIGH', projets: 1 }), false);
  assert.equal(await app.argosDesktop.setAlertCount(1), true);
  assert.equal(app.notifications.creees.length, 0);
  assert.deepEqual(app.zone.infobulles, ['ARGOS — 1 alerte ouverte']);
});

test('NOT-02-S1 : un clic sur la notification ouvre la fenêtre au premier plan sur cette alerte', async () => {
  const app = application();
  const ouvertes = [];
  app.argosDesktop.onOpenAlert((id) => ouvertes.push(id));
  await app.argosDesktop.notifyAlert({ id: ID, gravite: 'CRITICAL', projets: 3 });
  assert.equal(app.fenetre.visible, false);
  app.notifications.creees[0].cliquer();
  assert.equal(app.fenetre.visible, true);
  assert.equal(app.fenetre.auPremierPlan, true);
  assert.deepEqual(ouvertes, [ID], 'l’IHM reçoit l’identifiant de l’alerte à afficher');
});

test('NOT-03-S1 : deux alertes ouvertes, une troisième arrive, la zone de notification indique trois', async () => {
  const app = application();
  await app.argosDesktop.setAlertCount(2);
  await app.argosDesktop.notifyAlert({ id: '00000000-0000-4000-8000-000000000003', gravite: 'HIGH', projets: 1 });
  await app.argosDesktop.setAlertCount(3);
  assert.deepEqual(app.zone.infobulles, ['ARGOS — 2 alertes ouvertes', 'ARGOS — 3 alertes ouvertes']);
  assert.equal(app.poste.compteur(), 3);
});

test('NOT-04 : notifications du poste coupées depuis l’IHM, plus aucune notification, compteur à jour', async () => {
  const app = application();
  const vue = await app.argosDesktop.setSettings({ notificationsPoste: false });
  assert.equal(vue.notificationsPoste, false);
  assert.equal(await app.argosDesktop.notifyAlert({ id: ID, gravite: 'HIGH', projets: 1 }), false);
  assert.equal(await app.argosDesktop.setAlertCount(1), true);
  assert.equal(app.notifications.creees.length, 0);
  assert.deepEqual(app.zone.infobulles, ['ARGOS — 1 alerte ouverte']);
});

test('NOT-05 : une alerte invalide transmise par le pont est rejetée sans notification', async () => {
  const app = application();
  assert.equal(await app.argosDesktop.notifyAlert({ id: 'pas-un-uuid', gravite: 'HIGH', projets: 1 }), false);
  assert.equal(await app.argosDesktop.setAlertCount(-2), false);
  assert.equal(app.notifications.creees.length, 0);
  assert.deepEqual(app.zone.infobulles, []);
});
