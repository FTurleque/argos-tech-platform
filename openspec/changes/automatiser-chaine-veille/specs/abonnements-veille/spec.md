# Delta de spécification : abonnements-veille

## Purpose

Permettre à chaque utilisateur de recevoir le socle de veille de son équipe et d'y ajouter une veille personnelle, sans que ces préférences modifient ses droits d'accès. Fiche : [/docs/specifications/abonnements-veille.md](/docs/specifications/abonnements-veille.md).

## ADDED Requirements

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
