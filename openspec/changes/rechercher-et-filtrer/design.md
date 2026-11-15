# Conception : rechercher et filtrer la veille, et enregistrer des vues

<!-- Statuts : [EXIGENCE] [EXISTANT] [DÉCISION] [PROPOSITION] [QUESTION] (légende dans proposal.md). -->

**Niveau de conception** : Allégée : recherche sur la base existante, sans nouvelle dépendance.

## Contexte technique

Aucun index ni recherche n'existe ; la base est PostgreSQL 18 (`compose.yaml`) [EXISTANT]. Motivation : voir `proposal.md`.

## Exigences couvertes

| Exigence | Section(s) de conception | Diagramme(s) |
|---|---|---|
| RCH-01 | Composants | Composants |
| RCH-02 | Composants | Composants |
| RCH-03 | Composants | Composants |
| RCH-04 | Composants | Composants |
| RCH-05 | Composants | Performance |

## Objectifs et non-objectifs

**Objectifs :**
- recherche et filtres en une requête respectant la RLS
- vues personnelles simples

**Non-objectifs :**
- recherche sémantique (ADR-0015)

## Composants impactés, responsabilités et frontières

| Composant | Responsabilité dans ce changement | Ne doit pas | Statut |
|---|---|---|---|
| `signal` | index plein texte des éléments | contourner la RLS | modifié |
| `API & Security` | requête de recherche et filtres | — | modifié |
| `workspace` | vues enregistrées | partager une vue sans décision explicite | modifié |

## Modèle de domaine et invariants

Non applicable — conception allégée ; à préciser avec les tâches d'implémentation correspondantes.

## Traitements et interactions

Non applicable — conception allégée ; à préciser avec les tâches d'implémentation correspondantes.

## États et transitions

Non applicable — conception allégée ; à préciser avec les tâches d'implémentation correspondantes.

## Contrats d'interface

Non applicable — conception allégée ; à préciser avec les tâches d'implémentation correspondantes.

## Persistance et intégrité

Non applicable — conception allégée ; à préciser avec les tâches d'implémentation correspondantes.

## Transactions, concurrence et idempotence

Non applicable — conception allégée ; à préciser avec les tâches d'implémentation correspondantes.

## Sécurité et isolation

La recherche s'exécute avec l'espace courant (RLS) : un extrait d'un autre espace ne peut pas être renvoyé (R-17).

## Gestion des erreurs et reprise

Non applicable — conception allégée ; à préciser avec les tâches d'implémentation correspondantes.

## Observabilité

Compteurs agrégés et journaux d'audit selon arc42 §8.6 ; aucun suivi individuel des utilisateurs.

## Performance et stratégie de mesure

Cible : celle de arc42 §10.2 Q-05 ; mesure par campagne de charge sur un jeu au volume Q-2, exécution de QS-13.

## Compatibilité, migration et retour arrière

Non applicable — aucune donnée ni aucun contrat existants.

## Décisions, alternatives et compromis

### D1 — Recherche plein texte PostgreSQL

- **Statut** : [PROPOSITION]
- **Choix** : index plein texte dans la base existante
- **Justification** : aucune dépendance de plus, RLS appliquée
- **Alternatives écartées** : moteur dédié — synchronisation et droits à dupliquer
- **Compromis** : pertinence moindre à grande échelle (R-16)
- **ADR** : ADR-0015 (Proposé)

## Risques

- Limites de PostgreSQL à grande échelle (R-16) → mesure QS-13 avant tout autre moteur.

## Écarts code / tests / specs / documentation

Aucun écart constaté.

## Stratégie de vérification

| Exigence / scénario | Niveau de test | Moyen de preuve | État |
|---|---|---|---|
| RCH-01-S1, RCH-01-S2 | intégration | jeu de données de test | à écrire |
| RCH-02-S1 | intégration | filtres combinés | à écrire |
| RCH-03-S1 | intégration | inter-espaces | à écrire |
| RCH-04-S1, RCH-04-S2 | intégration API | vues | à écrire |
| RCH-05-S1 | charge | QS-13 | à écrire |

## Documentation durable impactée

- `/docs/specifications/recherche-veille.md`, registre `/docs/specifications/README.md`
- arc42 §8.10

## Questions ouvertes

Aucune autre que celles de la proposition.
