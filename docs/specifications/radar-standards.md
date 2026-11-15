# Fiche capacité : `radar-standards`

| Élément | Valeur |
|---|---|
| Préfixe d'exigences | `RAD` |
| Spec de référence | `/openspec/specs/radar-standards/spec.md` — absente tant qu'aucun changement n'est archivé |
| Changements en cours | [`construire-radar-et-standards`](/openspec/changes/construire-radar-et-standards/proposal.md) (proposé) |
| Statut de la capacité | proposée |
| Composants (arc42 §5) | `catalog`, `release-radar`, `impact-engine`, `alerting` |

## Objectif

[PROPOSITION — `construire-radar-et-standards`] Être la source de vérité d'un espace sur « ce que nous utilisons et comment », et voir venir les sorties et fins de support.

## Acteurs

| Acteur | Interaction | Statut |
|---|---|---|
| Administrateur d'espace | modifie le référentiel de son espace | [PROPOSITION] — ACC-03 |
| Utilisateur | consulte standards et radar | [PROPOSITION] — PRF-03 |
| endoflife.date et calendriers éditeurs | fournissent sorties et fins de support | [PROPOSITION] — baseline v2.3 §4 |

## Périmètre et exclusions

Inclus :

- Référentiel, radar, rappels, dette de version.

Exclus :

- Les sources de calendrier hors endoflife.date et calendriers éditeurs publics (question Q-2).

## Préconditions et déclencheurs

Inventaires des projets disponibles (`inventaire-sbom`).

## Entrées, sorties et validations

| Entrée / sortie | Contenu | Validation ou contrainte | Statut |
|---|---|---|---|
| Entrée du référentiel | technologie, statut, version, responsable, justification, revue | champs obligatoires (RAD-01) | [PROPOSITION] |
| Échéance | produit, version, date, type | source publique citée | [PROPOSITION] |

## Règles métier

| Règle | Exigence(s) | Source | Statut |
|---|---|---|---|
| Entrée du référentiel | RAD-01 | dossier v2.4 §8.3  | [PROPOSITION] |
| Gouvernance du référentiel | RAD-02 | dossier v2.4 §8.3  | [PROPOSITION] |
| Radar par horizon | RAD-03 | baseline v2.3 §4  | [PROPOSITION] |
| Rappels d'échéance | RAD-04 | dossier v2.4 §6.4 | [PROPOSITION] |
| Dette de version | RAD-05 | dossier v2.4 §6.4  | [PROPOSITION] |
| Annonce d'un changement de standard | RAD-06 | dossier v2.4 §8.2 (type STANDARD) | [PROPOSITION] |

## Droits d'accès et restrictions

Modification par les administrateurs d'espace ; consultation par les membres.

## Effets de bord observables

Rappels, annonces, impacts « dette de version ».

## Qualités mesurables

[QUESTION] Aucune cible chiffrée dans les sources ; voir les questions ouvertes.

## Diagrammes

### DF-RAD-1 — Statuts du référentiel

- **Question** : que signifie chaque statut d'une technologie ?
- **Type** : organigramme
- **Portée** : cible proposée
- **Exigences** : RAD-01, RAD-02

```mermaid
flowchart LR
    AS["ASSESS<br/>à étudier"] --> TR["TRIAL<br/>projets pilotes"]
    TR --> AD["ADOPT<br/>standard recommandé"]
    AD --> HO["HOLD<br/>ne plus introduire"]
    TR --> HO
```

Les statuts suivent le principe d'un radar technologique ; chaque changement est justifié et annoncé aux projets concernés.

## Critères d'acceptation

Scénarios RAD-01-S1 à RAD-06-S1 du changement ; aucun scénario qualité transverse supplémentaire.

## Interprétations introduites par un changement

- Rappels aux seuls utilisateurs des projets concernés (RAD-04) : à faire valider.

## Écarts constatés

- Les statuts du référentiel et les rappels ne figurent que dans le dossier de proposition v2.4, hors dépôt : à reporter en arc42 §8 (tâche 3.1).

## Questions ouvertes

| ID | Question | Impact | Destinataire |
|---|---|---|---|
| Q-1 | Les rappels J-30, J-7, J0 sont-ils fixes ou paramétrables par espace ? | non | Product Owner |
| Q-2 | Quelles sources de calendrier en plus d'endoflife.date ? | non | porteur du projet |
| Q-3 | Un espace peut-il partir d'un référentiel modèle publié par Argos ? | non | Product Owner |

## Traçabilité

| Exigence | Scénarios | Conception / diagramme | Tâche | Test ou preuve |
|---|---|---|---|---|
| RAD-01 | RAD-01-S1, RAD-01-S2 | Composants | 1.1 | à écrire |
| RAD-02 | RAD-02-S1, RAD-02-S2 | Composants | 1.2 | à écrire |
| RAD-03 | RAD-03-S1 | DC-1 | 2.1 | à écrire |
| RAD-04 | RAD-04-S1 | DC-1 | 2.2 | à écrire |
| RAD-05 | RAD-05-S1 | DC-1 | 2.3 | à écrire |
| RAD-06 | RAD-06-S1 | Composants | 1.3 | à écrire |
