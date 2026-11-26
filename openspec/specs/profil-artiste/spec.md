# profil-artiste Specification

## Purpose
Offrir aux artistes une veille des appels, aides, droits et événements qui correspondent à leur discipline et à leur territoire, avec leurs dates limites. Fiche : [/docs/specifications/profil-artiste.md](/docs/specifications/profil-artiste.md).

## Requirements

### Requirement: ART-01 Pack artiste au catalogue
Le système DOIT (MUST) publier au catalogue un profil `artiste` dont les rubriques sont : appels à projets et résidences, aides à la création, droits et rémunérations, statut social et fiscal, événements.
Source : ADR-0024 (Proposé) ; PRF-04. Statut : [EXIGENCE].

#### Scenario: ART-01-S1 Activation
- **GIVEN** un utilisateur dont le seul profil est `developpeur`
- **WHEN** il active le profil `artiste`
- **THEN** sa veille propose les cinq rubriques artiste en plus des siennes

### Requirement: ART-02 Disciplines et territoires suivis
Le système DOIT (MUST) demander à un utilisateur du profil `artiste` au moins une discipline et un territoire suivis, choisis dans les listes du pack.
Source : ADR-0024 (Proposé) ; questions Q-2, Q-3. Statut : [EXIGENCE].

#### Scenario: ART-02-S1 Choix enregistrés
- **WHEN** l'utilisateur choisit « arts visuels » et « Occitanie »
- **THEN** ses choix sont enregistrés

#### Scenario: ART-02-S2 Discipline hors liste
- **WHEN** l'utilisateur saisit une discipline absente de la liste du pack
- **THEN** la saisie est refusée avec la liste des disciplines disponibles

### Requirement: ART-03 Rapprochement par discipline, territoire et date limite
Le système DOIT (MUST) restituer un appel à un utilisateur du profil `artiste` lorsque sa discipline et son territoire correspondent à ceux suivis et que sa date limite n'est pas dépassée.
Source : ADR-0024 (Proposé) ; ADR-0013 (Proposé). Statut : [EXIGENCE].

#### Scenario: ART-03-S1 Appel correspondant
- **GIVEN** un utilisateur suivant « arts visuels » en « Occitanie »
- **WHEN** un appel à résidence en arts visuels en Occitanie est collecté, date limite dans 20 jours
- **THEN** l'appel lui est restitué avec sa date limite

#### Scenario: ART-03-S2 Date limite dépassée
- **GIVEN** le même utilisateur
- **WHEN** un appel correspondant est collecté avec une date limite déjà passée
- **THEN** l'appel ne lui est pas présenté comme une opportunité ouverte

### Requirement: ART-04 Rappel avant date limite
Le système DOIT (MUST) rappeler à l'utilisateur la date limite d'un appel qui lui a été restitué, aux échéances configurées.
Source : ALR-04 ; question Q-4. Statut : [EXIGENCE].

#### Scenario: ART-04-S1 Rappel
- **GIVEN** un appel restitué dont la date limite approche
- **WHEN** l'échéance de rappel est atteinte
- **THEN** l'utilisateur reçoit un rappel avec la date limite et le lien vers l'appel officiel

### Requirement: ART-05 Sources vérifiées
Le système NE DOIT PAS (MUST NOT) collecter une source du profil `artiste` dont les conditions d'usage n'ont pas été vérifiées et enregistrées par l'administrateur plateforme.
Source : ADR-0024 (Proposé) point 8 ; ADR-0025 (Accepté). Statut : [EXIGENCE].

#### Scenario: ART-05-S1 Source non vérifiée
- **GIVEN** une source ajoutée au pack sans conditions d'usage enregistrées
- **WHEN** la collecte s'exécute
- **THEN** cette source n'est pas collectée
- **AND** son état indique « conditions d'usage à vérifier »

### Requirement: ART-06 Aucun avis d'éligibilité
Le système NE DOIT PAS (MUST NOT) se prononcer sur l'éligibilité d'un utilisateur à un appel ou à une aide ; il renvoie au texte officiel.
Source : ADR-0024 (Proposé) point 7. Statut : [EXIGENCE].

#### Scenario: ART-06-S1 Résumé d'un appel
- **WHEN** le résumé d'un appel est affiché
- **THEN** il présente les critères tels qu'énoncés et renvoie au texte officiel
- **AND** il ne dit pas si l'utilisateur est éligible

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
