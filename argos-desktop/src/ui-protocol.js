'use strict';
// Résolution des chemins du protocole interne app://argos/ (ADR-0022).
// Module sans dépendance à Electron pour pouvoir être testé avec « node --test ».

const path = require('node:path');

const SCHEME = 'app';
const HOST = 'argos';
const ORIGIN = `${SCHEME}://${HOST}`;

/**
 * Politique de sécurité du contenu appliquée à l'IHM chargée par le desktop.
 * connect-src autorise uniquement l'origine du serveur ARGOS et, si elle est fournie, celle du
 * fournisseur d'identité configuré (échange et renouvellement des jetons, ACC-10).
 */
function contentSecurityPolicy(apiUrl, oidcUrl) {
  const origines = [new URL(apiUrl).origin];
  if (oidcUrl) {
    const oidc = new URL(oidcUrl).origin;
    if (!origines.includes(oidc)) origines.push(oidc);
  }
  return [
    "default-src 'self'",
    "script-src 'self'",
    "style-src 'self' 'unsafe-inline'",
    "img-src 'self' data:",
    "font-src 'self'",
    `connect-src 'self' ${origines.join(' ')}`,
    "object-src 'none'",
    "base-uri 'none'",
    "frame-ancestors 'none'",
  ].join('; ');
}

/**
 * Transforme une URL app://argos/... en chemin de fichier à l'intérieur de uiDir.
 * Renvoie null si l'URL vise un autre hôte ou sort du dossier (« ../ »).
 */
function resolveUiFile(requestUrl, uiDir) {
  let url;
  try {
    url = new URL(requestUrl);
  } catch {
    return null;
  }
  if (url.protocol !== `${SCHEME}:` || url.host !== HOST) return null;
  let relative;
  try {
    relative = decodeURIComponent(url.pathname);
  } catch {
    return null;
  }
  if (relative === '/' || relative === '') relative = '/index.html';
  const root = path.resolve(uiDir);
  const target = path.resolve(root, '.' + relative);
  if (target !== root && !target.startsWith(root + path.sep)) return null;
  return target;
}

const MIME_TYPES = {
  '.html': 'text/html; charset=utf-8',
  '.js': 'text/javascript; charset=utf-8',
  '.css': 'text/css; charset=utf-8',
  '.json': 'application/json',
  '.svg': 'image/svg+xml',
  '.png': 'image/png',
  '.ico': 'image/x-icon',
  '.woff2': 'font/woff2',
  '.woff': 'font/woff',
};

function mimeType(file) {
  return MIME_TYPES[path.extname(file).toLowerCase()] || 'application/octet-stream';
}

module.exports = { SCHEME, HOST, ORIGIN, contentSecurityPolicy, resolveUiFile, mimeType };
