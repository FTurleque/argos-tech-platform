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
Le dépôt DOIT (MUST) fournir une commande dédiée, exclue du build et des tests par défaut, qui exécute la campagne de PERF-01 avec des outils gratuits sous licence permissive, sans requête hors du poste, et produit un rapport versionnable daté indiquant la révision, l'environnement, le jeu de données, la concurrence et, par opération, le nombre d'appels, p50, p95 et maximum.
Source : décision J8 du porteur du projet (2026-11-19) ; ADR-0021 (licences permissives). Statut : [PROPOSITION].

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
