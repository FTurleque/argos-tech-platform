# Delta de spécification : collecte-avis-securite

## MODIFIED Requirements

### Requirement: COL-04 Enrichissement par les sources secondaires
Le système DOIT (MUST) enrichir un signal avec les données NVD, CISA KEV, FIRST EPSS et CERT-FR lorsque ses identifiants le permettent, sans créer de second signal pour une même vulnérabilité ; l'indisponibilité d'une source secondaire NE DOIT PAS (MUST NOT) empêcher ni retarder l'enregistrement du signal, et l'enrichissement manquant DOIT (MUST) être retenté automatiquement.
Source : ADR-0010 (Proposé) ; AUD-PER-02 ; ADR-0039 (Proposé). Statut : [PROPOSITION].

#### Scenario: COL-04-S1 Enrichissement par un identifiant CVE
- **GIVEN** un signal issu d'OSV qui référence un identifiant CVE
- **WHEN** la vulnérabilité figure dans le catalogue CISA KEV et que l'enrichissement du signal s'exécute
- **THEN** le signal indique l'exploitation avérée et la source KEV de cette information

#### Scenario: COL-04-S2 Même vulnérabilité publiée par deux sources
- **GIVEN** un signal issu d'OSV qui référence un identifiant CVE
- **WHEN** NVD publie la fiche de cette même CVE
- **THEN** les données NVD sont rattachées au signal existant
- **AND** aucun second signal n'est créé

#### Scenario: COL-04-S3 Source secondaire indisponible
- **GIVEN** un nouvel avis OSV et la source EPSS indisponible
- **WHEN** la synchronisation de la source OSV s'exécute
- **THEN** le signal est enregistré et consultable sans l'enrichissement EPSS
- **AND** quand la source EPSS répond de nouveau, un passage suivant de l'enrichissement rattache les données EPSS au signal sans action de l'exploitant

#### Scenario: COL-04-S4 Enregistrement sans attente des sources secondaires
- **GIVEN** une source secondaire qui tarde à répondre
- **WHEN** la synchronisation de la source OSV enregistre un nouvel avis
- **THEN** la synchronisation se poursuit avec l'avis suivant sans attendre la réponse de la source secondaire

## ADDED Requirements

### Requirement: COL-12 Lancement d'une synchronisation par l'exploitant
Le système DOIT (MUST) accepter le lancement d'une synchronisation par l'exploitant sans attendre sa fin, en renvoyant l'identifiant du traitement démarré, et NE DOIT PAS (MUST NOT) démarrer un second traitement pour une source dont un traitement est en cours, y compris pendant l'attente d'une nouvelle tentative.
Source : AUD-PER-01 ; AUD-PER-27 ; COL-06 ; COL-09 ; décision du porteur du projet (2026-11-26 : 409 pour un traitement en cours). Statut : [PROPOSITION].

#### Scenario: COL-12-S1 Lancement accepté
- **GIVEN** la source OSV sans traitement en cours
- **WHEN** l'exploitant lance sa synchronisation
- **THEN** la demande est acceptée par une réponse 202 qui porte l'identifiant du traitement, avant la fin de la synchronisation
- **AND** la synchronisation se poursuit en tâche de fond et son issue est visible dans l'état de la source (COL-07)

#### Scenario: COL-12-S2 Traitement déjà en cours
- **GIVEN** une synchronisation de la source OSV en cours
- **WHEN** l'exploitant lance une synchronisation de cette source
- **THEN** la demande est refusée par une réponse 409 qui indique qu'une synchronisation est en cours
- **AND** aucun second traitement n'est créé

#### Scenario: COL-12-S3 Attente d'une nouvelle tentative
- **GIVEN** une synchronisation de la source OSV en attente d'une nouvelle tentative après une erreur transitoire (COL-06)
- **WHEN** l'exploitant ou la synchronisation planifiée lance une synchronisation de cette source
- **THEN** aucun second traitement ne démarre et un seul traitement en cours existe pour cette source
- **AND** la nouvelle tentative reprend ensuite depuis le curseur persisté (COL-05)
