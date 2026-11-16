// Jeu d'exemple servant à prévisualiser une mise en page avant de la choisir.
// Les éléments sont fictifs et affichés comme tels : l'aperçu ne lit pas la veille restituée
// par le serveur (PRF-02), il montre seulement la forme de chaque mise en page.
import type { ElementVeille } from './modeles'

export const ELEMENTS_APERCU: readonly ElementVeille[] = [
  {
    identifiant: 'exemple-1',
    titre: 'Exemple : faille critique dans une bibliothèque de journalisation',
    resume: 'Une faille permet l’exécution de code à distance. Deux projets utilisent une version concernée.',
    gravite: 'critique',
    rubriques: [{ profil: 'developpeur', identifiant: 'securite', libelle: 'Sécurité' }],
    mentions: {
      source: { nom: 'OSV (exemple)' },
      publieLe: '2026-11-14T08:00:00Z',
      niveauConfiance: { code: 'confirme', libelle: 'confirmé' },
      resumeProduitParIA: true,
      validation: null,
    },
  },
  {
    identifiant: 'exemple-2',
    titre: 'Exemple : nouvelle version majeure du framework web',
    resume: 'La version 4 abandonne Java 17. La fin de support de la branche 3 est annoncée.',
    gravite: 'moyenne',
    rubriques: [{ profil: 'developpeur', identifiant: 'versions', libelle: 'Nouvelles versions' }],
    mentions: {
      source: { nom: 'Éditeur (exemple)' },
      publieLe: '2026-11-12T10:30:00Z',
      niveauConfiance: null,
      resumeProduitParIA: false,
      validation: null,
    },
  },
  {
    identifiant: 'exemple-3',
    titre: 'Exemple : modification d’une norme déclarative',
    resume: 'Un nouveau bloc devient obligatoire au prochain trimestre. Le module paie est concerné.',
    gravite: null,
    rubriques: [
      { profil: 'expert-metier', identifiant: 'normes-declaratives', libelle: 'Normes déclaratives' },
      { profil: 'developpeur', identifiant: 'versions', libelle: 'Nouvelles versions' },
    ],
    mentions: {
      source: { nom: 'net-entreprises (exemple)' },
      publieLe: '2026-11-10T09:00:00Z',
      niveauConfiance: { code: 'probable', libelle: 'probable' },
      resumeProduitParIA: true,
      validation: { par: 'l’expert métier référent', le: '2026-11-11T16:00:00Z' },
    },
  },
]
