'use strict';
// Connexion OIDC du client desktop (changement connecter-ihm-oidc : ACC-10 ; ADR-0022 point 6,
// RFC 8252). L'autorisation se déroule dans le navigateur du système ; le fournisseur redirige vers
// l'interface de bouclage 127.0.0.1, sur un port éphémère choisi par le système, où ce module écoute
// le temps d'un seul retour. Le code reçu est rendu à l'IHM, qui vérifie le state et l'échange avec
// son vérificateur PKCE : ce module ne voit aucun jeton et ne journalise rien du retour.
// L'écoute ne se ferme que sur le retour dont le state est celui de l'URL d'autorisation validée (ACC-14) :
// tout autre retour reçoit 400 et laisse l'écoute ouverte, si bien qu'un processus local ne peut ni interrompre
// la connexion ni lui substituer un code. Module sans dépendance à Electron : main.js lui fournit node:http, l'ouverture du navigateur et
// la remise au premier plan ; les tests lui fournissent un vrai serveur local et des doublures.

const { timingSafeEqual } = require('node:crypto');

const HOTE = '127.0.0.1';
const CHEMIN_RETOUR = '/argos/retour';
const DELAI_MAX_MS = 5 * 60 * 1000;
const URL_MAX = 8192;
const RECHERCHE_MAX = 4096;
const STATUT_OK = 200;
const STATUT_INTROUVABLE = 404;
const STATUT_REQUETE_INVALIDE = 400;

/** Page affichée dans le navigateur après le retour : statique, sans reprise des paramètres reçus. */
const PAGE_RETOUR = [
  '<!DOCTYPE html>',
  '<html lang="fr">',
  '<head><meta charset="utf-8"><title>ARGOS — connexion</title></head>',
  '<body><h1>Connexion transmise à ARGOS</h1>',
  '<p>Vous pouvez fermer cet onglet et revenir à l’application.</p></body>',
  '</html>',
].join('\n');

const ENTETES_PAGE = {
  'Content-Type': 'text/html; charset=utf-8',
  'Cache-Control': 'no-store',
  'Content-Security-Policy': "default-src 'none'",
  'Referrer-Policy': 'no-referrer',
  'X-Content-Type-Options': 'nosniff',
};

/** Point d'autorisation du royaume Keycloak, relatif à l'émetteur configuré. */
const CHEMIN_AUTORISATION = '/protocol/openid-connect/auth';

/** Paramètres recopiés dans l'URL ouverte, dans cet ordre ; tout autre paramètre est ignoré. */
const PARAMETRES_AUTORISATION = [
  'response_type', 'client_id', 'redirect_uri', 'scope', 'state', 'code_challenge', 'code_challenge_method',
];

/** Point d'autorisation construit à partir de l'émetteur configuré seul. */
function pointAutorisation(emetteur) {
  const racine = new URL(emetteur);
  racine.pathname = racine.pathname.replace(/\/+$/, '') + CHEMIN_AUTORISATION;
  racine.search = '';
  racine.hash = '';
  return racine;
}

/** Vrai si les paramètres demandent un code d'autorisation avec PKCE S256 et un state. */
function codeAvecPkce(parametres) {
  const pkce = parametres.get('code_challenge_method') === 'S256' && Boolean(parametres.get('code_challenge'));
  return parametres.get('response_type') === 'code' && pkce && Boolean(parametres.get('state'));
}

/**
 * URL d'autorisation à ouvrir, ou undefined si la demande reçue de l'IHM n'est pas acceptable.
 * Acceptable : point d'autorisation de l'émetteur configuré (même origine, même chemin, sans
 * identifiants), code d'autorisation avec PKCE S256, state présent et redirection vers l'écoute
 * locale en cours. L'URL renvoyée est reconstruite : son origine et son chemin viennent de la
 * configuration, et seuls les paramètres de PARAMETRES_AUTORISATION sont recopiés. L'identifiant
 * du client n'est pas contrôlé ici : le royaume n'accepte cette redirection que pour argos-ui.
 */
function urlAutorisationSure(url, { emetteur, redirectUri }) {
  if (typeof url !== 'string' || url.length > URL_MAX) return undefined;
  const urls = lireUrls(url, emetteur);
  if (!urls || !demandeConforme(urls.demande, urls.point, redirectUri)) return undefined;
  for (const nom of PARAMETRES_AUTORISATION) {
    const valeur = urls.demande.searchParams.get(nom);
    if (valeur !== null) urls.point.searchParams.set(nom, valeur);
  }
  return urls.point.toString();
}

/** URL reçue et point d'autorisation configuré, ou undefined si l'une des deux est illisible. */
function lireUrls(url, emetteur) {
  try {
    return { demande: new URL(url), point: pointAutorisation(emetteur) };
  } catch {
    return undefined;
  }
}

/** Vrai si la demande vise le point d'autorisation, sans identifiants, avec PKCE et la bonne redirection. */
function demandeConforme(demande, point, redirectUri) {
  if (demande.origin !== point.origin || demande.pathname !== point.pathname) return false;
  if (demande.username || demande.password) return false;
  const parametres = demande.searchParams;
  return codeAvecPkce(parametres) && parametres.get('redirect_uri') === redirectUri;
}

/** Vrai si `url` est une demande d'autorisation acceptable (voir urlAutorisationSure). */
function demandeAcceptable(url, contexte) {
  return urlAutorisationSure(url, contexte) !== undefined;
}

/** Vrai si la requête reçue vise le chemin de retour de l'écoute `courante`, encore active. */
function retourRecu(requete, url, courante, active) {
  if (requete.method !== 'GET' || !url) return false;
  return url.pathname === CHEMIN_RETOUR && active && Boolean(courante.resoudre);
}

/**
 * Vrai si `recu` est exactement le state de l'URL d'autorisation validée. Comparaison en temps constant, longueurs
 * différentes écartées d'abord (timingSafeEqual exige des tampons de même longueur).
 */
function stateAttendu(recu, attendu) {
  if (typeof recu !== 'string' || typeof attendu !== 'string' || attendu === '') return false;
  const octetsRecus = Buffer.from(recu, 'utf8');
  const octetsAttendus = Buffer.from(attendu, 'utf8');
  return octetsRecus.length === octetsAttendus.length && timingSafeEqual(octetsRecus, octetsAttendus);
}

/** URL de la requête reçue par l'écoute locale, ou undefined si elle est illisible. */
function lireUrlRequete(requete) {
  try {
    return new URL(requete.url, `http://${HOTE}`);
  } catch {
    return undefined;
  }
}

/**
 * Répond à une requête reçue par l'écoute locale : 404 hors du chemin de retour ; 400 pour un retour dont le state
 * est absent ou différent de celui de la demande, sans rien reprendre de la requête et sans fermer l'écoute ; pour le
 * retour attendu, page de retour, fenêtre au premier plan et fermeture de l'écoute avec la chaîne de requête reçue.
 */
function traiterRequete(requete, reponse, contexte) {
  const url = lireUrlRequete(requete);
  if (!retourRecu(requete, url, contexte.courante, contexte.estActive())) {
    reponse.writeHead(STATUT_INTROUVABLE, { 'Content-Type': 'text/plain; charset=utf-8' });
    reponse.end('Introuvable');
    return;
  }
  if (!stateAttendu(url.searchParams.get('state'), contexte.courante.state)) {
    reponse.writeHead(STATUT_REQUETE_INVALIDE, { 'Content-Type': 'text/plain; charset=utf-8' });
    reponse.end('Requête invalide');
    return;
  }
  reponse.writeHead(STATUT_OK, ENTETES_PAGE);
  reponse.end(PAGE_RETOUR);
  if (contexte.afficherFenetre) contexte.afficherFenetre();
  contexte.fermer(url.search.slice(0, RECHERCHE_MAX));
}

/** Ouvre l'URL dans le navigateur ; tout échec, synchrone ou non, appelle `echec`. */
function ouvrirSansErreur(ouvrirNavigateur, url, echec) {
  try {
    Promise.resolve(ouvrirNavigateur(url)).catch(echec);
  } catch {
    echec();
  }
}

/**
 * Écoute locale du retour d'autorisation ; une seule à la fois.
 * @typedef {object} Ecoute
 * @property {import('node:http').Server|null} serveur serveur HTTP de l'interface de bouclage
 * @property {string|null} redirectUri adresse de redirection annoncée au royaume
 * @property {string|null} state state de l'URL d'autorisation validée, seul accepté au retour
 * @property {((resultat: string|null) => void)|null} resoudre résout l'attente du retour
 * @property {unknown} minuterie minuterie du délai maximal, rendue par `planifier`
 */

/**
 * Écoute en cours et clearTimeout injectable.
 * @typedef {{ecoute: Ecoute|null, annuler: Function}} EtatEcoute
 */

/**
 * Ferme l'écoute en cours de `etat` et résout son attente avec `resultat`.
 * @param {EtatEcoute} etat écoute en cours et clearTimeout injectable
 * @param {string|null} resultat chaîne de requête du retour, ou null
 */
function fermerEcoute(etat, resultat) {
  const courante = etat.ecoute;
  etat.ecoute = null;
  if (!courante) return;
  if (courante.minuterie) etat.annuler(courante.minuterie);
  courante.serveur.close();
  if (courante.resoudre) courante.resoudre(resultat);
}

/** Ouvre une écoute locale et renvoie l'adresse de redirection, ou null si l'écoute échoue. */
function ouvrirEcoute(etat, dependances) {
  return new Promise((resolve) => {
    /** @type {Ecoute} */
    const courante = { serveur: null, redirectUri: null, state: null, resoudre: null, minuterie: null };
    const contexte = {
      courante,
      estActive: () => etat.ecoute === courante,
      afficherFenetre: dependances.afficherFenetre,
      fermer: (resultat) => fermerEcoute(etat, resultat),
    };
    const serveur = dependances.http.createServer((requete, reponse) => traiterRequete(requete, reponse, contexte));
    courante.serveur = serveur;
    serveur.on('error', () => {
      if (etat.ecoute === courante) fermerEcoute(etat, null);
      resolve(null);
    });
    serveur.listen(0, HOTE, () => {
      courante.redirectUri = `http://${HOTE}:${serveur.address().port}${CHEMIN_RETOUR}`;
      etat.ecoute = courante;
      resolve(courante.redirectUri);
    });
  });
}

/**
 * Ouvre l'URL sûre dans le navigateur et attend le retour de l'écoute `courante`, au plus `delaiMaxMs`.
 * Résout la chaîne de requête du retour, ou null (abandon, délai dépassé, navigateur en échec).
 */
function attendreRetour(etat, courante, urlSure, options) {
  const fermerSiCourante = () => {
    if (etat.ecoute === courante) fermerEcoute(etat, null);
  };
  return new Promise((resolve) => {
    courante.state = new URL(urlSure).searchParams.get('state');
    courante.resoudre = resolve;
    courante.minuterie = options.planifier(fermerSiCourante, options.delaiMaxMs);
    ouvrirSansErreur(options.ouvrirNavigateur, urlSure, fermerSiCourante);
  });
}

/**
 * @param {object} dependances dépendances
 * @param {{createServer: Function}} dependances.http module node:http
 * @param {(url: string) => unknown} dependances.ouvrirNavigateur ouvre l'URL dans le navigateur du système
 * @param {string} dependances.emetteur URL du royaume (ARGOS_OIDC_URL)
 * @param {() => void} [dependances.afficherFenetre] remet la fenêtre principale au premier plan
 * @param {number} [dependances.delaiMaxMs] durée d'attente maximale du retour
 * @param {Function} [dependances.planifier] setTimeout injectable
 * @param {Function} [dependances.annuler] clearTimeout injectable
 */
function creerConnexion(dependances) {
  /** @type {EtatEcoute} */
  const etat = { ecoute: null, annuler: dependances.annuler ?? clearTimeout };
  const options = {
    planifier: dependances.planifier ?? setTimeout,
    delaiMaxMs: dependances.delaiMaxMs ?? DELAI_MAX_MS,
    ouvrirNavigateur: dependances.ouvrirNavigateur,
  };

  /** Ouvre l'écoute locale et renvoie l'adresse de redirection ; ferme toute écoute précédente. */
  function preparer() {
    fermerEcoute(etat, null);
    return ouvrirEcoute(etat, dependances);
  }

  /**
   * Ouvre l'URL d'autorisation dans le navigateur du système et attend le retour.
   * Renvoie la chaîne de requête du retour, ou null (URL refusée, abandon, délai dépassé).
   */
  function autoriser(url) {
    const courante = etat.ecoute;
    if (!courante || courante.resoudre) return Promise.resolve(null);
    const sure = urlAutorisationSure(url, { emetteur: dependances.emetteur, redirectUri: courante.redirectUri });
    if (!sure) return Promise.resolve(null);
    return attendreRetour(etat, courante, sure, options);
  }

  return { preparer, autoriser, fermer: () => fermerEcoute(etat, null), enAttente: () => etat.ecoute !== null };
}

module.exports = { creerConnexion, demandeAcceptable, urlAutorisationSure, CHEMIN_RETOUR, HOTE, PAGE_RETOUR };
