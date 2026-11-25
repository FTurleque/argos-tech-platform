# Proposition : isoler les erreurs imprévues aux frontières de traitement

<!-- Statuts : [EXIGENCE] [EXISTANT] [DÉCISION] [PROPOSITION] [QUESTION] — légende : /docs/specifications/README.md#statuts-des-informations. -->

## Why — Pourquoi

Le lot 23 a remplacé 51 captures `RuntimeException`/`Exception` par des captures de `IllegalStateException` dans 29 fichiers, sans test des chemins d'échec ([EXISTANT] — commits `c4c8eb7`, `c341b49`, `926d486`, `13baff7`, `a7fbf21`, `488e2fa` ; `git grep -c 'catch (RuntimeException'` : 47 à `54add78`, 0 à `b142695`). Toute autre erreur d'exécution traverse désormais les frontières qui isolaient les pannes : un avis OSV ou une SBOM portant un PURL mal encodé (`pkg:npm/%zz@1.0.0`, donnée externe) lève une `IllegalArgumentException` qui interrompt la synchronisation des sources suivantes ou l'import des projets suivants, laisse le traitement `RUNNING` et n'amène jamais la source en `DEGRADED` ; la chaîne du radar fait échouer `PUT /standards` après l'enregistrement ; la garde MCP ne répond plus `INDISPONIBLE` à une erreur imprévue. Le constat AUD-QUA-20 (Élevée, sprint 4) le démontre au 2026-11-21 ; AUD-QUA-23 (Faible, sprint 4) relève une journalisation sans pile dans le moteur d'alertes ; aucune règle de gestion d'erreur n'est écrite en arc42 §8 [EXISTANT — `/docs/audit/2026-11-21/findings.md`, blocs AUD-QUA-20, AUD-QUA-23 et AUD-QUA-14].

## What Changes — Changements

- Une erreur imprévue pendant la synchronisation d'une source d'avis termine cette synchronisation en échec (visible, comptée pour `DEGRADED`) et n'empêche pas la synchronisation des sources suivantes.
- Une erreur imprévue pendant l'import planifié de l'inventaire d'un projet est comptée en échec et n'empêche pas l'import des projets suivants.
- Un PURL mal encodé est traité comme un PURL illisible, selon les règles déjà en place pour chaque entrée (avis, SBOM, saisie d'un standard, paramètre MCP).
- `PUT /standards` refuse une clé de paquet mal encodée par une erreur 400 et ne répond plus en erreur après avoir enregistré la modification.
- Toute erreur interne d'un outil MCP est journalisée avec sa cause et l'appel inscrit en erreur ; une panne d'accès aux données répond `INDISPONIBLE`, une erreur imprévue est relancée (erreur de protocole sans message interne), jamais traduite en `INDISPONIBLE`.
- Les autres boucles planifiées (alertes, radar, cycles de vie, fiches réglementaires, activité des projets) isolent chaque unité puis relancent la première erreur imprévue ; les observateurs synchrones traitent la panne d'accès aux données et propagent l'erreur imprévue ; la passerelle IA ne traduit en « modèle indisponible » que les pannes prévues du fournisseur ; la cause est journalisée avec sa pile, y compris pour le résumé IA indisponible des alertes.
- Décision du porteur du projet (2026-11-24) : « isoler puis relancer » — à chaque frontière, l'unité fautive est journalisée et comptée en échec, les suivantes sont traitées, puis la première erreur imprévue est relancée ; aucune erreur imprévue n'est absorbée.
- La règle des frontières d'erreur est écrite dans arc42 §8.
- Aucune rupture de contrat (**BREAKING** : aucun) ; le seul changement visible d'une API est le passage de 500 à 400 pour une clé mal encodée.

## Capabilities — Capacités

### New Capabilities — Nouvelles capacités

Aucune.

### Modified Capabilities — Capacités modifiées

- `collecte-avis-securite` : exigence ajoutée COL-11 (un avis mal formé ou une erreur imprévue n'arrête que sa source). COL-01 à COL-10 inchangées.
- `inventaire-sbom` : exigence ajoutée INV-11 (une SBOM fautive ou une erreur imprévue n'arrête que son projet). INV-01 à INV-10 inchangées.
- `radar-standards` : exigence ajoutée RAD-07 (clé mal encodée refusée en 400 ; aucune erreur renvoyée après l'enregistrement). RAD-01 à RAD-06 inchangées.
- `serveur-mcp` : exigence ajoutée MCP-08 (toute erreur interne journalisée et inscrite en erreur ; `INDISPONIBLE` pour une panne d'accès aux données, erreur imprévue relancée). MCP-01 à MCP-07 inchangées.

## Périmètre

### Inclus

- Les frontières citées par AUD-QUA-20 : synchronisation d'une source et synchronisation planifiée (`source-ingestion`, `signal`), import planifié des inventaires (`inventory`), observateurs synchrones du radar et des alertes (`impact-engine`, `alerting`), garde des outils MCP (`mcp-server`), boucles de `release-radar`, `reg-intel`, `project-intel`, appel du fournisseur par `ai-gateway`.
- Le décodage des clés de paquet (`catalog`).
- La journalisation de la cause avec sa pile (AUD-QUA-23, tâche seulement).
- La règle des frontières d'erreur dans arc42 §8, et les tests d'une erreur imprévue par frontière.

### Exclu

- La reprise de l'enrichissement différé (`retryMissing` sans appelant) et le passage asynchrone de l'enrichissement et de la corrélation : AUD-PER-02, sprint 8.
- Les métriques et journaux structurés : change `instrumenter-observabilite` (AUD-DEP-07, même sprint).
- La refonte des exceptions en un type de panne dédié : soumise au porteur du projet ([QUESTION] Q-1) ; ce changement fonctionne avec ou sans.

## Acteurs et parties prenantes

| Acteur | Rôle dans ce changement | Statut |
|---|---|---|
| Exploitant | voit une source en échec puis `DEGRADED`, et un bilan d'import avec les projets en échec, au lieu d'une simple trace du planificateur | [EXIGENCE] — COL-07 |
| Administrateur d'espace | reçoit 400 sur une clé mal encodée ; sa modification enregistrée n'est plus suivie d'une erreur | [EXIGENCE] — RAD-01, RAD-02 |
| Utilisateur d'un assistant IA (MCP) | reçoit `INDISPONIBLE` pour une panne d'accès aux données, une erreur de protocole sans détail interne pour une erreur imprévue | [PROPOSITION] — codes d'erreur de la fiche `serveur-mcp` |
| Porteur du projet | tranche Q-1 (type de panne dédié) et valide la règle d'arc42 §8 | [PROPOSITION] |

## Sources et preuves

| Élément | Statut | Source |
|---|---|---|
| Mise à l'écart d'un avis non normalisable et poursuite du lot | [PROPOSITION] (spec principale) | COL-08, `/openspec/specs/collecte-avis-securite/spec.md` |
| Source `DEGRADED` après le seuil d'échecs | [PROPOSITION] (spec principale) | COL-07 |
| Forge en erreur pour un dépôt : les autres projets sont importés | [PROPOSITION] (spec principale) | INV-07-S2, `/openspec/specs/inventaire-sbom/spec.md` |
| Saisie invalide refusée sans rien modifier | [EXIGENCE] | RAD-01-S2, `/openspec/specs/radar-standards/spec.md` |
| « Une erreur sur un produit ou un espace est journalisée sans interrompre les autres » | [EXIGENCE] (fiche) | `/docs/specifications/radar-standards.md:42` |
| Code d'erreur d'outil `INDISPONIBLE`, sans donnée partielle | [PROPOSITION] (fiche) | `/docs/specifications/serveur-mcp.md:46` |
| Pas de suppression silencieuse d'erreur | [EXIGENCE] | arc42 §11.2 (citée par COL-08) |
| Synchronisation : seule `IllegalStateException` est capturée | [EXISTANT] | `argos-server/src/main/java/io/argos/ingestion/SourceSynchronizer.java:95`, `:132` (seule `AdvisoryRejectedException` dans `runOnce`) ; `signal/SynchronisationPlanifiee.java:67` |
| Import planifié : seule `IllegalStateException` est capturée par projet | [EXISTANT] | `inventory/ImportPlanifie.java:66`, `:89` ; `inventory/InventoryService.java:233` (seule `InvalidDocumentException`) |
| Décodage d'un PURL mal encodé : `IllegalArgumentException` | [EXISTANT] | `catalog/PackageKey.java:47`, `:60-61`, `:125-126` (`URLDecoder.decode`) ; essai du constat `PackageKey.parse("pkg:npm/%zz@1.0.0")` |
| Chemins du PURL : avis, SBOM, saisie de standard, paramètre MCP | [EXISTANT] | `signal/SignalRecorder.java:307` ; `inventory/CycloneDxParser.java:125` ; `catalog/standard/SaisieStandard.java:40` ; `mcp/Parametres.java:22` |
| Aucune correspondance d'erreur pour `IllegalArgumentException` dans l'API | [EXISTANT] | `api/GestionErreurs.java:39-84` (correspondances : `AccesRefuse`, `InscriptionRefusee`, `Introuvable`, `RegleViolee`, `SubscriptionRejectedException`, `DonneeInvalide`, `LimiteDebitAtteinte`) |
| Radar : annonce déclenchée après l'enregistrement, observateurs ne capturant que `IllegalStateException` | [EXISTANT] | `catalog/standard/ReferentielEspace.java:101-104`, `:219` ; `impact/RadarProjets.java:198` ; `alerting/RappelsRadar.java:158` |
| Garde MCP : seule `IllegalStateException` mène à `INDISPONIBLE` et à `Issue.ERREUR` | [EXISTANT] | `mcp/GardeAppels.java:117-121` ; aucun `@WrapBusinessError` dans `argos-server` |
| Résumé IA indisponible journalisé sans pile | [EXISTANT] | `alerting/MoteurAlertes.java:342` ; règle suivie ailleurs : `projectintel/SyntheseActivite.java:104`, `impact/CorrelationEngine.java:160` |
| Aucune règle de gestion d'erreur en arc42 | [EXISTANT] | `grep -rn "IllegalStateException\|capture précise" docs/architecture` : aucun résultat (2026-11-24) |
| Critère de sortie du sprint 4 | [PROPOSITION] (plan d'audit) | `/docs/audit/2026-11-21/sprints.md`, sprint 4 |

## Écarts constatés

- Les preuves du constat sont aux mêmes lignes à `b142695` (relu le 2026-11-24) : `SourceSynchronizer.java:95`, `SynchronisationPlanifiee.java:67`, `ImportPlanifie.java:66,89`, `PackageKey.java:47,60,61,126`, `GardeAppels.java:117`, `RadarProjets.java:198`, `RappelsRadar.java:158`, `MoteurAlertes.java:141,342`.
- Le chemin de la saisie d'un standard (`SaisieStandard.java:40`, appelé avant l'enregistrement) n'est pas cité par le constat : relevé dans le code, il explique la réponse attendue « 400 » du critère de sortie du sprint 4 ; aujourd'hui l'`IllegalArgumentException` qui en sort n'a aucune correspondance dans `GestionErreurs` (réponse 500 attendue, non mesurée).
- Le constat propose comme test « SBOM avec PURL mal encodé : projet refusé ». La règle en place pour un PURL illisible dans une SBOM est d'écarter le composant et de poursuivre l'import (`CycloneDxParser.java:125`, `:138-141`, décision D6 du 2026-11-18) ; ce changement aligne le PURL mal encodé sur cette règle (design.md, D2). Écart avec le texte du constat soumis au porteur du projet ([QUESTION] Q-2).
- La Javadoc de `GardeAppels` (`:44-45`) ne promet plus `INDISPONIBLE` qu'en cas de « panne d'accès aux données », alors que la fiche `serveur-mcp` le donne comme code d'erreur générique.
- Le test `chain/ResilienceTest.java:216-223` a été renommé pour ne simuler qu'une `IllegalStateException` ; aucun test d'une autre erreur d'exécution, aucun test de `INDISPONIBLE` ni de `Bilan.enEchec` (constat AUD-QUA-20).

## Impact

- **Composants** (arc42 §5) : `source-ingestion`, `signal`, `inventory`, `catalog`, `impact-engine`, `alerting`, `mcp-server`, `release-radar`, `reg-intel`, `project-intel`, `ai-gateway`, `API & Security` (correspondance d'erreur, si retenue en D2).
- **Contrats d'interface** : `PUT /api/v1/radar/espaces/{espaceId}/standards` répond 400 `DONNEE_INVALIDE` (champ `cle`) pour une clé mal encodée, au lieu d'une erreur serveur ; outils MCP : `INDISPONIBLE` pour une panne d'accès aux données (code déjà documenté), erreur de protocole pour une erreur imprévue. OpenAPI : réponse 400 déjà déclarée par l'opération (vérifié, tâche 4.1).
- **Données et persistance** : aucune migration ; une synchronisation interrompue finit `FAILED` au lieu de rester `RUNNING`.
- **Sécurité, confidentialité, droits** : aucune modification des droits ; les messages renvoyés ne contiennent pas le détail de l'erreur interne.
- **Exploitation et observabilité** : sources en échec et `DEGRADED` de nouveau visibles ; bilans de passage avec les éléments en échec ; journaux d'erreur avec pile.
- **Documentation durable à mettre à jour** : fiches `/docs/specifications/collecte-avis-securite.md`, `inventaire-sbom.md`, `radar-standards.md`, `serveur-mcp.md` ; registre `/docs/specifications/README.md` ; arc42 §8.5 (règle des frontières d'erreur et de journalisation de la cause) et §8.16 si la règle y est référencée.
- **ADR** : aucun ADR modifié. La règle des frontières d'erreur est une convention transverse ; candidate ADR (Proposé) seulement si le porteur retient un type de panne dédié (Q-1).
- **Dépendances entre changes** : `mettre-en-place-integration-continue` (sprint 1) exécute les tests de chaque frontière à chaque intégration et aurait arrêté la régression (AUD-TST-01) ; `instrumenter-observabilite` (même sprint, placé avant dans l'ordre interne) fournit métriques et contrôles de santé des tâches planifiées, qui consommeront les bilans en échec ; `injecter-horloge-et-executeurs` (même sprint) touche des classes communes (`MoteurAlertes`, `PasserelleIa`, tâches planifiées) : intégrer l'un après l'autre pour éviter les conflits. `extraire-noyau-commun` (sprint 5) pourra accueillir le type de panne dédié si Q-1 le retient.

## Conception requise

complète — changement transverse (onze composants), qui fixe une règle d'architecture (frontières d'erreur) et modifie l'état visible des traitements planifiés et la réponse d'une API.

## Questions ouvertes

| ID | Question | Bloquante ? | Destinataire |
|---|---|---|---|
| Q-1 | Faut-il introduire un type de panne d'accès aux données dédié (par exemple une sous-classe de `IllegalStateException`) pour distinguer cette panne des autres erreurs à l'intérieur des frontières, comme le suggère le constat ? 35 des 147 `new IllegalStateException(` ne viennent pas d'une erreur SQL | tranchée le 2026-11-24 : pas de type dédié dans ce changement | Porteur du projet |
| Q-2 | Une SBOM portant un PURL mal encodé doit-elle voir ce composant écarté (règle actuelle d'un PURL illisible) ou l'import du projet refusé (texte du constat) ? | tranchée le 2026-11-24 : composant écarté et signalé (règle actuelle) | Porteur du projet |
