'use strict';
// Contenu des notifications et du compteur (notifier-sur-le-poste : NOT-03, NOT-05).
const test = require('node:test');
const assert = require('node:assert/strict');
const { TITRE_NOTIFICATION, validerAlerte, texteNotification, libelleCompteur, infobulle, compteurValide } = require('../src/notifications');

const ID = '3f2b8c1e-4d5a-4b6c-8d7e-9f0a1b2c3d4e';

test('NOT-05-S1 : titre générique, gravité et « 3 projets », sans nom de projet ni de composant', () => {
  const alerte = validerAlerte({
    id: ID,
    // Titre public d'un signal, qui nomme le composant touché : jamais affiché.
    titre: 'Remote code execution in log4j-core',
    gravite: 'CRITICAL',
    projets: 3,
    // Champs ajoutés par erreur ou par malveillance : ignorés.
    projetsNoms: ['portail-rh', 'paie'],
    composant: 'log4j-core',
    lien: 'https://argos.interne/signaux/1',
  });
  assert.deepEqual(Object.keys(alerte).sort(), ['gravite', 'id', 'projets']);
  const texte = texteNotification(alerte);
  assert.deepEqual(texte, { title: TITRE_NOTIFICATION, body: 'Gravité critique · 3 projets' });
  assert.equal(TITRE_NOTIFICATION, 'Nouvelle alerte ARGOS');
  const affiche = `${texte.title} ${texte.body}`;
  for (const interdit of ['portail-rh', 'paie', 'log4j', 'Remote code', 'argos.interne', ID]) {
    assert.ok(!affiche.includes(interdit), interdit);
  }
});

test('un seul projet est au singulier ; une gravité inconnue est « non précisée »', () => {
  const texte = texteNotification(validerAlerte({ id: ID, gravite: 'CVSS:3.1/AV:N', projets: 1 }));
  assert.equal(texte.body, 'Gravité non précisée · 1 projet');
  assert.equal(validerAlerte({ id: ID, gravite: ' high ', projets: 2 }).gravite, 'HIGH');
});

test('une alerte invalide est rejetée', () => {
  for (const payload of [
    null,
    'texte',
    { id: 'pas-un-uuid', projets: 1 },
    { id: ID, projets: -1 },
    { id: ID, projets: 1.5 },
    { id: ID, projets: '3' },
    { id: ID, projets: 10000 },
    { id: 42, projets: 1 },
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
