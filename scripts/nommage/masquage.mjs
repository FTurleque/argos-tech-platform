// Masquage des commentaires et des littéraux (chaînes, blocs de texte Java, gabarits, expressions régulières)
// avant la recherche des déclarations : longueur et sauts de ligne de la source conservés.

const MOTS_AVANT_REGEX = new Set([
  'return', 'typeof', 'case', 'in', 'of', 'new', 'delete', 'void', 'throw', 'yield', 'await', 'else', 'do',
]);

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
