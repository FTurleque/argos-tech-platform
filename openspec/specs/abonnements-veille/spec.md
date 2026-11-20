# abonnements-veille Specification

## Purpose
Permettre à chaque utilisateur de recevoir le socle de veille de son équipe et d'y ajouter une veille personnelle, sans que ces préférences modifient ses droits d'accès. Fiche : [/docs/specifications/abonnements-veille.md](/docs/specifications/abonnements-veille.md).

## Requirements

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

### Requirement: ABO-06 Socle d'équipe administré et obligatoire ou facultatif
Le système DOIT (MUST) réserver la création d'un abonnement `TEAM` à un administrateur de l'espace de l'équipe, refuser qu'un utilisateur quitte un abonnement `TEAM` obligatoire, et permettre à un utilisateur de quitter un abonnement `TEAM` facultatif sans effet sur les autres membres.
Source : décision du porteur du projet du 2026-11-16 (question Q-5) ; ADR-0016 (Proposé). Statut : [PROPOSITION].

#### Scenario: ABO-06-S1 Création d'un abonnement TEAM par un non-administrateur
- **GIVEN** un membre de l'espace qui n'en est pas administrateur
- **WHEN** il demande la création d'un abonnement `TEAM` pour cet espace
- **THEN** la demande est refusée et le refus est journalisé
- **AND** aucun abonnement n'est créé

#### Scenario: ABO-06-S2 Abonnement TEAM obligatoire
- **GIVEN** un abonnement `TEAM` obligatoire de l'équipe d'un utilisateur
- **WHEN** l'utilisateur demande à le quitter
- **THEN** la demande est refusée
- **AND** sa veille inclut toujours les éléments de cet abonnement

#### Scenario: ABO-06-S3 Abonnement TEAM facultatif
- **GIVEN** un abonnement `TEAM` facultatif de l'équipe d'un utilisateur
- **WHEN** l'utilisateur demande à le quitter
- **THEN** l'abonnement ne s'applique plus à cet utilisateur
- **AND** il s'applique toujours aux autres membres de l'équipe

### Requirement: ABO-07 Abonnement actif ou suspendu
Le système DOIT (MUST) donner à chaque abonnement l'état `actif` ou `suspendu` ; suspendre un abonnement DOIT (MUST) conserver ses critères, son canal et sa fréquence et arrêter les notifications qui en découlent, et sa réactivation DOIT (MUST) les rétablir. Seuls le propriétaire d'un `PERSONAL` ou un administrateur de l'espace d'un `TEAM` suspendent ou réactivent.
Source : décision J7 du porteur du projet (2026-11-19, Q-ABO-1) ; ABO-06 (décision Q-5). Statut : [PROPOSITION].

#### Scenario: ABO-07-S1 Suspension d'un abonnement personnel
- **GIVEN** un abonnement `PERSONAL` actif de `jules` avec deux critères `PACKAGE` et le canal `EMAIL`
- **WHEN** `jules` suspend cet abonnement puis qu'un signal correspondant à ses critères est corrélé
- **THEN** l'abonnement est `suspendu` avec ses deux critères, son canal et sa fréquence inchangés
- **AND** aucune alerte ni mention dans un résumé n'est produite au titre de cet abonnement

#### Scenario: ABO-07-S2 Réactivation
- **GIVEN** un abonnement `PERSONAL` suspendu de `jules`
- **WHEN** `jules` le réactive puis qu'un nouveau signal correspondant est corrélé
- **THEN** l'abonnement est `actif` avec les mêmes critères
- **AND** une alerte est produite pour ce nouveau signal, et aucune pour les signaux corrélés pendant la suspension

#### Scenario: ABO-07-S3 Suspension d'un TEAM par un non-administrateur
- **GIVEN** un abonnement `TEAM` actif de l'espace A et un membre `CONTRIBUTEUR` de l'espace A
- **WHEN** ce membre demande la suspension de l'abonnement
- **THEN** la demande est refusée par une réponse 403 journalisée
- **AND** l'abonnement reste `actif`

#### Scenario: ABO-07-S4 Abonnements suspendus listés à leur propriétaire
- **GIVEN** un abonnement `PERSONAL` actif et un abonnement `PERSONAL` suspendu de `jules`
- **WHEN** `jules` consulte la liste de ses abonnements
- **THEN** les deux abonnements sont restitués, chacun avec son état

### Requirement: ABO-08 Suppression distincte de la suspension
Le système DOIT (MUST) permettre au propriétaire d'un `PERSONAL` ou à un administrateur de l'espace d'un `TEAM` de supprimer un abonnement, actif ou suspendu ; la suppression DOIT (MUST) retirer définitivement l'abonnement, ses critères et les retraits de ses membres, sans possibilité de réactivation.
Source : décision J7 du porteur du projet (2026-11-19). Statut : [PROPOSITION].

#### Scenario: ABO-08-S1 Suppression d'un abonnement suspendu
- **GIVEN** un abonnement `PERSONAL` suspendu de `jules`
- **WHEN** `jules` le supprime
- **THEN** l'abonnement n'est plus restitué dans ses abonnements
- **AND** une demande de réactivation de cet abonnement est refusée par une réponse 404

#### Scenario: ABO-08-S2 Suppression par un autre utilisateur
- **GIVEN** un abonnement `PERSONAL` de `jules`
- **WHEN** `ines` demande sa suppression
- **THEN** la demande est refusée par une réponse 404 sans révéler l'abonnement
- **AND** l'abonnement est inchangé

#### Scenario: ABO-08-S3 Suppression d'un TEAM facultatif quitté par des membres
- **GIVEN** un abonnement `TEAM` facultatif de l'espace A que deux membres ont quitté
- **WHEN** un administrateur de l'espace A le supprime
- **THEN** l'abonnement et les deux retraits sont supprimés
- **AND** aucun membre de l'espace A ne le voit plus
