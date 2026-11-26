// Tests du contrôle de couverture par module (scripts/verifier-couverture-modules.mjs).
// Exécution : node --test scripts/*.test.mjs (aucune dépendance ; rapports JaCoCo factices).
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { mkdtempSync, mkdirSync, rmSync, writeFileSync } from 'node:fs';
import { join } from 'node:path';
import { tmpdir } from 'node:os';
import {
  agregerParModule, ecartsAuxSeuils, EntreeInvalide, lireOptions, moduleDuPaquet, seuilsDuPom, seuilsEffectifs, taux,
  verifier, POM_PAR_DEFAUT, RAPPORT_PAR_DEFAUT,
} from './verifier-couverture-modules.mjs';

const ENTETE = 'GROUP,PACKAGE,CLASS,INSTRUCTION_MISSED,INSTRUCTION_COVERED,BRANCH_MISSED,BRANCH_COVERED,'
  + 'LINE_MISSED,LINE_COVERED,COMPLEXITY_MISSED,COMPLEXITY_COVERED,METHOD_MISSED,METHOD_COVERED';

/** Ligne de rapport : paquet, classe, branches manquées/couvertes, lignes manquées/couvertes. */
function ligne(paquet, classe, branches, lignes) {
  return `argos-server,${paquet},${classe},0,0,${branches[0]},${branches[1]},${lignes[0]},${lignes[1]},0,0,0,0`;
}

function rapport(...lignes) {
  return [ENTETE, ...lignes].join('\n') + '\n';
}

const POM = `<project><properties>
  <argos.couverture.lignes>0.85</argos.couverture.lignes>
  <argos.couverture.branches>0.70</argos.couverture.branches>
</properties></project>`;

const SEUILS = { lignes: 0.85, branches: 0.70 };

/** Sortie qui retient les messages au lieu de les afficher. */
function sortieMemoire() {
  const messages = { log: [], error: [] };
  return { messages, log: (texte) => messages.log.push(texte), error: (texte) => messages.error.push(texte) };
}

/** Dépôt factice : pom et rapport aux emplacements par défaut. */
function depotFactice(texteRapport, textePom = POM) {
  const racine = mkdtempSync(join(tmpdir(), 'couverture-modules-'));
  mkdirSync(join(racine, 'argos-server', 'target', 'site', 'jacoco'), { recursive: true });
  writeFileSync(join(racine, POM_PAR_DEFAUT), textePom);
  writeFileSync(join(racine, RAPPORT_PAR_DEFAUT), texteRapport);
  return racine;
}

test('le module est le premier segment après io.argos.', () => {
  assert.equal(moduleDuPaquet('io.argos.workspace'), 'workspace');
  assert.equal(moduleDuPaquet('io.argos.api.comptes'), 'api');
  assert.equal(moduleDuPaquet('org.exemple'), 'org.exemple');
});

test('les compteurs de plusieurs paquets et classes d’un même module sont additionnés', () => {
  const agregats = agregerParModule(rapport(
    ligne('io.argos.api', 'A', [1, 3], [2, 8]),
    ligne('io.argos.api.comptes', 'B', [0, 4], [1, 9]),
    ligne('io.argos.audit', 'C', [0, 0], [0, 5]),
  ));
  assert.deepEqual([...agregats.keys()], ['api', 'audit']);
  assert.deepEqual(agregats.get('api'), { lignesManquees: 3, lignesCouvertes: 17, branchesManquees: 1, branchesCouvertes: 7 });
});

test('un module sans branche n’est pas en écart sur les branches', () => {
  assert.equal(taux(0, 0), null);
  const agregats = agregerParModule(rapport(ligne('io.argos.audit', 'C', [0, 0], [0, 5])));
  assert.deepEqual(ecartsAuxSeuils(agregats, SEUILS), []);
});

test('un module sous un seuil produit un message par compteur', () => {
  const agregats = agregerParModule(rapport(
    ligne('io.argos.scm', 'Forge', [4, 6], [3, 7]),
    ligne('io.argos.workspace', 'Espaces', [1, 9], [1, 19]),
  ));
  assert.deepEqual(ecartsAuxSeuils(agregats, SEUILS), [
    'module scm : lignes 70.0 % sous le seuil de 85.0 %',
    'module scm : branches 60.0 % sous le seuil de 70.0 %',
  ]);
});

test('un taux égal au seuil est conforme', () => {
  const agregats = agregerParModule(rapport(ligne('io.argos.audit', 'C', [3, 7], [15, 85])));
  assert.deepEqual(ecartsAuxSeuils(agregats, SEUILS), []);
});

test('un rapport vide, sans colonne attendue ou mal formé est refusé', () => {
  assert.throws(() => agregerParModule(''), EntreeInvalide);
  assert.throws(() => agregerParModule('GROUP,PACKAGE\nargos,io.argos.api\n'), /colonnes absentes/);
  assert.throws(() => agregerParModule(rapport('argos-server,io.argos.api,A,0')), /champs/);
  assert.throws(() => agregerParModule(rapport(ligne('io.argos.api', 'A', [0, 'x'], [0, 1]))), /entier/);
});

test('les seuils par défaut sont ceux du pom, remplacés par les options', () => {
  assert.deepEqual(seuilsDuPom(POM), SEUILS);
  assert.deepEqual(seuilsEffectifs({ lignes: '0.9' }, POM), { lignes: 0.9, branches: 0.70 });
  assert.throws(() => seuilsDuPom('<project/>'), /argos\.couverture\.lignes absente/);
  assert.throws(() => seuilsEffectifs({ branches: '70' }, POM), /entre 0 et 1/);
});

test('une option inconnue ou sans valeur est refusée', () => {
  assert.deepEqual(lireOptions(['--rapport', 'a.csv', '--branches', '0.5']), { rapport: 'a.csv', branches: '0.5' });
  assert.throws(() => lireOptions(['--seuil', '1']), /option inconnue/);
  assert.throws(() => lireOptions(['--lignes']), /sans valeur/);
});

test('le contrôle complet réussit quand chaque module atteint les seuils', (contexte) => {
  const racine = depotFactice(rapport(ligne('io.argos.audit', 'C', [1, 9], [1, 19])));
  contexte.after(() => rmSync(racine, { recursive: true, force: true }));
  const sortie = sortieMemoire();
  assert.equal(verifier([], racine, sortie), 0);
  assert.equal(sortie.messages.log.length, 1);
  assert.deepEqual(sortie.messages.error, []);
});

test('le contrôle complet échoue avec le code 1 et nomme le module sous le seuil', (contexte) => {
  const racine = depotFactice(rapport(
    ligne('io.argos.audit', 'C', [1, 9], [1, 19]),
    ligne('io.argos.vulnintel', 'Enricher', [5, 5], [5, 15]),
  ));
  contexte.after(() => rmSync(racine, { recursive: true, force: true }));
  const sortie = sortieMemoire();
  assert.equal(verifier([], racine, sortie), 1);
  assert.deepEqual(sortie.messages.error, [
    'module vulnintel : lignes 75.0 % sous le seuil de 85.0 %',
    'module vulnintel : branches 50.0 % sous le seuil de 70.0 %',
  ]);
  assert.equal(verifier(['--lignes', '0.7', '--branches', '0.5'], racine, sortieMemoire()), 0);
});

test('un rapport absent est une entrée invalide (code 2)', (contexte) => {
  const racine = depotFactice(rapport());
  contexte.after(() => rmSync(racine, { recursive: true, force: true }));
  const sortie = sortieMemoire();
  assert.equal(verifier(['--rapport', 'absent.csv'], racine, sortie), 2);
  assert.match(sortie.messages.error[0], /absent\.csv illisible/);
});
