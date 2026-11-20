#!/usr/bin/env node
// Contrôle de nommage ARGOS : aucun identifiant d'une ou deux lettres n'est déclaré dans le code
// (décisions du porteur du projet, 2026-11-20). Seuls sont admis `id` et les paramètres de type
// générique (`T`, `K`, `V`, `E`, `R`, `T1`…).
// Usage : node scripts/verifier-nommage.mjs [fichier…]
//   sans argument : fichiers .java, .ts, .vue, .js et .mjs suivis par Git (git ls-files), hors
//   node_modules, dist, target, e2e/resultats et fichiers générés.
// Déclarations contrôlées :
//   - Java : types (class, record, interface, enum, @interface), méthodes, variables locales et champs
//     (`Type nom =` ou `;`, déclarateurs multiples `int a = 1, b;` compris), paramètres de méthode, de
//     constructeur et de record, paramètres de lambda (`x ->`, `(a, b) ->`), variables de catch,
//     variables de boucle (for et for-each), variables de motif (`instanceof Type nom`) ;
//   - TS, JS et Vue : types (class, interface, enum, `type Nom =`), fonctions et méthodes (classes,
//     interfaces, objets littéraux), noms locaux des imports (`import nom`, `{ a as nom }`,
//     `* as nom`), `const`, `let`, `var` (déstructuration comprise), paramètres de fonctions, de
//     méthodes, de signatures de méthode d'interface et de fonctions fléchées, variables de catch,
//     champs de classe (avec ou sans modificateur), variables `v-for` et `v-slot` des gabarits Vue et
//     fonctions fléchées de leurs directives.
// Ignorés : commentaires, chaînes, blocs de texte et gabarits (SQL compris), appels, clés et propriétés
// d'objets et d'interfaces (contrats JSON), `id`, paramètres de type générique, liste blanche ci-dessous.
// Un élément public au nom court qui relève d'un contrat (propriété JSON, accesseur de record exposé
// par l'API) n'est pas renommé : il va dans la liste blanche, avec sa raison.
// Code de sortie : 0 sans violation, 1 sinon (liste fichier:ligne:nom).
// Analyseurs : scripts/nommage/ (masquage des littéraux, outils communs, JavaScript, Java, Vue).

import { existsSync, readFileSync } from 'node:fs';
import { spawnSync } from 'node:child_process';
import { dirname, relative, resolve, sep } from 'node:path';
import { fileURLToPath, pathToFileURL } from 'node:url';
import { declarationsJava } from './nommage/java.mjs';
import { declarationsJs } from './nommage/javascript.mjs';
import { masquer } from './nommage/masquage.mjs';
import { estCourt } from './nommage/outils.mjs';
import { declarationsVue } from './nommage/vue.mjs';

export { estCourt };

const RACINE = resolve(dirname(fileURLToPath(import.meta.url)), '..');

// Liste blanche : éléments de contrat au nom court, visibles hors de leur fichier, que la règle ne
// renomme pas. À garder minimale : chaque entrée nomme un fichier, un identifiant et sa raison.
export const LISTE_BLANCHE = [
  { fichier: 'argos-server/src/main/java/io/argos/impact/ImpactQueries.java', nom: 'de',
    raison: 'composant du record public TransitionView, propriété JSON « de » de l\'historique' },
  { fichier: 'argos-server/src/main/java/io/argos/regintel/FicheAnalyse.java', nom: 'le',
    raison: 'composant du record public EvenementHistorique, propriété JSON « le »' },
  { fichier: 'argos-desktop/test/electron-factice.js', nom: 'on',
    raison: 'doublure de l\'API Electron : méthode on() d\'ipcRenderer et de Notification' },
  { fichier: 'argos-desktop/test/poste.test.js', nom: 'on',
    raison: 'doublure de l\'API Electron : méthode on() de Notification' },
  { fichier: 'e2e/support/electron.ts', nom: 'on',
    raison: 'signature de l\'API Electron : méthode on() de BrowserWindow' },
];

const EXTENSIONS = /\.(java|ts|vue|js|mjs)$/;
const EXCLUS = /(^|\/)(node_modules|dist|target)\/|^e2e\/resultats\/|\.min\.js$/;
const GENERE = /@generated|auto-generated|généré automatiquement|fichier généré/i;

/** Déclarations d'identifiants courts d'une source : [{ ligne, nom }], triées. */
export function analyserSource(source, langage) {
  const trouves = new Map();
  const ajouter = (nom, position) => {
    if (!estCourt(nom)) return;
    const ligne = source.slice(0, position).split('\n').length;
    trouves.set(`${ligne}:${nom}`, { ligne, nom });
  };
  if (langage === 'java') declarationsJava(masquer(source, 'java'), ajouter);
  else if (langage === 'vue') declarationsVue(source, ajouter);
  else declarationsJs(masquer(source, 'ts'), ajouter);
  return [...trouves.values()].sort((premier, second) => premier.ligne - second.ligne);
}

function langageDe(fichier) {
  if (fichier.endsWith('.java')) return 'java';
  if (fichier.endsWith('.vue')) return 'vue';
  return 'ts';
}

/** Violations d'un fichier (chemin relatif à la racine, séparateur `/`), liste blanche appliquée. */
export function analyserFichier(fichier, source) {
  return analyserSource(source, langageDe(fichier))
    .filter(({ nom }) => !LISTE_BLANCHE.some((entree) => entree.fichier === fichier && entree.nom === nom))
    .map((violation) => ({ fichier, ...violation }));
}

// Git appelé par son chemin absolu, pris parmi des emplacements fixes : le contrôle ne dépend pas du PATH.
// ARGOS_GIT désigne un autre emplacement.
const EMPLACEMENTS_GIT = ['C:/Program Files/Git/cmd/git.exe', '/usr/bin/git', '/usr/local/bin/git', '/opt/homebrew/bin/git'];

function cheminGit() {
  const choisi = process.env.ARGOS_GIT ?? EMPLACEMENTS_GIT.find((chemin) => existsSync(chemin));
  if (!choisi || !existsSync(choisi)) {
    throw new Error('Git introuvable : installez-le ou indiquez son chemin dans ARGOS_GIT.');
  }
  return choisi;
}

function fichiersSuivis() {
  const resultat = spawnSync(cheminGit(), ['ls-files', '-z'], { cwd: RACINE, encoding: 'utf8', maxBuffer: 64 * 1024 * 1024 });
  if (resultat.status !== 0) throw new Error(`git ls-files a échoué : ${resultat.stderr}`);
  return resultat.stdout.split('\0').filter((fichier) => EXTENSIONS.test(fichier) && !EXCLUS.test(fichier));
}

function principal(arguments_) {
  const fichiers = arguments_.length > 0
    ? arguments_.map((fichier) => relative(RACINE, resolve(fichier)).split(sep).join('/'))
    : fichiersSuivis();
  const violations = [];
  let analyses = 0;
  for (const fichier of fichiers) {
    const source = readFileSync(resolve(RACINE, fichier), 'utf8');
    if (GENERE.test(source.split('\n', 5).join('\n'))) continue;
    analyses++;
    violations.push(...analyserFichier(fichier, source));
  }
  // Sur tout le dépôt, une entrée de la liste blanche qui ne sert plus est une erreur : la liste reste minimale.
  const inutiles = arguments_.length > 0 ? [] : LISTE_BLANCHE.filter((entree) => !fichiers.includes(entree.fichier)
    || !analyserSource(readFileSync(resolve(RACINE, entree.fichier), 'utf8'), langageDe(entree.fichier))
      .some(({ nom }) => nom === entree.nom));
  for (const { fichier, nom } of inutiles) {
    console.error(`ERREUR liste blanche : ${fichier}:${nom} ne correspond plus à aucune déclaration`);
  }
  for (const { fichier, ligne, nom } of violations) {
    console.error(`ERREUR ${fichier}:${ligne}:${nom} — identifiant de ${nom.length} lettre${nom.length > 1 ? 's' : ''} déclaré`);
  }
  console.log(`${analyses} fichiers analysés, ${violations.length} violation(s), ${LISTE_BLANCHE.length} exception(s) en liste blanche.`);
  return violations.length + inutiles.length === 0 ? 0 : 1;
}

if (process.argv[1] && import.meta.url === pathToFileURL(resolve(process.argv[1])).href) {
  process.exit(principal(process.argv.slice(2)));
}
