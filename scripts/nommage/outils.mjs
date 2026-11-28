// Constantes et outils communs aux analyseurs : motif d'identifiant, mots réservés, règle du nom court,
// parcours des blancs, des parenthèses et des listes, liaison des paramètres et des motifs de déstructuration.

export const IDENT = String.raw`[A-Za-z_$][\w$]*`;
const MOTS_CLES = new Set(['as', 'in', 'of', 'if', 'do', 'is']);
export const MOTS_NON_TYPES = new Set([
  'return', 'throw', 'new', 'else', 'case', 'default', 'yield', 'assert', 'package', 'import',
  'instanceof', 'extends', 'implements', 'throws', 'super', 'this', 'break', 'continue', 'do', 'try',
  'finally', 'synchronized', 'permits', 'goto', 'when',
]);
export const MOTS_AVANT_PARENTHESE = new Set([
  'if', 'for', 'while', 'switch', 'catch', 'with', 'return', 'typeof', 'await', 'yield', 'new',
  'delete', 'void', 'in', 'of', 'case', 'throw', 'else', 'do', 'instanceof',
]);
export const NOM_EN_FIN = new RegExp(String.raw`(${IDENT})\s*$`);

/**
 * Une langue par concept (AUD-QUA-09 ; décision du porteur du projet, 2026-11-27) : terme étranger → terme du projet.
 * Le terme étranger reste admis hors des identifiants déclarés : nom du module (paquet `io.argos.workspace`), tables
 * et colonnes SQL, propriétés JSON du contrat de l'API (chaînes et clés, que l'analyse ignore).
 */
export const TERMES_IMPOSES = Object.freeze({ workspace: 'espace' });

/** Terme étranger contenu dans `nom` (casse ignorée), ou chaîne vide. */
export function termeEtranger(nom) {
  const minuscules = nom.toLowerCase();
  return Object.keys(TERMES_IMPOSES).find((terme) => minuscules.includes(terme)) ?? '';
}

/** Vrai si `nom` est un identifiant d'une ou deux lettres interdit par la règle. */
export function estCourt(nom) {
  if (!/^[A-Za-z][A-Za-z\d]?$/.test(nom)) return false;
  if (nom === 'id' || MOTS_CLES.has(nom)) return false;
  return !/^[A-Z]\d?$/.test(nom);
}

// Position du premier caractère qui n'est pas un blanc (`\s`) à partir de `debut`.
export function finBlancs(texte, debut) {
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

// Position de la parenthèse, accolade ou du crochet fermant qui équilibre `texte[debut]`.
export function fermante(texte, debut) {
  let profondeur = 0;
  for (let rang = debut; rang < texte.length; rang++) {
    const caractere = texte[rang];
    if ('([{'.includes(caractere)) profondeur++;
    else if (')]}'.includes(caractere) && --profondeur === 0) return rang;
  }
  return -1;
}

// Découpe sur les virgules de premier niveau ; rend les morceaux avec leur décalage.
export function decouper(texte, decalage) {
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
export function lierListe(texte, liste, debut, ajouter) {
  for (const morceau of decouper(liste, debut)) lier(texte, morceau.texte, morceau.position, ajouter);
}

// Noms liés par un motif de déstructuration `{…}` ou `[…]` commençant à `texte[debut]`.
export function nomsDestructures(texte, debut, ajouter) {
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
export function ouvreFleche(apres) {
  const rang = finAnnotation(apres);
  return rang >= 0 && apres.startsWith('=>', rang);
}

// `(…)` suivi d'un corps `{`, annotation de type comprise : `^\s*(?::\s*[^;{}=]*?)?\s*\{`.
export function ouvreCorps(apres) {
  const rang = finAnnotation(apres);
  return rang >= 0 && apres[rang] === '{';
}
