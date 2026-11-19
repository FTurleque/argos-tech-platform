# Delta de spécification : inventaire-sbom

## Purpose

Maintenir pour chaque projet suivi un inventaire de ses composants, directs et transitifs, identifiés par PURL et version, avec une fraîcheur mesurable. Fiche : [/docs/specifications/inventaire-sbom.md](/docs/specifications/inventaire-sbom.md).

## ADDED Requirements

### Requirement: INV-01 SBOM CycloneDX de la branche principale comme référence
Le système DOIT (MUST) constituer l'inventaire de référence d'un projet à partir de la dernière SBOM CycloneDX produite par la CI de sa branche principale, chaque composant étant identifié par son PURL et sa version. Les SBOM acceptées sont au format JSON, schéma CycloneDX 1.5 ou 1.6. Un composant sans PURL exploitable est écarté et signalé avec l'import.
Source : ADR-0011 (Proposé) ; baseline v2.3 §4 ; décisions du 2026-11-16 (Q-7) et D6 du 2026-11-18. Statut : [PROPOSITION].

#### Scenario: INV-01-S1 Import d'une SBOM valide
- **GIVEN** un projet suivi dont la CI de la branche principale a produit une SBOM CycloneDX valide
- **WHEN** le système importe cette SBOM
- **THEN** l'inventaire du projet liste chaque composant de la SBOM avec son PURL et sa version
- **AND** la date de production de la SBOM est enregistrée avec l'inventaire

#### Scenario: INV-01-S2 Nouvelle SBOM remplaçant la précédente
- **GIVEN** un projet dont l'inventaire de référence provient d'une SBOM antérieure
- **WHEN** une SBOM plus récente de la branche principale est importée
- **THEN** l'inventaire de référence correspond à la nouvelle SBOM
- **AND** un composant absent de la nouvelle SBOM n'apparaît plus dans l'inventaire de référence

#### Scenario: INV-01-S3 Composant sans PURL signalé à l'import
- **GIVEN** une SBOM CycloneDX valide dont un composant n'est décrit que par son groupe, son nom, sa version et un CPE, sans PURL
- **WHEN** le système importe cette SBOM
- **THEN** ce composant n'apparaît pas dans l'inventaire de référence
- **AND** l'utilisateur qui consulte l'inventaire voit ce composant signalé avec son nom, son groupe, sa version et le motif « sans PURL »

### Requirement: INV-02 Dépendances transitives
Le système DOIT (MUST) inclure dans l'inventaire les dépendances transitives déclarées par la SBOM et permettre de distinguer une dépendance directe d'une dépendance transitive.
Source : ADR-0011 (Proposé, critère 2) ; baseline v2.3 §4 et §6 critère 2. Statut : [PROPOSITION].

#### Scenario: INV-02-S1 Dépendance transitive consultable
- **GIVEN** une SBOM importée dans laquelle le composant B n'est atteint que par le composant direct A
- **WHEN** un utilisateur autorisé consulte l'inventaire du projet
- **THEN** le composant B est listé avec son PURL et sa version
- **AND** B est indiqué comme dépendance transitive

### Requirement: INV-03 Âge de l'inventaire visible
Le système DOIT (MUST) afficher pour chaque projet l'âge de la SBOM à l'origine de son inventaire, signaler une SBOM obsolète, c'est-à-dire générée depuis plus de 30 jours, et signaler un projet suivi qui ne dispose d'aucune SBOM.
Source : ADR-0011 (Proposé, conséquences neutres) ; baseline v2.3 §6 critère 2 ; décision du 2026-11-16 (question Q-3). Statut : [PROPOSITION].

#### Scenario: INV-03-S1 Projet avec SBOM
- **GIVEN** un projet dont l'inventaire provient d'une SBOM produite à une date connue
- **WHEN** un utilisateur autorisé consulte le projet
- **THEN** l'âge de cette SBOM est affiché

#### Scenario: INV-03-S2 Projet sans SBOM
- **GIVEN** un projet suivi pour lequel aucune SBOM n'a été importée
- **WHEN** un utilisateur autorisé consulte le projet
- **THEN** l'absence de SBOM est signalée

#### Scenario: INV-03-S3 SBOM obsolète
- **GIVEN** un projet dont l'inventaire provient d'une SBOM générée il y a 31 jours
- **WHEN** un utilisateur autorisé consulte le projet
- **THEN** l'âge de la SBOM est affiché et la SBOM est signalée obsolète

### Requirement: INV-04 Repli sur les manifestes
Le système DOIT (MUST), lorsqu'aucune SBOM n'est disponible pour un projet, constituer un inventaire de repli à partir de la lecture de ses manifestes de dépendances, et indiquer que l'inventaire provient de ce repli.
Source : ADR-0011 (Proposé). Statut : [PROPOSITION].

#### Scenario: INV-04-S1 Projet sans SBOM avec manifeste
- **GIVEN** un projet suivi sans SBOM dont le dépôt contient un manifeste de dépendances lisible
- **WHEN** le système constitue l'inventaire du projet
- **THEN** l'inventaire liste les dépendances déclarées par le manifeste
- **AND** l'origine « manifeste » de l'inventaire est affichée

#### Scenario: INV-04-S2 SBOM disponible après un repli
- **GIVEN** un projet dont l'inventaire provient du repli sur les manifestes
- **WHEN** une SBOM CycloneDX de la branche principale est importée
- **THEN** l'inventaire de référence provient de la SBOM selon INV-01

### Requirement: INV-05 Rejet d'une SBOM non conforme
Le système DOIT (MUST) refuser l'import d'une SBOM qui n'est pas un document CycloneDX exploitable et rendre l'échec visible avec son motif.
Source : arc42 §8.3 (validation des entrées externes) ; arc42 §11.2 (pas de suppression silencieuse d'erreur). Statut : [PROPOSITION].

#### Scenario: INV-05-S1 Fichier non conforme
- **GIVEN** un projet suivi et un fichier SBOM qui n'est pas un document CycloneDX valide
- **WHEN** le système tente de l'importer
- **THEN** l'import est refusé et son motif est enregistré et consultable
- **AND** aucun composant issu de ce fichier n'est ajouté à l'inventaire

### Requirement: INV-06 Consultation de l'inventaire par projet
Le système DOIT (MUST) proposer le choix du projet parmi ceux des espaces de l'utilisateur, fournir la synthèse de l'inventaire de référence sans la liste des composants et lister les composants inventoriés du projet par pages de taille bornée, dans un ordre stable par nom puis version.
Source : décisions F1, F5 et G5 du porteur du projet, 2026-11-19 ; ADR-0016 ; arc42 §8.10. Statut : [PROPOSITION].

#### Scenario: INV-06-S1 Projets visibles limités aux espaces de l'utilisateur
- **GIVEN** un utilisateur membre de l'espace A, des projets dans l'espace A et un projet dans l'espace B dont il n'est pas membre
- **WHEN** l'utilisateur ouvre la vue d'inventaire
- **THEN** la liste de choix propose chaque projet de l'espace A avec le nom de son espace, triés par nom
- **AND** le projet de l'espace B n'y figure pas

#### Scenario: INV-06-S2 Composants inventoriés par pages
- **GIVEN** un projet visible de l'utilisateur dont l'inventaire de référence compte 25 composants
- **WHEN** l'utilisateur consulte la seconde page de 20 composants
- **THEN** les 5 derniers composants sont listés par nom puis version, chacun avec sa version, son PURL et son caractère direct ou transitif
- **AND** le nombre total de 25 composants et la position « page 2 sur 2 » sont affichés

#### Scenario: INV-06-S3 Projet inconnu ou d'un autre espace
- **GIVEN** un utilisateur membre de l'espace A seulement
- **WHEN** il demande les composants d'un projet inconnu ou d'un projet de l'espace B
- **THEN** la réponse est identique à celle d'un projet inexistant et ne révèle aucun composant
- **AND** le refus portant sur le projet de l'espace B est journalisé

#### Scenario: INV-06-S4 Synthèse de l'inventaire sans la liste des composants
- **GIVEN** un projet visible de l'utilisateur dont l'inventaire de référence compte 25 composants et 3 composants écartés faute de PURL
- **WHEN** l'utilisateur ouvre la vue d'inventaire de ce projet
- **THEN** la synthèse affiche l'origine, la date de la source, le nombre de 25 composants inventoriés et les composants écartés
- **AND** seule la page de composants affichée est transférée, jamais la liste complète des composants
