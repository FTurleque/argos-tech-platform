# performance-api-mcp Specification

## Purpose
Garantir et prouver que les consultations de l'API REST et les outils du serveur MCP répondent dans la cible de latence d'arc42, hors appels externes et IA, par une mesure automatisée, gratuite et reproductible. Fiche : [/docs/specifications/performance-api-mcp.md](/docs/specifications/performance-api-mcp.md).

## Requirements

### Requirement: PERF-01 Latence p95 des consultations API et MCP
Le système DOIT (MUST) répondre aux opérations de consultation de l'API REST et aux appels des outils du serveur MCP avec un 95e centile de latence inférieur à 500 ms, mesuré sur la pile de test locale avec le jeu de données de référence de la campagne, hors opérations qui appellent une source externe ou un modèle d'IA.
Source : décision J8 du porteur du projet (2026-11-19) ; arc42 §8 (cibles initiales) ; arc42 §10.2 Q-05 ; QS-06, QS-13. Statut : [PROPOSITION].

#### Scenario: PERF-01-S1 Campagne dans la cible
- **GIVEN** la pile de test locale démarrée avec le jeu de données de référence de la campagne
- **WHEN** la campagne de mesure appelle chaque opération de consultation retenue de l'API et chaque outil MCP
- **THEN** le rapport indique pour chaque opération un p95 inférieur à 500 ms
- **AND** la campagne se termine avec le code de sortie 0

#### Scenario: PERF-01-S2 Opération hors cible
- **GIVEN** une opération dont le p95 mesuré atteint ou dépasse 500 ms
- **WHEN** la campagne se termine
- **THEN** le rapport marque cette opération « hors cible » avec ses mesures
- **AND** la campagne se termine avec un code de sortie non nul

#### Scenario: PERF-01-S3 Opérations exclues
- **GIVEN** une opération qui appelle un modèle d'IA ou une source externe (résumé IA, synchronisation d'une source)
- **WHEN** la campagne s'exécute
- **THEN** cette opération n'est pas mesurée
- **AND** le rapport la liste parmi les opérations exclues avec le motif de l'exclusion

### Requirement: PERF-02 Campagne de mesure automatisée, gratuite et versionnée
Le dépôt DOIT (MUST) fournir une commande dédiée, hors build par défaut, qui exécute la campagne de PERF-01 avec des outils gratuits sous licence permissive, sans requête hors du poste, sur un jeu à plusieurs espaces de tailles différentes (PERF-02-S4), et produit un rapport daté (révision, environnement, jeu de données, concurrence, mesures par opération).
Source : décision J8 du porteur du projet (2026-11-19) ; ADR-0021 ; critère de sortie du sprint 10. Statut : [PROPOSITION].

#### Scenario: PERF-02-S1 Rapport produit
- **GIVEN** la pile de test locale disponible
- **WHEN** un développeur exécute la commande dédiée de la campagne
- **THEN** un rapport JSON et un rapport Markdown sont écrits, datés, avec la révision Git, l'environnement, le volume de données, la concurrence et les mesures par opération

#### Scenario: PERF-02-S2 Hors build par défaut
- **GIVEN** le dépôt dans son état courant
- **WHEN** `mvn -B -f argos-server/pom.xml verify`, `npm --prefix argos-ui test` ou `npm --prefix e2e test` s'exécute
- **THEN** la campagne de performance n'est pas lancée

#### Scenario: PERF-02-S3 Aucune requête hors du poste
- **GIVEN** la campagne en cours
- **WHEN** une requête vise un hôte hors de la boucle locale
- **THEN** la campagne échoue avec un message qui nomme l'hôte
- **AND** aucun rapport n'est présenté comme valide

#### Scenario: PERF-02-S4 Jeu de données à plusieurs espaces
- **GIVEN** la campagne lancée avec ses paramètres par défaut
- **WHEN** le jeu de données est chargé
- **THEN** il compte plusieurs espaces de tailles différentes, des abonnements `PERSONAL` par paquet et par technologie, et au moins 500 000 impacts dont une majorité résolus
- **AND** le rapport décrit chaque espace et le nombre d'impacts par état

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
