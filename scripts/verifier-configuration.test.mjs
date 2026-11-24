// Tests du contrôle de la configuration (scripts/verifier-configuration.mjs) : parité (EXP-07) et durcissement
// (EXP-12, EXP-16).
// Exécution : node --test scripts/*.test.mjs (aucune dépendance, aucun accès réseau).
import { test } from 'node:test';
import assert from 'node:assert/strict';
import {
  clesServices, ecartsDepot, ecartsDurcissement, ecartsOptionsKeycloak, ecartsParite, optionsConstructionKeycloak,
  variablesInterpolees, variablesProduction, variablesService,
} from './verifier-configuration.mjs';

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

const FRAGMENT = [
  'x-durcissement: &durcissement',
  '  read_only: true',
  '  cap_drop:',
  '    - ALL',
  '  security_opt:',
  '    - no-new-privileges:true',
].join('\n');

const COMPOSE_DURCI = [
  FRAGMENT,
  '',
  'services:',
  '  postgres:',
  '    <<: *durcissement',
  '    user: postgres',
  '    tmpfs:',
  '      - /tmp',
  '  ollama:',
  '    image: ollama/ollama:0.12.6',
  '    <<: *durcissement',
  '',
  'volumes:',
  '  argos-pgdata:',
].join('\n');

test('les clés de chaque service sont relevées sans les volumes de premier niveau', () => {
  assert.deepEqual([...clesServices(COMPOSE_DURCI).keys()], ['postgres', 'ollama']);
  assert.deepEqual(clesServices(COMPOSE_DURCI).get('postgres'), ['<<: *durcissement', 'user: postgres', 'tmpfs:']);
});

test('EXP-16-S1 : des services qui fusionnent le fragment complet ne produisent aucun écart', () => {
  assert.deepEqual(ecartsDurcissement(COMPOSE_DURCI), []);
});

test('EXP-16-S1 : une capacité réajoutée ou un service sans fusion est nommé', () => {
  const compose = COMPOSE_DURCI
    .replace('    user: postgres', '    user: postgres\n    cap_add:\n      - CHOWN')
    .replace('    image: ollama/ollama:0.12.6\n    <<: *durcissement', '    image: ollama/ollama:0.12.6');
  assert.deepEqual(ecartsDurcissement(compose), [
    'service ollama de compose.yaml : « <<: *durcissement » absent',
    'service postgres de compose.yaml : « cap_add » redéfini, le durcissement vient de x-durcissement',
  ]);
});

test('EXP-16-S1 : un fragment sans interdiction d\'élévation de privilèges est signalé', () => {
  const compose = COMPOSE_DURCI.replace('    - no-new-privileges:true\n', '');
  assert.deepEqual(ecartsDurcissement(compose), [
    'x-durcissement de compose.yaml : « security_opt: [no-new-privileges:true] » absent',
  ]);
});

test('EXP-16-S1 : read_only à false ou ALL hors de cap_drop ne passent pas le contrôle du fragment', () => {
  const compose = COMPOSE_DURCI
    .replace('  read_only: true', '  read_only: false')
    .replace('  cap_drop:\n    - ALL\n  security_opt:', '  cap_drop:\n    - NET_RAW\n  security_opt:\n    - ALL');
  assert.deepEqual(ecartsDurcissement(compose), [
    'x-durcissement de compose.yaml : « cap_drop: [ALL] » absent',
    'x-durcissement de compose.yaml : « read_only: true » absent',
  ]);
});

const DOCKERFILE_KEYCLOAK = [
  'ARG VERSION_KEYCLOAK',
  'FROM quay.io/keycloak/keycloak:${VERSION_KEYCLOAK} AS construction',
  'ENV KC_DB=postgres \\',
  '    KC_HEALTH_ENABLED=true',
  'RUN /opt/keycloak/bin/kc.sh build',
].join('\n');

const COMPOSE_KEYCLOAK = [
  'services:',
  '  keycloak:',
  '    environment:',
  '      KC_DB: postgres',
  '      KC_HEALTH_ENABLED: "true"',
  '      KC_HTTP_ENABLED: "true"',
].join('\n');

test('les options de construction KC_* sont lues sur une instruction ENV multiligne', () => {
  assert.deepEqual([...optionsConstructionKeycloak(DOCKERFILE_KEYCLOAK)],
    [['KC_DB', 'postgres'], ['KC_HEALTH_ENABLED', 'true']]);
});

test('EXP-16-S3 : options de construction et d\'exécution de Keycloak concordantes', () => {
  assert.deepEqual(ecartsOptionsKeycloak(DOCKERFILE_KEYCLOAK, COMPOSE_KEYCLOAK), []);
});

test('EXP-16-S3 : une option de construction absente ou divergente à l\'exécution est nommée', () => {
  const compose = COMPOSE_KEYCLOAK.replace('      KC_DB: postgres\n', '').replace('"true"', '"false"');
  assert.deepEqual(ecartsOptionsKeycloak(DOCKERFILE_KEYCLOAK, compose), [
    'KC_DB : postgres dans deploiement/keycloak/Dockerfile, absente dans le service keycloak de compose.yaml',
    'KC_HEALTH_ENABLED : true dans deploiement/keycloak/Dockerfile, false dans le service keycloak de compose.yaml',
  ]);
});

test('EXP-07, EXP-16 : application.properties, compose.yaml et .env.example du dépôt sont sans écart', () => {
  assert.deepEqual(ecartsDepot(), []);
});
