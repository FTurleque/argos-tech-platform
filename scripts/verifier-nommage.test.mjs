// Tests du contrôle de nommage (scripts/verifier-nommage.mjs).
// Exécution : node --test scripts/*.test.mjs (aucune dépendance).
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { spawnSync } from 'node:child_process';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';
import { analyserFichier, analyserSource, estCourt, LISTE_BLANCHE } from './verifier-nommage.mjs';

const SCRIPT = join(dirname(fileURLToPath(import.meta.url)), 'verifier-nommage.mjs');
const noms = (source, langage) => analyserSource(source, langage).map(({ nom }) => nom);

test('estCourt : une ou deux lettres, hors id, mots-clés et paramètres de type', () => {
  for (const nom of ['e', 'ps', 'rs', 'db', 'x1', 'ID', 'Ok']) assert.ok(estCourt(nom), nom);
  for (const nom of ['id', 'T', 'K', 'T1', 'in', 'of', 'as', 'connexion', '_', 'io1']) assert.ok(!estCourt(nom), nom);
});

test('Java : variables locales, champs et constantes', () => {
  assert.deepEqual(noms('class A { void traiter() { int i = 0; String s; List<Map<String, Integer>> lm = null; } }', 'java'),
    ['i', 's', 'lm']);
  assert.deepEqual(noms('class A {\n  @Inject TestDb db;\n  private static final int NB = 3;\n}', 'java'), ['db', 'NB']);
  assert.deepEqual(noms('class A { void traiter() { var ok = true; byte[] gz = lire(); } }', 'java'), ['ok', 'gz']);
});

test('Java : paramètres de méthode, de constructeur et de record', () => {
  assert.deepEqual(noms('class A { A(Connection c) {} void traiter(String a, int... nb) {} }', 'java'), ['c', 'a', 'nb']);
  assert.deepEqual(noms('record Compte(int vp, String libelle) {}', 'java'), ['vp']);
  assert.deepEqual(noms('class A { void traiter(@PathParam("id") UUID id, final Instant at) {} }', 'java'), ['at']);
});

test('Java : lambdas, catch, boucles et motifs', () => {
  assert.deepEqual(noms('class A { void traiter() { liste.forEach(s -> traiter(s)); } }', 'java'), ['s']);
  assert.deepEqual(noms('class A { void traiter() { carte.forEach((k, v) -> {}); } }', 'java'), ['k', 'v']);
  assert.deepEqual(noms('class A { void traiter() { try { g(); } catch (IOException | SQLException e) {} } }', 'java'), ['e']);
  assert.deepEqual(noms('class A { void traiter() { for (int j = 0; j < 3; j++) {} for (Ligne li : lignes) {} } }', 'java'),
    ['j', 'li']);
  assert.deepEqual(noms('class A { boolean traiter(Object objet) { return objet instanceof Texte tx && tx.vide(); } }', 'java'),
    ['tx']);
});

test('Java : déclarateurs multiples', () => {
  const source = [
    'class A {',
    '  private int ab, cd;',
    '  void traiter(String nom, int valeur) {',
    '    int a = 1, b = somme(nom, valeur), total = 0;',
    '    int c, d;',
    '    for (int i = 0, j = 0; i < j; i++) {}',
    '  }',
    '}',
  ].join('\n');
  assert.deepEqual(noms(source, 'java'), ['ab', 'cd', 'a', 'b', 'c', 'd', 'i', 'j']);
});

test('Java : un appel à comparaisons n\'est pas un type générique', () => {
  assert.deepEqual(noms('class A { void traiter() { g(a < b, c > d); } }', 'java'), []);
  assert.deepEqual(noms('class A { List<String> ab; java.util.Map<String, Integer> cd = null; }', 'java'), ['ab', 'cd']);
});

test('Java : méthodes et types d\'une ou deux lettres', () => {
  const source = [
    'class Fabrique {',
    '  static Resume de(Opportunite opportunite) { return null; }',
    '  private static long ms(long debut) { return debut; }',
    '  public <T> List<T> en(T valeur) { return List.of(valeur); }',
    '  private record Lu(String titre) {}',
    '  interface Ab {}',
    '  enum Ok { OUI, NON }',
    '}',
  ].join('\n');
  assert.deepEqual(noms(source, 'java'), ['de', 'ms', 'en', 'Lu', 'Ab', 'Ok']);
});

test('Java : les étiquettes de switch, appels et retours ne sont pas des déclarations', () => {
  const source = [
    'class A {',
    '  static Resume depuis(Opportunite opportunite) { return ms(opportunite); }',
    '  int classer(Etat etat) { return switch (etat) { case OK -> 1; case KO, NA -> 2; default -> 3; }; }',
    '  void lever() { throw erreur; }',
    '}',
  ].join('\n');
  assert.deepEqual(noms(source, 'java'), []);
});

test('Java : commentaires, chaînes, caractères et blocs de texte ignorés', () => {
  const source = [
    'class A {',
    '  // int a = 1;',
    '  /* String b; */',
    '  /** @param c connexion */',
    '  String sql = "SELECT e.id, e.nom AS n FROM element e";',
    '  char guillemet = \'"\';',
    '  String bloc = """',
    '      SELECT s.titre FROM signal s WHERE s.id = ?',
    '      """;',
    '}',
  ].join('\n');
  assert.deepEqual(noms(source, 'java'), []);
});

test('Java : génériques et id admis, numéro de ligne exact', () => {
  assert.deepEqual(noms('class Boite<T, K2> { <R> R traiter(T valeur, UUID id) { return null; } }', 'java'), []);
  assert.deepEqual(analyserSource('class A {\n\n  void traiter() {\n    int n = 1;\n  }\n}', 'java'), [{ ligne: 4, nom: 'n' }]);
});

test('TS/JS : const, let, var et déstructuration', () => {
  assert.deepEqual(noms('const a = 1\nlet b, cd = 2\nvar ef = 3', 'ts'), ['a', 'b', 'cd', 'ef']);
  assert.deepEqual(noms('const { de, nom: nm, ...rs } = objet\nconst [x, , yy = 1] = paire', 'ts'), ['de', 'nm', 'rs', 'x', 'yy']);
  assert.deepEqual(noms('for (let i = 0, j = 1; i < j; i++) {}\nfor (const [k, v] of entrees) {}', 'ts'), ['i', 'j', 'k', 'v']);
});

test('TS/JS : paramètres de fonctions, méthodes, fonctions fléchées et catch', () => {
  assert.deepEqual(noms('function lire(fs, fichier = "x") {}', 'ts'), ['fs']);
  assert.deepEqual(noms('const f1 = async (a: string, { b, c: cc }: Options): Promise<void> => {}', 'ts'),
    ['f1', 'a', 'b', 'cc']);
  assert.deepEqual(noms('liste.map(x => x * 2).filter((el) => el)', 'ts'), ['x', 'el']);
  assert.deepEqual(noms('const objet = { on(canal, fn) { return canal } }', 'ts'), ['on', 'fn']);
  assert.deepEqual(noms('class Horloge {\n  constructor(private readonly ms: number) {}\n  private dt = 0\n}', 'ts'),
    ['ms', 'dt']);
  assert.deepEqual(noms('try { lire() } catch (e: unknown) {}', 'ts'), ['e']);
  assert.deepEqual(noms('type Planifier = (fn: () => void, delai: number) => unknown', 'ts'), ['fn']);
});

test('TS/JS : clés d\'objet, propriétés, appels, commentaires, chaînes, gabarits et regex ignorés', () => {
  const source = [
    'const historique = { de: "A", ok: true }',
    'interface Ligne { de: string; at?: number }',
    'if (reponse.ok) appeler(a, b)',
    '// const x = 1',
    '/* let y = 2 */',
    'const texte = "const z = 3" + \'(w) => w\'',
    'const gabarit = `let q = ${valeur} (r) => r`',
    'const motif = /(?:a|b)=>"\'/g',
  ].join('\n');
  assert.deepEqual(noms(source, 'ts'), []);
});

test('TS/JS : noms locaux des imports', () => {
  const source = [
    'import { h } from \'vue\'',
    'import { x as yz, longNom } from \'./a\'',
    'import h2 from \'b\'',
    'import * as fs from \'node:fs\'',
    'import type { Ab } from \'./types\'',
    'import Defaut, {',
    '  cd,',
    '  ef as eFichier,',
    '} from \'c\'',
    'import \'./style.css\'',
    'const module = await import(\'./dynamique\')',
    'export { gh } from \'./d\'',
  ].join('\n');
  assert.deepEqual(noms(source, 'ts'), ['h', 'yz', 'h2', 'fs', 'Ab', 'cd']);
  assert.deepEqual(noms('import { h as creerNoeud } from \'vue\'', 'ts'), []);
});

test('TS : champs de classe sans modificateur et signatures de méthode d\'interface', () => {
  const source = [
    'class Compteur {',
    '  ab = 1',
    '  cd: number',
    '  ef?: string',
    '  #gh = 2',
    '  valeur = 3',
    '  constructor() {}',
    '  incrementer(pas: number) { return pas }',
    '}',
    'interface Service {',
    '  traiter(ij: string, options: Options): void',
    '  de: string',
    '}',
  ].join('\n');
  assert.deepEqual(noms(source, 'ts'), ['ab', 'cd', 'ef', 'gh', 'ij']);
});

test('TS/JS : fonctions, méthodes et types d\'une ou deux lettres', () => {
  const source = [
    'function ab() {}',
    'class Lu {',
    '  ok() { return true }',
    '  static async de(valeur: string) { return valeur }',
    '}',
    'const objet = { il(jours) { return jours } }',
    'interface Ecouteur { on(evenement: string): void }',
    'interface Ab {}',
    'enum Ok { Oui, Non }',
    'type Cd<T> = T[]',
  ].join('\n');
  assert.deepEqual(noms(source, 'ts'), ['ab', 'Lu', 'ok', 'de', 'il', 'on', 'Ab', 'Ok', 'Cd']);
});

test('TS : génériques et id admis', () => {
  assert.deepEqual(noms('function premier<T, K>(liste: T[], id: K): T { return liste[0] }', 'ts'), []);
});

test('Vue : script, v-for, v-slot et fonctions fléchées des directives', () => {
  const source = [
    '<script setup lang="ts">',
    'const ab = 1',
    '</script>',
    '<template>',
    '  <li v-for="(p, ip) in projets" :key="ip">{{ p.nom }}</li>',
    '  <Liste #ligne="{ el }" @choisir="(x) => choisir(x)" />',
    '  <p class="a b">Le texte n\'est pas du code : (u) => u</p>',
    '</template>',
  ].join('\n');
  assert.deepEqual(analyserSource(source, 'vue'), [
    { ligne: 2, nom: 'ab' }, { ligne: 5, nom: 'p' }, { ligne: 5, nom: 'ip' }, { ligne: 6, nom: 'el' }, { ligne: 6, nom: 'x' },
  ]);
});

test('TS : préfixes de paramètre (décorateurs, modificateurs, reste) et annotation de type de retour', () => {
  const source = [
    'class Service {',
    '  constructor(@Inject(JETON) private readonly ab: Depot, @Optionnel public cd = 1) {}',
    '}',
    'function lire(\t...rs) {}',
    'function ecrire(ef): Promise<Map<string, number>> { return ef }',
    'const traiter = (gh): Resultat => gh',
    'appeler(ij): valeur = { }',
    'function exporter(kl): { nom: string } { return kl }',
  ].join('\n');
  assert.deepEqual(noms(source, 'ts'), ['ab', 'cd', 'rs', 'ef', 'gh', 'kl']);
});

test('TS/JS : imports sur plusieurs lignes, `type` modificateur ou nom, et import sans `from`', () => {
  const source = [
    'import type ab from \'./a\'',
    'import type from \'./b\'',
    'import cd',
    '',
    '  from \'./c\'',
    'import ef; const resultat = 1 from',
    'import\tgh, { ij as kl } from \'./d\'',
  ].join('\n');
  assert.deepEqual(analyserSource(source, 'ts'), [
    { ligne: 1, nom: 'ab' }, { ligne: 3, nom: 'cd' }, { ligne: 7, nom: 'gh' }, { ligne: 7, nom: 'kl' },
  ]);
});

test('Vue : formes de v-for (parenthèses, blancs, in et of)', () => {
  const source = [
    '<template>',
    '  <li v-for="ab in liste" />',
    '  <li v-for="  ( cd , ef )  of liste" />',
    '  <li v-for="gh\tin\tliste" />',
    '  <li v-for="(ij, index) in liste" />',
    '  <li v-for=" in liste" />',
    '  <li v-for="kl" />',
    '</template>',
  ].join('\n');
  assert.deepEqual(analyserSource(source, 'vue'), [
    { ligne: 2, nom: 'ab' }, { ligne: 3, nom: 'cd' }, { ligne: 3, nom: 'ef' }, { ligne: 4, nom: 'gh' }, { ligne: 5, nom: 'ij' },
  ]);
});

test('performance : une ligne pathologique longue est analysée en moins de 200 ms', () => {
  const blancs = ' '.repeat(50000);
  const cas = [
    [`import ab${blancs}x;`, 'ts'],
    [`import ab${'\t '.repeat(25000)}`, 'ts'],
    [`<template>\n  <li v-for="ab${blancs}x"></li>\n</template>`, 'vue'],
    [`<template>\n  <li v-for="(ab,${blancs}"></li>\n</template>`, 'vue'],
    [`function lire(${'@Decore '.repeat(10000)}ab) {}`, 'ts'],
  ];
  for (const [source, langage] of cas) {
    const debut = performance.now();
    analyserSource(source, langage);
    const duree = performance.now() - debut;
    assert.ok(duree < 200, `${source.slice(0, 30)}… analysé en ${Math.round(duree)} ms`);
  }
});

test('liste blanche : chaque entrée est commentée et ne vaut que pour son fichier', () => {
  for (const entree of LISTE_BLANCHE) {
    assert.ok(entree.fichier && entree.nom && entree.raison, JSON.stringify(entree));
  }
  const source = 'record EvenementHistorique(Instant le, String libelle) {}';
  assert.deepEqual(analyserFichier('argos-server/src/main/java/io/argos/regintel/FicheAnalyse.java', source), []);
  assert.deepEqual(analyserFichier('argos-server/src/main/java/io/argos/regintel/Autre.java', source).map(({ nom }) => nom), ['le']);
});

test('le dépôt respecte la règle de nommage', () => {
  const resultat = spawnSync(process.execPath, [SCRIPT], { encoding: 'utf8' });
  assert.equal(resultat.status, 0, resultat.stderr + resultat.stdout);
});
