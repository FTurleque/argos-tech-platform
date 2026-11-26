# Delta de spécification : activite-projets

## ADDED Requirements

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
