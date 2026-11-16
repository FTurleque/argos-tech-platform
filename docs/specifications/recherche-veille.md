# Fiche capacité : `recherche-veille`

| Élément | Valeur |
|---|---|
| Préfixe d'exigences | `RCH` |
| Spec de référence | `/openspec/specs/recherche-veille/spec.md` — absente tant qu'aucun changement n'est archivé |
| Changements en cours | [`rechercher-et-filtrer`](/openspec/changes/rechercher-et-filtrer/proposal.md) (proposé) |
| Statut de la capacité | proposée |
| Composants (arc42 §5) | `signal`, `API & Security`, `workspace`, `mcp-server` |

## Objectif

[PROPOSITION — `rechercher-et-filtrer`] Retrouver vite l'information utile et rappeler ses recherches habituelles, sans jamais voir ce qui ne nous est pas destiné.

## Acteurs

| Acteur | Interaction | Statut |
|---|---|---|
| Utilisateur | recherche, filtre, enregistre des vues | [PROPOSITION] — ADR-0015 (Proposé) |

## Périmètre et exclusions

Inclus :

- Recherche dans le portail, le desktop et l'outil MCP `search_signals`.

Exclus :

- La recherche sémantique et les embeddings (ADR-0015 : différés).

## Préconditions et déclencheurs

Utilisateur authentifié.

## Entrées, sorties et validations

| Entrée / sortie | Contenu | Validation ou contrainte | Statut |
|---|---|---|---|
| Recherche | mots, filtres | filtres connus (RCH-02) | [PROPOSITION] |
| Vue enregistrée | nom, mots, filtres | visible de son auteur seulement (RCH-04) | [PROPOSITION] |

## Règles métier

| Règle | Exigence(s) | Source | Statut |
|---|---|---|---|
| Recherche plein texte | RCH-01 | ADR-0015 (Proposé) | [PROPOSITION] |
| Filtres combinables | RCH-02 | maquette « Fil de veille »  | [PROPOSITION] |
| Résultats limités aux droits | RCH-03 | ACC-04  | [PROPOSITION] |
| Vues enregistrées | RCH-04 | maquette « Mes vues »  | [PROPOSITION] |
| Temps de réponse | RCH-05 | arc42 §10.2 Q-05  | [PROPOSITION] |

## Droits d'accès et restrictions

Résultats limités aux espaces de l'utilisateur (RCH-03).

## Effets de bord observables

Vues enregistrées ; aucune autre donnée modifiée.

## Qualités mesurables

[QUESTION] Aucune cible chiffrée dans les sources ; voir les questions ouvertes.

## Diagrammes

### DF-RCH-1 — Une recherche filtrée

- **Question** : dans quel ordre droits, mots et filtres s'appliquent-ils ?
- **Type** : organigramme
- **Portée** : cible proposée
- **Exigences** : RCH-01, RCH-02, RCH-03

```mermaid
flowchart LR
    A["Mots et filtres"] --> B["Éléments des espaces<br/>de l'utilisateur"]
    B --> C["Correspondance<br/>plein texte"]
    C --> D["Filtres projet, technologie,<br/>type, gravité, horizon, profil"]
    D --> E["Résultats triés<br/>par pertinence"]
```

Les droits limitent d'abord l'ensemble cherché : un élément d'un autre espace n'entre jamais dans les résultats.

## Critères d'acceptation

Scénarios RCH-01-S1 à RCH-05-S1 du changement ; QS-13 (recherche).

## Interprétations introduites par un changement

- Vues strictement personnelles, sans partage (RCH-04) : à faire valider.

## Écarts constatés

Aucun écart constaté.

## Questions ouvertes

| ID | Question | Impact | Destinataire |
|---|---|---|---|
| Q-1 | Faut-il une recherche bilingue français et anglais (racines des mots) ? | répondue le 2026-11-16 : oui : recherche plein texte PostgreSQL avec les configurations `french` et `english`, sans moteur externe [DÉCISION] | Product Owner |
| Q-2 | Quel volume d'éléments viser par espace pour mesurer la performance ? | répondue le 2026-11-16 : 100 000 éléments par espace pour la mesure de performance [DÉCISION] | porteur du projet |

## Traçabilité

| Exigence | Scénarios | Conception / diagramme | Tâche | Test ou preuve |
|---|---|---|---|---|
| RCH-01 | RCH-01-S1, RCH-01-S2 | Composants | 1.1, 1.3 | à écrire |
| RCH-02 | RCH-02-S1 | Composants | 1.2 | à écrire |
| RCH-03 | RCH-03-S1 | Composants | 1.1, 1.3 | à écrire |
| RCH-04 | RCH-04-S1, RCH-04-S2 | Composants | 2.1 | à écrire |
| RCH-05 | RCH-05-S1 | Performance | 2.2 | à écrire |
