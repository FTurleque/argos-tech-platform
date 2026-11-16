'use strict';
// Contenu des notifications et du compteur (notifier-sur-le-poste : NOT-03, NOT-05).
const test = require('node:test');
const assert = require('node:assert/strict');
const { validerAlerte, texteNotification, libelleCompteur, infobulle, compteurValide } = require('../src/notifications');

const ID = '3f2b8c1e-4d5a-4b6c-8d7e-9f0a1b2c3d4e';

test('NOT-05-S1 : titre, gravité et « 3 projets », sans nom de projet ni de composant', () => {
  const alerte = validerAlerte({
    id: ID,
    titre: 'Exécution de code à distance',
    gravite: 'CRITICAL',
    projets: 3,
    // Champs ajoutés par erreur ou par malveillance : ignorés.
    projetsNoms: ['portail-rh', 'paie'],
    composant: 'log4j-core',
    lien: 'https://argos.interne/signaux/1',
  });
  assert.deepEqual(Object.keys(alerte).sort(), ['gravite', 'id', 'projets', 'titre']);
  const texte = texteNotification(alerte);
  assert.deepEqual(texte, { title: 'Exécution de code à distance', body: 'Gravité critique · 3 projets' });
  const affiche = `${texte.title} ${texte.body}`;
  for (const interdit of ['portail-rh', 'paie', 'log4j', 'argos.interne', ID]) {
    assert.ok(!affiche.includes(interdit), interdit);
  }
});

test('un seul projet est au singulier ; une gravité inconnue est « non précisée »', () => {
  const texte = texteNotification(validerAlerte({ id: ID, titre: 'Avis', gravite: 'CVSS:3.1/AV:N', projets: 1 }));
  assert.equal(texte.body, 'Gravité non précisée · 1 projet');
});

test('le titre est ramené à une ligne, sans caractère de contrôle, et borné', () => {
  const alerte = validerAlerte({ id: ID, titre: '  Ligne 1\n‮Ligne\t2\u0007 ', gravite: 'high', projets: 2 });
  assert.equal(alerte.titre, 'Ligne 1 Ligne 2');
  assert.equal(alerte.gravite, 'HIGH');
  const long = validerAlerte({ id: ID, titre: 'x'.repeat(500), gravite: null, projets: 0 });
  assert.equal(long.titre.length, 120);
  assert.ok(long.titre.endsWith('…'));
});

test('une alerte invalide est rejetée', () => {
  for (const payload of [
    null,
    'texte',
    { id: 'pas-un-uuid', titre: 'A', projets: 1 },
    { id: ID, titre: '', projets: 1 },
    { id: ID, titre: '\n\t', projets: 1 },
    { id: ID, titre: 'A', projets: -1 },
    { id: ID, titre: 'A', projets: 1.5 },
    { id: ID, titre: 'A', projets: '3' },
    { id: ID, titre: 42, projets: 1 },
  ]) {
    assert.equal(validerAlerte(payload), null, JSON.stringify(payload));
  }
});

test('NOT-03 : libellé du compteur dans la zone de notification', () => {
  assert.equal(libelleCompteur(0), 'aucune alerte ouverte');
  assert.equal(libelleCompteur(1), '1 alerte ouverte');
  assert.equal(infobulle(3), 'ARGOS — 3 alertes ouvertes');
  assert.equal(compteurValide(3), 3);
  for (const n of [-1, 1.2, NaN, '3', 10000, null]) assert.equal(compteurValide(n), null, String(n));
});
