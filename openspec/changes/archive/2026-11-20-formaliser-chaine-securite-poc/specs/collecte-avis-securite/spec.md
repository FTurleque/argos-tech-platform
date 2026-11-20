# Delta de spécification : collecte-avis-securite

## Purpose

Collecter les avis de vulnérabilité publiés par les sources externes, les normaliser en signaux de sécurité traçables et rester fiable face aux rejeux et aux pannes de source. Fiche : [/docs/specifications/collecte-avis-securite.md](/docs/specifications/collecte-avis-securite.md).

## ADDED Requirements

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
Le système DOIT (MUST) enrichir un signal avec les données NVD, CISA KEV, FIRST EPSS et CERT-FR lorsque ses identifiants le permettent, sans créer de second signal pour une même vulnérabilité ; l'indisponibilité d'une source secondaire NE DOIT PAS (MUST NOT) empêcher l'enregistrement du signal.
Source : ADR-0010 (Proposé). Statut : [PROPOSITION].

#### Scenario: COL-04-S1 Enrichissement par un identifiant CVE
- **GIVEN** un signal issu d'OSV qui référence un identifiant CVE
- **WHEN** la vulnérabilité figure dans le catalogue CISA KEV
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
