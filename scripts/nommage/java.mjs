// Déclarations Java : types, méthodes, variables, champs, paramètres (de méthode, de record, de lambda),
// variables de catch, de boucle et de motif.

import { IDENT, MOTS_NON_TYPES, NOM_EN_FIN, decouper, fermante } from './outils.mjs';

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

export function declarationsJava(code, ajouter) {
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
