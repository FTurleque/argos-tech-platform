'use strict';
// Pont window.argosDesktop (preload.js, notifier-sur-le-poste : NOT-01 à NOT-05).
// Le preload livré est exécuté dans un contexte isolé avec un Electron factice.
const test = require('node:test');
const assert = require('node:assert/strict');
const systemeFichiers = require('node:fs');
const { CANAUX } = require('../src/canaux');
const { chargerPreload, PRELOAD } = require('./electron-factice');

const ID_ALERTE = '3f2b8c1e-4d5a-4b6c-8d7e-9f0a1b2c3d4e';

/** ipcRenderer factice qui enregistre les appels et renvoie `reponse`. */
function rendu(reponse = true) {
  const appels = [];
  const ecouteurs = new Map();
  return {
    appels,
    ecouteurs,
    // Clonage structuré, comme entre les processus d'Electron (et hors du contexte du preload).
    invoke: async (canal, valeur) => { appels.push({ canal, valeur: structuredClone(valeur) }); return reponse; },
    on: (canal, ecouteur) => { ecouteurs.set(canal, [...(ecouteurs.get(canal) ?? []), ecouteur]); },
    removeListener: (canal, ecouteur) => { ecouteurs.set(canal, (ecouteurs.get(canal) ?? []).filter((present) => present !== ecouteur)); },
    emettre: (canal, valeur) => { for (const ecouteur of ecouteurs.get(canal) ?? []) ecouteur({}, valeur); },
  };
}

test('le pont expose seulement les notifications, le compteur, les réglages du poste et la connexion', () => {
  const { argosDesktop, ...autres } = chargerPreload(rendu(), 'linux');
  assert.deepEqual(Object.keys(autres), [], 'un seul objet exposé');
  assert.deepEqual(Object.keys(argosDesktop).sort(), [
    'autoriserConnexion', 'getSettings', 'notify', 'notifyAlert', 'onOpenAlert', 'onSettingsChanged',
    'platform', 'preparerConnexion', 'setAlertCount', 'setSettings',
  ]);
  assert.equal(argosDesktop.platform, 'linux');
});

test('le preload ne charge que le module electron (preload sandboxé)', () => {
  // chargerPreload refuse tout autre module ; le chargement ci-dessus a réussi.
  const source = systemeFichiers.readFileSync(PRELOAD, 'utf8');
  const modules = [...source.matchAll(/require\(\s*['"]([^'"]+)['"]\s*\)/g)].map((correspondance) => correspondance[1]);
  assert.deepEqual(modules, ['electron']);
});

test('les canaux du preload sont ceux déclarés par le processus principal', () => {
  const source = systemeFichiers.readFileSync(PRELOAD, 'utf8');
  const utilises = new Set([...source.matchAll(/'(argos:[a-z-]+)'/g)].map((correspondance) => correspondance[1]));
  assert.deepEqual([...utilises].sort(), Object.values(CANAUX).sort());
});

test('NOT-01-S1 / NOT-05 : notifyAlert ne transmet que l’identifiant, la gravité et le nombre de projets', async () => {
  const ipc = rendu(true);
  const { argosDesktop } = chargerPreload(ipc);
  const resultat = await argosDesktop.notifyAlert({
    id: ID_ALERTE,
    gravite: 'CRITICAL',
    projets: 3,
    titre: 'Prototype pollution in lodash',
    contenu: { elements: [{ projets: [{ nom: 'portail-rh' }] }] },
  });
  assert.equal(resultat, true);
  assert.deepEqual(ipc.appels, [{ canal: CANAUX.ALERTE, valeur: { id: ID_ALERTE, gravite: 'CRITICAL', projets: 3 } }]);
});

test('notifyAlert convertit les valeurs en types simples, même pour une entrée absente ou piégée', async () => {
  const ipc = rendu(false);
  const { argosDesktop } = chargerPreload(ipc);
  await argosDesktop.notifyAlert(undefined);
  await argosDesktop.notifyAlert({ id: { toString: () => ID_ALERTE }, gravite: 7, projets: '2' });
  assert.deepEqual(ipc.appels.map((appel) => appel.valeur), [
    { id: '', gravite: null, projets: Number.NaN },
    { id: ID_ALERTE, gravite: '7', projets: 2 },
  ]);
});

test('NOT-03-S1 : setAlertCount transmet un nombre sur le canal du compteur', async () => {
  const ipc = rendu(true);
  const { argosDesktop } = chargerPreload(ipc);
  await argosDesktop.setAlertCount(3);
  await argosDesktop.setAlertCount('4');
  assert.deepEqual(ipc.appels, [
    { canal: CANAUX.COMPTEUR, valeur: 3 },
    { canal: CANAUX.COMPTEUR, valeur: 4 },
  ]);
});

test('NOT-02-S1 : onOpenAlert reçoit l’identifiant de l’alerte cliquée et se désabonne', () => {
  const ipc = rendu();
  const { argosDesktop } = chargerPreload(ipc);
  const recus = [];
  const desabonner = argosDesktop.onOpenAlert((id) => recus.push(id));
  ipc.emettre(CANAUX.OUVRIR_ALERTE, ID_ALERTE);
  desabonner();
  ipc.emettre(CANAUX.OUVRIR_ALERTE, 'autre');
  assert.deepEqual(recus, [ID_ALERTE]);
  assert.equal(ipc.ecouteurs.get(CANAUX.OUVRIR_ALERTE).length, 0);
  // Un rappel qui n'est pas une fonction n'abonne rien.
  assert.equal(typeof argosDesktop.onOpenAlert('pas une fonction'), 'function');
  assert.equal(ipc.ecouteurs.get(CANAUX.OUVRIR_ALERTE).length, 0);
});

test('ACC-10 : la connexion ne transmet qu’une URL texte et ne renvoie que la réponse du processus principal', async () => {
  const ipc = rendu('?code=c&state=s');
  const { argosDesktop } = chargerPreload(ipc);
  await argosDesktop.preparerConnexion('ignoré');
  assert.equal(await argosDesktop.autoriserConnexion({ toString: () => 'http://kc/auth' }), '?code=c&state=s');
  await argosDesktop.autoriserConnexion(undefined);
  assert.deepEqual(ipc.appels, [
    { canal: CANAUX.CONNEXION_PREPARER, valeur: undefined },
    { canal: CANAUX.CONNEXION_AUTORISER, valeur: 'http://kc/auth' },
    { canal: CANAUX.CONNEXION_AUTORISER, valeur: '' },
  ]);
});

test('NOT-04 : setSettings ne transmet que les deux réglages du poste', async () => {
  const ipc = rendu({});
  const { argosDesktop } = chargerPreload(ipc);
  await argosDesktop.setSettings({ notificationsPoste: false, demarrageSession: true, autre: 'x' });
  await argosDesktop.getSettings();
  assert.deepEqual(ipc.appels, [
    { canal: CANAUX.REGLAGES_ECRIRE, valeur: { notificationsPoste: false, demarrageSession: true } },
    { canal: CANAUX.REGLAGES_LIRE, valeur: undefined },
  ]);
});
