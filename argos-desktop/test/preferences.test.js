'use strict';
// Réglages du poste (notifier-sur-le-poste : NOT-04, Q-2).
const test = require('node:test');
const assert = require('node:assert/strict');
const path = require('node:path');
const preferences = require('../src/preferences');

/** Système de fichiers en mémoire : les tests n'écrivent rien sur le disque. */
function fsMemoire(initial = {}) {
  const fichiers = new Map(Object.entries(initial));
  return {
    fichiers,
    readFileSync(fichier) {
      if (!fichiers.has(fichier)) throw Object.assign(new Error('absent'), { code: 'ENOENT' });
      return fichiers.get(fichier);
    },
    writeFileSync(fichier, contenu) { fichiers.set(fichier, contenu); },
    renameSync(source, vers) { fichiers.set(vers, fichiers.get(source)); fichiers.delete(source); },
    mkdirSync() {},
  };
}

const FICHIER = path.join('donnees', 'reglages-poste.json');

test('Q-2 : par défaut, notifications du poste actives et démarrage avec la session désactivé', () => {
  assert.deepEqual(preferences.lire(fsMemoire(), FICHIER), { notificationsPoste: true, demarrageSession: false });
  assert.deepEqual(preferences.DEFAUTS, { notificationsPoste: true, demarrageSession: false });
});

test('un fichier illisible ou des valeurs inattendues redonnent les valeurs par défaut', () => {
  assert.deepEqual(preferences.lire(fsMemoire({ [FICHIER]: '{pas du json' }), FICHIER), preferences.DEFAUTS);
  const systemeFichiers = fsMemoire({ [FICHIER]: JSON.stringify({ notificationsPoste: 'non', demarrageSession: 1, autre: true }) });
  assert.deepEqual(preferences.lire(systemeFichiers, FICHIER), preferences.DEFAUTS);
});

test('NOT-04 : couper les notifications du poste est enregistré puis relu', () => {
  const systemeFichiers = fsMemoire();
  const nouveaux = preferences.fusionner(preferences.DEFAUTS, { notificationsPoste: false });
  preferences.ecrire(systemeFichiers, FICHIER, nouveaux);
  assert.deepEqual(preferences.lire(systemeFichiers, FICHIER), { notificationsPoste: false, demarrageSession: false });
  assert.ok(!systemeFichiers.fichiers.has(`${FICHIER}.tmp`));
});

test('une demande sans clé valide est refusée ; les clés inconnues sont ignorées', () => {
  assert.equal(preferences.fusionner(preferences.DEFAUTS, {}), undefined);
  assert.equal(preferences.fusionner(preferences.DEFAUTS, { notificationsPoste: 'false' }), undefined);
  assert.equal(preferences.fusionner(preferences.DEFAUTS, null), undefined);
  assert.deepEqual(
    preferences.fusionner(preferences.DEFAUTS, { demarrageSession: true, __proto__: { x: 1 }, admin: true }),
    { notificationsPoste: true, demarrageSession: true },
  );
});
