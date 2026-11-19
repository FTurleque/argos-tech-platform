# Delta de spécification : inventaire-sbom

## Purpose

Maintenir pour chaque projet suivi un inventaire de ses composants, directs et transitifs, identifiés par PURL et version, avec une fraîcheur mesurable. Fiche : [/docs/specifications/inventaire-sbom.md](/docs/specifications/inventaire-sbom.md).

## ADDED Requirements

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
