# Delta de spécification : abonnements-veille

## Purpose

Permettre à chaque utilisateur de recevoir le socle de veille de son équipe et d'y ajouter une veille personnelle, sans que ces préférences modifient ses droits d'accès. Fiche : [/docs/specifications/abonnements-veille.md](/docs/specifications/abonnements-veille.md).

## ADDED Requirements

### Requirement: ABO-01 Portée d'un abonnement
Le système DOIT (MUST) attribuer à chaque abonnement de veille exactement une portée : `TEAM`, socle défini pour une équipe, ou `PERSONAL`, veille ajoutée par un utilisateur.
Source : baseline v2.4 §4 ; arc42 §5.4 ; arc42 §8.2. Statut : [PROPOSITION].

#### Scenario: ABO-01-S1 Abonnement sans portée refusé
- **GIVEN** une demande de création d'abonnement qui ne précise pas de portée
- **WHEN** le système valide la demande
- **THEN** la création est refusée avec un message indiquant la portée manquante
- **AND** aucun abonnement n'est créé

### Requirement: ABO-02 Attributs obligatoires d'un abonnement
Le système DOIT (MUST) refuser la création d'un abonnement dépourvu de l'un des attributs suivants : portée, propriétaire (équipe ou utilisateur), filtres, canal et fréquence, caractère obligatoire ou facultatif ; il enregistre la date de création et l'état.
Source : arc42 §8.2. Statut : [PROPOSITION].

#### Scenario: ABO-02-S1 Abonnement complet
- **GIVEN** une demande d'abonnement `PERSONAL` qui précise propriétaire, filtres, canal, fréquence et caractère facultatif
- **WHEN** l'utilisateur propriétaire la soumet
- **THEN** l'abonnement est créé avec sa date de création et son état

#### Scenario: ABO-02-S2 Filtre manquant
- **GIVEN** une demande d'abonnement sans filtre
- **WHEN** le système valide la demande
- **THEN** la création est refusée avec un message indiquant l'attribut manquant
- **AND** aucun abonnement n'est créé

### Requirement: ABO-03 Socle d'équipe hérité
Le système DOIT (MUST) appliquer à chaque utilisateur les abonnements `TEAM` de l'équipe à laquelle il appartient, sans action de sa part.
Source : baseline v2.4 §4 ; arc42 §4.2 (P7). Statut : [PROPOSITION].

#### Scenario: ABO-03-S1 Nouveau membre d'équipe
- **GIVEN** une équipe disposant d'abonnements `TEAM`
- **WHEN** un utilisateur rejoint cette équipe
- **THEN** sa veille inclut les éléments correspondant aux abonnements `TEAM` de l'équipe

### Requirement: ABO-04 Veille personnelle libre
Le système DOIT (MUST) permettre à un utilisateur de créer des abonnements `PERSONAL` sur des technologies, éditeurs, thèmes, domaines ou sources sans lien avec les projets de son équipe.
Source : baseline v2.4 §4 ; arc42 §8.2. Statut : [PROPOSITION].

#### Scenario: ABO-04-S1 Technologie hors périmètre de l'équipe
- **GIVEN** un développeur de l'équipe RH dont aucun projet n'utilise Rust
- **WHEN** il crée un abonnement `PERSONAL` sur Rust
- **THEN** l'abonnement est créé
- **AND** sa veille inclut les éléments de veille externes relatifs à Rust

### Requirement: ABO-05 Aucun droit accordé par un abonnement
Le système NE DOIT PAS (MUST NOT) donner accès, au titre d'un abonnement de veille, à un projet ou à une donnée interne situés hors du périmètre autorisé de l'utilisateur ; l'autorisation reste déterminée uniquement par ses rôles.
Source : baseline v2.4 §4 ; arc42 §5.4, §5.5 (règle 6), §8.2 ; ADR-0016 (Proposé). Statut : [PROPOSITION].

#### Scenario: ABO-05-S1 Technologie utilisée par un projet d'une autre équipe
- **GIVEN** un utilisateur de l'espace A abonné à titre `PERSONAL` à une technologie utilisée par un projet de l'espace B
- **AND** un signal de sécurité qui impacte ce projet de l'espace B
- **WHEN** l'utilisateur consulte sa veille
- **THEN** le signal de sécurité public relatif à la technologie est restitué
- **AND** l'impact sur le projet de l'espace B n'est pas restitué

#### Scenario: ABO-05-S2 Filtre désignant un projet non autorisé
- **GIVEN** un utilisateur sans droit sur le projet P
- **WHEN** il crée un abonnement `PERSONAL` dont le filtre désigne le projet P
- **THEN** aucune donnée interne du projet P ne lui est restituée par cet abonnement
- **AND** ses rôles et autorisations sont inchangés
