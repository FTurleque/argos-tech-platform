# Delta de spécification : correlation-impact

## Purpose

Relier de façon déterministe, explicable et traçable un signal de sécurité aux seuls projets dont l'inventaire contient un composant affecté, sans dépendre d'un fournisseur d'IA. Fiche : [/docs/specifications/correlation-impact.md](/docs/specifications/correlation-impact.md).

## ADDED Requirements

### Requirement: COR-01 Rapprochement par PURL et plage de versions
Le système DOIT (MUST) créer un impact projet lorsqu'un composant de l'inventaire d'un projet a le même PURL qu'un paquet affecté par un signal de sécurité et une version comprise dans la plage affectée, et NE DOIT PAS (MUST NOT) en créer pour les autres projets. Les versions Maven s'ordonnent selon `ComparableVersion`.
Source : ADR-0013 ; ADR-0011 (Proposés) ; baseline v2.3 §4 ; décision D5 du 2026-11-18 (Q-COR-3). Statut : [PROPOSITION].

#### Scenario: COR-01-S1 Version dans la plage affectée
- **GIVEN** un projet dont l'inventaire contient `pkg:maven/org.exemple/lib@1.2.0`
- **AND** un signal qui affecte `pkg:maven/org.exemple/lib` pour les versions inférieures à 1.3.0
- **WHEN** la corrélation de ce signal s'exécute
- **THEN** un impact relie le signal à ce projet et au composant concerné

#### Scenario: COR-01-S2 Version hors plage
- **GIVEN** un projet dont l'inventaire contient `pkg:maven/org.exemple/lib@1.3.0`
- **AND** un signal qui affecte ce paquet pour les versions inférieures à 1.3.0
- **WHEN** la corrélation de ce signal s'exécute
- **THEN** aucun impact n'est créé pour ce projet

#### Scenario: COR-01-S3 Dépendance transitive affectée
- **GIVEN** un projet dont le composant affecté n'est qu'une dépendance transitive
- **WHEN** la corrélation du signal s'exécute
- **THEN** un impact est créé pour ce projet
- **AND** l'impact indique que le composant est une dépendance transitive

#### Scenario: COR-01-S4 Projet sans le paquet
- **GIVEN** un projet dont l'inventaire ne contient aucun composant du paquet affecté
- **WHEN** la corrélation du signal s'exécute
- **THEN** aucun impact n'est créé pour ce projet

#### Scenario: COR-01-S5 Qualificatifs inconnus d'une version Maven
- **GIVEN** un projet dont l'inventaire contient `pkg:maven/com.google.guava/guava@32.0.0-jre`
- **AND** un signal qui affecte ce paquet pour les versions inférieures à `32.0.0-android`
- **WHEN** la corrélation de ce signal s'exécute
- **THEN** aucun impact n'est créé pour ce composant, `32.0.0-jre` suivant `32.0.0-android` dans l'ordre des versions Maven
- **AND** un composant `31.1-jre` du même paquet reçoit un impact

### Requirement: COR-02 Niveau de chaque impact
Le système DOIT (MUST) attribuer à chaque impact exactement un niveau, affiché avec l'impact : `exact` (PURL déclaré et version dans la plage, SBOM non obsolète), `probable` (même correspondance, inventaire de repli ou SBOM obsolète) ou `à vérifier` (correspondance sans PURL, version inconnue ou plage non interprétable).
Source : ADR-0013 (Proposé) ; décision du porteur du projet du 2026-11-16 (question Q-2). Statut : [PROPOSITION].

#### Scenario: COR-02-S1 Correspondance PURL et version établie
- **GIVEN** un impact créé selon COR-01 à partir d'une SBOM CycloneDX non obsolète
- **WHEN** un utilisateur autorisé consulte l'impact
- **THEN** l'impact porte le niveau `exact`

#### Scenario: COR-02-S2 Correspondance sur un inventaire de repli ou une SBOM obsolète
- **GIVEN** un projet dont l'inventaire provient des manifestes ou d'une SBOM générée il y a plus de 30 jours
- **AND** un signal dont le PURL et la plage correspondent à un composant de cet inventaire
- **WHEN** la corrélation de ce signal s'exécute
- **THEN** l'impact porte le niveau `probable`

#### Scenario: COR-02-S3 Correspondance sans PURL ou version inconnue
- **GIVEN** un signal dont le paquet affecté n'est identifié que par son nom, ou un composant dont la version est inconnue
- **WHEN** la corrélation de ce signal s'exécute
- **THEN** l'impact porte le niveau `à vérifier`

### Requirement: COR-03 Provenance de chaque impact
Le système DOIT (MUST) associer à chaque impact le signal source, le projet, le composant rapproché, l'inventaire utilisé avec sa date, la méthode d'analyse et sa provenance ; aucun impact NE DOIT (MUST NOT) exister sans cette provenance.
Source : arc42 §10.2 Q-01 ; QS-01 ; arc42 §1.4. Statut : [PROPOSITION].

#### Scenario: COR-03-S1 Consultation d'un impact
- **GIVEN** un impact créé par la corrélation
- **WHEN** un utilisateur autorisé ouvre cet impact
- **THEN** le signal source et sa provenance, le projet, le composant, l'inventaire utilisé et la méthode d'analyse sont affichés

#### Scenario: COR-03-S2 Contrôle d'intégrité
- **GIVEN** l'ensemble des impacts enregistrés
- **WHEN** le contrôle d'intégrité des impacts s'exécute
- **THEN** il ne trouve aucun impact sans signal source, sans projet ou sans méthode d'analyse

### Requirement: COR-04 Aucune alerte critique sur la seule suggestion de l'IA
Le système NE DOIT PAS (MUST NOT) déclencher une alerte critique à partir d'une suggestion produite par un modèle d'IA en l'absence de rapprochement déterministe.
Source : ADR-0013 (Proposé) ; arc42 §4.2 (P4). Statut : [PROPOSITION].

#### Scenario: COR-04-S1 Suggestion IA sans correspondance déterministe
- **GIVEN** une analyse IA qui suggère qu'un projet est concerné par un signal
- **AND** aucun composant de l'inventaire de ce projet ne satisfait COR-01
- **WHEN** les règles d'alerte sont évaluées pour ce projet
- **THEN** aucune alerte critique n'est émise pour ce projet

### Requirement: COR-05 Corrélation sans fournisseur d'IA
Le système DOIT (MUST) calculer et enregistrer les impacts déterministes lorsque le fournisseur d'IA est indisponible ou que le plafond budgétaire d'IA est atteint.
Source : baseline v2.3 §6 critère 6 ; QS-10 ; ADR-0013 (Proposé). Statut : [PROPOSITION].

#### Scenario: COR-05-S1 Fournisseur d'IA indisponible
- **GIVEN** un signal qui satisfait COR-01 pour un projet et le fournisseur d'IA indisponible
- **WHEN** la corrélation de ce signal s'exécute
- **THEN** l'impact est créé et consultable avec sa provenance

#### Scenario: COR-05-S2 Plafond budgétaire d'IA atteint
- **GIVEN** un signal qui satisfait COR-01 pour un projet et le plafond budgétaire d'IA atteint
- **WHEN** la corrélation de ce signal s'exécute
- **THEN** l'impact est créé et consultable avec sa provenance
- **AND** aucun appel au fournisseur d'IA n'est effectué

### Requirement: COR-06 Confiance réduite sans SBOM à jour
Le système DOIT (MUST) attribuer un niveau autre que `exact` à un impact calculé à partir d'un inventaire de repli ou d'une SBOM obsolète. Une SBOM est obsolète 30 jours après sa date de génération.
Source : ADR-0011 (Proposé, conséquences neutres) ; décision du 2026-11-16 (question Q-3). Statut : [PROPOSITION].

#### Scenario: COR-06-S1 Inventaire issu des manifestes
- **GIVEN** un projet dont l'inventaire provient du repli sur les manifestes
- **AND** un signal dont le paquet et la plage correspondent à une dépendance déclarée
- **WHEN** la corrélation de ce signal s'exécute
- **THEN** l'impact est créé avec un niveau autre que `exact`

### Requirement: COR-07 Idempotence de la corrélation
Le système DOIT (MUST) garantir qu'une nouvelle exécution de la corrélation pour un même signal, un même projet et un même composant ne crée pas de second impact.
Source : arc42 §6.1 (invariants) ; QS-04. Statut : [PROPOSITION].

#### Scenario: COR-07-S1 Corrélation rejouée
- **GIVEN** un impact existant pour un signal, un projet et un composant
- **WHEN** la corrélation de ce signal est exécutée de nouveau sans changement d'inventaire
- **THEN** un seul impact existe pour ce triplet

### Requirement: COR-08 Visibilité des impacts limitée au périmètre autorisé
Le système DOIT (MUST) ne restituer à un utilisateur que les impacts des projets de son périmètre autorisé, et journaliser toute tentative d'accès à un impact hors de ce périmètre.
Source : ADR-0016 (Proposé) ; arc42 §6.1 ; arc42 §8.1. Statut : [PROPOSITION].

#### Scenario: COR-08-S1 Liste des impacts filtrée
- **GIVEN** un signal qui impacte un projet de l'espace A et un projet de l'espace B
- **AND** un utilisateur autorisé uniquement sur l'espace A
- **WHEN** cet utilisateur consulte les impacts du signal
- **THEN** seul l'impact du projet de l'espace A est restitué

#### Scenario: COR-08-S2 Accès direct à un impact d'un autre espace
- **GIVEN** un utilisateur autorisé uniquement sur l'espace A
- **WHEN** il demande directement un impact d'un projet de l'espace B
- **THEN** la demande est refusée sans révéler le contenu de l'impact
- **AND** le refus est journalisé avec l'utilisateur, la ressource demandée et la date
