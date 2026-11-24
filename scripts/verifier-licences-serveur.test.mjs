// Tests du contrôle des licences du serveur (scripts/verifier-licences-serveur.mjs).
// Exécution : node --test scripts/*.test.mjs (aucune dépendance, aucun accès réseau).
// Chaque cas écrit un inventaire CycloneDX factice dans un dossier temporaire, lance le script sur cet
// inventaire avec un dépôt Maven local vide, puis vérifie le code de sortie et le message.
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { spawnSync } from 'node:child_process';
import { existsSync, mkdirSync, mkdtempSync, readFileSync, rmSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const SCRIPT = join(dirname(fileURLToPath(import.meta.url)), 'verifier-licences-serveur.mjs');
const SORTIE_CONFORME = 0;
const SORTIE_ECART = 1;
const SORTIE_ARGUMENTS = 2;

// Composant sans groupe : le script ne cherche pas d'archive dans le dépôt Maven local.
function composant(nom, licences) {
  return { type: 'library', name: nom, version: '1.0.0', licenses: licences };
}

function verifier(composants, { ecrireInventaire = true } = {}) {
  const dossier = mkdtempSync(join(tmpdir(), 'argos-licences-'));
  try {
    const depot = join(dossier, 'depot');
    mkdirSync(depot);
    const inventaire = join(dossier, 'bom.json');
    if (ecrireInventaire) {
      writeFileSync(inventaire, JSON.stringify({ bomFormat: 'CycloneDX', specVersion: '1.6', components: composants }));
    }
    const execution = spawnSync(process.execPath, [SCRIPT, inventaire, '--depot', depot], { encoding: 'utf8' });
    const listeTiers = join(dossier, 'THIRD-PARTY.txt');
    return {
      statut: execution.status,
      erreurs: execution.stderr,
      listeTiers: existsSync(listeTiers) ? readFileSync(listeTiers, 'utf8') : undefined,
    };
  } finally {
    rmSync(dossier, { recursive: true, force: true });
  }
}

test('une dépendance sous GPL est refusée', () => {
  const resultat = verifier([composant('outil-gpl', [{ license: { id: 'GPL-3.0-only' } }])]);
  assert.equal(resultat.statut, SORTIE_ECART);
  assert.match(resultat.erreurs, /outil-gpl:1\.0\.0 : licence non admise sans décision explicite \(GPL-3\.0-only\)/);
});

test('une dépendance sans licence déclarée est refusée', () => {
  const resultat = verifier([composant('sans-licence', [])]);
  assert.equal(resultat.statut, SORTIE_ECART);
  assert.match(resultat.erreurs, /sans-licence:1\.0\.0 : aucune licence déclarée/);
});

test('un inventaire CycloneDX absent est une erreur d\'arguments', () => {
  const resultat = verifier([], { ecrireInventaire: false });
  assert.equal(resultat.statut, SORTIE_ARGUMENTS);
  assert.match(resultat.erreurs, /Fichier CycloneDX absent/);
});

test('un inventaire conforme est accepté et produit la liste des dépendances tierces', () => {
  const resultat = verifier([
    composant('bibliotheque-apache', [{ license: { id: 'Apache-2.0' } }]),
    composant('bibliotheque-domaine-public', [{ expression: 'CC0-1.0 OR GPL-2.0-only' }]),
  ]);
  assert.equal(resultat.statut, SORTIE_CONFORME, resultat.erreurs);
  assert.match(resultat.listeTiers, /bibliotheque-apache:1\.0\.0\n {4}licences déclarées : Apache-2\.0/);
  assert.match(resultat.listeTiers, /bibliotheque-domaine-public:1\.0\.0[^]*licence retenue {4}: CC0-1\.0/);
});
