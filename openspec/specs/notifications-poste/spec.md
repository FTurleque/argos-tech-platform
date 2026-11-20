# notifications-poste Specification

## Purpose
Faire apparaître sur le poste de l'utilisateur les alertes immédiates qui le concernent et lui permettre d'y accéder en un clic. Fiche : [/docs/specifications/notifications-poste.md](/docs/specifications/notifications-poste.md).

## Requirements

### Requirement: NOT-01 Notification d'une alerte immédiate
Le client desktop DOIT (MUST) afficher une notification système pour chaque alerte immédiate destinée à l'utilisateur connecté, que sa fenêtre soit visible ou masquée.
Source : ADR-0022 (Proposé) ; ALR-06. Statut : [PROPOSITION].

#### Scenario: NOT-01-S1 Fenêtre masquée
- **GIVEN** le client desktop ouvert, fenêtre masquée dans la zone de notification
- **WHEN** une alerte immédiate est émise pour l'utilisateur
- **THEN** une notification système s'affiche

#### Scenario: NOT-01-S2 Notifications refusées par le système
- **GIVEN** les notifications d'Argos refusées dans les réglages du système
- **WHEN** une alerte immédiate est émise
- **THEN** aucune notification ne s'affiche
- **AND** l'alerte est comptée dans la zone de notification et visible dans l'application

### Requirement: NOT-02 Ouverture de l'élément au clic
Le client desktop DOIT (MUST) afficher l'élément concerné lorsque l'utilisateur clique sur une notification.
Source : ADR-0022 (Proposé). Statut : [PROPOSITION].

#### Scenario: NOT-02-S1 Clic sur la notification
- **WHEN** l'utilisateur clique sur la notification d'une alerte
- **THEN** la fenêtre d'Argos s'ouvre au premier plan sur cette alerte

### Requirement: NOT-03 Compteur dans la zone de notification
Le client desktop DOIT (MUST) indiquer dans la zone de notification le nombre d'alertes ouvertes de l'utilisateur.
Source : ADR-0022 (Proposé). Statut : [PROPOSITION].

#### Scenario: NOT-03-S1 Nouvelle alerte
- **GIVEN** deux alertes ouvertes
- **WHEN** une troisième alerte est émise
- **THEN** la zone de notification indique trois alertes ouvertes

### Requirement: NOT-04 Préférence de notification du poste
Le système DOIT (MUST) permettre à l'utilisateur de couper les notifications de son poste sans modifier ses autres canaux d'alerte.
Source : ABO-02 (canal) ; ADR-0022 (Proposé). Statut : [PROPOSITION].

#### Scenario: NOT-04-S1 Notifications coupées
- **GIVEN** un utilisateur qui a coupé les notifications du poste, alertes par e-mail actives
- **WHEN** une alerte immédiate est émise
- **THEN** aucune notification système ne s'affiche
- **AND** l'e-mail est envoyé

### Requirement: NOT-05 Contenu minimal affiché
Le client desktop NE DOIT PAS (MUST NOT) afficher dans une notification autre chose que le titre, la gravité et le nombre de projets concernés.
Source : interprétation de ce change (écran verrouillé) ; ACC-04. Statut : [PROPOSITION].

#### Scenario: NOT-05-S1 Alerte sur trois projets
- **WHEN** une notification est affichée pour une alerte touchant trois projets
- **THEN** elle indique le titre, la gravité et « 3 projets »
- **AND** elle ne nomme aucun projet ni composant

### Requirement: NOT-06 Reprise après une coupure
Le client desktop DOIT (MUST) signaler la perte de connexion au serveur et, à son retour, présenter les alertes émises pendant la coupure.
Source : ADR-0022 (Proposé) ; arc42 §8.5. Statut : [PROPOSITION].

#### Scenario: NOT-06-S1 Serveur injoignable puis rétabli
- **GIVEN** le serveur injoignable pendant que deux alertes sont émises
- **WHEN** la connexion revient
- **THEN** le client indique les deux alertes émises pendant la coupure
- **AND** aucune alerte n'est perdue ni dupliquée
