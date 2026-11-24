# Proposition : vérifier les types et les frontières de modules

<!-- Statuts : [EXIGENCE] [EXISTANT] [DÉCISION] [PROPOSITION] [QUESTION] — légende : /docs/specifications/README.md#statuts-des-informations. -->

## Why — Pourquoi

Quatre contrôles statiques manquent ou sont trop larges, d'après l'audit du 2026-11-21 (`/docs/audit/2026-11-21/findings.md`) :

- la table des dépendances autorisées entre modules du serveur autorise 7 dépendances sur 57 que le code n'utilise pas et ne contraint pas un paquet `io.argos` de premier niveau absent de la table (AUD-ARC-07) ;
- les 24 fichiers TypeScript de `e2e/` (environ 3 500 lignes) ne sont soumis à aucune vérification de types ; un `tsc --strict` relève 6 erreurs (AUD-QUA-19) ;
- `argos-desktop` n'a qu'un contrôle de syntaxe ; un `tsc --checkJs` non strict relève 8 erreurs, dont 2 annotations JSDoc invalides sur les dépendances des canaux IPC (AUD-TST-26) ;
- la campagne de performance lance Maven par le `PATH` avec `shell: true` sous Windows, contrairement à la règle des chemins absolus qu'elle applique à `docker`, `git` et `taskkill` (AUD-SEC-24).

Ces quatre constats appartiennent au sprint 1 de l'audit (« Remettre les gates en marche », `/docs/audit/2026-11-21/sprints.md`) ; ils complètent les barrières posées par `mettre-en-place-integration-continue`.

## What Changes — Changements

- Table des frontières du serveur : retrait des 7 dépendances autorisées inutilisées (ou motivation écrite de chacune conservée) ; nouvelle règle d'architecture : toute classe de `io.argos` réside dans un paquet de premier niveau listé dans la table, sinon le test échoue.
- `e2e/` : `tsconfig.json` strict sans émission, `typescript` en dépendance de développement, script `typecheck` exécuté avant les tests ; déclaration de `support/docker.mjs`, typage des fonctions de rappel `webRequest`, regroupement des conversions forcées `__argosE2E` derrière une fonction typée ; les 6 erreurs corrigées.
- `argos-desktop` : `tsconfig.json` (`checkJs`, strictness progressive), script `typecheck` inclus dans `check` ; les 8 erreurs corrigées (`typeof import(...)`, typedef de l'écoute, déclaration du champ `argos` du `package.json`).
- Campagne de performance : Maven résolu par chemin absolu (emplacements fixes et variable d'environnement dédiée), appelé sans `shell: true` ; même revue pour le serveur web lancé par `e2e/playwright.config.ts`.
- Les deux vérifications de types sont ajoutées au workflow d'intégration continue.
- Aucun comportement applicatif modifié ; aucune rupture de contrat (**BREAKING** : aucun).

## Capabilities — Capacités

### New Capabilities — Nouvelles capacités

Aucune.

### Modified Capabilities — Capacités modifiées

Aucune — contrôles statiques et outillage de test sans comportement spécifié : `skip_specs: true` dans `.openspec.yaml`.

## Périmètre

### Inclus

- `argos-server/src/test/java/io/argos/architecture/ModuleBoundariesTest.java` (table et nouvelle règle).
- `e2e/` : `tsconfig.json`, `package.json`, `support/docker.mjs` (déclaration), `support/electron.ts`, `support/donnees.ts`, `desktop/connexion.spec.ts` ; `performance/mesurer-p95.mjs` ; `playwright.config.ts`.
- `argos-desktop/` : `tsconfig.json`, `package.json`, `src/canaux.js`, `src/connexion.js`, `src/main.js` (annotations et déclarations seulement).
- Arc42 §5 (table des dépendances autorisées), §8.11 et §8.16 ; README de `e2e/` et d'`argos-desktop`.

### Exclu

- Création du workflow d'intégration continue : `mettre-en-place-integration-continue`.
- Mode strict complet pour `argos-desktop` dès ce changement : [QUESTION] Q-2.
- `reuseExistingServer: true` du serveur web de la campagne E2E (AUD-TST-20, sprint 11) : seul l'appel à Maven est revu ici.
- Modification d'une dépendance de code entre modules : seules les autorisations inutilisées sont retirées ; aucune classe de production n'est déplacée.

## Acteurs et parties prenantes

| Acteur | Rôle dans ce changement | Statut |
|---|---|---|
| Développeur (humain ou agent) | voit une erreur de type ou de frontière à la compilation au lieu de l'exécution | [PROPOSITION] — AUD-QUA-19, AUD-TST-26, AUD-ARC-07 |
| Porteur du projet | tranche le sort des 7 autorisations (retrait ou motivation) et le niveau de strictness du desktop | [PROPOSITION] — Q-1, Q-2 |

## Sources et preuves

| Élément | Statut | Source |
|---|---|---|
| Monolithe modulaire ; dépendances autorisées entre modules vérifiées par `ModuleBoundariesTest` | [PROPOSITION] (ADR Proposé) ; vérification [EXISTANT] | ADR-0001 (Proposé) ; `/docs/architecture/arc42/05-vue-blocs.md` (paragraphe sur le socle `argos-server/`) |
| Table de 16 entrées ; règles générées pour les seules clés ; `allowEmptyShould(true)` | [EXISTANT] | `ModuleBoundariesTest.java:32-57` (table), `:68-82` (règle générée) |
| 7 autorisations inutilisées : catalog→audit, impact→vulnintel, mcp→projectintel, regintel→signal, releaseradar→signal, vulnintel→catalog, workspace→audit | [EXISTANT] (analyse de l'audit, `compare.py`, non rejouée ici) | AUD-ARC-07 |
| Aucun `tsconfig` dans `e2e/` ; seule dépendance `@playwright/test` 1.61.1 | [EXISTANT] | `e2e/package.json` ; `ls e2e/tsconfig*` vide |
| 24 fichiers `.ts` versionnés dans `e2e/` ; 16 `as unknown as` dans `support/electron.ts` | [EXISTANT] | `git ls-files e2e` ; `grep -c` |
| 6 erreurs `tsc --strict` (donnees.ts:9, electron.ts:299, :319, :326) | [EXISTANT] (mesure de l'audit sur copie, non rejouée ici) | AUD-QUA-19 |
| `check` = `check:syntax` + `test`, sans vérification de types | [EXISTANT] | `argos-desktop/package.json` |
| Annotations `ReturnType<import('./poste').creerPoste>` invalides | [EXISTANT] | `argos-desktop/src/canaux.js:57`, `:60` |
| Champ `argos` lu dans `package.json`, absent en développement | [EXISTANT] | `argos-desktop/src/main.js:27` |
| `mvn.cmd` par le `PATH`, `shell: true` sous Windows | [EXISTANT] | `e2e/performance/mesurer-p95.mjs:117-120` ; `e2e/playwright.config.ts:89` |
| Règle des exécutables par chemin absolu de la campagne | [EXISTANT] | commentaire `e2e/performance/mesurer-p95.mjs:77-78` ; `e2e/support/docker.mjs` |
| Aucun identifiant d'une ou deux lettres | [DÉCISION] | arc42 §8.16 ; `scripts/verifier-nommage.mjs` |

## Écarts constatés

- Preuves inchangées à `b142695` : lignes de `mesurer-p95.mjs` (117-120) et `playwright.config.ts:89` identiques à l'audit ; la table `ALLOWED` occupe désormais les lignes 32 à 57 et la règle générée les lignes 68 à 82 (l'audit cite 31-57 et 73-82 selon les relevés : décalage sans effet).
- La règle générée de `ModuleBoundariesTest` cite arc42 §5 dans son message, mais arc42 §5 ne publie pas la table des dépendances autorisées : il renvoie au test et décrit les dépendances changement par changement. La tâche documentaire aligne arc42 sur la table resserrée.
- ADR-0001 (monolithe modulaire) et ADR-0022 (client desktop) sont au statut Proposé alors que le code les applique : écart signalé, hors de ce changement (registre des ADR, sprint 5 de l'audit).

## Impact

- **Composants** (noms de arc42 §5) : aucun composant de production modifié ; tests d'architecture du `Quarkus Backend` ; outillage du `Client desktop Electron` et de la campagne E2E.
- **Contrats d'interface** : aucun.
- **Données et persistance** : aucune.
- **Sécurité, confidentialité, droits** : la campagne de performance n'exécute plus un `mvn.cmd` trouvé dans le `PATH` ni une ligne `cmd.exe` construite avec le chemin du dépôt (AUD-SEC-24, poste du développeur uniquement).
- **Exploitation et observabilité** : aucune.
- **Dépendances entre changes** : dépend de `mettre-en-place-integration-continue` pour l'exécution automatique de `ModuleBoundariesTest` (dans `mvn verify`) et des deux vérifications de types (étapes ajoutées à son workflow) ; sans lui, ces contrôles restent locaux. Peut être développé avant, en ajoutant les étapes au workflow une fois celui-ci fusionné.
- **Documentation durable à mettre à jour** : arc42 §5 (dépendances autorisées entre modules), §8.11 (tests d'architecture et vérifications de types), §8.16 (conventions TypeScript et JSDoc) ; `e2e/README.md`, `argos-desktop/README.md`, `README.md` (« Vérifications locales »). Aucune fiche de capacité ni registre `/docs/specifications/README.md` : aucun comportement spécifié.
- **ADR** : ADR-0001 (Proposé) appliqué par le test ; ADR-0022 (Proposé, client desktop) inchangé. Aucune décision candidate à un nouvel ADR.

## Conception requise

allégée — contrôles statiques locaux ; la conception précise la règle d'architecture, la configuration TypeScript et la résolution de Maven.

## Questions ouvertes

| ID | Question | Bloquante ? | Destinataire |
|---|---|---|---|
| Q-1 | Les 7 autorisations inutilisées sont-elles retirées toutes, ou certaines conservées par anticipation (avec motivation écrite dans la table) ? | non : retrait par défaut (action d'AUD-ARC-07) | porteur du projet |
| Q-2 | `argos-desktop` : `strict: true` dès ce changement, ou `checkJs` non strict puis strictness progressive ? | non : non strict par défaut (action d'AUD-TST-26) | porteur du projet |
| Q-3 | Variable d'environnement de l'emplacement de Maven : nom (proposition `ARGOS_E2E_MVN`, sur le modèle de `ARGOS_E2E_GIT`) et emplacements fixes à essayer sous Windows ? | non | porteur du projet |
