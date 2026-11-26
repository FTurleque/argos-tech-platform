# Delta de spécification : radar-standards

## ADDED Requirements

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
