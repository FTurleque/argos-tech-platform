// Déclarations TS, JS et Vue (blocs <script>, valeurs des directives) : variables, captures, fonctions
// fléchées, champs, paramètres, imports, types et membres des corps de classe et d'interface.

import {
  IDENT, MOTS_AVANT_PARENTHESE, NOM_EN_FIN, decouper, fermante, finBlancs, lierListe, nomsDestructures, ouvreCorps,
  ouvreFleche,
} from './outils.mjs';

export function declarationsJs(code, ajouter) {
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

const MODIFICATEURS_MEMBRE = '(?:(?:static|readonly|declare|override|public|private|protected|abstract|async'
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
