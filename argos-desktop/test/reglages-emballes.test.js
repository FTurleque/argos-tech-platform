'use strict';
const test = require('node:test');
const assert = require('node:assert/strict');
const { resoudreReglages, API_URL_PAR_DEFAUT, OIDC_URL_PAR_DEFAUT } = require('../src/reglages-emballes');

const EMBALLES = { apiUrl: 'https://argos.exemple.fr', oidcUrl: 'https://auth.exemple.fr/realms/argos' };

test('sans variable ni réglage emballé, les valeurs par défaut du poste de développement s’appliquent', () => {
  assert.deepEqual(resoudreReglages({}, undefined), { apiUrl: API_URL_PAR_DEFAUT, oidcUrl: OIDC_URL_PAR_DEFAUT });
  assert.deepEqual(resoudreReglages({}, {}), { apiUrl: API_URL_PAR_DEFAUT, oidcUrl: OIDC_URL_PAR_DEFAUT });
});

test('les réglages emballés l’emportent sur les valeurs par défaut', () => {
  assert.deepEqual(resoudreReglages({}, EMBALLES), EMBALLES);
});

test('la variable d’environnement l’emporte sur le réglage emballé', () => {
  const environnement = { ARGOS_API_URL: 'http://nas:8080', ARGOS_OIDC_URL: 'http://nas:8180/realms/argos' };
  assert.deepEqual(resoudreReglages(environnement, EMBALLES), { apiUrl: 'http://nas:8080', oidcUrl: 'http://nas:8180/realms/argos' });
});

test('chaque réglage se résout seul : l’un emballé, l’autre par variable', () => {
  assert.deepEqual(
    resoudreReglages({ ARGOS_OIDC_URL: 'http://nas:8180/realms/argos' }, { apiUrl: EMBALLES.apiUrl }),
    { apiUrl: EMBALLES.apiUrl, oidcUrl: 'http://nas:8180/realms/argos' },
  );
});

test('une variable vide est ignorée', () => {
  assert.deepEqual(resoudreReglages({ ARGOS_API_URL: '', ARGOS_OIDC_URL: '' }, EMBALLES), EMBALLES);
});

test('le package.json livré déclare ses réglages sous le champ argos, lu par main.js', () => {
  const livre = require('../package.json');
  assert.ok(livre.argos === undefined || typeof livre.argos === 'object');
  const resolus = resoudreReglages({}, livre.argos);
  assert.match(resolus.apiUrl, /^https?:\/\//);
  assert.match(resolus.oidcUrl, /^https?:\/\//);
});
