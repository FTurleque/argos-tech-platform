'use strict';
// Mise à jour automatique du client desktop (AUD-DEP-15 ; décisions du porteur du projet, 2026-11-27) : canal GitHub
// Releases du dépôt, lu par electron-updater (licence MIT). Au démarrage d'une application empaquetée sous Windows
// (installeur NSIS) ou Linux (AppImage), la dernière version publiée est cherchée, téléchargée en arrière-plan puis
// installée à la fermeture d'ARGOS ; une notification annonce la version prête. electron-updater vérifie l'empreinte
// SHA-512 du fichier téléchargé, publiée avec la version (latest.yml). macOS n'accepte une mise à jour que d'une
// application signée : sous macOS, la mise à jour reste manuelle. ARGOS_MISE_A_JOUR=off désactive la recherche.

/** Variable d'environnement qui désactive la mise à jour automatique quand elle vaut `off`. */
const VARIABLE_DESACTIVATION = 'ARGOS_MISE_A_JOUR';
const VALEUR_DESACTIVEE = 'off';
/** Plateformes où electron-updater installe une mise à jour d'une application non signée. */
const PLATEFORMES_MISES_A_JOUR = new Set(['win32', 'linux']);
const TITRE_PRETE = 'Mise à jour d’ARGOS prête';

/** Texte de la notification d'une version téléchargée. */
function texteVersionPrete(version) {
  return { title: TITRE_PRETE, body: `La version ${version} sera installée à la fermeture d’ARGOS.` };
}

/** Message d'une erreur de mise à jour, quelle que soit sa forme. */
function messageErreur(erreur) {
  return erreur instanceof Error ? erreur.message : String(erreur);
}

/**
 * Mise à jour automatique.
 * @param {object} dependances
 * @param {boolean} dependances.estEmpaquete application installée (et non lancée depuis les sources)
 * @param {string} dependances.plateforme valeur de process.platform
 * @param {Record<string, string | undefined>} dependances.env variables d'environnement
 * @param {() => any} dependances.chargerMiseAJour renvoie l'autoUpdater d'electron-updater (chargé à la demande)
 * @param {(texte: {title: string, body: string}) => void} dependances.notifier notification du poste
 * @param {{error: (...valeurs: unknown[]) => void, info: (...valeurs: unknown[]) => void}} dependances.journal
 */
function creerMiseAJour({ estEmpaquete, plateforme, env, chargerMiseAJour, notifier, journal }) {
  const active = estEmpaquete && PLATEFORMES_MISES_A_JOUR.has(plateforme)
    && env[VARIABLE_DESACTIVATION] !== VALEUR_DESACTIVEE;
  return {
    active: () => active,
    /** Lance la recherche d'une version plus récente ; renvoie vrai si elle est lancée. */
    demarrer() {
      if (!active) return false;
      let miseAJour;
      try {
        miseAJour = chargerMiseAJour();
      } catch (erreur) {
        // Module absent ou incompatible : ARGOS démarre sans mise à jour automatique.
        journal.error('Mise à jour d’ARGOS indisponible :', messageErreur(erreur));
        return false;
      }
      miseAJour.autoDownload = true;
      miseAJour.autoInstallOnAppQuit = true;
      miseAJour.on('error', (erreur) => journal.error('Mise à jour d’ARGOS impossible :', messageErreur(erreur)));
      miseAJour.on('update-downloaded', (information) => {
        journal.info(`Mise à jour d’ARGOS ${information.version} téléchargée.`);
        notifier(texteVersionPrete(information.version));
      });
      // Une erreur levée dès l'appel comme une promesse rejetée est journalisée sans interrompre ARGOS.
      Promise.resolve().then(() => miseAJour.checkForUpdates()).catch((erreur) => {
        journal.error('Recherche de mise à jour d’ARGOS impossible :', messageErreur(erreur));
      });
      return true;
    },
  };
}

module.exports = { creerMiseAJour, texteVersionPrete, VARIABLE_DESACTIVATION };
