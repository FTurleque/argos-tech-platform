#!/usr/bin/env node
// Contrôle de nommage ARGOS : aucun identifiant d'une ou deux lettres n'est déclaré dans le code
// (décisions du porteur du projet, 2026-11-20). Seuls sont admis `id` et les paramètres de type
// générique (`T`, `K`, `V`, `E`, `R`, `T1`…).
// Usage : node scripts/verifier-nommage.mjs [fichier…]
//   sans argument : fichiers .java, .ts, .vue, .js et .mjs suivis par Git (git ls-files), hors
//   node_modules, dist, target, e2e/resultats et fichiers générés.
// Déclarations contrôlées :
//   - Java : variables locales et champs (`Type nom =` ou `;`), paramètres de méthode, de constructeur
//     et de record, paramètres de lambda (`x ->`, `(a, b) ->`), variables de catch, variables de boucle
//     (for et for-each), variables de motif (`instanceof Type nom`) ;
//   - TS, JS et Vue : `const`, `let`, `var` (déstructuration comprise), paramètres de fonctions, de
//     méthodes et de fonctions fléchées, variables de catch, champs de classe à modificateur, variables
//     `v-for` et `v-slot` des gabarits Vue et fonctions fléchées de leurs directives.
// Ignorés : commentaires, chaînes, blocs de texte et gabarits (SQL compris), noms de méthodes, clés et
// propriétés d'objets (contrats JSON), `id`, paramètres de type générique, liste blanche ci-dessous.
// Code de sortie : 0 sans violation, 1 sinon (liste fichier:ligne:nom).

import { readFileSync } from 'node:fs';
import { spawnSync } from 'node:child_process';
import { dirname, relative, resolve, sep } from 'node:path';
import { fileURLToPath, pathToFileURL } from 'node:url';

const RACINE = resolve(dirname(fileURLToPath(import.meta.url)), '..');

// Liste blanche : éléments de contrat au nom court, visibles hors de leur fichier, que la règle ne
// renomme pas. À garder minimale : chaque entrée nomme un fichier, un identifiant et sa raison.
export const LISTE_BLANCHE = [
  { fichier: 'argos-server/src/main/java/io/argos/impact/ImpactQueries.java', nom: 'de',
    raison: 'composant du record public TransitionView, propriété JSON « de » de l\'historique' },
  { fichier: 'argos-server/src/main/java/io/argos/regintel/FicheAnalyse.java', nom: 'le',
    raison: 'composant du record public EvenementHistorique, propriété JSON « le »' },
  { fichier: 'argos-server/src/test/java/io/argos/chain/JeuReferenceCorrelationTest.java', nom: 'vp',
    raison: 'composant du record Compte (accesseur vp())' },
  { fichier: 'argos-server/src/test/java/io/argos/chain/JeuReferenceCorrelationTest.java', nom: 'fp',
    raison: 'composant du record Compte (accesseur fp())' },
  { fichier: 'argos-server/src/test/java/io/argos/chain/JeuReferenceCorrelationTest.java', nom: 'fn',
    raison: 'composant du record Compte (accesseur fn())' },
  { fichier: 'argos-server/src/test/java/io/argos/testsupport/RecordingSleeper.java', nom: 'at',
    raison: 'composant du record public Wait (accesseur at())' },
  { fichier: 'argos-server/src/test/java/io/argos/testsupport/StubServer.java', nom: 'at',
    raison: 'composant du record public Received (accesseur at())' },
];

const EXTENSIONS = /\.(java|ts|vue|js|mjs)$/;
const EXCLUS = /(^|\/)(node_modules|dist|target)\/|^e2e\/resultats\/|\.min\.js$/;
const GENERE = /@generated|auto-generated|généré automatiquement|fichier généré/i;
const IDENT = '[A-Za-z_$][\\w$]*';
const MOTS_CLES = new Set(['as', 'in', 'of', 'if', 'do', 'is']);
const MOTS_NON_TYPES = new Set([
  'return', 'throw', 'new', 'else', 'case', 'default', 'yield', 'assert', 'package', 'import',
  'instanceof', 'extends', 'implements', 'throws', 'super', 'this', 'break', 'continue', 'do', 'try',
  'finally', 'synchronized', 'permits', 'goto', 'when',
]);
const MOTS_AVANT_PARENTHESE = new Set([
  'if', 'for', 'while', 'switch', 'catch', 'with', 'return', 'typeof', 'await', 'yield', 'new',
  'delete', 'void', 'in', 'of', 'case', 'throw', 'else', 'do', 'instanceof',
]);
const MOTS_AVANT_REGEX = new Set([
  'return', 'typeof', 'case', 'in', 'of', 'new', 'delete', 'void', 'throw', 'yield', 'await', 'else', 'do',
]);

/** Vrai si `nom` est un identifiant d'une ou deux lettres interdit par la règle. */
export function estCourt(nom) {
  if (!/^[A-Za-z][A-Za-z0-9]?$/.test(nom)) return false;
  if (nom === 'id' || MOTS_CLES.has(nom)) return false;
  return !/^[A-Z][0-9]?$/.test(nom);
}

/**
 * Remplace commentaires, chaînes, blocs de texte, gabarits et expressions régulières par des blancs,
 * sans changer la longueur ni les sauts de ligne. Les expressions `${…}` des gabarits restent du code.
 */
export function masquer(source, langage) {
  const syntaxeJs = langage !== 'java';
  const sortie = source.split('');
  const blanchir = (debut, fin) => {
    for (let rang = debut; rang < fin && rang < sortie.length; rang++) {
      if (sortie[rang] !== '\n' && sortie[rang] !== '\r') sortie[rang] = ' ';
    }
  };
  const pileGabarits = [];
  let accolades = 0;
  let dernierCaractere = '';
  let dernierMot = '';
  let position = 0;

  // Parcourt un gabarit depuis `debut` ; rend la position après le gabarit ou après `${`.
  const parcourirGabarit = (debut) => {
    let curseur = debut;
    while (curseur < source.length) {
      const caractere = source[curseur];
      if (caractere === '\\') { curseur += 2; continue; }
      if (caractere === '`') { blanchir(debut, curseur + 1); return curseur + 1; }
      if (caractere === '$' && source[curseur + 1] === '{') {
        blanchir(debut, curseur + 2);
        pileGabarits.push(accolades);
        accolades++;
        return curseur + 2;
      }
      curseur++;
    }
    blanchir(debut, curseur);
    return curseur;
  };

  const regexPossible = () => dernierCaractere === '' || '(,=:[!&|?{};+-*%<>~^'.includes(dernierCaractere)
    || (/[\w$]/.test(dernierCaractere) && MOTS_AVANT_REGEX.has(dernierMot));

  while (position < source.length) {
    const caractere = source[position];
    const suivant = source[position + 1];
    if (caractere === '/' && suivant === '/') {
      const fin = source.indexOf('\n', position);
      const borne = fin < 0 ? source.length : fin;
      blanchir(position, borne);
      position = borne;
      continue;
    }
    if (caractere === '/' && suivant === '*') {
      const fin = source.indexOf('*/', position + 2);
      const borne = fin < 0 ? source.length : fin + 2;
      blanchir(position, borne);
      position = borne;
      continue;
    }
    if (!syntaxeJs && source.startsWith('"""', position)) {
      let curseur = position + 3;
      while (curseur < source.length && !source.startsWith('"""', curseur)) curseur += source[curseur] === '\\' ? 2 : 1;
      blanchir(position, curseur + 3);
      position = curseur + 3;
      dernierCaractere = '"';
      continue;
    }
    if (caractere === '"' || caractere === '\'') {
      let curseur = position + 1;
      while (curseur < source.length && source[curseur] !== caractere && source[curseur] !== '\n') {
        curseur += source[curseur] === '\\' ? 2 : 1;
      }
      blanchir(position, curseur + 1);
      position = curseur + 1;
      dernierCaractere = '"';
      continue;
    }
    if (syntaxeJs && caractere === '`') {
      position = parcourirGabarit(position + 1);
      blanchir(position - 1, position);
      dernierCaractere = '"';
      continue;
    }
    if (syntaxeJs && caractere === '}' && pileGabarits.length > 0 && pileGabarits.at(-1) === accolades - 1) {
      pileGabarits.pop();
      accolades--;
      const debut = position;
      position = parcourirGabarit(position + 1);
      blanchir(debut, debut + 1);
      dernierCaractere = '"';
      continue;
    }
    if (syntaxeJs && caractere === '/' && regexPossible()) {
      let curseur = position + 1;
      let classe = false;
      while (curseur < source.length && source[curseur] !== '\n') {
        const caractereLu = source[curseur];
        if (caractereLu === '\\') { curseur += 2; continue; }
        if (caractereLu === '[') classe = true;
        else if (caractereLu === ']') classe = false;
        else if (caractereLu === '/' && !classe) break;
        curseur++;
      }
      blanchir(position, curseur + 1);
      position = curseur + 1;
      while (/[a-z]/.test(source[position] ?? '')) position++;
      dernierCaractere = '"';
      continue;
    }
    if (caractere === '{') accolades++;
    else if (caractere === '}') accolades--;
    if (/[\w$]/.test(caractere)) {
      let fin = position;
      while (fin < source.length && /[\w$]/.test(source[fin])) fin++;
      dernierMot = source.slice(position, fin);
      dernierCaractere = source[fin - 1];
      position = fin;
      continue;
    }
    if (!/\s/.test(caractere)) dernierCaractere = caractere;
    position++;
  }
  return sortie.join('');
}

// Position de la parenthèse, accolade ou du crochet fermant qui équilibre `texte[debut]`.
function fermante(texte, debut) {
  let profondeur = 0;
  for (let rang = debut; rang < texte.length; rang++) {
    const caractere = texte[rang];
    if ('([{'.includes(caractere)) profondeur++;
    else if (')]}'.includes(caractere) && --profondeur === 0) return rang;
  }
  return -1;
}

// Découpe sur les virgules de premier niveau ; rend les morceaux avec leur décalage.
function decouper(texte, decalage) {
  const morceaux = [];
  let profondeur = 0;
  let debut = 0;
  for (let rang = 0; rang <= texte.length; rang++) {
    const caractere = texte[rang];
    if (rang === texte.length || (caractere === ',' && profondeur === 0)) {
      morceaux.push({ texte: texte.slice(debut, rang), position: decalage + debut });
      debut = rang + 1;
    } else if ('([{<'.includes(caractere)) profondeur++;
    else if (')]}>'.includes(caractere) && !(caractere === '>' && texte[rang - 1] === '=')) profondeur--;
  }
  return morceaux;
}

// Noms liés par un motif de déstructuration `{…}` ou `[…]` commençant à `texte[debut]`.
function nomsDestructures(texte, debut, ajouter) {
  const fin = fermante(texte, debut);
  if (fin < 0) return;
  const objet = texte[debut] === '{';
  for (const morceau of decouper(texte.slice(debut + 1, fin), debut + 1)) {
    let element = morceau.texte;
    let position = morceau.position;
    if (objet) {
      const deuxPoints = deuxPointsPremierNiveau(element);
      if (deuxPoints >= 0) {
        position += deuxPoints + 1;
        element = element.slice(deuxPoints + 1);
      }
    }
    lier(texte, element, position, ajouter);
  }
}

function deuxPointsPremierNiveau(texte) {
  let profondeur = 0;
  for (let rang = 0; rang < texte.length; rang++) {
    if ('([{'.includes(texte[rang])) profondeur++;
    else if (')]}'.includes(texte[rang])) profondeur--;
    else if (texte[rang] === ':' && profondeur === 0) return rang;
  }
  return -1;
}

// Lie l'élément d'une liste de paramètres ou d'un motif (`nom`, `...nom`, `nom = défaut`, `{…}`).
function lier(texte, element, position, ajouter) {
  const prefixe = /^\s*(?:@\w+(?:\([^)]*\))?\s*)*(?:(?:public|private|protected|readonly|override)\s+)*(?:\.\.\.)?\s*/
    .exec(element)[0];
  const reste = element.slice(prefixe.length);
  const debut = position + prefixe.length;
  if (reste.startsWith('{') || reste.startsWith('[')) {
    nomsDestructures(texte, debut, ajouter);
    return;
  }
  const nom = new RegExp(`^${IDENT}`).exec(reste);
  if (nom && nom[0] !== 'this') ajouter(nom[0], debut);
}

function declarationsJs(code, ajouter) {
  let trouve;
  const declaration = new RegExp(`\\b(?:const|let|var)\\s+(?:(${IDENT})|([{[]))`, 'g');
  while ((trouve = declaration.exec(code))) {
    let suite;
    if (trouve[1]) {
      ajouter(trouve[1], trouve.index + trouve[0].length - trouve[1].length);
      suite = trouve.index + trouve[0].length;
    } else {
      const debut = trouve.index + trouve[0].length - 1;
      nomsDestructures(code, debut, ajouter);
      suite = fermante(code, debut) + 1;
      if (suite <= 0) continue;
    }
    // Déclarateurs suivants : `let a = 1, b = 2` ou `for (let i = 0, j = 1; …)`.
    let profondeur = 0;
    for (let rang = suite; rang < code.length; rang++) {
      const caractere = code[rang];
      if ('([{'.includes(caractere)) profondeur++;
      else if (')]}'.includes(caractere) && --profondeur < 0) break;
      else if ((caractere === ';' || caractere === '\n') && profondeur === 0) break;
      else if (caractere === ',' && profondeur === 0) {
        const nom = new RegExp(`^\\s*(${IDENT})\\s*(?=[=,;:\\n)]|$)`).exec(code.slice(rang + 1));
        if (nom) ajouter(nom[1], rang + 1 + nom[0].indexOf(nom[1]));
      }
    }
  }
  const capture = new RegExp(`\\bcatch\\s*\\(\\s*(${IDENT})`, 'g');
  while ((trouve = capture.exec(code))) ajouter(trouve[1], trouve.index + trouve[0].length - trouve[1].length);
  const fleche = new RegExp(`(?<![\\w$.])(${IDENT})\\s*=>`, 'g');
  while ((trouve = fleche.exec(code))) ajouter(trouve[1], trouve.index);
  const champ = new RegExp(`\\b(?:private|protected|public|readonly|static)\\s+(?:readonly\\s+)?#?(${IDENT})\\s*[?!]?\\s*[:=;]`, 'g');
  while ((trouve = champ.exec(code))) ajouter(trouve[1], trouve.index + trouve[0].search(new RegExp(`${trouve[1]}\\s*[?!]?\\s*[:=;]$`)));
  for (let rang = code.indexOf('('); rang >= 0; rang = code.indexOf('(', rang + 1)) {
    const fin = fermante(code, rang);
    if (fin < 0) continue;
    const apres = code.slice(fin + 1, fin + 400);
    const avant = code.slice(Math.max(0, rang - 200), rang);
    let parametres = /^\s*(?::\s*[^;{}=]*?)?\s*=>/.test(apres);
    if (!parametres && /^\s*(?::\s*[^;{}=]*?)?\s*\{/.test(apres)) {
      const nomFonction = new RegExp(`(?:\\bfunction\\s*\\*?\\s*(?:${IDENT})?|(${IDENT}))\\s*(?:<[^<>]*>)?\\s*$`).exec(avant);
      parametres = Boolean(nomFonction) && !MOTS_AVANT_PARENTHESE.has(nomFonction[1] ?? '');
    }
    if (!parametres) continue;
    for (const morceau of decouper(code.slice(rang + 1, fin), rang + 1)) lier(code, morceau.texte, morceau.position, ajouter);
  }
}

function declarationsJava(code, ajouter) {
  let trouve;
  const type = `${IDENT}(?:\\s*\\.\\s*${IDENT})*(?:\\s*<[^;(){}=&|!+*/%]*?>)?(?:\\s*\\[\\s*\\])*(?:\\s*\\.\\.\\.)?`;
  const declaration = new RegExp(`(?<![\\w$.@])(${type})\\s+(${IDENT})(?=\\s*(?:[=;,:)&|?]|->))`, 'g');
  while ((trouve = declaration.exec(code))) {
    const premierMot = /^[\w$]+/.exec(trouve[1])[0];
    if (MOTS_NON_TYPES.has(premierMot)) {
      declaration.lastIndex = trouve.index + premierMot.length;
      continue;
    }
    ajouter(trouve[2], trouve.index + trouve[0].length - trouve[2].length);
  }
  const fleche = new RegExp(`(?<![\\w$.])(${IDENT})\\s*->`, 'g');
  while ((trouve = fleche.exec(code))) {
    const instruction = code.slice(0, trouve.index).split(/[;{}]/).at(-1).trim();
    if (/^(case|default)\b/.test(instruction)) continue;
    ajouter(trouve[1], trouve.index);
  }
  for (let rang = code.indexOf('('); rang >= 0; rang = code.indexOf('(', rang + 1)) {
    const fin = fermante(code, rang);
    if (fin < 0 || !/^\s*->/.test(code.slice(fin + 1, fin + 10))) continue;
    for (const morceau of decouper(code.slice(rang + 1, fin), rang + 1)) {
      const nom = new RegExp(`(${IDENT})\\s*$`).exec(morceau.texte);
      if (nom) ajouter(nom[1], morceau.position + nom.index);
    }
  }
}

// Gabarit Vue : variables de v-for, v-slot et fonctions fléchées des directives.
function declarationsGabarit(source, debutGabarit, finGabarit, ajouter) {
  const gabarit = source.slice(debutGabarit, finGabarit);
  const attribut = /\s(v-[\w:.\-[\]]+|[:@#][\w:.\-[\]]*)\s*=\s*"([^"]*)"/g;
  let trouve;
  while ((trouve = attribut.exec(gabarit))) {
    const directive = trouve[1];
    const valeur = trouve[2];
    const decalage = debutGabarit + trouve.index + trouve[0].length - valeur.length - 1;
    const code = masquer(valeur, 'ts');
    const decaler = (nom, position) => ajouter(nom, decalage + position);
    if (directive === 'v-for') {
      const boucle = /^\s*(\(?)([^]*?)\)?\s+(?:in|of)\s/.exec(code);
      if (boucle) {
        const liste = boucle[2];
        const debutListe = boucle[0].indexOf(liste, boucle[1].length);
        for (const morceau of decouper(liste, debutListe)) {
          lier(code, morceau.texte, morceau.position, decaler);
        }
      }
    } else if (directive.startsWith('v-slot') || directive.startsWith('#')) {
      for (const morceau of decouper(code, 0)) lier(code, morceau.texte, morceau.position, decaler);
    } else {
      declarationsJs(code, decaler);
    }
  }
}

/** Déclarations d'identifiants courts d'une source : [{ ligne, nom }], triées. */
export function analyserSource(source, langage) {
  const trouves = new Map();
  const ajouter = (nom, position) => {
    if (!estCourt(nom)) return;
    const ligne = source.slice(0, position).split('\n').length;
    trouves.set(`${ligne}:${nom}`, { ligne, nom });
  };
  if (langage === 'java') declarationsJava(masquer(source, 'java'), ajouter);
  else if (langage === 'vue') {
    const scripts = source.split('').map((caractere) => (caractere === '\n' ? '\n' : ' '));
    const bloc = /<script\b[^>]*>([^]*?)<\/script>/g;
    let trouve;
    while ((trouve = bloc.exec(source))) {
      const debut = trouve.index + trouve[0].indexOf('>') + 1;
      for (let rang = 0; rang < trouve[1].length; rang++) scripts[debut + rang] = trouve[1][rang];
    }
    declarationsJs(masquer(scripts.join(''), 'ts'), ajouter);
    const gabarit = /<template\b[^>]*>([^]*)<\/template>/.exec(source);
    if (gabarit) {
      const debut = gabarit.index + gabarit[0].indexOf('>') + 1;
      declarationsGabarit(source, debut, debut + gabarit[1].length, ajouter);
    }
  } else declarationsJs(masquer(source, 'ts'), ajouter);
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

function fichiersSuivis() {
  const resultat = spawnSync('git', ['ls-files', '-z'], { cwd: RACINE, encoding: 'utf8', maxBuffer: 64 * 1024 * 1024 });
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
