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

import { existsSync, readFileSync } from 'node:fs';
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
const IDENT = String.raw`[A-Za-z_$][\w$]*`;
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
  if (!/^[A-Za-z][A-Za-z\d]?$/.test(nom)) return false;
  if (nom === 'id' || MOTS_CLES.has(nom)) return false;
  return !/^[A-Z]\d?$/.test(nom);
}

// Position du premier caractère qui n'est pas un blanc (`\s`) à partir de `debut`.
function finBlancs(texte, debut) {
  let rang = debut;
  while (rang < texte.length && /\s/.test(texte[rang])) rang++;
  return rang;
}

// Position qui suit le mot (`\w+`) commençant à `debut`.
function finMot(texte, debut) {
  let rang = debut;
  while (rang < texte.length && /\w/.test(texte[rang])) rang++;
  return rang;
}

// --- Masquage des commentaires et des littéraux -------------------------------------------------------

// État du masquage : source, sortie modifiable et contexte lexical (gabarits ouverts, dernier jeton lu).
function creerEtatMasquage(source, langage) {
  return {
    source, syntaxeJs: langage !== 'java', sortie: source.split(''), pileGabarits: [],
    accolades: 0, dernierCaractere: '', dernierMot: '', position: 0,
  };
}

function blanchir(etat, debut, fin) {
  const { sortie } = etat;
  for (let rang = debut; rang < fin && rang < sortie.length; rang++) {
    if (sortie[rang] !== '\n' && sortie[rang] !== '\r') sortie[rang] = ' ';
  }
}

// Parcourt un gabarit depuis `debut` ; rend la position après le gabarit ou après `${`.
function parcourirGabarit(etat, debut) {
  const { source } = etat;
  let curseur = debut;
  while (curseur < source.length) {
    const caractere = source[curseur];
    if (caractere === '\\') { curseur += 2; continue; }
    if (caractere === '`') { blanchir(etat, debut, curseur + 1); return curseur + 1; }
    if (caractere === '$' && source[curseur + 1] === '{') {
      blanchir(etat, debut, curseur + 2);
      etat.pileGabarits.push(etat.accolades);
      etat.accolades++;
      return curseur + 2;
    }
    curseur++;
  }
  blanchir(etat, debut, curseur);
  return curseur;
}

// Blanchit un littéral terminé à `fin` ; le code qui suit le voit comme une valeur.
function terminerLitteral(etat, debut, fin) {
  blanchir(etat, debut, fin);
  etat.position = fin;
  etat.dernierCaractere = '"';
}

function masquerCommentaire(etat) {
  const { source, position } = etat;
  if (source[position] !== '/') return false;
  let borne;
  if (source[position + 1] === '/') {
    const fin = source.indexOf('\n', position);
    borne = fin < 0 ? source.length : fin;
  } else if (source[position + 1] === '*') {
    const fin = source.indexOf('*/', position + 2);
    borne = fin < 0 ? source.length : fin + 2;
  } else {
    return false;
  }
  blanchir(etat, position, borne);
  etat.position = borne;
  return true;
}

// Bloc de texte Java `"""…"""`.
function masquerBlocTexte(etat) {
  const { source, position } = etat;
  if (etat.syntaxeJs || !source.startsWith('"""', position)) return false;
  let curseur = position + 3;
  while (curseur < source.length && !source.startsWith('"""', curseur)) curseur += source[curseur] === '\\' ? 2 : 1;
  terminerLitteral(etat, position, curseur + 3);
  return true;
}

function masquerChaine(etat) {
  const { source, position } = etat;
  const delimiteur = source[position];
  if (delimiteur !== '"' && delimiteur !== '\'') return false;
  let curseur = position + 1;
  while (curseur < source.length && source[curseur] !== delimiteur && source[curseur] !== '\n') {
    curseur += source[curseur] === '\\' ? 2 : 1;
  }
  terminerLitteral(etat, position, curseur + 1);
  return true;
}

function masquerGabarit(etat) {
  if (!etat.syntaxeJs || etat.source[etat.position] !== '`') return false;
  etat.position = parcourirGabarit(etat, etat.position + 1);
  blanchir(etat, etat.position - 1, etat.position);
  etat.dernierCaractere = '"';
  return true;
}

// Accolade qui ferme une expression `${…}` : le gabarit reprend après elle.
function reprendreGabarit(etat) {
  const { pileGabarits } = etat;
  if (!etat.syntaxeJs || etat.source[etat.position] !== '}' || pileGabarits.length === 0
    || pileGabarits.at(-1) !== etat.accolades - 1) return false;
  pileGabarits.pop();
  etat.accolades--;
  const debut = etat.position;
  etat.position = parcourirGabarit(etat, debut + 1);
  blanchir(etat, debut, debut + 1);
  etat.dernierCaractere = '"';
  return true;
}

// Un `/` ouvre une expression régulière après un opérateur, un début d'instruction ou certains mots-clés.
function regexPossible({ dernierCaractere, dernierMot }) {
  return dernierCaractere === '' || '(,=:[!&|?{};+-*%<>~^'.includes(dernierCaractere)
    || (/[\w$]/.test(dernierCaractere) && MOTS_AVANT_REGEX.has(dernierMot));
}

// Position du `/` qui ferme l'expression régulière ouverte avant `debut` (ou de la fin de ligne).
function finRegex(source, debut) {
  let curseur = debut;
  let classe = false;
  while (curseur < source.length && source[curseur] !== '\n') {
    const caractereLu = source[curseur];
    if (caractereLu === '\\') { curseur += 2; continue; }
    if (caractereLu === '[') classe = true;
    else if (caractereLu === ']') classe = false;
    else if (caractereLu === '/' && !classe) break;
    curseur++;
  }
  return curseur;
}

function masquerRegex(etat) {
  const { source, position } = etat;
  if (!etat.syntaxeJs || source[position] !== '/' || !regexPossible(etat)) return false;
  terminerLitteral(etat, position, finRegex(source, position + 1) + 1);
  while (/[a-z]/.test(source[etat.position] ?? '')) etat.position++;
  return true;
}

// Code ordinaire : compte les accolades et retient le dernier mot et le dernier caractère significatif.
function avancerCode(etat) {
  const { source, position } = etat;
  const caractere = source[position];
  if (caractere === '{') etat.accolades++;
  else if (caractere === '}') etat.accolades--;
  if (/[\w$]/.test(caractere)) {
    let fin = position;
    while (fin < source.length && /[\w$]/.test(source[fin])) fin++;
    etat.dernierMot = source.slice(position, fin);
    etat.dernierCaractere = source[fin - 1];
    etat.position = fin;
    return;
  }
  if (!/\s/.test(caractere)) etat.dernierCaractere = caractere;
  etat.position++;
}

// Formes essayées dans cet ordre à chaque position ; à défaut, le caractère est du code.
const LITTERAUX = [masquerCommentaire, masquerBlocTexte, masquerChaine, masquerGabarit, reprendreGabarit, masquerRegex];

/**
 * Remplace commentaires, chaînes, blocs de texte, gabarits et expressions régulières par des blancs,
 * sans changer la longueur ni les sauts de ligne. Les expressions `${…}` des gabarits restent du code.
 */
export function masquer(source, langage) {
  const etat = creerEtatMasquage(source, langage);
  while (etat.position < source.length) {
    if (!LITTERAUX.some((masquerLitteral) => masquerLitteral(etat))) avancerCode(etat);
  }
  return etat.sortie.join('');
}

// --- Outils communs ------------------------------------------------------------------------------------

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

// Lie chaque élément de la liste `liste` (paramètres, variables), qui commence à `debut` dans `texte`.
function lierListe(texte, liste, debut, ajouter) {
  for (const morceau of decouper(liste, debut)) lier(texte, morceau.texte, morceau.position, ajouter);
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

const MODIFICATEURS_PARAMETRE = ['public', 'private', 'protected', 'readonly', 'override'];

// Fin d'un décorateur `@Nom` ou `@Nom(…)` suivi de blancs, ou `debut` s'il n'y en a pas.
function finDecorateur(texte, debut) {
  if (texte[debut] !== '@') return debut;
  let curseur = finMot(texte, debut + 1);
  if (curseur === debut + 1) return debut;
  if (texte[curseur] === '(') {
    const fermeture = texte.indexOf(')', curseur);
    if (fermeture >= 0) curseur = fermeture + 1;
  }
  return finBlancs(texte, curseur);
}

// Fin d'un modificateur de paramètre suivi d'au moins un blanc, ou `debut` s'il n'y en a pas.
function finModificateur(texte, debut) {
  const mot = MODIFICATEURS_PARAMETRE.find((modificateur) => texte.startsWith(modificateur, debut));
  if (!mot) return debut;
  const fin = finBlancs(texte, debut + mot.length);
  return fin > debut + mot.length ? fin : debut;
}

// Longueur du préfixe d'un paramètre : blancs, décorateurs, modificateurs, `...` et blancs. Équivaut à
// `^\s*(?:@\w+(?:\([^)]*\))?\s*)*(?:(?:public|private|protected|readonly|override)\s+)*(?:\.\.\.)?\s*`.
function longueurPrefixe(element) {
  let curseur = finBlancs(element, 0);
  for (let suite = finDecorateur(element, curseur); suite > curseur; suite = finDecorateur(element, curseur)) curseur = suite;
  for (let suite = finModificateur(element, curseur); suite > curseur; suite = finModificateur(element, curseur)) curseur = suite;
  if (element.startsWith('...', curseur)) curseur += 3;
  return finBlancs(element, curseur);
}

const NOM_EN_TETE = new RegExp(`^${IDENT}`);

// Lie l'élément d'une liste de paramètres ou d'un motif (`nom`, `...nom`, `nom = défaut`, `{…}`).
function lier(texte, element, position, ajouter) {
  const prefixe = longueurPrefixe(element);
  const reste = element.slice(prefixe);
  const debut = position + prefixe;
  if (reste.startsWith('{') || reste.startsWith('[')) {
    nomsDestructures(texte, debut, ajouter);
    return;
  }
  const nom = NOM_EN_TETE.exec(reste);
  if (nom && nom[0] !== 'this') ajouter(nom[0], debut);
}

// Position qui suit l'annotation de type facultative (`: Type`) placée après une liste de paramètres, ou -1
// si l'annotation ne se termine pas : premier `;`, `{`, `}` ou `=` après les deux-points.
function finAnnotation(texte) {
  const debut = finBlancs(texte, 0);
  if (texte[debut] !== ':') return debut;
  for (let rang = debut + 1; rang < texte.length; rang++) {
    if (';{}='.includes(texte[rang])) return rang;
  }
  return -1;
}

// `(…)` suivi de `=>` (fonction fléchée), annotation de type comprise : `^\s*(?::\s*[^;{}=]*?)?\s*=>`.
function ouvreFleche(apres) {
  const rang = finAnnotation(apres);
  return rang >= 0 && apres.startsWith('=>', rang);
}

// `(…)` suivi d'un corps `{`, annotation de type comprise : `^\s*(?::\s*[^;{}=]*?)?\s*\{`.
function ouvreCorps(apres) {
  const rang = finAnnotation(apres);
  return rang >= 0 && apres[rang] === '{';
}

// --- TS, JS et Vue ---------------------------------------------------------------------------------------

function declarationsJs(code, ajouter) {
  variablesJs(code, ajouter);
  capturesEtFlechesJs(code, ajouter);
  champsAModificateurJs(code, ajouter);
  parametresJs(code, ajouter);
  importsJs(code, ajouter);
  typesJs(code, ajouter);
  membresDeCorps(code, ajouter);
}

// `const`, `let` et `var`, déstructuration et déclarateurs suivants compris.
function variablesJs(code, ajouter) {
  const declaration = new RegExp(String.raw`\b(?:const|let|var)\s+(?:(${IDENT})|([{[]))`, 'g');
  let trouve;
  while ((trouve = declaration.exec(code))) {
    const finDeclaration = trouve.index + trouve[0].length;
    let suite = finDeclaration;
    if (trouve[1]) {
      ajouter(trouve[1], finDeclaration - trouve[1].length);
    } else {
      nomsDestructures(code, finDeclaration - 1, ajouter);
      suite = fermante(code, finDeclaration - 1) + 1;
      if (suite <= 0) continue;
    }
    declarateursSuivantsJs(code, suite, ajouter);
  }
}

const DECLARATEUR_SUIVANT_JS = new RegExp(String.raw`^\s*(${IDENT})\s*(?=[=,;:\n)]|$)`);

// Déclarateurs suivants : `let a = 1, b = 2` ou `for (let i = 0, j = 1; …)`.
function declarateursSuivantsJs(code, suite, ajouter) {
  let profondeur = 0;
  for (let rang = suite; rang < code.length; rang++) {
    const caractere = code[rang];
    if ('([{'.includes(caractere)) profondeur++;
    else if (')]}'.includes(caractere) && --profondeur < 0) break;
    else if ((caractere === ';' || caractere === '\n') && profondeur === 0) break;
    else if (caractere === ',' && profondeur === 0) {
      const nom = DECLARATEUR_SUIVANT_JS.exec(code.slice(rang + 1));
      if (nom) ajouter(nom[1], rang + 1 + nom[0].indexOf(nom[1]));
    }
  }
}

// Variables de catch et paramètre unique d'une fonction fléchée (`x =>`).
function capturesEtFlechesJs(code, ajouter) {
  let trouve;
  const capture = new RegExp(String.raw`\bcatch\s*\(\s*(${IDENT})`, 'g');
  while ((trouve = capture.exec(code))) ajouter(trouve[1], trouve.index + trouve[0].length - trouve[1].length);
  const fleche = new RegExp(String.raw`(?<![\w$.])(${IDENT})\s*=>`, 'g');
  while ((trouve = fleche.exec(code))) ajouter(trouve[1], trouve.index);
}

// Champs de classe précédés d'un modificateur (`private nom: Type`, `static readonly nom =`).
function champsAModificateurJs(code, ajouter) {
  const champ = new RegExp(
    String.raw`\b(?:private|protected|public|readonly|static)\s+(?:readonly\s+)?#?(${IDENT})\s*[?!]?\s*[:=;]`, 'g');
  let trouve;
  while ((trouve = champ.exec(code))) {
    ajouter(trouve[1], trouve.index + trouve[0].search(new RegExp(String.raw`${trouve[1]}\s*[?!]?\s*[:=;]$`)));
  }
}

// Paramètres des fonctions fléchées (`(…) =>`), des fonctions et des méthodes (`(…) {`).
function parametresJs(code, ajouter) {
  for (let rang = code.indexOf('('); rang >= 0; rang = code.indexOf('(', rang + 1)) {
    const fin = fermante(code, rang);
    if (fin < 0) continue;
    const apres = code.slice(fin + 1, fin + 400);
    const parametres = ouvreFleche(apres) || (ouvreCorps(apres) && nommerFonction(code, rang, ajouter));
    if (parametres) lierListe(code, code.slice(rang + 1, fin), rang + 1, ajouter);
  }
}

const NOM_AVANT_PARENTHESE = new RegExp(
  String.raw`(?:\bfunction\s*\*?\s*(${IDENT})?|(${IDENT}))\s*(?:<[^<>]*>)?\s*$`);

// Devant `(…) {` : nom de la fonction (`function nom(`) ou de la méthode (`nom(…) {` dans une classe ou un
// objet). Rend vrai si la parenthèse ouvre une liste de paramètres, et lie alors ce nom.
function nommerFonction(code, rang, ajouter) {
  const avant = code.slice(Math.max(0, rang - 200), rang);
  const nomFonction = NOM_AVANT_PARENTHESE.exec(avant);
  if (!nomFonction || MOTS_AVANT_PARENTHESE.has(nomFonction[2] ?? '')) return false;
  const nom = nomFonction[1] ?? nomFonction[2];
  if (nom) ajouter(nom, rang - avant.length + nomFonction.index + nomFonction[0].lastIndexOf(nom));
  return true;
}

// Vrai si `from` commence à `rang` et n'est pas suivi d'un caractère de mot (`from\b`).
function motFrom(code, rang) {
  return code.startsWith('from', rang) && !/\w/.test(code[rang + 4] ?? '');
}

// Clause d'un import commençant à `debut` : { fin, finFrom } ou null. Équivaut à `(?![\s(])([^;]*?)\s+from\b`
// (la clause la plus courte, sans `;`, suivie de blancs puis de `from`), en temps linéaire.
function clauseImport(code, debut) {
  if (debut >= code.length || /[\s(]/.test(code[debut])) return null;
  let rang = debut;
  while (rang < code.length) {
    const finSeparateur = finBlancs(code, rang);
    if (finSeparateur > rang) {
      if (motFrom(code, finSeparateur)) return { fin: rang, finFrom: finSeparateur + 4 };
      rang = finSeparateur;
    } else if (code[rang] === ';') {
      return null;
    } else {
      rang++;
    }
  }
  return null;
}

// Instruction `import [type] clause from` dont la clause commence après `apresImport` : { debut, fin, finFrom }
// ou null. Le mot `type` suivi de blancs est d'abord pris comme modificateur, puis comme début de clause.
function instructionImport(code, apresImport) {
  const avecType = code.startsWith('type', apresImport) && /\s/.test(code[apresImport + 4] ?? '');
  if (avecType) {
    const debut = finBlancs(code, apresImport + 4);
    const clause = clauseImport(code, debut);
    if (clause) return { debut, ...clause };
  }
  const clause = clauseImport(code, apresImport);
  return clause ? { debut: apresImport, ...clause } : null;
}

// Noms locaux des imports : `import nom from`, `import { a, b as nom } from`, `import * as nom from`.
function importsJs(code, ajouter) {
  const debutImport = /(?<![\w$.])import[ \t]+/g;
  let trouve;
  while ((trouve = debutImport.exec(code))) {
    const instruction = instructionImport(code, trouve.index + trouve[0].length);
    if (!instruction) {
      debutImport.lastIndex = trouve.index + 1;
      continue;
    }
    debutImport.lastIndex = instruction.finFrom;
    const clause = code.slice(instruction.debut, instruction.fin);
    nomsImportes(clause, trouve.index + code.slice(trouve.index, instruction.finFrom).indexOf(clause), ajouter);
  }
}

const NOM_IMPORT_PAR_DEFAUT = new RegExp(String.raw`(?:\*\s*as\s+)?(${IDENT})\s*,?\s*$`);
const NOM_EN_TETE_BLANCS = new RegExp(String.raw`^\s*(${IDENT})`);
const NOM_EN_FIN = new RegExp(String.raw`(${IDENT})\s*$`);

// Noms liés par la clause d'un import (`Defaut`, `* as nom`, `{ a, b as nom }`), qui commence à `debutClause`.
function nomsImportes(clause, debutClause, ajouter) {
  const accolade = clause.indexOf('{');
  const tete = accolade >= 0 ? clause.slice(0, accolade) : clause;
  const nomTete = NOM_IMPORT_PAR_DEFAUT.exec(tete) ?? NOM_EN_TETE_BLANCS.exec(tete);
  if (nomTete) ajouter(nomTete[1], debutClause + nomTete.index + nomTete[0].lastIndexOf(nomTete[1]));
  if (accolade < 0) return;
  const fin = fermante(clause, accolade);
  for (const morceau of decouper(clause.slice(accolade + 1, fin < 0 ? clause.length : fin), debutClause + accolade + 1)) {
    const local = NOM_EN_FIN.exec(morceau.texte);
    if (local) ajouter(local[1], morceau.position + local.index);
  }
}

// Déclarations de types TS : `class Nom`, `interface Nom`, `enum Nom`, `type Nom =`.
function typesJs(code, ajouter) {
  const declaration = new RegExp(String.raw`(?<![\w$.])(?:(?:class|interface|enum)\s+(${IDENT})`
    + String.raw`|type\s+(${IDENT})\s*(?:<[^=;]*>)?\s*=(?!=))`, 'g');
  let trouve;
  while ((trouve = declaration.exec(code))) {
    const nom = trouve[1] ?? trouve[2];
    ajouter(nom, trouve.index + trouve[0].indexOf(nom, trouve[0].search(/\s/)));
  }
}

const MODIFICATEURS_MEMBRE = String.raw`(?:(?:static|readonly|declare|override|public|private|protected|abstract|async`
  + String.raw`|accessor|get|set)\s+)*`;
const DECORATEURS = String.raw`(?:@[\w$.]+(?:\([^)]*\))?\s*)*`;
const SIGNATURE_METHODE = new RegExp(
  String.raw`^\s*${DECORATEURS}${MODIFICATEURS_MEMBRE}\*?\s*#?(${IDENT})\s*\??\s*(?:<[^()]*?>)?\s*\(`);
const CHAMP_DE_CLASSE = new RegExp(String.raw`^\s*${DECORATEURS}${MODIFICATEURS_MEMBRE}#?(${IDENT})\s*[?!]?\s*[:=;]`);

// Membres des corps de classe et d'interface : champs de classe (avec ou sans modificateur), noms et
// paramètres des méthodes et des signatures de méthode. Les propriétés d'interface restent des contrats.
function membresDeCorps(code, ajouter) {
  const entete = new RegExp(String.raw`(?<![\w$.])(class|interface)\b(?:\s+${IDENT})?(?:\s*<[^{;]*?>)?`
    + String.raw`(?:\s+(?:extends|implements)\b[^{;]*)?\s*\{`, 'g');
  let trouve;
  while ((trouve = entete.exec(code))) {
    const ouverture = trouve.index + trouve[0].length - 1;
    const fermeture = fermante(code, ouverture);
    if (fermeture < 0) continue;
    const corps = { code, fermeture, classe: trouve[1] === 'class', ajouter };
    for (const debut of debutsDeMembres(code, ouverture, fermeture)) examinerMembre(corps, debut);
  }
}

// Débuts possibles d'un membre : après l'accolade ouvrante, et après chaque `;`, saut de ligne ou bloc `}`
// de premier niveau.
function debutsDeMembres(code, ouverture, fermeture) {
  const debuts = [ouverture + 1];
  let profondeur = 0;
  for (let rang = ouverture + 1; rang < fermeture; rang++) {
    const caractere = code[rang];
    if ('([{'.includes(caractere)) profondeur++;
    else if (')]}'.includes(caractere)) {
      profondeur--;
      if (profondeur === 0 && caractere === '}') debuts.push(rang + 1);
    } else if (profondeur === 0 && (caractere === ';' || caractere === '\n')) debuts.push(rang + 1);
  }
  return debuts;
}

function examinerMembre({ code, fermeture, classe, ajouter }, debut) {
  const ligne = code.slice(debut, fermeture).split('\n', 1)[0];
  const signature = SIGNATURE_METHODE.exec(ligne);
  if (signature) {
    if (signature[1] !== 'constructor') methodeDeCorps(code, debut, signature, ajouter);
    return;
  }
  const propriete = classe ? CHAMP_DE_CLASSE.exec(ligne) : null;
  if (propriete && !['constructor', 'static'].includes(propriete[1])) {
    ajouter(propriete[1], debut + propriete[0].lastIndexOf(propriete[1]));
  }
}

// Nom d'une méthode ; paramètres d'une signature sans corps (ceux d'une méthode à corps sont repérés ailleurs).
function methodeDeCorps(code, debut, signature, ajouter) {
  ajouter(signature[1], debut + signature[0].lastIndexOf(signature[1]));
  const parenthese = debut + signature[0].length - 1;
  const finParametres = fermante(code, parenthese);
  if (finParametres <= 0) return;
  const apres = code.slice(finParametres + 1, finParametres + 400);
  if (ouvreCorps(apres) || ouvreFleche(apres)) return;
  lierListe(code, code.slice(parenthese + 1, finParametres), parenthese + 1, ajouter);
}

// --- Java --------------------------------------------------------------------------------------------------

const DECLARATEUR_SUIVANT_JAVA = new RegExp(String.raw`^\s*(${IDENT})\s*(?=[=,;)])`);

// Déclarateurs Java suivant le premier : `int a = 1, b = 2;`, `int c, d;`, `for (int i = 0, j = 0; …)`.
// Dans une liste de paramètres, l'élément suivant porte son type (`String a, int b`) et n'est pas lié ici.
function declarateursSuivants(code, debut, ajouter) {
  let profondeur = 0;
  for (let rang = debut; rang < code.length; rang++) {
    const caractere = code[rang];
    if ('([{'.includes(caractere)) profondeur++;
    else if (')]}'.includes(caractere) && --profondeur < 0) return;
    else if (caractere === ';' && profondeur === 0) return;
    else if (caractere === '-' && code[rang + 1] === '>' && profondeur === 0) return;
    else if (caractere === ',' && profondeur === 0) {
      const nom = DECLARATEUR_SUIVANT_JAVA.exec(code.slice(rang + 1, rang + 200));
      if (nom) ajouter(nom[1], rang + 1 + nom[0].indexOf(nom[1]));
    }
  }
}

function declarationsJava(code, ajouter) {
  declarationsTypeesJava(code, ajouter);
  typesDeclaresJava(code, ajouter);
  flechesJava(code, ajouter);
  parametresLambdaJava(code, ajouter);
}

const TYPE_JAVA = String.raw`${IDENT}(?:\s*\.\s*${IDENT})*(?:\s*<[^;(){}=&|!+*/%]*?>)?(?:\s*\[\s*\])*(?:\s*\.\.\.)?`;

// Un type générique commence par une majuscule (`List<…>`) : `g(a < b, c > d)` est un appel.
function generiqueInvalide(type) {
  const chevron = type.indexOf('<');
  return chevron >= 0 && !/(?:^|\.)\s*[A-Z][\w$]*\s*$/.test(type.slice(0, chevron));
}

// `Type nom` suivi de `=`, `;`, `,`, `:`, `)`, `&`, `|`, `?`, `(` ou `->` : variables, champs, paramètres,
// méthodes, variables de catch, de boucle et de motif.
function declarationsTypeesJava(code, ajouter) {
  const declaration = new RegExp(String.raw`(?<![\w$.@])(${TYPE_JAVA})\s+(${IDENT})(?=\s*(?:[=;,:)&|?(]|->))`, 'g');
  let trouve;
  while ((trouve = declaration.exec(code))) {
    const premierMot = /^[\w$]+/.exec(trouve[1])[0];
    if (MOTS_NON_TYPES.has(premierMot) || generiqueInvalide(trouve[1])) {
      declaration.lastIndex = trouve.index + premierMot.length;
      continue;
    }
    const finNom = trouve.index + trouve[0].length;
    ajouter(trouve[2], finNom - trouve[2].length);
    const suite = /^\s*(.)/.exec(code.slice(finNom, finNom + 50))?.[1];
    // `Type nom(` : méthode (ses paramètres sont des déclarations repérées à part).
    if (suite === '=' || suite === ',') declarateursSuivants(code, finNom, ajouter);
  }
}

function typesDeclaresJava(code, ajouter) {
  const typeDeclare = new RegExp(String.raw`(?<![\w$.])(?:class|record|interface|enum|@\s*interface)\s+(${IDENT})`, 'g');
  let trouve;
  while ((trouve = typeDeclare.exec(code))) ajouter(trouve[1], trouve.index + trouve[0].length - trouve[1].length);
}

// Paramètre unique d'une lambda (`x ->`), hors étiquettes de switch (`case A ->`, `default ->`).
function flechesJava(code, ajouter) {
  const fleche = new RegExp(String.raw`(?<![\w$.])(${IDENT})\s*->`, 'g');
  let trouve;
  while ((trouve = fleche.exec(code))) {
    const instruction = code.slice(0, trouve.index).split(/[;{}]/).at(-1).trim();
    if (!/^(case|default)\b/.test(instruction)) ajouter(trouve[1], trouve.index);
  }
}

// Paramètres d'une lambda entre parenthèses (`(a, b) ->`, `(String a) ->`).
function parametresLambdaJava(code, ajouter) {
  for (let rang = code.indexOf('('); rang >= 0; rang = code.indexOf('(', rang + 1)) {
    const fin = fermante(code, rang);
    if (fin < 0 || !/^\s*->/.test(code.slice(fin + 1, fin + 10))) continue;
    for (const morceau of decouper(code.slice(rang + 1, fin), rang + 1)) {
      const nom = NOM_EN_FIN.exec(morceau.texte);
      if (nom) ajouter(nom[1], morceau.position + nom.index);
    }
  }
}

// --- Gabarits Vue --------------------------------------------------------------------------------------------

// Vrai si `in` ou `of` commence à `rang`, suivi d'un blanc.
function motInOf(code, rang) {
  return (code.startsWith('in', rang) || code.startsWith('of', rang)) && /\s/.test(code[rang + 2] ?? '');
}

// Vrai si des blancs commencent à `rang`, suivis de `in` ou `of` et d'un blanc (`\s+(?:in|of)\s`).
function blancsPuisInOf(code, rang) {
  const fin = finBlancs(code, rang);
  return fin > rang && motInOf(code, fin);
}

// Première fin possible de la liste des variables d'un v-for commençant à `debut` : position suivie de
// `)` facultative, de blancs, de `in` ou `of` et d'un blanc ; -1 s'il n'y en a pas.
function finListeVFor(code, debut) {
  let rang = debut;
  while (rang < code.length) {
    if (code[rang] === ')' && blancsPuisInOf(code, rang + 1)) return rang;
    const fin = finBlancs(code, rang);
    if (fin === rang) rang++;
    else if (motInOf(code, fin)) return rang;
    else rang = fin;
  }
  return -1;
}

// Liste des variables d'un v-for : { parenthese, debut, fin } ou null. Équivaut, en temps linéaire, à
// `^\s*(\(?)([^]*?)\)?\s+(?:in|of)\s`, y compris son repli : quand `in` ou `of` suit directement les blancs
// initiaux, la liste est vide et commence au dernier de ces blancs.
function listeVFor(code) {
  const blancs = finBlancs(code, 0);
  const parenthese = code[blancs] === '(' ? 1 : 0;
  const fin = finListeVFor(code, blancs + parenthese);
  if (fin >= 0) return { parenthese, debut: blancs + parenthese, fin };
  if (blancs > 0 && motInOf(code, blancs)) return { parenthese: 0, debut: blancs - 1, fin: blancs - 1 };
  return null;
}

function variablesVFor(code, ajouter) {
  const boucle = listeVFor(code);
  if (!boucle) return;
  const liste = code.slice(boucle.debut, boucle.fin);
  lierListe(code, liste, code.slice(0, boucle.fin).indexOf(liste, boucle.parenthese), ajouter);
}

// Gabarit Vue : variables de v-for, v-slot et fonctions fléchées des directives.
function declarationsGabarit(source, debutGabarit, finGabarit, ajouter) {
  const gabarit = source.slice(debutGabarit, finGabarit);
  const attribut = /\s(v-[\w:.\-[\]]+|[:@#][\w:.\-[\]]*)\s*=\s*"([^"]*)"/g;
  let trouve;
  while ((trouve = attribut.exec(gabarit))) {
    const [, directive, valeur] = trouve;
    const decalage = debutGabarit + trouve.index + trouve[0].length - valeur.length - 1;
    const code = masquer(valeur, 'ts');
    const decaler = (nom, position) => ajouter(nom, decalage + position);
    if (directive === 'v-for') variablesVFor(code, decaler);
    else if (directive.startsWith('v-slot') || directive.startsWith('#')) lierListe(code, code, 0, decaler);
    else declarationsJs(code, decaler);
  }
}

// Composant Vue : blocs <script> (le reste blanchi, positions conservées), puis gabarit.
function declarationsVue(source, ajouter) {
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
