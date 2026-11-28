# collecte-avis-securite Specification

## Purpose
Collecter les avis de vulnérabilité publiés par les sources externes, les normaliser en signaux de sécurité traçables et rester fiable face aux rejeux et aux pannes de source. Fiche : [/docs/specifications/collecte-avis-securite.md](/docs/specifications/collecte-avis-securite.md).

## Requirements

### Requirement: COL-01 Collecte des avis depuis la source principale
Le système DOIT (MUST) collecter les avis de vulnérabilité publiés par OSV, source principale, et enregistrer chaque avis collecté sous la forme d'un signal de sécurité normalisé, indépendant du format OSV.
Source : ADR-0010 (Proposé) ; ADR-0003 (Proposé) ; baseline v2.3 §4. Statut : [PROPOSITION].

#### Scenario: COL-01-S1 Nouvel avis collecté
- **GIVEN** un avis OSV publié et inconnu d'ARGOS
- **WHEN** la synchronisation de la source OSV s'exécute
- **THEN** un signal de sécurité normalisé est enregistré pour cet avis
- **AND** le signal référence les paquets et plages de versions affectées déclarés par l'avis

#### Scenario: COL-01-S2 Aucun nouvel avis
- **GIVEN** aucun avis publié ni modifié depuis la dernière synchronisation réussie
- **WHEN** la synchronisation de la source OSV s'exécute
- **THEN** la synchronisation se termine sans erreur
- **AND** aucun signal n'est créé ni modifié

### Requirement: COL-02 Provenance de chaque signal
Le système DOIT (MUST) conserver pour chaque signal de sécurité la source, l'identifiant externe de l'avis, la date de collecte et le contenu brut reçu ou une référence d'audit vers ce contenu.
Source : arc42 §4.2 (P3) ; arc42 §10.2 Q-01. Statut : [PROPOSITION].

#### Scenario: COL-02-S1 Consultation de la provenance
- **GIVEN** un signal de sécurité issu d'un avis OSV
- **WHEN** un utilisateur autorisé consulte ce signal
- **THEN** la source, l'identifiant externe, la date de collecte et l'accès au contenu brut ou à sa référence d'audit sont affichés

#### Scenario: COL-02-S2 Signal sans provenance impossible
- **GIVEN** un avis reçu dont l'identifiant externe est absent
- **WHEN** le système tente de le normaliser
- **THEN** aucun signal n'est créé
- **AND** l'avis est mis à l'écart selon COL-08

### Requirement: COL-03 Idempotence sur source et identifiant externe
Le système DOIT (MUST) garantir qu'un même avis, identifié par sa source et son identifiant externe, ne produit qu'un seul signal logique, quel que soit le nombre de collectes ; une modification publiée par la source DOIT (MUST) être historisée sur ce même signal.
Source : arc42 §6.1 (invariants) ; baseline v2.3 §4 ; arc42 §10.2 Q-03. Statut : [PROPOSITION].

#### Scenario: COL-03-S1 Rejeu d'un avis inchangé
- **GIVEN** un avis déjà enregistré comme signal
- **WHEN** le même avis, au contenu identique, est collecté de nouveau
- **THEN** aucun nouveau signal ni nouvelle version n'est créé

#### Scenario: COL-03-S2 Avis modifié par la source
- **GIVEN** un avis déjà enregistré comme signal
- **WHEN** la source publie une modification de cet avis et la synchronisation s'exécute
- **THEN** le signal existant reflète le contenu modifié
- **AND** la version précédente reste consultable dans l'historique du signal
- **AND** aucun second signal n'est créé

#### Scenario: COL-03-S3 Collectes concurrentes du même avis
- **GIVEN** deux traitements de collecte qui reçoivent simultanément le même avis inconnu
- **WHEN** les deux traitements enregistrent l'avis
- **THEN** un seul signal existe pour cet avis à la fin des deux traitements

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

### Requirement: COL-05 Curseur de synchronisation durable
Le système DOIT (MUST) n'avancer le curseur de synchronisation d'une source qu'après l'enregistrement durable des avis reçus jusqu'à ce curseur.
Source : arc42 §6.3 (règles proposées) ; arc42 §10.2 Q-03 ; QS-03, QS-07. Statut : [PROPOSITION].

#### Scenario: COL-05-S1 Échec après réception partielle
- **GIVEN** une synchronisation qui a reçu une partie des avis disponibles
- **WHEN** la source échoue avant la fin de la synchronisation
- **THEN** le curseur conserve la dernière position dont les avis sont enregistrés
- **AND** la reprise suivante collecte les avis restants sans perte ni duplication logique

#### Scenario: COL-05-S2 Arrêt brutal du backend
- **GIVEN** une synchronisation en cours
- **WHEN** le backend s'arrête brutalement puis redémarre
- **THEN** la synchronisation reprend depuis le dernier curseur persisté
- **AND** aucun avis enregistré avant l'arrêt n'est perdu ni dupliqué

### Requirement: COL-06 Nouvelles tentatives bornées
Le système DOIT (MUST) distinguer les erreurs transitoires des erreurs permanentes d'une source, retenter les erreurs transitoires avec un délai croissant et un nombre maximal de tentatives configuré, 5 par synchronisation par défaut, et respecter le délai `Retry-After` indiqué par la source.
Source : arc42 §6.3 ; arc42 §8.5 ; QS-03 ; décision du 2026-11-16 (question Q-4). Statut : [PROPOSITION].

#### Scenario: COL-06-S1 Limitation de débit avec Retry-After
- **GIVEN** une source qui répond 429 avec un en-tête `Retry-After`
- **WHEN** le système planifie la tentative suivante
- **THEN** aucune nouvelle requête n'est envoyée à cette source avant l'expiration du délai indiqué
- **AND** la tentative et l'erreur sont enregistrées

#### Scenario: COL-06-S2 Nombre maximal de tentatives atteint
- **GIVEN** une source qui renvoie des erreurs transitoires (timeout ou 5xx) à chaque tentative
- **WHEN** le nombre maximal de tentatives configuré est atteint
- **THEN** le système cesse de retenter cette synchronisation
- **AND** le curseur de la source est inchangé

#### Scenario: COL-06-S3 Erreur permanente
- **GIVEN** une source qui refuse la requête pour une cause permanente (authentification refusée ou requête invalide)
- **WHEN** la synchronisation échoue
- **THEN** l'erreur est enregistrée comme permanente
- **AND** aucune nouvelle tentative automatique n'est planifiée pour cette erreur

### Requirement: COL-07 Source dégradée visible
Le système DOIT (MUST) placer une source en état `DEGRADED` lorsque son seuil d'échec configuré est atteint, 3 synchronisations consécutives en échec par défaut, créer un événement d'exploitation et exposer cet état à l'exploitant.
Source : arc42 §6.3 ; arc42 §8.6 ; décision du 2026-11-16 (question Q-4). Statut : [PROPOSITION].

#### Scenario: COL-07-S1 Seuil d'échec atteint
- **GIVEN** une source dont les synchronisations échouent
- **WHEN** le seuil d'échec configuré est atteint
- **THEN** la source est affichée en état `DEGRADED` dans les indicateurs d'exploitation
- **AND** un événement d'exploitation daté est enregistré

#### Scenario: COL-07-S2 Retour à la normale
- **GIVEN** une source en état `DEGRADED`
- **WHEN** une synchronisation de cette source réussit
- **THEN** la source quitte l'état `DEGRADED`
- **AND** les avis reçus sont enregistrés selon COL-01 et COL-03

### Requirement: COL-08 Mise à l'écart des avis non conformes
Le système DOIT (MUST) mettre à l'écart, avec le motif du rejet, tout avis qui ne peut pas être normalisé, et poursuivre le traitement des autres avis de la même synchronisation.
Source : arc42 §8.3 (validation des entrées externes) ; arc42 §8.5 (table d'échec) ; arc42 §11.2 (pas de suppression silencieuse d'erreur). Statut : [PROPOSITION].

#### Scenario: COL-08-S1 Avis malformé au milieu d'un lot
- **GIVEN** une synchronisation qui reçoit trois avis dont le deuxième est malformé
- **WHEN** le système normalise le lot
- **THEN** les premier et troisième avis sont enregistrés comme signaux
- **AND** le deuxième avis est enregistré dans la zone d'échec avec son motif et sa provenance
- **AND** aucun signal n'est créé pour le deuxième avis

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

### Requirement: COL-11 Erreur d'une source isolée
Le système DOIT (MUST) terminer en échec visible la synchronisation d'une source qu'interrompt une erreur imprévue, y compris due à un avis mal formé, et poursuivre la synchronisation des autres sources du même passage ; il NE DOIT PAS (MUST NOT) laisser cette synchronisation en cours.
Source : AUD-QUA-20 ; COL-07 ; COL-08 ; arc42 §8.5. Statut : [PROPOSITION].

#### Scenario: COL-11-S1 Avis au PURL mal encodé
- **GIVEN** deux sources d'avis synchronisées dans le même passage planifié, la première recevant un avis dont un paquet porte le PURL `pkg:npm/%zz@1.0.0`
- **WHEN** le passage planifié s'exécute
- **THEN** la seconde source est synchronisée et ses avis enregistrés selon COL-01 et COL-03
- **AND** la synchronisation de la première source n'est plus en cours à la fin du passage, et l'avis au PURL mal encodé est enregistré comme signal ou mis à l'écart avec son motif (COL-08), jamais ignoré sans trace

#### Scenario: COL-11-S2 Erreur imprévue pendant la synchronisation d'une source
- **GIVEN** deux sources d'avis, le traitement d'un avis de la première (lecture, normalisation ou enregistrement) échouant sur une erreur imprévue autre qu'une panne d'accès aux données
- **WHEN** le passage planifié s'exécute
- **THEN** la synchronisation de la première source se termine en échec permanent, avec son motif, et compte pour le seuil de COL-07
- **AND** le curseur de la première source est inchangé (COL-05), la seconde source est synchronisée, puis le passage relance l'erreur imprévue

#### Scenario: COL-11-S3 Seuil atteint par des erreurs imprévues
- **GIVEN** une source dont les synchronisations échouent sur la même erreur imprévue
- **WHEN** le seuil d'échec configuré est atteint
- **THEN** la source passe en état `DEGRADED` et un événement d'exploitation daté est enregistré (COL-07-S1)

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
