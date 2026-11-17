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

/**
 * Vrai si `url` est une demande d'autorisation acceptable : même origine et chemin sous l'émetteur
 * configuré, code d'autorisation avec PKCE S256, client et redirection attendus.
 */
function demandeAcceptable(url, { emetteur, redirectUri }) {
  if (typeof url !== 'string' || url.length > URL_MAX) return false;
  let demande;
  let racine;
  try {
    demande = new URL(url);
    racine = new URL(emetteur);
  } catch {
    return false;
  }
  const prefixe = racine.pathname.replace(/\/+$/, '') + '/';
  if (demande.origin !== racine.origin || !demande.pathname.startsWith(prefixe)) return false;
  if (demande.username || demande.password) return false;
  const p = demande.searchParams;
  return p.get('response_type') === 'code'
    && p.get('code_challenge_method') === 'S256'
    && Boolean(p.get('code_challenge'))
    && Boolean(p.get('state'))
    && p.get('redirect_uri') === redirectUri;
}

/**
 * @param {object} d dépendances
 * @param {{createServer: Function}} d.http module node:http
 * @param {(url: string) => unknown} d.ouvrirNavigateur ouvre l'URL dans le navigateur du système
 * @param {string} d.emetteur URL du royaume (ARGOS_OIDC_URL)
 * @param {() => void} [d.afficherFenetre] remet la fenêtre principale au premier plan
 * @param {number} [d.delaiMaxMs] durée d'attente maximale du retour
 * @param {Function} [d.planifier] setTimeout injectable
 * @param {Function} [d.annuler] clearTimeout injectable
 */
function creerConnexion(d) {
  const planifier = d.planifier ?? setTimeout;
  const annuler = d.annuler ?? clearTimeout;
  const delaiMaxMs = d.delaiMaxMs ?? DELAI_MAX_MS;
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
      const serveur = d.http.createServer((requete, reponse) => {
        let url;
        try {
          url = new URL(requete.url, `http://${HOTE}`);
        } catch {
          url = null;
        }
        if (requete.method !== 'GET' || !url || url.pathname !== CHEMIN_RETOUR || ecoute !== courante || !courante.resoudre) {
          reponse.writeHead(404, { 'Content-Type': 'text/plain; charset=utf-8' });
          reponse.end('Introuvable');
          return;
        }
        reponse.writeHead(200, ENTETES_PAGE);
        reponse.end(PAGE_RETOUR);
        if (d.afficherFenetre) d.afficherFenetre();
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
    if (!courante || courante.resoudre || !demandeAcceptable(url, { emetteur: d.emetteur, redirectUri: courante.redirectUri })) {
      return Promise.resolve(null);
    }
    return new Promise((resolve) => {
      courante.resoudre = resolve;
      courante.minuterie = planifier(() => { if (ecoute === courante) fermer(null); }, delaiMaxMs);
      const echec = () => { if (ecoute === courante) fermer(null); };
      try {
        Promise.resolve(d.ouvrirNavigateur(url)).catch(echec);
      } catch {
        echec();
      }
    });
  }

  return { preparer, autoriser, fermer: () => fermer(null), enAttente: () => ecoute !== null };
}

module.exports = { creerConnexion, demandeAcceptable, CHEMIN_RETOUR, HOTE, PAGE_RETOUR };
