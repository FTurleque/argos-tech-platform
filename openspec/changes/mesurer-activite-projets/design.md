# Conception : mesurer l'activité factuelle des projets (Project Intelligence)

<!-- Statuts : [EXIGENCE] [EXISTANT] [DÉCISION] [PROPOSITION] [QUESTION] (légende dans proposal.md). -->

**Niveau de conception** : Allégée : agrégation de données d'API existantes, sans cycle de vie nouveau.

## Contexte technique

Les modules `projectintel` et `scm` ne contiennent qu'un `package-info.java` ; `ModuleBoundariesTest` autorise `projectintel` à dépendre de `inventory` et `scm` seulement [EXISTANT — test non exécuté]. Motivation : voir `proposal.md`.

## Exigences couvertes

| Exigence | Section(s) de conception | Diagramme(s) |
|---|---|---|
| PIN-01 | Composants | Composants |
| PIN-02 | Composants | Composants |
| PIN-03 | Composants | Composants |
| PIN-04 | Composants | Composants |
| PIN-05 | Composants | Composants |

## Objectifs et non-objectifs

**Objectifs :**
- agrégats sans identité de personne
- chiffres vérifiables à la source

**Non-objectifs :**
- mesure de productivité individuelle
- prévision de date de fin

## Composants impactés, responsabilités et frontières

| Composant | Responsabilité dans ce changement | Ne doit pas | Statut |
|---|---|---|---|
| `project-intel` | agréger et présenter les indicateurs | stocker ou exposer une identité de personne | modifié |
| `scm-connector` | lire les API GitHub et GitLab en lecture seule | écrire dans un dépôt | modifié |
| `ai-gateway` | synthèse marquée | calculer un indicateur | existant (proposé) |

## Modèle de domaine et invariants

Non applicable — conception allégée ; à préciser avec les tâches d'implémentation correspondantes.

## Traitements et interactions

Non applicable — conception allégée ; à préciser avec les tâches d'implémentation correspondantes.

## États et transitions

Non applicable — conception allégée ; à préciser avec les tâches d'implémentation correspondantes.

## Contrats d'interface

Non applicable — conception allégée ; à préciser avec les tâches d'implémentation correspondantes.

## Persistance et intégrité

Agrégats par projet, période et type d'événement, sans identifiant de personne ; durée de conservation à fixer (Q-2).

## Transactions, concurrence et idempotence

Non applicable — conception allégée ; à préciser avec les tâches d'implémentation correspondantes.

## Sécurité et isolation

Jeton en lecture seule, droits minimaux, hors dépôt ; agrégation avant stockage pour ne conserver aucune identité (R-09).

## Gestion des erreurs et reprise

Non applicable — conception allégée ; à préciser avec les tâches d'implémentation correspondantes.

## Observabilité

Compteurs agrégés et journaux d'audit selon arc42 §8.6 ; aucun suivi individuel des utilisateurs.

## Performance et stratégie de mesure

[QUESTION] Aucune cible propre à ce changement dans les sources ; la cible de consultation de arc42 §10 (Q-05) s'applique quand elle est pertinente.

## Compatibilité, migration et retour arrière

Non applicable — aucune donnée ni aucun contrat existants.

## Décisions, alternatives et compromis

### D1 — Agréger avant de stocker

- **Statut** : [PROPOSITION]
- **Choix** : seuls des compteurs par projet et période sont conservés
- **Justification** : impossible de produire un indicateur par personne même par erreur
- **Alternatives écartées** : stocker les événements bruts — risque R-09
- **Compromis** : pas d'analyse fine a posteriori
- **ADR** : non : décision locale

## Risques

- Métrique trompeuse (R-02) → dénominateur explicite obligatoire.
- Surveillance perçue (R-09) → aucune identité stockée.

## Écarts code / tests / specs / documentation

Aucun écart constaté.

## Stratégie de vérification

| Exigence / scénario | Niveau de test | Moyen de preuve | État |
|---|---|---|---|
| PIN-01-S1 | intégration | réponses GitHub enregistrées | à écrire |
| PIN-02-S1, PIN-02-S2 | unitaire | calcul d'avancement | à écrire |
| PIN-03-S1 | revue + test d'architecture | aucune identité dans le schéma | à écrire |
| PIN-04-S1 | intégration | mention IA | à écrire |
| PIN-05-S1 | intégration | quota simulé | à écrire |

## Documentation durable impactée

- `/docs/specifications/activite-projets.md`, registre `/docs/specifications/README.md`
- arc42 §6.2

## Questions ouvertes

Aucune autre que celles de la proposition.
