// Composants Vue : blocs <script> analysés comme du TS, puis gabarit (variables v-for et v-slot, fonctions
// fléchées des directives).

import { declarationsJs } from './javascript.mjs';
import { masquer } from './masquage.mjs';
import { finBlancs, lierListe } from './outils.mjs';

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
export function declarationsVue(source, ajouter) {
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
