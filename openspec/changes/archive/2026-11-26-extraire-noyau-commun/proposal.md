# Proposition : extraire le noyau commun du serveur

<!-- Statuts : [EXIGENCE] [EXISTANT] [DÉCISION] [PROPOSITION] [QUESTION] — légende : /docs/specifications/README.md#statuts-des-informations. -->

## Why — Pourquoi

Les mécanismes techniques que tous les modules partagent sont logés dans `workspace` et `api.security`, ou recopiés : le test des frontières compte cinq règles d'exception au cas par cas (AUD-ARC-05) ; le protocole RLS « mécanisme unique » est écrit quatre fois, dont deux variantes qui ne réinitialisent ni le sujet ni le courriel courants (AUD-ARC-04) ; six transactions manuelles `setAutoCommit(false)` coexistent avec 90 `@Transactional` et 23 `QuarkusTransaction`, et l'une ne restaure pas l'autocommit (AUD-QUA-04) ; le squelette JDBC est recopié (272 `prepareStatement` dans 54 fichiers, 130 `catch (SQLException`, une dizaine de copies du même convertisseur de date) (AUD-QUA-06) ; 13 tables sont lues par plusieurs modules sans que leur propriétaire soit documenté, dont trois lectures qu'aucune dépendance autorisée ne couvre (AUD-ARC-06) [EXISTANT — /docs/audit/2026-11-21/findings.md, blocs AUD-ARC-05, AUD-ARC-04, AUD-QUA-04, AUD-QUA-06, AUD-ARC-06, vérification du 21/11 à `b142695`, preuves relues le 2026-11-24]. Le sprint 5 fixe le critère de sortie : un module transverse unique porte le contexte RLS, le modèle transactionnel et l'utilitaire JDBC ; aucune autre classe ne pose `SET LOCAL ROLE` ni `setAutoCommit` ; la propriété de chaque table est documentée et les lectures croisées sont vérifiées par un test [EXISTANT — /docs/audit/2026-11-21/sprints.md, « Sprint 5 »].

## What Changes — Changements

- Créer un paquet transverse du serveur (« noyau commun », nom proposé `io.argos.commun`) autorisé à tous les modules et ne dépendant d'aucun module métier ; y déplacer le contexte RLS, les erreurs du domaine partagées (`DonneeInvalide`, `RegleViolee`, `Introuvable`, `AccesRefuse`, `MotifRefus`) et, dans un sous-paquet de sécurité, l'appelant et le limiteur de débit (AUD-ARC-05).
- Un seul point d'application du contexte RLS : supprimer `impact/RlsSession` et les deux variantes partielles de `workspace` ; tous les contextes posent les quatre paramètres (compte, sujet, courriel, espace) (AUD-ARC-04).
- Un seul modèle transactionnel : tout en JTA, le noyau commun ouvrant la transaction et appliquant le contexte RLS ; plus aucun `setAutoCommit` dans le serveur (AUD-QUA-04) [DÉCISION — décision du porteur, 2026-11-24 ; design.md, D2].
- Un seul utilitaire d'accès JDBC interne, sans dépendance nouvelle, et des convertisseurs communs (dates, troncature) ; migration d'abord des modules cœur `signal`, `impact`, `alerting` (AUD-QUA-06) [DÉCISION — décision du porteur, 2026-11-24 ; design.md, D3].
- Documenter dans arc42 §5.3 le module propriétaire de chaque table ; faire passer les trois lectures non couvertes par le propriétaire (service de lecture ou vue SQL nommée) ; ajouter un test qui vérifie que chaque module ne cite que ses tables ou des vues publiées (AUD-ARC-06).
- Règles d'architecture : retirer les cinq règles d'exception et les entrées de la table des dépendances devenues inutiles ; interdire `setAutoCommit` et les chaînes `SET LOCAL ROLE` / `set_config('argos.` hors du noyau commun.
- Comportement observable inchangé : mêmes réponses REST et MCP, mêmes politiques RLS, mêmes migrations (**BREAKING** : aucun).

## Capabilities — Capacités

### New Capabilities — Nouvelles capacités

Aucune.

### Modified Capabilities — Capacités modifiées

Aucune — refactoring sans comportement contractuel (`skip_specs: true` dans `.openspec.yaml`). Les exigences de cloisonnement existantes (ACC-04, COR-08) doivent rester vérifiées par leurs tests.

## Périmètre

### Inclus

- `argos-server` : paquets `workspace`, `api.security`, `impact`, `signal`, `alerting`, `catalog`, `projectintel`, `regintel`, `inventory`, `aigateway`, `releaseradar`, `ingestion`, `vulnintel`, `mcp`, `api` pour les déplacements et la migration des accès.
- Tests d'architecture : `ModuleBoundariesTest`, `PreferencesIsolationTest`, nouveaux tests (contexte RLS, transactions, propriété des tables).
- arc42 §5.3 (propriété des tables, nouveau composant), §5.5 (règles de dépendance), §8.7 (règle d'accès aux données et modèle transactionnel).

### Exclu

- Toute migration de schéma ou de politique RLS : les politiques de V2 et suivantes restent inchangées ; seule une vue SQL nommée peut être ajoutée si D4 la retient (nouvelle migration, numéro libre à la date de l'implémentation).
- La fabrique de clients HTTP commune (AUD-ARC-10 la place dans le même module) : hors sprint 5.
- La réécriture d'arc42 au-delà de §5.3, §5.5 et §8.7 (AUD-DOC-02, sprint 12).
- La migration de tous les modules vers l'utilitaire JDBC : seuls les modules cœur sont migrés ici ; les autres le sont au fil de leurs changements (D3).

## Acteurs et parties prenantes

| Acteur | Rôle dans ce changement | Statut |
|---|---|---|
| Porteur du projet | a choisi le modèle transactionnel (D2) et l'outil JDBC (D3) ; accepte ou non l'ADR du noyau commun | [DÉCISION] — décision du porteur, 2026-11-24 (D2, D3) ; [QUESTION] — Q-1 |
| Développeur, agent de codage | implémente le noyau, migre les consommateurs, écrit les tests d'architecture | [PROPOSITION] |
| Utilisateur | aucun effet visible | [PROPOSITION] |

## Sources et preuves

| Élément | Statut | Source |
|---|---|---|
| Cinq règles d'exception du test des frontières | [EXISTANT] | `argos-server/src/test/java/io/argos/architecture/ModuleBoundariesTest.java:93, :125, :144, :164, :191` ; AUD-ARC-05 |
| Classes de `workspace` importées hors du module (16 fichiers pour `DonneeInvalide` et `ContexteBase`) | [EXISTANT] | AUD-ARC-05 (comptage `grep -rl` à `b142695`) |
| Quatre écritures de `SET LOCAL ROLE argos_app` | [EXISTANT] | `workspace/ContexteBase.java:78`, `impact/RlsSession.java:24`, `workspace/subscription/SubscriptionService.java:335`, `workspace/preference/PreferencesRepository.java:116` (relu le 2026-11-24) |
| Six transactions manuelles | [EXISTANT] | `setAutoCommit(false)` : `workspace/ContexteBase.java:55`, `aigateway/QuotasIa.java:86`, `impact/RadarProjets.java:207`, `impact/RapprochementActualites.java:143`, `regintel/PreparationFiches.java:154`, `releaseradar/CyclesDeVie.java:101` (relu le 2026-11-24) |
| 90 `@Transactional`, 23 `QuarkusTransaction.` | [EXISTANT] | `grep -rn` sous `argos-server/src/main/java` (relu le 2026-11-24) |
| Contexte RLS déjà posé dans une transaction JTA | [EXISTANT] | `impact/ImpactQueries.java:111-117` (`@Transactional` puis `RlsSession.account`) ; `ContexteBase.java:73` (« transaction JTA comprise ») |
| 272 `prepareStatement`, 130 `catch (SQLException` | [EXISTANT] | `grep -rn` sous `argos-server/src/main/java` (relu le 2026-11-24) ; AUD-QUA-06 |
| 13 tables multi-lecteurs, trois lectures non couvertes | [EXISTANT] | AUD-ARC-06 ; `impact/ImpactQueries.java:322` (`LEFT JOIN compte`), `catalog/dictionnaire/DictionnaireMetier.java:191` (`FROM appartenance`), `alerting/Destinataires.java:52` (`JOIN workspace_preference_utilisateur`) |
| arc42 affirme que les préférences ne sont lues que par `api` et `mcp` | [EXISTANT] | `/docs/architecture/arc42/05-vue-blocs.md` §5.4.1 (ligne 162) ; contredit par `alerting/Destinataires.java:52` |
| Monolithe modulaire et frontières par test | [PROPOSITION] | ADR-0001 (Proposé ; acceptation soumise par `ecrire-decisions-architecture-socle`) |
| RBAC + RLS, rôle `argos_app` | [PROPOSITION] | ADR-0016 (Proposé ; idem) ; `V2__comptes_et_acces.sql` |
| Licence des dépendances livrées | [PROPOSITION] | ADR-0021 point 4 (Proposé) ; contrôle bloquant `argos-server/pom.xml:221` (profil `licences`) |
| Modèle transactionnel tout en JTA ; utilitaire JDBC interne sans dépendance nouvelle | [DÉCISION] | décision du porteur, 2026-11-24 (design.md, D2, D3) |
| Noyau commun | [PROPOSITION] | ADR du noyau commun, rédigé par `ecrire-decisions-architecture-socle` (Proposé) |

## Écarts constatés

- `regintel/PreparationFiches.java:154` passe en `setAutoCommit(false)` sans restaurer l'autocommit en `finally`, contrairement aux cinq autres copies (AUD-QUA-04, réserve du 21/11) ; toujours vrai le 2026-11-24.
- `workspace/preference/PreferencesRepository.java:120` convertit le compte par `compte.toString()` (exception si le compte est nul) là où les autres copies passent une chaîne vide (AUD-ARC-04).
- `private static Instant instant(ResultSet` : 10 occurrences de cette signature exacte le 2026-11-24, l'audit en relève 11 avec les variantes de signature ; l'écart vient du motif de recherche, pas d'une correction.
- arc42 §5.4.1 contredit le code sur la lecture des préférences (voir « Sources et preuves ») ; corrigé par la tâche de propriété des tables.

## Impact

- **Composants** (arc42 §5) : nouveau composant transverse « commun » (noyau commun) ; modifiés : `workspace`, `API & Security` (`api.security`), `impact-engine`, `signal`, `alerting`, `catalog`, `project-intel`, `reg-intel`, `inventory`, `ai-gateway`, `release-radar`, `mcp-server`.
- **Contrats d'interface** : aucun changement ; OpenAPI, outils MCP et événements CDI identiques.
- **Données et persistance** : aucune modification de table ni de politique ; une vue SQL nommée possible pour une lecture étrangère (D4), en nouvelle migration Flyway (ADR-0032, Accepté).
- **Sécurité, confidentialité, droits** : le contexte RLS devient unique et complet (sujet et courriel réinitialisés partout) ; les tests de cloisonnement existants restent la preuve de non-régression.
- **Exploitation et observabilité** : aucune.
- **Documentation durable à mettre à jour** : arc42 §5.3, §5.4.1, §5.5, §8.7 ; diagramme de composants du serveur (arc42 §5.2) et inventaire `/docs/architecture/diagrams/README.md` ; aucune fiche de capacité.
- **ADR** : ADR du noyau commun (Proposé, rédigé par `ecrire-decisions-architecture-socle`) ; D2 (tout en JTA) et D3 (utilitaire JDBC interne) y sont consignés s'il est encore Proposé, sinon dans un ADR distinct. Aucune dépendance nouvelle : pas de revue de licence au titre d'ADR-0021.
- **Dépendances entre changes** : dépend de `ecrire-decisions-architecture-socle` (réponse du porteur sur l'ADR du noyau commun). Précède `typer-echanges-entre-modules` (mêmes fichiers `impact/ImpactQueries.java` et `alerting/MoteurAlertes.java`). Partir de l'état intégré de `verifier-types-et-frontieres-modules` (test des frontières), `isoler-erreurs-aux-frontieres` (traduction des erreurs) et `injecter-horloge-et-executeurs` s'ils touchent les mêmes fichiers. `durcir-autorisations-et-debits` (sprint 6) trouvera le limiteur de débit dans le noyau commun.

## Conception requise

complète — changement transverse sur plusieurs modules, deux décisions structurantes à alternatives sérieuses (modèle transactionnel, outil JDBC, tranchées par le porteur le 2026-11-24) et une règle de propriété des données.

## Questions ouvertes

| ID | Question | Bloquante ? | Destinataire |
|---|---|---|---|
| Q-1 | L'ADR du noyau commun, encore à rédiger par `ecrire-decisions-architecture-socle` (tâche 3.1), est-il accepté ? | oui : bloque le groupe 2 et suivants | porteur du projet |
| Q-4 | Lectures étrangères : service de lecture du propriétaire ou vue SQL nommée, au cas par cas (design.md, D4) ? | non : la conception propose un choix par cas, révisable | porteur du projet |
| Q-5 | Au-delà de `signal`, `impact` et `alerting`, quand migrer les autres modules vers l'utilitaire JDBC ? | non : au fil des changements suivants | porteur du projet |
