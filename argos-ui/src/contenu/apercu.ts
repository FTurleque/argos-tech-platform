// Jeu d'exemple servant à prévisualiser une mise en page avant de la choisir.
// Les éléments sont fictifs et présentés comme tels (`exemple: true`, « exemple » dans chaque titre et
// chaque source, adresses du domaine réservé example.org) : l'aperçu ne lit pas la veille restituée
// par le serveur (PRF-02), il montre seulement la forme de chaque mise en page sur un même jeu.
// Le jeu couvre chaque nature d'élément et chaque gravité de l'échelle (hors `non-classee`), un résumé
// produit par l'IA, une validation humaine avec garde-fou et mention de licence, un élément sans gravité,
// sans projet ou sans lien, un élément rangé dans deux rubriques et un élément sans rubrique, retenu par
// un seul abonnement personnel.
import type { RubriqueAffichee, VeilleAffichee } from '../mises-en-page/modele'

const SECURITE: RubriqueAffichee = { profil: 'developpeur', identifiant: 'securite', libelle: 'Sécurité' }
const VERSIONS: RubriqueAffichee = { profil: 'developpeur', identifiant: 'versions', libelle: 'Nouvelles versions' }
const NORMES: RubriqueAffichee = { profil: 'expert-metier', identifiant: 'normes-declaratives', libelle: 'Normes déclaratives' }

export const VEILLE_APERCU: VeilleAffichee = {
  exemple: true,
  composeeLe: '2026-11-15T07:00:00Z',
  rubriques: [SECURITE, VERSIONS, NORMES],
  elements: [
    {
      identifiant: 'exemple-1',
      titre: 'Exemple : faille critique dans une bibliothèque de journalisation',
      resume: 'Une faille permet l’exécution de code à distance. Deux projets utilisent une version concernée.',
      type: { code: 'avis-securite', libelle: 'Avis de sécurité' },
      gravite: { code: 'critique', libelle: 'critique' },
      rubriques: [SECURITE],
      projets: [
        { nom: 'Projet A (exemple)', niveau: { code: 'EXACT', libelle: 'correspondance exacte' } },
        { nom: 'Projet B (exemple)', niveau: { code: 'PROBABLE', libelle: 'correspondance probable' } },
      ],
      action: 'passer à la version corrigée, puis renouveler les secrets exposés.',
      mentions: {
        source: { nom: 'Base d’avis (exemple)', url: 'https://example.org/avis/exemple-1' },
        publieLe: '2026-11-14T08:00:00Z',
        niveauConfiance: { code: 'confirme', libelle: 'confirmé' },
        resumeProduitParIA: true,
        attribution: null,
        validation: null,
        gardeFous: [],
        origines: [],
      },
    },
    {
      identifiant: 'exemple-2',
      titre: 'Exemple : fin de support d’un moteur de base de données',
      resume: 'La branche utilisée par un projet n’est plus maintenue depuis le 12 novembre 2026.',
      type: { code: 'fin-de-support', libelle: 'Fin de support' },
      gravite: { code: 'moyenne', libelle: 'moyenne' },
      rubriques: [VERSIONS],
      projets: [{ nom: 'Projet A (exemple)', niveau: { code: 'EXACT', libelle: 'correspondance exacte' } }],
      action: 'planifier la montée vers la version standard.',
      mentions: {
        source: { nom: 'Calendrier de support (exemple)', url: 'https://example.org/support/exemple-2' },
        publieLe: '2026-11-12T10:30:00Z',
        niveauConfiance: null,
        resumeProduitParIA: false,
        attribution: null,
        validation: null,
        gardeFous: [],
        origines: [],
      },
    },
    {
      identifiant: 'exemple-3',
      titre: 'Exemple : modification d’une norme déclarative',
      resume: 'Un nouveau bloc devient obligatoire au prochain trimestre. Le module de paie est concerné.',
      type: { code: 'fiche-reglementaire', libelle: 'Fiche réglementaire' },
      gravite: null,
      rubriques: [NORMES, VERSIONS],
      projets: [{ nom: 'Projet C (exemple)', niveau: null }],
      action: 'demande d’évolution à spécifier avec l’architecte fonctionnel.',
      mentions: {
        source: { nom: 'Texte officiel (exemple)', url: 'https://example.org/textes/exemple-3' },
        publieLe: '2026-11-10T09:00:00Z',
        niveauConfiance: { code: 'probable', libelle: 'probable' },
        resumeProduitParIA: true,
        attribution: 'Source : éditeur fictif de textes (exemple), licence fictive (exemple).',
        validation: 'validée par l’expert métier (exemple)',
        gardeFous: ['Les fiches informent ; elles ne constituent pas un avis juridique (exemple).'],
        origines: ['Profil Expert métier (exemple)'],
      },
    },
    {
      identifiant: 'exemple-4',
      titre: 'Exemple : nouvelle version majeure d’un framework web',
      resume: 'La version 4 exige Java 21. La branche 3 reste maintenue un an.',
      type: { code: 'nouvelle-version', libelle: 'Nouvelle version' },
      gravite: { code: 'faible', libelle: 'faible' },
      rubriques: [VERSIONS],
      projets: [],
      action: null,
      mentions: {
        source: { nom: 'Notes de version (exemple)', url: 'https://example.org/versions/exemple-4' },
        publieLe: '2026-11-09T14:00:00Z',
        niveauConfiance: null,
        resumeProduitParIA: false,
        attribution: null,
        validation: null,
        gardeFous: [],
        origines: [],
      },
    },
    {
      identifiant: 'exemple-5',
      titre: 'Exemple : correctif trimestriel annoncé par un éditeur',
      resume: null,
      type: { code: 'echeance', libelle: 'Échéance' },
      gravite: null,
      rubriques: [SECURITE],
      projets: [{ nom: 'Projet B (exemple)', niveau: { code: 'EXACT', libelle: 'correspondance exacte' } }],
      action: 'réserver un créneau de mise à jour.',
      mentions: {
        source: { nom: 'Calendrier éditeur (exemple)', url: null },
        publieLe: null,
        niveauConfiance: null,
        resumeProduitParIA: false,
        attribution: null,
        validation: null,
        gardeFous: [],
        origines: [],
      },
    },
    {
      identifiant: 'exemple-6',
      titre: 'Exemple : annonce d’un éditeur suivie par un abonnement personnel',
      resume: 'L’éditeur annonce un changement de licence pour sa prochaine version.',
      type: { code: 'actualite', libelle: 'Actualité' },
      gravite: { code: 'elevee', libelle: 'élevée' },
      rubriques: [],
      projets: [],
      action: null,
      mentions: {
        source: { nom: 'Annonces éditeur (exemple)', url: 'https://example.org/annonces/exemple-6' },
        publieLe: '2026-11-08T11:00:00Z',
        niveauConfiance: null,
        resumeProduitParIA: false,
        attribution: null,
        validation: null,
        gardeFous: [],
        origines: ['Abonnement personnel (exemple)'],
      },
    },
  ],
}
