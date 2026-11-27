# Delta de spécification : performance-api-mcp

## ADDED Requirements

### Requirement: PERF-03 Listes paginées au format commun
Le système DOIT (MUST) renvoyer toute liste de l'API REST dont la taille croît avec les données collectées, importées ou journalisées sous forme de page : paramètres `page` (à partir de 0) et `size` (50 par défaut, de 1 à 200), réponse portant `items`, `page`, `size` et `total`, avec un tri stable.
Source : /docs/audit/2026-11-21/sprints.md « Sprint 9 » (AUD-PER-10) ; arc42 §8.10 ; ADR-0040 (Proposé). Statut : [PROPOSITION].

#### Scenario: PERF-03-S1 Page par défaut
- **GIVEN** 120 événements d'exploitation pour une source
- **WHEN** un exploitant demande les événements de la source sans paramètre de page
- **THEN** la réponse contient les 50 événements les plus récents, `page` = 0, `size` = 50 et `total` = 120

#### Scenario: PERF-03-S2 Taille hors bornes ramenée
- **GIVEN** une liste paginée
- **WHEN** l'appelant demande `size=1000` puis `size=0`, et `page=-3`
- **THEN** la taille appliquée vaut 200 puis 1, la page appliquée vaut 0
- **AND** la réponse indique la taille et la page appliquées

#### Scenario: PERF-03-S3 Paramètre non entier refusé
- **GIVEN** une liste paginée
- **WHEN** l'appelant demande `size=abc`
- **THEN** la réponse est 400 avec le code `DONNEE_INVALIDE` et l'attribut `size`

#### Scenario: PERF-03-S4 Pages disjointes et stables
- **GIVEN** 60 impacts d'un projet, dont plusieurs créés au même instant
- **WHEN** l'appelant lit la page 0 puis la page 1 de taille 50
- **THEN** les deux pages ne partagent aucun impact et réunissent les 60 impacts

#### Scenario: PERF-03-S5 Listes d'exploitation et d'import paginées
- **WHEN** un exploitant lit les avis mis à l'écart ou les événements d'une source, ou un membre lit les échecs d'import, les impacts ou l'inventaire d'un projet, ou les opportunités du profil artiste
- **THEN** chaque réponse est une page au format commun
