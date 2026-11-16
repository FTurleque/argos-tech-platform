'use strict';
// Comportement du poste pour les alertes (changement notifier-sur-le-poste : NOT-01 à NOT-05).
// Module sans dépendance directe à Electron : main.js lui fournit la fabrique de notifications,
// la zone de notification et la fenêtre ; les tests lui fournissent des doublures.
//
// Le processus principal n'appelle pas l'API et ne filtre aucune donnée : l'IHM lui transmet les
// alertes déjà reçues du serveur et le nombre d'alertes ouvertes.

const { validerAlerte, texteNotification, compteurValide, infobulle } = require('./notifications');
const { fusionner } = require('./preferences');

/**
 * @param {object} d dépendances
 * @param {() => boolean} d.notificationsSupportees vrai si le système sait afficher une notification
 * @param {(options: {title: string, body: string}) => {on: Function, show: Function}} d.creerNotification
 * @param {() => {notificationsPoste: boolean, demarrageSession: boolean}} d.reglages réglages courants
 * @param {(reglages: object) => void} d.enregistrerReglages persiste et applique les réglages
 * @param {(n: number) => void} d.afficherCompteur met à jour la zone de notification
 * @param {(id: string) => void} d.ouvrirAlerte remet la fenêtre au premier plan sur l'alerte
 * @param {boolean} [d.demarrageDisponible] faux là où le démarrage avec la session n'existe pas
 */
function creerPoste(d) {
  let compteur = 0;
  // Références gardées tant que la notification peut être cliquée (sinon le ramasse-miettes
  // peut détruire l'objet et le clic est perdu).
  const enCours = new Set();

  /**
   * Notification d'une alerte (NOT-01, NOT-02, NOT-04, NOT-05).
   * @returns {{affichee: boolean, raison?: string}}
   */
  function alerte(payload) {
    const valide = validerAlerte(payload);
    if (!valide) return { affichee: false, raison: 'INVALIDE' };
    if (!d.reglages().notificationsPoste) return { affichee: false, raison: 'COUPEES' };
    if (!d.notificationsSupportees()) return { affichee: false, raison: 'NON_SUPPORTEES' };
    const notification = d.creerNotification(texteNotification(valide));
    enCours.add(notification);
    const liberer = () => enCours.delete(notification);
    notification.on('click', () => {
      liberer();
      d.ouvrirAlerte(valide.id);
    });
    notification.on('close', liberer);
    notification.on('failed', liberer);
    notification.show();
    return { affichee: true };
  }

  /** Nombre d'alertes ouvertes affiché dans la zone de notification (NOT-03). */
  function definirCompteur(n) {
    const valide = compteurValide(n);
    if (valide === null) return false;
    compteur = valide;
    d.afficherCompteur(compteur);
    return true;
  }

  /** Modification des réglages du poste ; renvoie les réglages appliqués, ou null si refusée. */
  function changerReglages(demande) {
    let filtree = demande;
    if (d.demarrageDisponible === false && demande && typeof demande === 'object') {
      // Option indisponible sur ce système : la clé est ignorée et le réglage reste désactivé.
      const { demarrageSession: _ignore, ...reste } = demande;
      filtree = reste;
    }
    const nouveaux = fusionner(d.reglages(), filtree);
    if (!nouveaux) return null;
    if (d.demarrageDisponible === false) nouveaux.demarrageSession = false;
    d.enregistrerReglages(nouveaux);
    return nouveaux;
  }

  return {
    alerte,
    definirCompteur,
    changerReglages,
    compteur: () => compteur,
    infobulle: () => infobulle(compteur),
    notificationsEnCours: () => enCours.size,
  };
}

module.exports = { creerPoste };
