# radar-standards Specification

## Purpose
Dire pour chaque espace quelles technologies et versions sont recommandées et ce qui arrive bientôt (sorties, fins de support), pour préparer les montées de version. Fiche : [/docs/specifications/radar-standards.md](/docs/specifications/radar-standards.md).

## Requirements

### Requirement: RAD-01 Entrée du référentiel
Le système DOIT (MUST) enregistrer pour chaque technologie d'un espace un statut parmi ADOPT, TRIAL, ASSESS, HOLD, une version recommandée pour ADOPT, un responsable, une justification et une date de revue.
Source : dossier v2.4 §8.3 ; ADR-0025 (Accepté). Statut : [EXIGENCE].

#### Scenario: RAD-01-S1 Standard complet
- **WHEN** un administrateur enregistre Spring Boot en ADOPT, version 3.5, avec responsable, justification et date de revue
- **THEN** l'entrée est créée

#### Scenario: RAD-01-S2 Justification manquante
- **WHEN** un administrateur enregistre une technologie en HOLD sans justification
- **THEN** l'enregistrement est refusé avec un message indiquant le champ manquant
- **AND** le référentiel est inchangé

### Requirement: RAD-02 Gouvernance du référentiel
Le système DOIT (MUST) réserver la modification du référentiel aux administrateurs de l'espace et NE DOIT PAS (MUST NOT) laisser l'IA le modifier ; une suggestion de l'IA reste en attente de décision.
Source : dossier v2.4 §8.3 ; ADR-0013 (Proposé). Statut : [EXIGENCE].

#### Scenario: RAD-02-S1 Lecteur
- **WHEN** un lecteur tente de modifier un statut
- **THEN** la demande est refusée comme interdite

#### Scenario: RAD-02-S2 Suggestion de l'IA
- **WHEN** l'IA suggère de passer une version en fin de support à HOLD
- **THEN** la suggestion est enregistrée en attente
- **AND** le statut est inchangé

### Requirement: RAD-03 Radar par horizon
Le système DOIT (MUST) présenter les sorties, fins de support et correctifs à venir des technologies de l'espace sur les horizons 30 jours, 6 mois et 1 an.
Source : baseline v2.3 §4 ; maquette « Radar des sorties ». Statut : [EXIGENCE].

#### Scenario: RAD-03-S1 Horizon 30 jours
- **GIVEN** une sortie dans 10 jours et une fin de support dans 166 jours
- **WHEN** l'utilisateur choisit l'horizon 30 jours
- **THEN** seule la sortie dans 10 jours est présentée

### Requirement: RAD-04 Rappels d'échéance
Le système DOIT (MUST) rappeler une échéance du radar aux utilisateurs des projets concernés à J-30, J-7 et J0.
Source : dossier v2.4 §6.4. Statut : [EXIGENCE].

#### Scenario: RAD-04-S1 Rappel à J-30
- **GIVEN** une fin de support d'une technologie utilisée par un projet suivi, dans 30 jours
- **WHEN** la tâche quotidienne s'exécute
- **THEN** les utilisateurs du projet reçoivent un rappel

### Requirement: RAD-05 Dette de version
Le système DOIT (MUST) créer un impact « dette de version » lorsqu'une version présente dans l'inventaire d'un projet a atteint sa fin de support.
Source : dossier v2.4 §6.4 ; ADR-0013 (Proposé). Statut : [EXIGENCE].

#### Scenario: RAD-05-S1 Version plus maintenue
- **GIVEN** le projet Portail agent utilisant PostgreSQL 14
- **WHEN** la fin de support de PostgreSQL 14 est atteinte
- **THEN** un impact « dette de version » est créé pour Portail agent avec la version standard de l'espace

### Requirement: RAD-06 Annonce d'un changement de standard
Le système DOIT (MUST) annoncer aux utilisateurs des projets concernés tout changement de statut ou de version recommandée d'une technologie qu'ils utilisent.
Source : dossier v2.4 §8.2 (type STANDARD). Statut : [EXIGENCE].

#### Scenario: RAD-06-S1 Passage en HOLD
- **GIVEN** une bibliothèque utilisée par deux projets de l'espace
- **WHEN** l'administrateur la passe en HOLD
- **THEN** les utilisateurs des deux projets reçoivent l'annonce avec la justification

### Requirement: RAD-07 Enregistrement d'un standard sans erreur différée
Le système DOIT (MUST) refuser par une erreur de saisie (400) une clé de technologie mal encodée, sans rien modifier, et NE DOIT PAS (MUST NOT) répondre en erreur à une modification du référentiel déjà enregistrée lorsque son annonce (RAD-06) échoue sur une panne d'accès aux données ; cet échec est journalisé avec sa cause.
Source : AUD-QUA-20 ; RAD-01-S2 ; RAD-06 ; arc42 §8.5. Statut : [PROPOSITION].

#### Scenario: RAD-07-S1 Clé mal encodée
- **GIVEN** un administrateur de l'espace
- **WHEN** il enregistre un standard de clé `pkg:maven/%zz/x`
- **THEN** l'enregistrement est refusé par une réponse 400 qui indique le champ `cle`
- **AND** le référentiel et son historique sont inchangés

#### Scenario: RAD-07-S2 Annonce en échec après l'enregistrement
- **GIVEN** une bibliothèque utilisée par un projet de l'espace, et l'annonce du changement de standard qui échoue sur une panne d'accès aux données
- **WHEN** l'administrateur la passe en HOLD avec une justification
- **THEN** la modification est confirmée à l'administrateur avec l'entrée enregistrée
- **AND** l'entrée et sa ligne d'historique sont persistées, et l'échec de l'annonce est journalisé avec sa cause

#### Scenario: RAD-07-S3 Erreur imprévue de l'annonce
- **GIVEN** une bibliothèque utilisée par un projet de l'espace, et l'annonce du changement de standard qui échoue sur une erreur imprévue
- **WHEN** l'administrateur la passe en HOLD avec une justification
- **THEN** l'erreur est journalisée avec sa cause et la requête répond en erreur serveur, sans détail interne
- **AND** l'entrée et sa ligne d'historique restent persistées
