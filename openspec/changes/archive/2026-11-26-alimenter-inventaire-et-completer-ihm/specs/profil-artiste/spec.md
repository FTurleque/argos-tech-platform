# Delta de spécification : profil-artiste

## ADDED Requirements

### Requirement: ART-07 Vues de l'IHM du profil artiste
Le système DOIT (MUST) offrir dans l'IHM, web et desktop, une vue du profil artiste permettant de choisir ses disciplines et territoires suivis, de consulter les opportunités rapprochées, le résumé d'une opportunité, les sources vérifiées et les rubriques de veille de l'utilisateur, à une adresse stable par opportunité.
Source : AUD-ARC-15 ; ART-02, ART-03, ART-05 ; AUD-ARC-22. Statut : [PROPOSITION].

#### Scenario: ART-07-S1 Choix des suivis
- **GIVEN** un utilisateur connecté sans suivi
- **WHEN** il choisit une discipline et un territoire dans la vue du profil artiste puis enregistre
- **THEN** ses suivis sont enregistrés et la liste des opportunités rapprochées s'affiche

#### Scenario: ART-07-S2 Suivis requis
- **GIVEN** un utilisateur sans suivi
- **WHEN** il ouvre la vue des opportunités
- **THEN** la vue affiche le message indiquant que des suivis sont requis, sans liste

#### Scenario: ART-07-S3 Adresse d'une opportunité
- **GIVEN** une opportunité rapprochée d'identifiant O
- **WHEN** l'utilisateur ouvre l'adresse de l'opportunité O
- **THEN** la vue met en avant l'opportunité O et propose son résumé, marqué comme produit par l'IA lorsqu'il l'est
