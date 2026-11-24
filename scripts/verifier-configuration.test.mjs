// Tests du contrôle de parité de la configuration (scripts/verifier-configuration.mjs, EXP-07).
// Exécution : node --test scripts/*.test.mjs (aucune dépendance, aucun accès réseau).
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { ecartsDepot, ecartsParite, variablesInterpolees, variablesProduction, variablesService } from './verifier-configuration.mjs';

const PROPRIETES = [
  '# ${ARGOS_COMMENTEE} ignorée',
  '%prod.quarkus.datasource.jdbc.url=${ARGOS_DB_URL}',
  'quarkus.mailer.host=${ARGOS_SMTP_HOST:localhost}',
  '%dev.quarkus.datasource.password=${ARGOS_DEV_SEULEMENT}',
  '%test.argos.valeur=${ARGOS_TEST_SEULEMENT:1}',
].join('\n');

const COMPOSE = [
  'services:',
  '  postgres:',
  '    environment:',
  '      ARGOS_SMTP_HOST: autre-service',
  '      POSTGRES_PASSWORD: ${ARGOS_DB_ADMIN_PASSWORD:?obligatoire}',
  '    healthcheck:',
  '      test: ["CMD-SHELL", "pg_isready -U $${POSTGRES_USER}"]',
  '  argos:',
  '    environment:',
  '      # commentaire',
  '      ARGOS_DB_URL: jdbc:postgresql://postgres:5432/${ARGOS_DB_NAME:-argos}',
  '      ARGOS_SMTP_HOST: ${ARGOS_SMTP_HOST:-localhost}',
  '    mem_limit: ${ARGOS_MEM_LIMIT:-1g}',
].join('\n');

const MODELE = [
  'ARGOS_DB_NAME=argos',
  'ARGOS_DB_ADMIN_PASSWORD=',
  '# ARGOS_SMTP_HOST=relais.exemple.fr',
  'ARGOS_DB_URL=',
  'ARGOS_MEM_LIMIT=1g',
].join('\n');

test('seules les variables lues sans profil ou en %prod. sont des variables de production', () => {
  assert.deepEqual([...variablesProduction(PROPRIETES)].sort(), ['ARGOS_DB_URL', 'ARGOS_SMTP_HOST']);
});

test('le bloc environment du service argos est lu sans celui des autres services', () => {
  assert.deepEqual([...variablesService(COMPOSE, 'argos')].sort(), ['ARGOS_DB_URL', 'ARGOS_SMTP_HOST']);
});

test('les interpolations de Compose sont relevées, dollars échappés exclus', () => {
  assert.deepEqual([...variablesInterpolees(COMPOSE)].sort(),
    ['ARGOS_DB_ADMIN_PASSWORD', 'ARGOS_DB_NAME', 'ARGOS_MEM_LIMIT', 'ARGOS_SMTP_HOST']);
});

test('EXP-07-S3 : une configuration à parité ne produit aucun écart', () => {
  assert.deepEqual(ecartsParite({ proprietes: PROPRIETES, compose: COMPOSE, modele: MODELE }), []);
});

test('EXP-07-S2 : une variable lue mais non transmise est nommée avec le fichier où elle manque', () => {
  const proprietes = `${PROPRIETES}\nargos.scm.github.token=\${ARGOS_GITHUB_TOKEN:}`;
  assert.deepEqual(ecartsParite({ proprietes, compose: COMPOSE, modele: MODELE }), [
    'ARGOS_GITHUB_TOKEN : lue par argos-server/src/main/resources/application.properties, absente de .env.example',
    'ARGOS_GITHUB_TOKEN : lue par argos-server/src/main/resources/application.properties, absente du bloc '
      + 'environment du service argos de compose.yaml',
  ]);
});

test('une variable interpolée par Compose mais absente du modèle est nommée', () => {
  const compose = `${COMPOSE}\n  ollama:\n    mem_limit: \${ARGOS_OLLAMA_MEM_LIMIT:-3g}`;
  assert.deepEqual(ecartsParite({ proprietes: PROPRIETES, compose, modele: MODELE }),
    ['ARGOS_OLLAMA_MEM_LIMIT : interpolée par compose.yaml, absente de .env.example']);
});

test('EXP-07 : application.properties, compose.yaml et .env.example du dépôt sont à parité', () => {
  assert.deepEqual(ecartsDepot(), []);
});
