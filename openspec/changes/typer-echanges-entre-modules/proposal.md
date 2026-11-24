# Proposition : typer les échanges entre modules

<!-- Statuts : [EXIGENCE] [EXISTANT] [DÉCISION] [PROPOSITION] [QUESTION] — légende : /docs/specifications/README.md#statuts-des-informations. -->

## Why — Pourquoi

Les modules du serveur échangent encore des valeurs fermées sous forme de chaînes : le niveau d'impact circule en `String` alors que l'énumération `MatchRules.Level` existe ; le moteur d'alertes compare des littéraux (`"A_VERIFIER"`, `case "EXACT"`) et range toute valeur inconnue au dernier rang par une branche `default` ; la clé « fréquence:canal » des résumés est fabriquée par concaténation puis découpée par `split(":")` ; les statuts d'import d'inventaire et d'analyse complémentaire de la corrélation sont des chaînes, et l'import planifié compte toute valeur inattendue comme « conservée » par un `default` silencieux [EXISTANT — AUD-QUA-08, /docs/audit/2026-11-21/findings.md#aud-qua-08, vérification du 21/11 à `b142695`, preuves relues le 2026-11-24]. Une faute de frappe ou un renommage n'est détecté qu'à l'exécution, ou pas du tout. Le sprint 5 en fait un critère de sortie : le niveau circule sous l'énumération de bout en bout, les statuts sont des énumérations sans branche `default` silencieuse, la clé n'est plus découpée [EXISTANT — /docs/audit/2026-11-21/sprints.md, « Sprint 5 »].

## What Changes — Changements

- Le niveau d'impact est typé par l'énumération du moteur d'impact (`EXACT`, `PROBABLE`, `A_VERIFIER`) depuis la lecture en base jusqu'aux vues REST, MCP et au contenu des alertes ; le rang des niveaux dans les alertes est calculé sur l'énumération, sans branche par défaut.
- La clé d'un résumé devient un record (fréquence, canal) ; plus de concaténation ni de `split`.
- Le statut d'un import d'inventaire (`IMPORTED`, `REJECTED`, `KEPT`, `NONE`, `ERROR`, `DEFERRED`) et le statut de l'analyse complémentaire d'une corrélation (`NONE`, `BUDGET_EXHAUSTED`, `DONE`, `UNAVAILABLE`) deviennent des énumérations ; les `switch` sur ces statuts sont exhaustifs, sans `default`.
- Contrat REST et MCP : le JSON échangé est inchangé, chaque énumération étant sérialisée par son nom, identique aux chaînes actuelles. Le document OpenAPI décrit désormais `level` comme une énumération de trois valeurs au lieu d'une chaîne libre : précision du schéma, pas de changement des valeurs sur le fil (**BREAKING** : aucun, à confirmer par la comparaison de la tâche 4.2).

## Capabilities — Capacités

### New Capabilities — Nouvelles capacités

Aucune.

### Modified Capabilities — Capacités modifiées

Aucune — refactoring de typage sans comportement contractuel (`skip_specs: true` dans `.openspec.yaml`). Les exigences existantes COR-02 (niveau de chaque impact), ALR-02 (pas d'alerte immédiate sur un impact à vérifier), ALR-04 (résumés périodiques) et INV-07 (import périodique) doivent rester vérifiées par leurs tests.

## Périmètre

### Inclus

- `argos-server` : `impact` (énumération du niveau, vue d'impact, statut d'analyse de la corrélation), `alerting` (moteur d'alertes, contenu des alertes, clé de résumé), `inventory` (statut d'import, import planifié), `api` et `mcp` (vues qui reprennent le niveau).
- Tests unitaires et d'intégration correspondants ; comparaison du document OpenAPI.

### Exclu

- Le type TypeScript `level: string` de l'IHM (`argos-ui/src/api/impacts-modele.ts:41`) : il reste compatible ; la génération des types depuis OpenAPI relève d'AUD-QUA-13.
- Les autres chaînes d'état (`status` de l'inventaire exposé `AVAILABLE` / `NO_SBOM`, état de traitement d'un impact) : hors constat.
- Le record `ResumeObtenu` du résumé IA, déjà introduit (commit `12c541f`).
- Toute migration de schéma : la colonne `project_impact.level` et sa contrainte `CHECK` restent inchangées.

## Acteurs et parties prenantes

| Acteur | Rôle dans ce changement | Statut |
|---|---|---|
| Développeur, agent de codage | implémente le typage, écrit les tests | [PROPOSITION] |
| Client REST ou MCP (IHM, desktop, assistant) | reçoit le même JSON | [PROPOSITION] — à vérifier (tâche 4.2) |
| Exploitant | lit le même bilan d'import planifié dans les journaux | [PROPOSITION] |

## Sources et preuves

| Élément | Statut | Source |
|---|---|---|
| Énumération du niveau | [EXISTANT] | `argos-server/src/main/java/io/argos/impact/MatchRules.java:27` (`enum Level { EXACT, PROBABLE, A_VERIFIER }`) |
| Niveau en chaîne dans la vue d'impact | [EXISTANT] | `impact/ImpactQueries.java:46` (`String level`) ; repris par `api/veille/VeilleProfilsResource.java:197, :231`, `api/WatchResource.java:68`, `mcp/OutilsSecurite.java:375, :399`, `alerting/ContenuAlerte.java:42` (`String niveau`) |
| Littéraux et défaut silencieux du moteur d'alertes | [EXISTANT] | `alerting/MoteurAlertes.java:250` (`"A_VERIFIER".equals`), `:291-293` (`case "EXACT" -> 0; case "PROBABLE" -> 1; default -> 2`) |
| Clé « fréquence:canal » | [EXISTANT] | `alerting/MoteurAlertes.java:243` (concaténation), `:199` (`cle.split(":")`) |
| Statut d'analyse de la corrélation | [EXISTANT] | `impact/CorrelationEngine.java:147-159` ; record `CorrelationReport` (`:65`), utilisé seulement dans `impact` et les tests |
| Statut d'import et défaut silencieux | [EXISTANT] | `inventory/InventoryService.java:37-42` (valeurs documentées), `inventory/ImportPlanifie.java:81-86` (`default -> conserves++`) ; `ImportResult` n'est utilisé que dans `inventory` |
| Valeurs du niveau en base | [EXISTANT] | `db/migration/V5__impacts_projet.sql:12` (`CHECK (level IN ('EXACT', 'PROBABLE', 'A_VERIFIER'))`) |
| Contenu des alertes conservé en JSON | [EXISTANT] | `alerting/MoteurAlertes.java:425` (écriture), `alerting/ConsultationAlertes.java:131` et `alerting/EnvoiAlertes.java:198` (relecture) |
| Niveaux exact / probable / à vérifier | [EXIGENCE] | COR-02 (`/openspec/specs/correlation-impact/spec.md`) ; ADR-0013 (Proposé, acceptation soumise par `ecrire-decisions-architecture-socle`) |
| Pas d'alerte immédiate sur un impact à vérifier | [EXIGENCE] | ALR-02 (`/openspec/specs/alertes-digests/spec.md`) |

## Écarts constatés

- **Preuve qui a bougé** : AUD-QUA-08 citait aussi le couple `String[] {texte, état}` du résumé IA ; il est remplacé par le record `ResumeObtenu` (`alerting/MoteurAlertes.java:302`, commit `12c541f`), constaté par la vérification du 21/11. Aucun test ne verrouille ce record (réserve de l'audit) ; il n'est pas repris ici.
- Les numéros de ligne de la preuve d'origine (`ImpactQueries.java:34`, `MoteurAlertes.java:191`, `:163`, `CorrelationEngine.java:106-121`, `InventoryService.java:30`) ont bougé ; les lignes à jour sont celles de « Sources et preuves ».
- L'import planifié range `KEPT` et `NONE` dans le même compteur « conservés » par la branche `default` ; le typage rend ce choix explicite (Q-1).

## Impact

- **Composants** (arc42 §5) : `impact-engine`, `alerting`, `inventory`, `API & Security` (vues de veille), `mcp-server`.
- **Contrats d'interface** : JSON REST et MCP inchangé (noms d'énumération identiques aux chaînes) ; le schéma OpenAPI de `level` gagne une énumération (précision) ; le schéma des sorties structurées MCP peut faire de même. Aucun événement CDI ne change de forme publique hormis le type Java des champs.
- **Données et persistance** : aucune migration ; les contenus d'alerte déjà stockés en JSON se relisent (mêmes valeurs).
- **Sécurité, confidentialité, droits** : aucun changement.
- **Exploitation et observabilité** : bilan de l'import planifié inchangé dans les journaux (Q-1).
- **Documentation durable à mettre à jour** : arc42 §8.16 (conventions de code : valeurs fermées échangées entre modules typées par énumération) ; aucune fiche de capacité, aucun diagramme.
- **ADR** : aucun ADR modifié ; aucune décision candidate (décision locale de typage).
- **Dépendances entre changes** : après `extraire-noyau-commun` (ordre du sprint 5 ; mêmes fichiers `impact/ImpactQueries.java` et `alerting/MoteurAlertes.java`, migrés vers l'utilitaire JDBC par ce changement). Aucune dépendance fonctionnelle envers `ecrire-decisions-architecture-socle`.

## Conception requise

allégée — refactoring local à trois modules, sans nouveau composant ni contrat ; la conception fixe l'emplacement des types, la règle d'exhaustivité et la vérification du contrat.

## Questions ouvertes

| ID | Question | Bloquante ? | Destinataire |
|---|---|---|---|
| Q-1 | L'import planifié doit-il compter `NONE` (aucune source) à part de `KEPT` (inventaire conservé) dans son bilan, au lieu de les cumuler comme aujourd'hui ? | non : le comportement actuel est conservé à l'identique par défaut | porteur du projet |
