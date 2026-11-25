# inventaire-sbom Specification

## Purpose
Maintenir pour chaque projet suivi un inventaire de ses composants, directs et transitifs, identifiés par PURL et version, avec une fraîcheur mesurable. Fiche : [/docs/specifications/inventaire-sbom.md](/docs/specifications/inventaire-sbom.md).

## Requirements

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

### Requirement: INV-07 Import périodique depuis les dépôts configurés
Le système DOIT (MUST) importer, à une cadence configurable, quotidienne par défaut, l'inventaire de chaque dépôt GitHub ou GitLab configuré d'un projet, en interrogeant la forge en lecture seule selon INV-01, INV-04 et INV-05 ; il NE DOIT PAS (MUST NOT) exposer de point d'entrée entrant (webhook) pour déclencher un import.
Source : décision J3 du porteur du projet (2026-11-19, Q-INV-1) ; ADR-0031 (Accepté) ; ADR-0011 (Proposé). Statut : [PROPOSITION].

#### Scenario: INV-07-S1 Passage quotidien
- **GIVEN** deux projets dont les dépôts GitLab et GitHub publient chacun une SBOM CycloneDX JSON 1.6 sur leur branche principale
- **WHEN** l'échéance quotidienne de l'import arrive
- **THEN** l'inventaire de chaque dépôt est importé depuis sa forge sans action d'un utilisateur
- **AND** l'origine, la date de la source et la date d'import de chaque inventaire sont enregistrées

#### Scenario: INV-07-S2 Forge en erreur pour un dépôt
- **GIVEN** deux projets dont la forge du premier répond 503 et celle du second répond normalement
- **WHEN** le passage quotidien de l'import s'exécute
- **THEN** l'inventaire du second projet est importé
- **AND** l'inventaire en place du premier projet est conservé et l'échec, avec son motif, est enregistré sans jeton dans le message

#### Scenario: INV-07-S3 Aucun point d'entrée entrant
- **GIVEN** le serveur démarré
- **WHEN** un client non authentifié envoie à l'API une requête de notification de pipeline ou de webhook de forge
- **THEN** aucune route ne l'accepte et aucun import n'est déclenché

#### Scenario: INV-07-S4 Cadence configurée
- **GIVEN** l'expression de planification de l'import réglée à 02:00 chaque jour dans la configuration
- **WHEN** le serveur démarre
- **THEN** le passage d'import s'exécute chaque jour à 02:00, au fuseau configuré, sans recompilation

### Requirement: INV-08 Import manuel conservé et imports sérialisés
Le système DOIT (MUST) conserver l'import manuel existant d'une SBOM ou de manifestes pour un projet et sérialiser les imports d'un même projet, qu'ils soient planifiés ou manuels, de sorte que l'inventaire de référence résulte toujours d'un import complet.
Source : décision J3 du porteur du projet (2026-11-19) ; INV-01 ; arc42 §6.1 (invariants). Statut : [PROPOSITION].

#### Scenario: INV-08-S1 Import manuel après l'introduction de l'import périodique
- **GIVEN** un projet dont l'inventaire a été importé par le passage quotidien
- **WHEN** une SBOM CycloneDX valide est importée manuellement pour ce projet
- **THEN** elle remplace l'inventaire de référence selon INV-01
- **AND** les avis connus sont recorrélés selon INV-09

#### Scenario: INV-08-S2 Import manuel et passage planifié simultanés
- **GIVEN** un import manuel en cours pour un projet
- **WHEN** le passage quotidien atteint ce même projet
- **THEN** les deux imports s'exécutent l'un après l'autre
- **AND** l'inventaire de référence final est celui de l'import terminé en dernier, sans mélange de composants des deux imports

### Requirement: INV-09 Recorrélation à chaque import
Le système DOIT (MUST), après chaque import réussi de l'inventaire d'un projet, recorréler pour ce projet les signaux de sécurité déjà connus : les impacts nouvellement vérifiés sont créés et les impacts qui ne sont plus vérifiés sont traités selon COR-11.
Source : décision J4 du porteur du projet (2026-11-19, Q-INV-2) ; baseline v2.3 §3. Statut : [PROPOSITION].

#### Scenario: INV-09-S1 Nouveau composant affecté
- **GIVEN** un signal connu qui affecte `pkg:npm/exemple@<2.0.0` et un projet dont l'inventaire ne contient pas ce paquet
- **WHEN** un import ajoute `pkg:npm/exemple@1.5.0` à l'inventaire du projet
- **THEN** un impact `ouvert` relie ce signal, ce projet et ce composant, sans nouvelle collecte de l'avis

#### Scenario: INV-09-S2 Composant retiré
- **GIVEN** un impact `ouvert` sur un composant d'un projet
- **WHEN** un import produit un inventaire qui ne contient plus ce composant
- **THEN** l'impact n'est plus compté parmi les impacts ouverts du projet et est traité selon COR-11

#### Scenario: INV-09-S3 Import refusé ou inventaire conservé
- **GIVEN** un projet avec un inventaire de référence et des impacts
- **WHEN** le passage quotidien refuse la SBOM du dépôt (INV-05) ou conserve l'inventaire faute de nouvelle SBOM
- **THEN** aucune recorrélation n'est lancée pour ce projet
- **AND** ses impacts et leurs états sont inchangés

#### Scenario: INV-09-S4 Échec de la recorrélation
- **GIVEN** un import réussi pour un projet
- **WHEN** la recorrélation qui le suit échoue
- **THEN** l'inventaire importé reste l'inventaire de référence
- **AND** l'échec est journalisé et la recorrélation du projet est relancée au passage planifié suivant

### Requirement: INV-10 Projet à plusieurs dépôts
Le système DOIT (MUST) permettre de rattacher un ou plusieurs dépôts à un projet et constituer l'inventaire du projet comme l'union des inventaires de ses dépôts, chaque composant indiquant les dépôts qui le contiennent.
Source : décision J6 du porteur du projet (2026-11-19, glossaire « projet ») ; glossaire « Repository ». Statut : [PROPOSITION].

#### Scenario: INV-10-S1 Union de deux dépôts
- **GIVEN** un projet rattaché à un dépôt `front` dont l'inventaire contient `pkg:npm/a@1.0.0` et `pkg:npm/b@2.0.0`, et à un dépôt `back` dont l'inventaire contient `pkg:npm/b@2.0.0` et `pkg:maven/org.exemple/c@3.0.0`
- **WHEN** un utilisateur autorisé consulte l'inventaire du projet
- **THEN** l'inventaire contient trois composants, `pkg:npm/b@2.0.0` une seule fois avec les dépôts `front` et `back`
- **AND** l'origine et la date de la source de chaque dépôt sont affichées

#### Scenario: INV-10-S2 Import refusé pour un seul dépôt
- **GIVEN** un projet à deux dépôts dont les inventaires sont importés
- **WHEN** la nouvelle SBOM du dépôt `front` est refusée et celle du dépôt `back` importée
- **THEN** les composants issus de `front` restent ceux de son dernier import réussi
- **AND** les composants issus de `back` sont remplacés par ceux de sa nouvelle SBOM

#### Scenario: INV-10-S3 Dépôt détaché du projet
- **GIVEN** un projet à deux dépôts dont un composant n'est présent que dans le dépôt `front`
- **WHEN** le dépôt `front` est détaché du projet puis que l'inventaire du projet est reconstitué
- **THEN** ce composant ne figure plus dans l'inventaire du projet
- **AND** ses impacts sont traités selon COR-11

#### Scenario: INV-10-S4 Projet existant à un seul dépôt
- **GIVEN** un projet créé avant l'introduction des projets à plusieurs dépôts, avec un seul dépôt et un inventaire
- **WHEN** la migration des données s'exécute
- **THEN** le projet est rattaché à ce dépôt, son inventaire et ses impacts sont inchangés

### Requirement: INV-11 Erreur d'un projet isolée
Le système DOIT (MUST), lors de l'import planifié, limiter à son projet l'effet d'une SBOM fautive ou d'une erreur imprévue : les projets suivants du passage sont importés, le projet en échec est compté au bilan du passage et l'erreur est journalisée avec sa cause.
Source : AUD-QUA-20 ; INV-05 ; INV-07-S2 ; arc42 §8.5. Statut : [PROPOSITION].

#### Scenario: INV-11-S1 SBOM portant un PURL mal encodé
- **GIVEN** deux projets dont le premier publie une SBOM CycloneDX dont un composant porte le PURL `pkg:npm/%zz@1.0.0`
- **WHEN** le passage quotidien de l'import s'exécute
- **THEN** l'inventaire du second projet est importé
- **AND** le composant au PURL mal encodé du premier projet est écarté et signalé comme tout composant au PURL illisible (INV-01), les autres composants de sa SBOM étant importés

#### Scenario: INV-11-S2 Erreur imprévue pendant l'import d'un projet
- **GIVEN** deux projets, l'import du premier échouant sur une erreur imprévue autre qu'une panne d'accès aux données
- **WHEN** le passage quotidien de l'import s'exécute
- **THEN** l'inventaire du second projet est importé
- **AND** l'inventaire en place du premier projet est conservé, le bilan du passage compte un projet en échec, l'erreur est journalisée avec sa cause et le passage la relance
