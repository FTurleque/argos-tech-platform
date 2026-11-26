# activite-projets Specification

## Purpose
Donner une vue factuelle de l'activité des projets, chiffrée seulement quand un dénominateur existe, sans jamais mesurer les personnes. Fiche : [/docs/specifications/activite-projets.md](/docs/specifications/activite-projets.md).

## Requirements

### Requirement: PIN-01 Indicateurs factuels par projet
Le système DOIT (MUST) présenter pour un projet relié à un dépôt le nombre de tickets ouverts et fermés, de demandes de fusion ouvertes et fusionnées, l'état des jalons, les résultats des pipelines et les versions publiées sur une période.
Source : baseline v2.3 §2.4 ; arc42 §6.2. Statut : [EXIGENCE].

#### Scenario: PIN-01-S1 Projet relié
- **GIVEN** un projet relié à un dépôt GitHub
- **WHEN** l'utilisateur consulte son activité sur 30 jours
- **THEN** les compteurs de tickets, demandes de fusion, jalons, pipelines et versions sont affichés avec leur période

### Requirement: PIN-02 Pourcentage avec dénominateur explicite
Le système NE DOIT PAS (MUST NOT) afficher de pourcentage d'avancement sans dénominateur explicite, et NE DOIT PAS (MUST NOT) laisser l'IA calculer un indicateur.
Source : risque R-02 ; baseline v2.3 §2.4. Statut : [EXIGENCE].

#### Scenario: PIN-02-S1 Jalon avec total connu
- **WHEN** un jalon compte 27 tickets fermés sur 30
- **THEN** l'avancement est affiché « 27 sur 30 (90 %) »

#### Scenario: PIN-02-S2 Pas de dénominateur
- **WHEN** un projet n'a pas de jalon
- **THEN** aucun pourcentage d'avancement n'est affiché

### Requirement: PIN-03 Aucune mesure individuelle
Le système NE DOIT PAS (MUST NOT) produire, afficher ou exporter d'indicateur par personne ni de classement de personnes.
Source : risque R-09. Statut : [EXIGENCE].

#### Scenario: PIN-03-S1 Demande par personne
- **WHEN** un utilisateur cherche les indicateurs d'un contributeur précis
- **THEN** aucun indicateur par personne n'est disponible

### Requirement: PIN-04 Synthèse IA marquée
Le système DOIT (MUST) marquer comme produite par l'IA toute synthèse de l'activité et la fonder uniquement sur les indicateurs factuels affichés.
Source : IAG-05 ; baseline v2.3 §2.4. Statut : [EXIGENCE].

#### Scenario: PIN-04-S1 Synthèse affichée
- **WHEN** une synthèse de l'activité d'un projet est affichée
- **THEN** elle porte la mention « produite par l'IA » et renvoie aux indicateurs dont elle découle

### Requirement: PIN-05 Connexion en lecture seule et quotas
Le système DOIT (MUST) se connecter aux dépôts avec un jeton en lecture seule et, en cas de quota d'API épuisé, reporter la collecte en indiquant l'âge des données affichées.
Source : risque R-14 ; arc42 §6.3. Statut : [EXIGENCE].

#### Scenario: PIN-05-S1 Quota épuisé
- **GIVEN** le quota d'API GitHub épuisé
- **WHEN** la collecte d'activité s'exécute
- **THEN** elle est reportée après la réinitialisation du quota
- **AND** les indicateurs affichés indiquent la date de leur dernière mise à jour

### Requirement: PIN-06 Collecte immédiate de l'activité d'un projet
Le système DOIT (MUST) permettre à un contributeur ou à un administrateur de l'espace d'un projet de lancer, par l'API et l'IHM, la collecte immédiate de l'activité de ce projet, avec les mêmes règles de lecture seule et de quotas que la collecte planifiée (PIN-05).
Source : AUD-QUA-17 ; PIN-05. Statut : [PROPOSITION].

#### Scenario: PIN-06-S1 Collecte d'un projet doté d'un dépôt
- **GIVEN** un projet rattaché à un dépôt dont la forge répond
- **WHEN** un contributeur de l'espace lance la collecte immédiate
- **THEN** la réponse donne l'issue `A_JOUR` et les indicateurs d'activité du projet sont mis à jour

#### Scenario: PIN-06-S2 Projet sans dépôt ou quota épuisé
- **GIVEN** un projet sans dépôt, ou une forge dont le quota est épuisé
- **WHEN** un contributeur lance la collecte immédiate
- **THEN** la réponse donne l'issue `SANS_DEPOT` ou `REPORTEE_QUOTA` et les indicateurs existants sont conservés

#### Scenario: PIN-06-S3 Lecteur ou non-membre
- **GIVEN** un lecteur de l'espace du projet, ou un utilisateur non membre
- **WHEN** il lance la collecte immédiate
- **THEN** la réponse est 403 pour le lecteur et 404 pour le non-membre, refus journalisés
