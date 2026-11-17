'use strict';
// Canaux du processus principal (canaux.js, notifier-sur-le-poste) : seuls les messages de la
// fenêtre principale sont traités ; les valeurs reçues sont bornées.
const test = require('node:test');
const assert = require('node:assert/strict');
const {
  CANAUX, bornerNotification, depuisFenetre, afficherFenetre, ouvrirAlerte, enregistrerCanaux,
} = require('../src/canaux');
const { creerBus, creerFenetre } = require('./electron-factice');

const ID = '3f2b8c1e-4d5a-4b6c-8d7e-9f0a1b2c3d4e';

function installer() {
  const bus = creerBus();
  const fenetre = creerFenetre(bus.webContents);
  const etat = { alertes: [], compteurs: [], demandes: [], notifications: [], connexions: [] };
  const connexion = {
    preparer: async () => { etat.connexions.push('preparer'); return 'http://127.0.0.1:50123/argos/retour'; },
    autoriser: async (url) => { etat.connexions.push(url); return '?code=c&state=s'; },
  };
  const poste = {
    alerte: (p) => { etat.alertes.push(p); return { affichee: true }; },
    definirCompteur: (n) => { etat.compteurs.push(n); return Number.isInteger(n) && n >= 0; },
    changerReglages: (d) => { etat.demandes.push(d); return d?.notificationsPoste === false ? d : null; },
  };
  enregistrerCanaux(bus.ipcMain, {
    connexion,
    fenetre: () => fenetre,
    poste,
    notifier: (n) => { etat.notifications.push(n); return true; },
    vueReglages: () => ({ notificationsPoste: true, demarrageSession: false, demarrageSessionDisponible: true }),
  });
  return { bus, fenetre, etat };
}

test('les sept canaux d’appel de l’IHM sont déclarés', () => {
  const { bus } = installer();
  assert.deepEqual([...bus.gestionnaires.keys()].sort(), [
    CANAUX.ALERTE, CANAUX.COMPTEUR, CANAUX.NOTIFIER, CANAUX.REGLAGES_ECRIRE, CANAUX.REGLAGES_LIRE,
    CANAUX.CONNEXION_PREPARER, CANAUX.CONNEXION_AUTORISER,
  ].sort());
});

test('ACC-10 : la connexion n’est ouverte que pour la fenêtre principale, avec une URL texte', async () => {
  const { bus, etat } = installer();
  const intrus = {};
  assert.equal(await bus.invoquer(CANAUX.CONNEXION_PREPARER, undefined, intrus), null);
  assert.equal(await bus.invoquer(CANAUX.CONNEXION_AUTORISER, 'http://kc/auth', intrus), null);
  assert.equal(await bus.invoquer(CANAUX.CONNEXION_AUTORISER, { url: 'x' }), null);
  assert.deepEqual(etat.connexions, []);
  assert.equal(await bus.invoquer(CANAUX.CONNEXION_PREPARER), 'http://127.0.0.1:50123/argos/retour');
  assert.equal(await bus.invoquer(CANAUX.CONNEXION_AUTORISER, 'http://kc/auth'), '?code=c&state=s');
  assert.deepEqual(etat.connexions, ['preparer', 'http://kc/auth']);
});

test('un message qui ne vient pas de la fenêtre principale est refusé sans effet', async () => {
  const { bus, etat } = installer();
  const intrus = {};
  assert.equal(await bus.invoquer(CANAUX.ALERTE, { id: ID, gravite: 'HIGH', projets: 1 }, intrus), false);
  assert.equal(await bus.invoquer(CANAUX.COMPTEUR, 3, intrus), false);
  assert.equal(await bus.invoquer(CANAUX.NOTIFIER, { title: 't' }, intrus), false);
  assert.equal(await bus.invoquer(CANAUX.REGLAGES_LIRE, undefined, intrus), null);
  assert.equal(await bus.invoquer(CANAUX.REGLAGES_ECRIRE, { notificationsPoste: false }, intrus), null);
  assert.deepEqual(etat, { alertes: [], compteurs: [], demandes: [], notifications: [], connexions: [] });
});

test('sans fenêtre principale, aucun message n’est accepté', () => {
  assert.equal(depuisFenetre(null, { sender: {} }), false);
  assert.equal(depuisFenetre(creerFenetre({}), undefined), false);
});

test('NOT-01 / NOT-03 : alerte et compteur sont transmis au comportement du poste', async () => {
  const { bus, etat } = installer();
  assert.equal(await bus.invoquer(CANAUX.ALERTE, { id: ID, gravite: 'HIGH', projets: 1 }), true);
  assert.equal(await bus.invoquer(CANAUX.COMPTEUR, 3), true);
  assert.equal(await bus.invoquer(CANAUX.COMPTEUR, -1), false);
  assert.deepEqual(etat.alertes, [{ id: ID, gravite: 'HIGH', projets: 1 }]);
  assert.deepEqual(etat.compteurs, [3, -1]);
});

test('NOT-04 : réglages lus, modifiés ou refusés', async () => {
  const { bus } = installer();
  assert.deepEqual(await bus.invoquer(CANAUX.REGLAGES_LIRE), {
    notificationsPoste: true, demarrageSession: false, demarrageSessionDisponible: true,
  });
  assert.ok(await bus.invoquer(CANAUX.REGLAGES_ECRIRE, { notificationsPoste: false }));
  assert.equal(await bus.invoquer(CANAUX.REGLAGES_ECRIRE, { notificationsPoste: 'oui' }), null);
});

test('notification libre : titre obligatoire, titre et corps bornés', async () => {
  const { bus, etat } = installer();
  assert.equal(await bus.invoquer(CANAUX.NOTIFIER, { title: '', body: 'x' }), false);
  assert.equal(await bus.invoquer(CANAUX.NOTIFIER, { title: 'T'.repeat(500), body: 'C'.repeat(900) }), true);
  assert.equal(etat.notifications[0].title.length, 120);
  assert.equal(etat.notifications[0].body.length, 400);
  assert.equal(bornerNotification({ title: 42 }), null);
  assert.deepEqual(bornerNotification({ title: 'T' }), { title: 'T', body: '' });
});

test('NOT-02-S1 : ouvrir une alerte remet au premier plan la fenêtre masquée ou réduite, sur cette alerte', () => {
  const bus = creerBus();
  const fenetre = creerFenetre(bus.webContents, { visible: false, reduite: true });
  ouvrirAlerte(fenetre, ID);
  assert.equal(fenetre.reduite, false);
  assert.equal(fenetre.visible, true);
  assert.equal(fenetre.auPremierPlan, true);
  assert.deepEqual(bus.webContents.envoyes, [{ canal: CANAUX.OUVRIR_ALERTE, valeur: ID }]);
  assert.doesNotThrow(() => { ouvrirAlerte(null, ID); afficherFenetre(null); });
});
