# Delta de spécification : alertes-digests

## ADDED Requirements

### Requirement: ALR-09 Liens des alertes vers des vues de l'IHM
Le système DOIT (MUST) construire chaque lien d'une alerte, d'un résumé ou d'un rappel à partir de l'adresse publique de la plateforme, au format d'adresse de l'IHM, vers une vue existante : le signal, le radar de l'espace ou l'opportunité concernés ; un lien NE DOIT PAS (MUST NOT) viser un chemin que l'IHM ne sert pas.
Source : AUD-ARC-22 ; ALR-06 ; ADR-0038. Statut : [PROPOSITION].

#### Scenario: ALR-09-S1 Lien d'un signal
- **GIVEN** l'adresse publique `https://argos.example` et une alerte sur le signal S
- **WHEN** l'alerte est produite
- **THEN** le lien de son élément vaut `https://argos.example/#/signaux/S`
- **AND** ouvert dans l'IHM, il affiche la vue du signal S après connexion

#### Scenario: ALR-09-S2 Liens du radar et des opportunités
- **GIVEN** la même adresse publique
- **WHEN** un rappel du radar de l'espace E ou un rappel d'opportunité O est produit
- **THEN** le lien vaut `https://argos.example/#/espaces/E/radar` ou `https://argos.example/#/profils/artiste/opportunites/O`

#### Scenario: ALR-09-S3 Gabarits couverts par l'IHM
- **GIVEN** les gabarits de lien produits par le serveur
- **WHEN** la suite de tests de l'IHM s'exécute
- **THEN** chaque gabarit correspond à une route déclarée de l'IHM, sinon la suite échoue
