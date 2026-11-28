'use strict';
// Mise à jour automatique du client desktop (AUD-DEP-15).
const test = require('node:test');
const assert = require('node:assert/strict');
const { creerMiseAJour, texteVersionPrete, VARIABLE_DESACTIVATION } = require('../src/mise-a-jour');

/** autoUpdater factice : réglages, écouteurs et recherches retenus. */
function creerMiseAJourFactice(recherche = () => Promise.resolve(undefined)) {
  return {
    ecouteurs: {},
    recherches: 0,
    autoDownload: false,
    autoInstallOnAppQuit: false,
    on(evenement, ecouteur) { this.ecouteurs[evenement] = ecouteur; },
    checkForUpdates() {
      this.recherches += 1;
      return recherche();
    },
  };
}

function creerJournal() {
  return {
    erreurs: [],
    infos: [],
    consignerErreur(...valeurs) { this.erreurs.push(valeurs.join(' ')); },
    consignerInformation(...valeurs) { this.infos.push(valeurs.join(' ')); },
  };
}

function creer({ estEmpaquete = true, plateforme = 'win32', env = {}, miseAJour = creerMiseAJourFactice() } = {}) {
  const notifications = [];
  const journal = creerJournal();
  let chargements = 0;
  const instance = creerMiseAJour({
    estEmpaquete,
    plateforme,
    env,
    chargerMiseAJour: () => { chargements += 1; return miseAJour; },
    notifier: (texte) => notifications.push(texte),
    journal,
  });
  return { instance, notifications, journal, miseAJour, chargements: () => chargements };
}

test('application empaquetée sous Windows ou Linux : recherche lancée, téléchargement et installation à la fermeture', async () => {
  for (const plateforme of ['win32', 'linux']) {
    const { instance, miseAJour } = creer({ plateforme });
    assert.equal(instance.demarrer(), true, plateforme);
    await new Promise((resolve) => setImmediate(resolve));
    assert.equal(miseAJour.recherches, 1);
    assert.equal(miseAJour.autoDownload, true);
    assert.equal(miseAJour.autoInstallOnAppQuit, true);
  }
});

test('lancement depuis les sources, macOS ou désactivation : electron-updater n’est pas même chargé', () => {
  const cas = [
    { estEmpaquete: false },
    { plateforme: 'darwin' },
    { env: { [VARIABLE_DESACTIVATION]: 'off' } },
  ];
  for (const options of cas) {
    const { instance, chargements } = creer(options);
    assert.equal(instance.active(), false, JSON.stringify(options));
    assert.equal(instance.demarrer(), false);
    assert.equal(chargements(), 0);
  }
});

test('version téléchargée : notification de la version prête et journal', () => {
  const { instance, miseAJour, notifications, journal } = creer();
  instance.demarrer();
  miseAJour.ecouteurs['update-downloaded']({ version: '0.2.0' });
  assert.deepEqual(notifications, [texteVersionPrete('0.2.0')]);
  assert.deepEqual(notifications[0], {
    title: 'Mise à jour d’ARGOS prête', body: 'La version 0.2.0 sera installée à la fermeture d’ARGOS.',
  });
  assert.deepEqual(journal.infos, ['Mise à jour d’ARGOS 0.2.0 téléchargée.']);
});

test('erreur de mise à jour ou recherche en échec : journalisées avec leur cause, sans interrompre ARGOS', async () => {
  const miseAJour = creerMiseAJourFactice(() => Promise.reject(new Error('dépôt injoignable')));
  const { instance, journal } = creer({ miseAJour });
  assert.equal(instance.demarrer(), true);
  miseAJour.ecouteurs.error(new Error('empreinte SHA-512 différente'));
  await new Promise((resolve) => setImmediate(resolve));
  assert.deepEqual(journal.erreurs, [
    'Mise à jour d’ARGOS impossible : empreinte SHA-512 différente',
    'Recherche de mise à jour d’ARGOS impossible : dépôt injoignable',
  ]);
});

test('electron-updater qui ne se charge pas : erreur journalisée, ARGOS démarre sans mise à jour', () => {
  const journal = creerJournal();
  const instance = creerMiseAJour({
    estEmpaquete: true,
    plateforme: 'linux',
    env: {},
    chargerMiseAJour: () => { throw new Error('module introuvable'); },
    notifier: () => {},
    journal,
  });
  assert.equal(instance.demarrer(), false);
  assert.deepEqual(journal.erreurs, ['Mise à jour d’ARGOS indisponible : module introuvable']);
});

test('recherche qui lève dès l’appel : erreur journalisée, sans rejet non géré', async () => {
  const miseAJour = creerMiseAJourFactice(() => { throw new Error('flux illisible'); });
  const { instance, journal } = creer({ miseAJour });
  assert.equal(instance.demarrer(), true);
  await new Promise((resolve) => setImmediate(resolve));
  assert.deepEqual(journal.erreurs, ['Recherche de mise à jour d’ARGOS impossible : flux illisible']);
});
