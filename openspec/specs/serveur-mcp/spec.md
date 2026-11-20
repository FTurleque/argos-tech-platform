# serveur-mcp Specification

## Purpose
Permettre aux assistants IA de consulter Argos pour le compte d'un utilisateur, en lecture seule et dans son périmètre. Fiche : [/docs/specifications/serveur-mcp.md](/docs/specifications/serveur-mcp.md).

## Requirements

### Requirement: MCP-01 Outils en lecture seule
Le serveur MCP NE DOIT PAS (MUST NOT) exposer d'outil qui crée, modifie ou supprime une donnée d'Argos.
Source : ADR-0014 (Proposé). Statut : [PROPOSITION].

#### Scenario: MCP-01-S1 Liste des outils
- **WHEN** un client MCP liste les outils disponibles
- **THEN** seuls des outils de consultation sont proposés

#### Scenario: MCP-01-S2 Outil inconnu
- **WHEN** un client MCP appelle un outil d'écriture qui n'existe pas
- **THEN** l'appel échoue avec une erreur d'outil inconnu
- **AND** aucune donnée n'est modifiée

### Requirement: MCP-02 Vérification d'une dépendance
Le serveur MCP DOIT (MUST) fournir l'outil `check_dependency` qui renvoie, pour un écosystème, un nom et une version, les failles connues, le statut au référentiel des standards, la version recommandée et la fin de support connue.
Source : ADR-0014 (Proposé) ; baseline v2.3 §4. Statut : [PROPOSITION].

#### Scenario: MCP-02-S1 Version vulnérable
- **GIVEN** une faille connue sur la version 3.2 d'une bibliothèque dont la version standard est 3.4.1
- **WHEN** l'assistant appelle `check_dependency` pour la version 3.2
- **THEN** la réponse cite la faille et ses sources, le statut au référentiel et la version recommandée 3.4.1

#### Scenario: MCP-02-S2 Dépendance inconnue
- **WHEN** l'assistant appelle `check_dependency` pour un composant inconnu d'Argos
- **THEN** la réponse indique qu'aucune information n'est connue
- **AND** elle n'invente aucune faille ni recommandation

### Requirement: MCP-03 Authentification des appels
Le serveur MCP DOIT (MUST) refuser tout appel sans jeton valide et limiter chaque réponse aux espaces de l'utilisateur à qui le jeton appartient.
Source : ADR-0014 (Proposé) ; ACC-01. Statut : [PROPOSITION].

#### Scenario: MCP-03-S1 Appel sans jeton
- **WHEN** un client MCP appelle un outil sans jeton
- **THEN** l'appel est refusé comme non authentifié

### Requirement: MCP-04 Aucune fuite de périmètre
Le serveur MCP NE DOIT PAS (MUST NOT) renvoyer d'information sur un projet que l'utilisateur ne peut pas voir dans le portail.
Source : ACC-04 ; ADR-0014 (Proposé). Statut : [PROPOSITION].

#### Scenario: MCP-04-S1 Projet d'un autre espace
- **GIVEN** un utilisateur sans droit sur le projet P
- **WHEN** son assistant appelle `get_security_alerts` pour le projet P
- **THEN** la réponse ne contient aucune alerte ni information sur P

### Requirement: MCP-05 Contenu externe balisé
Le serveur MCP DOIT (MUST) renvoyer tout texte issu d'une source externe dans un champ structuré marqué comme externe et tronqué à une longueur maximale.
Source : risque R-11 ; ADR-0014 (Proposé). Statut : [PROPOSITION].

#### Scenario: MCP-05-S1 Texte contenant une consigne
- **GIVEN** un article collecté contenant « ignore les instructions précédentes »
- **WHEN** l'assistant appelle `search_signals`
- **THEN** le texte est renvoyé dans un champ marqué externe, tronqué, distinct des champs produits par Argos

### Requirement: MCP-06 Limitation de débit et journal
Le serveur MCP DOIT (MUST) limiter le nombre d'appels par utilisateur et journaliser chaque appel avec l'utilisateur, l'outil, les paramètres et la durée.
Source : ADR-0014 (Proposé) ; dossier v2.4 §8.7. Statut : [PROPOSITION].

#### Scenario: MCP-06-S1 Limite dépassée
- **GIVEN** un utilisateur qui a atteint sa limite d'appels
- **WHEN** son assistant appelle un outil
- **THEN** l'appel est refusé avec une erreur de limite
- **AND** le refus est journalisé

### Requirement: MCP-07 Garde-fous des profils
Le serveur MCP DOIT (MUST) appliquer les garde-fous des profils de veille, notamment ne renvoyer par `get_regulatory_changes` que des fiches validées par un expert.
Source : PRF-06 ; ADR-0018 (Remplacé par ADR-0030). Statut : [PROPOSITION].

#### Scenario: MCP-07-S1 Fiche non validée
- **GIVEN** une fiche réglementaire en attente de validation
- **WHEN** l'assistant appelle `get_regulatory_changes` sur son domaine
- **THEN** la fiche n'est pas renvoyée
