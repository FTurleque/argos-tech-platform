# Delta de spécification : profils-veille

## MODIFIED Requirements

### Requirement: PRF-07 Restitution de « Ma veille » dans la cible de latence
Le système DOIT (MUST) restituer la veille d'un utilisateur (`GET /api/v1/me/veille`) avec un p95 inférieur à 500 ms, mesuré par la campagne de performance, y compris pour le membre d'un petit espace dans une table d'impacts volumineuse et avec des abonnements `PERSONAL`, sans modifier le contenu restitué (éléments, champs, ordre, droits : PRF-02, PRF-05).
Source : décisions K2 et K3 du porteur du projet (2026-11-19) ; arc42 §8, §10.2 Q-05 ; PERF-01 ; AUD-PER-06, AUD-PER-26. Statut : [PROPOSITION].

#### Scenario: PRF-07-S1 Ma veille dans la cible avec le jeu de référence
- **GIVEN** la pile de test locale chargée du jeu de référence : un espace de 20 projets de 100 composants, 1 000 avis et 20 000 impacts
- **AND** un utilisateur membre de cet espace, au profil actif `developpeur`, avec un abonnement `PERSONAL`
- **WHEN** la campagne de performance appelle `GET /api/v1/me/veille` 200 fois avec 4 clients simultanés, après échauffement
- **THEN** le rapport indique pour cette opération un p95 inférieur à 500 ms

#### Scenario: PRF-07-S2 Contenu restitué inchangé
- **GIVEN** des avis aux dates distinctes, dont un sans date de publication, qui impactent plusieurs projets de l'espace d'un utilisateur
- **AND** un impact écarté et un projet d'un espace dont l'utilisateur n'est pas membre
- **WHEN** l'utilisateur consulte sa veille
- **THEN** les éléments restitués, leurs champs et leur ordre sont ceux de l'union de ses profils actifs (PRF-02), du plus récent au plus ancien
- **AND** aucun impact écarté ni aucun projet d'un espace dont il n'est pas membre n'est restitué

#### Scenario: PRF-07-S3 Petit espace dans une grande table
- **GIVEN** une table d'au moins 500 000 impacts, majoritairement résolus, répartis entre plusieurs espaces de tailles différentes
- **AND** un utilisateur membre d'un seul espace qui compte moins de 200 impacts ouverts ou confirmés, avec des abonnements `PERSONAL` par paquet et par technologie
- **WHEN** la campagne de performance appelle `GET /api/v1/me/veille` pour cet utilisateur
- **THEN** le rapport indique pour cette opération un p95 inférieur à 500 ms


## ADDED Requirements

### Requirement: PRF-09 Ma veille paginée au format commun
Le système DOIT (MUST) restituer la veille d'un utilisateur (`GET /api/v1/me/veille`) par pages au format commun des listes, découpées dans l'union de PRF-02 triée par date décroissante, puis type et identifiant ; l'interface affiche le total et passe d'une page à l'autre.
Source : AUD-PER-06 ; ADR-0040 ; PERF-03 ; décisions du porteur du projet (2026-11-27). Statut : [PROPOSITION].

#### Scenario: PRF-09-S1 Première page par défaut
- **GIVEN** un utilisateur dont la veille compte 60 éléments
- **WHEN** il appelle `GET /api/v1/me/veille` sans paramètre de page
- **THEN** la réponse contient les 50 éléments les plus récents, `page` = 0, `size` = 50 et `total` = 60
- **AND** `profilsActifs` liste ses profils actifs

#### Scenario: PRF-09-S2 Pages disjointes et stables
- **GIVEN** une veille de 60 éléments, dont plusieurs à la même date
- **WHEN** l'utilisateur lit la page 0 puis la page 1 de taille 50
- **THEN** les deux pages ne partagent aucun élément et réunissent, dans l'ordre, les éléments de la veille lue en une page de 200

#### Scenario: PRF-09-S3 Paramètre non entier refusé
- **WHEN** l'utilisateur appelle `GET /api/v1/me/veille?size=abc`
- **THEN** la réponse est 400 avec le code `DONNEE_INVALIDE` et l'attribut `size`

#### Scenario: PRF-09-S4 Interface paginée
- **GIVEN** une veille de plus de 50 éléments
- **WHEN** l'utilisateur ouvre « Ma veille » puis demande la page suivante
- **THEN** l'interface indique le nombre total d'éléments et la position de la page, puis affiche les éléments de la page suivante dans sa mise en page
