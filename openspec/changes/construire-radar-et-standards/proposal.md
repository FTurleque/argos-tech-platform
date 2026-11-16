# Proposition : tenir le référentiel des standards et le radar des sorties

<!-- Statuts : [EXIGENCE] [EXISTANT] [DÉCISION] [PROPOSITION] [QUESTION] — légende : /docs/specifications/README.md#statuts-des-informations. -->

## Why — Pourquoi

Les promesses « Anticiper » et « source de vérité technique » reposent sur un référentiel des standards et un radar des sorties [PROPOSITION — `/docs/specifications/README.md`, lot 4 ; baseline v2.3 §4]. Les maquettes de la version classique et des versions journal/gazette montrent déjà ce radar. Produit ouvert (ADR-0025) : chaque espace tient ses propres standards.

## What Changes — Changements

- Référentiel des technologies par espace, avec statut, version recommandée, responsable, justification et date de revue.
- Modification réservée aux rôles habilités ; l'IA peut suggérer, jamais modifier.
- Radar des sorties et fins de support sur 30 jours, 6 mois et 1 an.
- Rappels J-30, J-7 et J0 ; dette de version ; annonce des changements de standard.
- Aucune rupture de contrat (**BREAKING** : aucun) : aucune spec de référence n'existe.

## Capabilities — Capacités

### New Capabilities — Nouvelles capacités

- `radar-standards` (préfixe `RAD`) : référentiel des technologies et standards, radar des sorties et fins de support, rappels, dette de version.

### Modified Capabilities — Capacités modifiées

Aucune : aucune spec de référence n'existe.

## Périmètre

### Inclus

- Référentiel, radar, rappels, dette de version.

### Exclu

- Les sources de calendrier hors endoflife.date et calendriers éditeurs publics (question Q-2).

## Acteurs et parties prenantes

| Acteur | Rôle dans ce changement | Statut |
|---|---|---|
| Administrateur d'espace | modifie le référentiel de son espace | [PROPOSITION] — ACC-03 |
| Utilisateur | consulte standards et radar | [PROPOSITION] — PRF-03 |
| endoflife.date et calendriers éditeurs | fournissent sorties et fins de support | [PROPOSITION] — baseline v2.3 §4 |

## Sources et preuves

| Élément | Statut | Source |
|---|---|---|
| Radar versions / EOL et standards internes | [PROPOSITION] | `/docs/specifications/README.md` (lot 4) ; baseline v2.3 §4 |
| Statuts ADOPT / TRIAL / ASSESS / HOLD | [PROPOSITION] | dossier de proposition v2.4 §8.3 (hors dépôt) |
| Rappels J-30, J-7, J0 | [PROPOSITION] | dossier de proposition v2.4 §6.4 (hors dépôt) |
| Référentiel par espace | [DÉCISION] | ADR-0025 (Accepté) |
| Socle applicatif sans code métier | [EXISTANT] | `README.md` §Statut ; `argos-server/src/main/java/io/argos/` |

## Écarts constatés

- Les statuts du référentiel et les rappels ne figurent que dans le dossier de proposition v2.4, hors dépôt : à reporter en arc42 §8 (tâche 3.1).

## Impact

- **Composants** (arc42 §5) : `catalog`, `release-radar`, `impact-engine`, `alerting`
- **Contrats d'interface** : OpenAPI du référentiel et du radar ; outils MCP `get_technology_standard`, `upcoming_releases`
- **Données et persistance** : technologies, statuts et historique, échéances, rappels
- **Sécurité, confidentialité, droits** : modification réservée aux rôles habilités
- **Exploitation et observabilité** : fraîcheur de la source endoflife.date
- **Documentation durable à mettre à jour** : `/docs/specifications/radar-standards.md`, registre `/docs/specifications/README.md`, arc42 §8
- **ADR** : aucun nouvel ADR requis

## Conception requise

Allégée : modules existants, une source externe déjà prévue, pas de nouveau cycle de vie complexe.

## Questions ouvertes

| ID | Question | Bloquante ? | Destinataire |
|---|---|---|---|
| Q-1 | Les rappels J-30, J-7, J0 sont-ils fixes ou paramétrables par espace ? | non | Product Owner |
| Q-2 | Quelles sources de calendrier en plus d'endoflife.date ? | non | porteur du projet |
| Q-3 | Un espace peut-il partir d'un référentiel modèle publié par Argos ? | non | Product Owner |
