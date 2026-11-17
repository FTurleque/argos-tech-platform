# comptes-acces Specification

## Purpose
Garantir que seuls des utilisateurs authentifiés accèdent à Argos, chacun dans les espaces dont il est membre et avec le rôle qui lui est attribué. Fiche : [/docs/specifications/comptes-acces.md](/docs/specifications/comptes-acces.md).

## Requirements

### Requirement: ACC-01 Authentification obligatoire
Le système DOIT (MUST) exiger une session authentifiée par OIDC pour toute lecture ou modification de veille, d'impacts, d'abonnements ou de préférences ; seuls l'état de santé et l'identité du serveur restent publics.
Source : ADR-0008 (Proposé) ; ADR-0025 (Accepté). Statut : [EXIGENCE].

#### Scenario: ACC-01-S1 Accès sans session
- **WHEN** une requête sans session demande la veille d'un utilisateur
- **THEN** la requête est refusée comme non authentifiée
- **AND** aucune donnée de veille n'est renvoyée

#### Scenario: ACC-01-S2 Point technique public
- **WHEN** une requête sans session demande l'identité du serveur
- **THEN** la réponse indique le nom et la version du serveur, sans donnée d'utilisateur

### Requirement: ACC-02 Création du compte à la première connexion
Le système DOIT (MUST) créer le compte d'un utilisateur à sa première connexion OIDC réussie, sans lui donner accès à aucun espace existant.
Source : ADR-0025 (Accepté) ; ADR-0016 (Proposé). Statut : [EXIGENCE].

#### Scenario: ACC-02-S1 Première connexion
- **GIVEN** une personne sans compte Argos
- **WHEN** elle se connecte avec succès auprès du fournisseur d'identité
- **THEN** un compte Argos est créé à son nom
- **AND** elle n'est membre d'aucun espace existant

#### Scenario: ACC-02-S2 Connexion refusée par le fournisseur
- **GIVEN** une personne sans compte Argos
- **WHEN** le fournisseur d'identité refuse son authentification
- **THEN** la connexion échoue avec un message explicite
- **AND** aucun compte n'est créé

### Requirement: ACC-03 Espaces et rôles
Le système DOIT (MUST) permettre à un utilisateur de créer un espace dont il devient administrateur, et n'autoriser la gestion des rôles d'un espace qu'à ses administrateurs.
Source : ADR-0016 (Proposé) ; ADR-0025 (Accepté). Statut : [EXIGENCE].

#### Scenario: ACC-03-S1 Création d'un espace
- **GIVEN** un utilisateur connecté
- **WHEN** il crée l'espace « Atelier »
- **THEN** l'espace est créé
- **AND** l'utilisateur en est administrateur

#### Scenario: ACC-03-S2 Gestion des rôles par un contributeur
- **GIVEN** un contributeur de l'espace « Atelier »
- **WHEN** il tente de nommer un autre membre administrateur
- **THEN** la demande est refusée comme interdite
- **AND** les rôles de l'espace sont inchangés

### Requirement: ACC-04 Cloisonnement par espace
Le système NE DOIT PAS (MUST NOT) restituer une donnée d'un espace à un utilisateur qui n'en est pas membre, que la demande passe par l'API, le serveur MCP ou une requête en base sans contexte d'espace.
Source : ADR-0016 (Proposé) ; exigence COR-08 ; risque R-17. Statut : [EXIGENCE].

#### Scenario: ACC-04-S1 Lecture d'un autre espace
- **GIVEN** un utilisateur membre du seul espace A
- **WHEN** il demande un impact de l'espace B
- **THEN** la demande est refusée
- **AND** aucune donnée de l'espace B ne lui est renvoyée

#### Scenario: ACC-04-S2 Requête en base sans contexte
- **GIVEN** une requête sur les impacts exécutée sans espace courant
- **WHEN** la base l'exécute
- **THEN** aucune ligne n'est renvoyée

### Requirement: ACC-05 Invitations et révocation
Le système DOIT (MUST) permettre à un administrateur d'espace d'inviter un utilisateur avec un rôle et de révoquer son accès, la révocation s'appliquant dès la requête suivante.
Source : ADR-0016 (Proposé). Statut : [EXIGENCE].

#### Scenario: ACC-05-S1 Invitation acceptée
- **GIVEN** un administrateur de l'espace « Atelier »
- **WHEN** il invite un utilisateur comme lecteur et celui-ci accepte
- **THEN** l'utilisateur voit les données de l'espace en lecture seule

#### Scenario: ACC-05-S2 Révocation
- **GIVEN** un lecteur de l'espace « Atelier »
- **WHEN** l'administrateur révoque son accès
- **THEN** sa requête suivante sur l'espace est refusée
- **AND** il ne figure plus parmi les membres

### Requirement: ACC-06 Journal des refus d'accès
Le système DOIT (MUST) journaliser chaque refus d'accès avec l'utilisateur, la ressource demandée et l'horodatage, sans recopier le contenu de la ressource.
Source : ADR-0016 (Proposé) ; arc42 §8.1. Statut : [EXIGENCE].

#### Scenario: ACC-06-S1 Refus journalisé
- **WHEN** un utilisateur se voit refuser l'accès à une ressource d'un autre espace
- **THEN** une entrée de journal enregistre l'utilisateur, la ressource et l'heure
- **AND** l'entrée ne contient aucune donnée de la ressource
