// Tests du contrôle du royaume livré (scripts/verifier-royaume.mjs) : ACC-13-S4.
// Exécution : node --test scripts/*.test.mjs (aucune dépendance, aucun accès réseau).
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { ecartsDepot, ecartsRoyaume, REGLES_MOT_DE_PASSE } from './verifier-royaume.mjs';

const CONFORME = {
  sslRequired: 'external',
  bruteForceProtected: true,
  failureFactor: 5,
  waitIncrementSeconds: 60,
  maxFailureWaitSeconds: 900,
  passwordPolicy: REGLES_MOT_DE_PASSE,
  clients: [{
    clientId: 'argos-ui',
    redirectUris: ['${ARGOS_PUBLIC_URL}/', 'http://127.0.0.1/argos/retour'],
    webOrigins: ['${ARGOS_PUBLIC_URL}', 'app://argos'],
    attributes: { 'post.logout.redirect.uris': '${ARGOS_PUBLIC_URL}/' },
  }],
};

test('le royaume livré par le dépôt respecte les réglages exigés', () => {
  assert.deepEqual(ecartsDepot(), []);
});

test('un royaume conforme ne produit aucun écart', () => {
  assert.deepEqual(ecartsRoyaume(CONFORME), []);
});

test('la protection contre la force brute absente ou désactivée est signalée', () => {
  const sans = { ...CONFORME };
  delete sans.bruteForceProtected;
  assert.equal(ecartsRoyaume(sans).filter((ecart) => ecart.startsWith('bruteForceProtected')).length, 1);
  assert.equal(ecartsRoyaume({ ...CONFORME, bruteForceProtected: false }).length, 1);
});

test('des seuils différents de 5 échecs, 60 secondes et 15 minutes sont signalés un par un', () => {
  const ecarts = ecartsRoyaume({ ...CONFORME, failureFactor: 30, waitIncrementSeconds: 5, maxFailureWaitSeconds: 3600 });
  assert.equal(ecarts.length, 3);
  assert.ok(ecarts.some((ecart) => ecart.startsWith('failureFactor : 5 attendu, 30 trouvé')));
});

test('le blocage définitif est signalé', () => {
  assert.equal(ecartsRoyaume({ ...CONFORME, permanentLockout: true }).length, 1);
});

test('une politique de mot de passe absente ou différente est signalée', () => {
  assert.equal(ecartsRoyaume({ ...CONFORME, passwordPolicy: '' }).length, 1);
  assert.equal(ecartsRoyaume({ ...CONFORME, passwordPolicy: 'length(8)' }).length, 1);
});

test('une exigence de TLS autre que « external » est signalée', () => {
  for (const valeur of ['none', undefined]) {
    assert.equal(ecartsRoyaume({ ...CONFORME, sslRequired: valeur }).length, 1);
  }
});

test('un joker dans une adresse du client du portail est signalé, où qu’il soit', () => {
  const client = CONFORME.clients[0];
  for (const variante of [
    { ...client, redirectUris: ['http://localhost:8088/*'] },
    { ...client, webOrigins: ['*'] },
    { ...client, attributes: { 'post.logout.redirect.uris': 'http://localhost:8088/*' } },
    { ...client, attributes: { 'post.logout.redirect.uris': 'http://a/##http://b/*' } },
  ]) {
    assert.equal(ecartsRoyaume({ ...CONFORME, clients: [variante] }).length, 1);
  }
});

test('un royaume sans client du portail est signalé', () => {
  assert.equal(ecartsRoyaume({ ...CONFORME, clients: [] }).length, 1);
});
