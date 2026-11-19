# Delta de spécification : collecte-avis-securite

## Purpose

Collecter les avis de vulnérabilité publiés par les sources externes, les normaliser en signaux de sécurité traçables et rester fiable face aux rejeux et aux pannes de source. Fiche : [/docs/specifications/collecte-avis-securite.md](/docs/specifications/collecte-avis-securite.md).

## ADDED Requirements

### Requirement: COL-09 Synchronisation planifiée des sources d'avis
Le système DOIT (MUST) synchroniser chaque source d'avis et rafraîchir chaque source d'enrichissement à une cadence configurable, une heure par défaut, depuis le curseur persisté ; il NE DOIT PAS (MUST NOT) démarrer une synchronisation planifiée pour une source déjà en cours de synchronisation. Le lancement par l'exploitant reste possible.
Source : décision J1 du porteur du projet (2026-11-19, Q-COL-1) ; ADR-0031 (Accepté) ; COL-05. Statut : [PROPOSITION].

#### Scenario: COL-09-S1 Cadence atteinte
- **GIVEN** la source OSV configurée, en état `NOMINAL`, avec un curseur persisté, et la cadence par défaut d'une heure
- **WHEN** une heure s'est écoulée depuis la dernière synchronisation planifiée
- **THEN** une synchronisation de la source OSV démarre sans action de l'exploitant et ne demande que les avis postérieurs au curseur
- **AND** les avis reçus sont enregistrés selon COL-01 et COL-03, puis le curseur avance selon COL-05

#### Scenario: COL-09-S2 Synchronisation déjà en cours
- **GIVEN** une synchronisation de la source OSV lancée à la demande de l'exploitant et toujours en cours
- **WHEN** l'échéance de la synchronisation planifiée arrive
- **THEN** aucune seconde synchronisation de cette source ne démarre
- **AND** la synchronisation en cours se poursuit et un seul traitement en cours existe pour cette source

#### Scenario: COL-09-S3 Cadence configurée
- **GIVEN** la cadence de synchronisation réglée à 30 minutes dans la configuration
- **WHEN** le serveur démarre puis que 30 minutes s'écoulent
- **THEN** la synchronisation planifiée de chaque source s'exécute toutes les 30 minutes, sans recompilation

#### Scenario: COL-09-S4 Source dégradée
- **GIVEN** la source OSV en état `DEGRADED`
- **WHEN** l'échéance de la synchronisation planifiée arrive
- **THEN** une synchronisation de la source est tentée selon COL-06
- **AND** en cas de succès la source quitte l'état `DEGRADED` (COL-07-S2)

#### Scenario: COL-09-S5 Source d'enrichissement rafraîchie
- **GIVEN** l'index des avis CERT-FR chargé depuis plus d'une cadence
- **WHEN** la synchronisation planifiée s'exécute
- **THEN** l'index CERT-FR est relu avant l'enrichissement des signaux de cette synchronisation
- **AND** l'indisponibilité du CERT-FR n'empêche pas l'enregistrement des signaux (COL-04-S3)

### Requirement: COL-10 Avis inchangé jugé sur l'empreinte du contenu normalisé
Le système DOIT (MUST) juger un avis déjà connu inchangé lorsque l'empreinte de son contenu normalisé est identique à celle de la version courante du signal, et le mettre à jour en historisant une nouvelle version dans le cas contraire ; une différence de présentation du contenu brut NE DOIT PAS (MUST NOT) créer de version.
Source : décision J2 du porteur du projet (2026-11-19, Q-COL-2) ; COL-03. Statut : [PROPOSITION].

#### Scenario: COL-10-S1 Contenu brut réorganisé, contenu normalisé identique
- **GIVEN** un signal enregistré pour un avis OSV
- **WHEN** la source renvoie le même avis avec les champs JSON dans un autre ordre et des espaces différents
- **THEN** l'avis est jugé inchangé
- **AND** aucune nouvelle version n'est créée et aucune corrélation n'est déclenchée

#### Scenario: COL-10-S2 Seule la date de modification change
- **GIVEN** un signal enregistré pour un avis OSV
- **WHEN** la source republie l'avis avec une date de modification plus récente et un contenu normalisé identique
- **THEN** l'avis est jugé inchangé
- **AND** aucune nouvelle version n'est créée

#### Scenario: COL-10-S3 Plage affectée modifiée
- **GIVEN** un signal enregistré dont l'avis affecte un paquet pour les versions inférieures à 1.3.0
- **WHEN** la source republie l'avis avec une plage étendue aux versions inférieures à 1.4.0
- **THEN** une nouvelle version du signal est historisée avec le contenu brut reçu et la nouvelle empreinte
- **AND** la corrélation du signal est déclenchée

#### Scenario: COL-10-S4 Premier passage après le changement d'empreinte
- **GIVEN** des signaux enregistrés avant l'introduction de l'empreinte du contenu normalisé
- **WHEN** la première synchronisation suivante reçoit ces avis sans modification de contenu
- **THEN** l'empreinte normalisée de chaque signal est enregistrée
- **AND** aucune nouvelle version n'est créée pour ces avis
