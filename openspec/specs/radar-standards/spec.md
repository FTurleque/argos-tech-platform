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
Le système DOIT (MUST) refuser par une erreur de saisie (400) une clé de technologie mal encodée, sans rien modifier, et DOIT (MUST) confirmer une modification enregistrée du référentiel sans attendre son annonce (RAD-06) ; un échec de l'annonce NE DOIT PAS (MUST NOT) changer la réponse et est journalisé avec sa cause.
Source : AUD-QUA-20 ; AUD-ARC-09 ; RAD-01-S2 ; RAD-06 ; arc42 §8.5 ; ADR-0039 (Proposé). Statut : [PROPOSITION].

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
- **THEN** la modification est confirmée à l'administrateur avec l'entrée enregistrée
- **AND** l'erreur est journalisée avec sa cause et comptée parmi les échecs de traitement asynchrone exposés à l'exploitant

#### Scenario: RAD-07-S4 Annonce sans attente
- **GIVEN** une bibliothèque utilisée par un projet de l'espace, et l'annonce du changement de standard qui tarde
- **WHEN** l'administrateur la passe en HOLD avec une justification
- **THEN** la modification est confirmée sans attendre la fin de l'annonce
- **AND** les utilisateurs du projet reçoivent ensuite l'annonce (RAD-06-S1)

### Requirement: RAD-08 Suggestions de l'IA produites après la synchronisation des cycles de vie
Le système DOIT (MUST), après chaque synchronisation des cycles de vie, demander à la passerelle IA une suggestion de passage à `HOLD` pour chaque standard d'espace dont la version recommandée a atteint sa fin de support, et l'enregistrer en attente sans modifier le référentiel ; il NE DOIT PAS (MUST NOT) dupliquer une suggestion encore en attente.
Source : AUD-QUA-17 ; RAD-02-S2 ; ADR-0029 ; décision du porteur du projet, 2026-11-26. Statut : [PROPOSITION].

#### Scenario: RAD-08-S1 Version recommandée en fin de support
- **GIVEN** l'espace E recommande PostgreSQL 14 dans son référentiel et le cycle de vie de PostgreSQL 14 est échu
- **WHEN** la tâche des suggestions s'exécute avec la passerelle IA disponible
- **THEN** une suggestion `HOLD` en attente, avec la justification produite par l'IA et son origine, apparaît dans les suggestions de E
- **AND** le statut de PostgreSQL dans le référentiel de E est inchangé

#### Scenario: RAD-08-S2 Suggestion déjà en attente
- **GIVEN** une suggestion en attente pour PostgreSQL dans E
- **WHEN** la tâche s'exécute à nouveau
- **THEN** aucune nouvelle suggestion n'est enregistrée pour PostgreSQL dans E

#### Scenario: RAD-08-S3 Passerelle IA indisponible ou sortie refusée
- **GIVEN** une version recommandée échue et la passerelle IA indisponible, ou une sortie de l'IA refusée par la validation
- **WHEN** la tâche s'exécute
- **THEN** aucune suggestion n'est enregistrée, l'absence est journalisée et les autres espaces sont traités

#### Scenario: RAD-08-S4 Version encore supportée
- **GIVEN** une version recommandée dont le cycle de vie n'est pas échu
- **WHEN** la tâche s'exécute
- **THEN** aucune demande n'est adressée à l'IA pour ce standard

### Requirement: RAD-09 Vue radar de l'espace
Le système DOIT (MUST) offrir dans l'IHM, à tout membre d'un espace, une vue radar de cet espace : échéances par horizon, dettes de version, référentiel et suggestions en attente, la décision sur une suggestion étant proposée au seul administrateur de l'espace.
Source : AUD-QUA-17 ; AUD-ARC-22 ; RAD-02, RAD-03 ; ADR-0016 (Accepté). Statut : [PROPOSITION].

#### Scenario: RAD-09-S1 Consultation par un membre
- **GIVEN** un lecteur de l'espace E
- **WHEN** il ouvre la vue radar de E
- **THEN** il voit les échéances à 30 jours, les dettes de version, le référentiel et les suggestions en attente, sans commande de décision

#### Scenario: RAD-09-S2 Décision par l'administrateur
- **GIVEN** l'administrateur de E et une suggestion en attente
- **WHEN** il l'accepte depuis la vue radar
- **THEN** la suggestion quitte la liste des suggestions en attente et le référentiel affiche le statut suggéré

### Requirement: RAD-10 Radar en un nombre fixe de requêtes par espace
Le système DOIT (MUST) calculer les dettes de version et les échéances de rappel d'un espace en un nombre de requêtes indépendant du nombre de composants de ses projets, des composants des autres espaces et du nombre de jours de rappel, sans changer les dettes ni les rappels produits (RAD-04, RAD-05).
Source : AUD-PER-05 ; critère de sortie du sprint 10. Statut : [PROPOSITION].

#### Scenario: RAD-10-S1 Composants d'autres espaces ignorés en base
- **GIVEN** deux espaces qui suivent la même technologie, l'un avec 3 composants concernés, l'autre avec 30
- **WHEN** les dettes de version du premier espace sont calculées
- **THEN** seuls ses 3 composants sont lus
- **AND** le calcul exécute le même nombre de requêtes que pour l'espace de 30 composants concernés

#### Scenario: RAD-10-S2 Rappels sur plusieurs jours
- **GIVEN** un espace avec des rappels à 30, 7 et 1 jour
- **WHEN** les rappels du jour sont calculés
- **THEN** les échéances des trois jours sont trouvées en une seule lecture du calendrier et des composants de l'espace
