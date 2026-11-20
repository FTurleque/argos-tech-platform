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
 * @param {object} dependances dépendances
 * @param {() => boolean} dependances.notificationsSupportees vrai si le système sait afficher une notification
 * @param {(options: {title: string, body: string}) => {on: Function, show: Function}} dependances.creerNotification
 * @param {() => {notificationsPoste: boolean, demarrageSession: boolean}} dependances.reglages réglages courants
 * @param {(reglages: object) => void} dependances.enregistrerReglages persiste et applique les réglages
 * @param {(n: number) => void} dependances.afficherCompteur met à jour la zone de notification
 * @param {(id: string) => void} dependances.ouvrirAlerte remet la fenêtre au premier plan sur l'alerte
 * @param {boolean} [dependances.demarrageDisponible] faux là où le démarrage avec la session n'existe pas
 */
function creerPoste(dependances) {
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
    if (!dependances.reglages().notificationsPoste) return { affichee: false, raison: 'COUPEES' };
    if (!dependances.notificationsSupportees()) return { affichee: false, raison: 'NON_SUPPORTEES' };
    const notification = dependances.creerNotification(texteNotification(valide));
    enCours.add(notification);
    const liberer = () => enCours.delete(notification);
    notification.on('click', () => {
      liberer();
      dependances.ouvrirAlerte(valide.id);
    });
    notification.on('close', liberer);
    notification.on('failed', liberer);
    notification.show();
    return { affichee: true };
  }

  /** Nombre d'alertes ouvertes affiché dans la zone de notification (NOT-03). */
  function definirCompteur(nombre) {
    const valide = compteurValide(nombre);
    if (valide === null) return false;
    compteur = valide;
    dependances.afficherCompteur(compteur);
    return true;
  }

  /** Modification des réglages du poste ; renvoie les réglages appliqués, ou null si refusée. */
  function changerReglages(demande) {
    let filtree = demande;
    if (dependances.demarrageDisponible === false && demande && typeof demande === 'object') {
      // Option indisponible sur ce système : la clé est ignorée et le réglage reste désactivé.
      const { demarrageSession: _ignore, ...reste } = demande;
      filtree = reste;
    }
    const nouveaux = fusionner(dependances.reglages(), filtree);
    if (!nouveaux) return null;
    if (dependances.demarrageDisponible === false) nouveaux.demarrageSession = false;
    dependances.enregistrerReglages(nouveaux);
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
