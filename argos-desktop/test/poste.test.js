'use strict';
// Comportement du poste face aux alertes (notifier-sur-le-poste : NOT-01 à NOT-05).
// Les objets Electron (Notification, zone de notification, fenêtre) sont remplacés par des doublures.
const test = require('node:test');
const assert = require('node:assert/strict');
const { creerPoste } = require('../src/poste');
const preferences = require('../src/preferences');

const ID = '3f2b8c1e-4d5a-4b6c-8d7e-9f0a1b2c3d4e';
const ALERTE = { id: ID, gravite: 'CRITICAL', projets: 3 };

function doublures({ supportees = true, reglages = { ...preferences.DEFAUTS }, demarrageDisponible } = {}) {
  const etat = { notifications: [], compteurs: [], ouvertures: [], enregistres: [], reglages };
  class NotificationFactice {
    constructor(options) {
      this.options = options;
      this.ecouteurs = {};
      this.affichee = false;
      etat.notifications.push(this);
    }
    on(evenement, fn) { this.ecouteurs[evenement] = fn; }
    show() { this.affichee = true; }
    cliquer() { this.ecouteurs.click?.(); }
  }
  const poste = creerPoste({
    notificationsSupportees: () => supportees,
    creerNotification: (options) => new NotificationFactice(options),
    reglages: () => etat.reglages,
    enregistrerReglages: (r) => { etat.reglages = r; etat.enregistres.push(r); },
    afficherCompteur: (n) => etat.compteurs.push(n),
    ouvrirAlerte: (id) => etat.ouvertures.push(id),
    demarrageDisponible,
  });
  return { poste, etat };
}

test('NOT-01-S1 : une alerte reçue, fenêtre masquée, affiche une notification système', () => {
  const { poste, etat } = doublures();
  assert.deepEqual(poste.alerte(ALERTE), { affichee: true });
  assert.equal(etat.notifications.length, 1);
  assert.ok(etat.notifications[0].affichee);
  assert.deepEqual(etat.notifications[0].options, {
    title: 'Nouvelle alerte ARGOS',
    body: 'Gravité critique · 3 projets',
  });
});

test('NOT-01-S2 : notifications non disponibles sur le système, le compteur est quand même mis à jour', () => {
  const { poste, etat } = doublures({ supportees: false });
  assert.deepEqual(poste.alerte(ALERTE), { affichee: false, raison: 'NON_SUPPORTEES' });
  assert.equal(etat.notifications.length, 0);
  assert.ok(poste.definirCompteur(1));
  assert.deepEqual(etat.compteurs, [1]);
  assert.equal(poste.infobulle(), 'ARGOS — 1 alerte ouverte');
});

test('NOT-02-S1 : un clic sur la notification ouvre la fenêtre sur cette alerte', () => {
  const { poste, etat } = doublures();
  poste.alerte(ALERTE);
  assert.equal(poste.notificationsEnCours(), 1);
  etat.notifications[0].cliquer();
  assert.deepEqual(etat.ouvertures, [ID]);
  assert.equal(poste.notificationsEnCours(), 0);
});

test('NOT-03-S1 : deux alertes ouvertes, une troisième arrive, le compteur indique trois', () => {
  const { poste, etat } = doublures();
  poste.definirCompteur(2);
  assert.equal(poste.infobulle(), 'ARGOS — 2 alertes ouvertes');
  poste.alerte({ ...ALERTE, id: '00000000-0000-4000-8000-000000000003' });
  poste.definirCompteur(3);
  assert.equal(poste.compteur(), 3);
  assert.equal(poste.infobulle(), 'ARGOS — 3 alertes ouvertes');
  assert.deepEqual(etat.compteurs, [2, 3]);
  assert.equal(poste.definirCompteur(-4), false);
  assert.equal(poste.compteur(), 3);
});

test('NOT-04-S1 : notifications du poste coupées, aucune notification, le compteur reste à jour', () => {
  const { poste, etat } = doublures();
  assert.deepEqual(poste.changerReglages({ notificationsPoste: false }), {
    notificationsPoste: false,
    demarrageSession: false,
  });
  assert.deepEqual(poste.alerte(ALERTE), { affichee: false, raison: 'COUPEES' });
  assert.equal(etat.notifications.length, 0);
  assert.ok(poste.definirCompteur(1));
  assert.deepEqual(etat.enregistres, [{ notificationsPoste: false, demarrageSession: false }]);
});

test('Q-2 : le démarrage avec la session s’active à la demande, sans toucher aux notifications', () => {
  const { poste, etat } = doublures();
  assert.equal(etat.reglages.demarrageSession, false);
  poste.changerReglages({ demarrageSession: true });
  assert.deepEqual(etat.reglages, { notificationsPoste: true, demarrageSession: true });
  assert.equal(poste.changerReglages({ inconnu: true }), null);
});

test('Q-2 : là où l’option est indisponible (Linux), le démarrage avec la session reste désactivé', () => {
  const { poste, etat } = doublures({ demarrageDisponible: false });
  assert.equal(poste.changerReglages({ demarrageSession: true }), null);
  assert.deepEqual(etat.enregistres, []);
  assert.deepEqual(poste.changerReglages({ notificationsPoste: false, demarrageSession: true }), {
    notificationsPoste: false,
    demarrageSession: false,
  });
  assert.equal(etat.reglages.demarrageSession, false);
});

test('NOT-05 : une alerte malformée n’affiche rien', () => {
  const { poste, etat } = doublures();
  assert.deepEqual(poste.alerte({ ...ALERTE, id: '../../etc' }), { affichee: false, raison: 'INVALIDE' });
  assert.deepEqual(poste.alerte({ ...ALERTE, projets: 'trois' }), { affichee: false, raison: 'INVALIDE' });
  assert.equal(etat.notifications.length, 0);
});
