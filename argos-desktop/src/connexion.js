'use strict';
// Connexion OIDC du client desktop (changement connecter-ihm-oidc : ACC-10 ; ADR-0022 point 6,
// RFC 8252). L'autorisation se déroule dans le navigateur du système ; le fournisseur redirige vers
// l'interface de bouclage 127.0.0.1, sur un port éphémère choisi par le système, où ce module écoute
// le temps d'un seul retour. Le code reçu est rendu à l'IHM, qui vérifie le state et l'échange avec
// son vérificateur PKCE : ce module ne voit aucun jeton et ne journalise rien du retour.
// Module sans dépendance à Electron : main.js lui fournit node:http, l'ouverture du navigateur et
// la remise au premier plan ; les tests lui fournissent un vrai serveur local et des doublures.

const HOTE = '127.0.0.1';
const CHEMIN_RETOUR = '/argos/retour';
const DELAI_MAX_MS = 5 * 60 * 1000;
const URL_MAX = 8192;
const RECHERCHE_MAX = 4096;

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
 * URL d'autorisation à ouvrir, ou null si la demande reçue de l'IHM n'est pas acceptable.
 * Acceptable : point d'autorisation de l'émetteur configuré (même origine, même chemin, sans
 * identifiants), code d'autorisation avec PKCE S256, state présent et redirection vers l'écoute
 * locale en cours. L'URL renvoyée est reconstruite : son origine et son chemin viennent de la
 * configuration, et seuls les paramètres de PARAMETRES_AUTORISATION sont recopiés. L'identifiant
 * du client n'est pas contrôlé ici : le royaume n'accepte cette redirection que pour argos-ui.
 */
function urlAutorisationSure(url, { emetteur, redirectUri }) {
  if (typeof url !== 'string' || url.length > URL_MAX) return null;
  const urls = lireUrls(url, emetteur);
  if (!urls || !demandeConforme(urls.demande, urls.point, redirectUri)) return null;
  for (const nom of PARAMETRES_AUTORISATION) {
    const valeur = urls.demande.searchParams.get(nom);
    if (valeur !== null) urls.point.searchParams.set(nom, valeur);
  }
  return urls.point.toString();
}

/** URL reçue et point d'autorisation configuré, ou null si l'une des deux est illisible. */
function lireUrls(url, emetteur) {
  try {
    return { demande: new URL(url), point: pointAutorisation(emetteur) };
  } catch {
    return null;
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
  return urlAutorisationSure(url, contexte) !== null;
}

/** Vrai si la requête reçue est le retour attendu par l'écoute `courante`, encore active. */
function retourAttendu(requete, url, courante, active) {
  if (requete.method !== 'GET' || !url) return false;
  return url.pathname === CHEMIN_RETOUR && active && Boolean(courante.resoudre);
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
  const planifier = dependances.planifier ?? setTimeout;
  const annuler = dependances.annuler ?? clearTimeout;
  const delaiMaxMs = dependances.delaiMaxMs ?? DELAI_MAX_MS;
  /** Écoute en cours : { serveur, redirectUri, resoudre, minuterie } ; une seule à la fois. */
  let ecoute = null;

  function fermer(resultat) {
    const courante = ecoute;
    ecoute = null;
    if (!courante) return;
    if (courante.minuterie) annuler(courante.minuterie);
    courante.serveur.close();
    if (courante.resoudre) courante.resoudre(resultat);
  }

  /** Ouvre l'écoute locale et renvoie l'adresse de redirection ; ferme toute écoute précédente. */
  function preparer() {
    fermer(null);
    return new Promise((resolve) => {
      const courante = { serveur: null, redirectUri: null, resoudre: null, minuterie: null };
      const serveur = dependances.http.createServer((requete, reponse) => {
        let url;
        try {
          url = new URL(requete.url, `http://${HOTE}`);
        } catch {
          url = null;
        }
        if (!retourAttendu(requete, url, courante, ecoute === courante)) {
          reponse.writeHead(404, { 'Content-Type': 'text/plain; charset=utf-8' });
          reponse.end('Introuvable');
          return;
        }
        reponse.writeHead(200, ENTETES_PAGE);
        reponse.end(PAGE_RETOUR);
        if (dependances.afficherFenetre) dependances.afficherFenetre();
        fermer(url.search.slice(0, RECHERCHE_MAX));
      });
      courante.serveur = serveur;
      serveur.on('error', () => {
        if (ecoute === courante) fermer(null);
        resolve(null);
      });
      serveur.listen(0, HOTE, () => {
        courante.redirectUri = `http://${HOTE}:${serveur.address().port}${CHEMIN_RETOUR}`;
        ecoute = courante;
        resolve(courante.redirectUri);
      });
    });
  }

  /**
   * Ouvre l'URL d'autorisation dans le navigateur du système et attend le retour.
   * Renvoie la chaîne de requête du retour, ou null (URL refusée, abandon, délai dépassé).
   */
  function autoriser(url) {
    const courante = ecoute;
    if (!courante || courante.resoudre) return Promise.resolve(null);
    const sure = urlAutorisationSure(url, { emetteur: dependances.emetteur, redirectUri: courante.redirectUri });
    if (!sure) return Promise.resolve(null);
    return new Promise((resolve) => {
      courante.resoudre = resolve;
      courante.minuterie = planifier(() => { if (ecoute === courante) fermer(null); }, delaiMaxMs);
      const echec = () => { if (ecoute === courante) fermer(null); };
      try {
        Promise.resolve(dependances.ouvrirNavigateur(sure)).catch(echec);
      } catch {
        echec();
      }
    });
  }

  return { preparer, autoriser, fermer: () => fermer(null), enAttente: () => ecoute !== null };
}

module.exports = { creerConnexion, demandeAcceptable, urlAutorisationSure, CHEMIN_RETOUR, HOTE, PAGE_RETOUR };
