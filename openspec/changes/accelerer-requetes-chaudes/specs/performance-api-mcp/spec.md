# Delta de spécification : performance-api-mcp

## MODIFIED Requirements

### Requirement: PERF-02 Campagne de mesure automatisée, gratuite et versionnée
Le dépôt DOIT (MUST) fournir une commande dédiée, exclue du build et des tests par défaut, qui exécute la campagne de PERF-01 avec des outils gratuits sous licence permissive, sans requête hors du poste, sur un jeu à plusieurs espaces de tailles différentes avec abonnements et impacts résolus, et produit un rapport daté (révision, environnement, jeu de données, concurrence ; par opération : appels, p50, p95, maximum).
Source : décision J8 du porteur du projet (2026-11-19) ; ADR-0021 (licences permissives) ; critère de sortie du sprint 10. Statut : [PROPOSITION].

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
