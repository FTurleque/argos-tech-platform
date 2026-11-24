# Tâches : vérifier les types et les frontières de modules

<!--
Changement d'outillage (skip_specs: true) issu de l'audit du 2026-11-21, sprint 1 (/docs/audit/2026-11-21/sprints.md).
Exécution automatique : dépend de mettre-en-place-integration-continue (groupe 5).
Règles : aucune classe de production déplacée ; aucun comportement du desktop modifié ; aucun identifiant d'une ou deux lettres (arc42 §8.16).
-->

## 1. Frontières de modules du serveur

- [x] 1.1 Rejouer l'analyse des autorisations inutilisées (dépendances réelles par ArchUnit contre `ALLOWED`) et consigner le résultat dans ce fichier — couvre AUD-ARC-07 — vérification : liste des 7 autorisations (catalog→audit, impact→vulnintel, mcp→projectintel, regintel→signal, releaseradar→signal, vulnintel→catalog, workspace→audit) confirmée ou corrigée
- [x] 1.2 Retirer de `ALLOWED` les autorisations inutilisées, ou les motiver en commentaire selon Q-1 — couvre AUD-ARC-07 — vérification : `mvn -B -f argos-server/pom.xml test -Dtest=ModuleBoundariesTest` passe
- [x] 1.3 Ajouter la règle « toute classe de `io.argos` réside dans un module de la table » et un test d'exemple prouvant la détection d'un module non déclaré (design.md DC-1) — couvre AUD-ARC-07 — vérification : `ModuleBoundariesTest` vert ; le test d'exemple échoue bien sur la classe factice
- [x] 1.4 Documentation du groupe : arc42 §5 (paragraphe du socle `argos-server/` : table resserrée, règle du module déclaré) et §8.11 (tests d'architecture) — vérification : `node scripts/verifier-docs.mjs` sans erreur

## 2. Types de la campagne E2E

- [x] 2.1 Ajouter `typescript` en dépendance de développement, `e2e/tsconfig.json` (`strict`, `noEmit`) et le script `typecheck`, exécuté avant `test` — couvre AUD-QUA-19 — vérification : `npm --prefix e2e run typecheck` s'exécute
- [x] 2.2 Corriger les 6 erreurs : déclaration de `support/docker.mjs`, typage des rappels `webRequest` de `support/electron.ts`, regroupement des conversions `__argosE2E` derrière une fonction typée — couvre AUD-QUA-19 — vérification : `npm --prefix e2e run typecheck` sans erreur ; `grep -c "as unknown as" e2e/support/electron.ts` réduit à la fonction typée
- [x] 2.3 Documentation du groupe : `e2e/README.md` (`typecheck`), arc42 §8.16 (TypeScript strict dans `e2e/`) — vérification : `node scripts/verifier-docs.mjs` sans erreur

## 3. Types du client desktop

- [x] 3.1 Ajouter `typescript` en dépendance de développement, `argos-desktop/tsconfig.json` (`allowJs`, `checkJs`, `noEmit`, strictness selon Q-2) et le script `typecheck`, inclus dans `check` — couvre AUD-TST-26 — vérification : `npm --prefix argos-desktop run check` exécute `typecheck`
- [x] 3.2 Corriger les 8 erreurs : `typeof import(...)` dans `src/canaux.js:57` et `:60`, typedef de l'écoute de `src/connexion.js`, déclaration du champ `argos` lu par `src/main.js:27` — couvre AUD-TST-26 — vérification : `npm --prefix argos-desktop run check` sans erreur ; 54 tests toujours verts
- [x] 3.3 Documentation du groupe : `argos-desktop/README.md` (`check`), arc42 §8.16 (JSDoc vérifiées) — vérification : `node scripts/verifier-docs.mjs` sans erreur

## 4. Lancement de Maven par la campagne

- [x] 4.1 Résoudre Maven par variable d'environnement (Q-3) puis emplacements fixes, et le lancer sans `shell: true` (design.md D3) dans `e2e/performance/mesurer-p95.mjs` — couvre AUD-SEC-24 — vérification : test `node --test "e2e/performance/*.test.mjs"` couvrant la résolution (variable, emplacement fixe, absence → erreur explicite) ; `grep -n "shell:" e2e/performance/mesurer-p95.mjs` vide
- [x] 4.2 Appliquer la même résolution au serveur web de `e2e/playwright.config.ts:89` — couvre AUD-SEC-24 — vérification : `npm --prefix e2e run typecheck` sans erreur ; commande construite à partir du chemin résolu (revue)
- [x] 4.3 Documentation du groupe : `e2e/README.md` (variable d'emplacement de Maven) — vérification : `node scripts/verifier-docs.mjs` sans erreur

## 5. Intégration continue

- [ ] 5.1 Ajouter au workflow de `mettre-en-place-integration-continue` les étapes `npm ci` + `npm run typecheck` pour `e2e` et vérifier que `npm run check` du desktop inclut le contrôle de types — couvre AUD-QUA-19, AUD-TST-26 — vérification : étapes vertes sur une pull request ; une erreur de type introduite rend le statut rouge
- [x] 5.2 Mettre à jour `README.md` (« Vérifications locales » : `typecheck` de `e2e`) et arc42 §8.11 (vérifications de types exécutées en intégration continue) — vérification : `node scripts/verifier-docs.mjs` sans erreur

## 6. Vérifications d'intégration

- [x] 6.1 Valider le changement — vérification : `openspec validate verifier-types-et-frontieres-modules --strict` réussit et `node scripts/verifier-docs.mjs` sans erreur
- [x] 6.2 Lancer `mvn -B -f argos-server/pom.xml clean verify`, `npm --prefix argos-desktop run check`, `npm --prefix e2e run typecheck` et `node scripts/verifier-nommage.mjs` — vérification : tous verts

## Preuves

Poste du mainteneur, 2026-11-24 :

- 1.1 : analyse rejouée par un test ArchUnit ponctuel (non versionné ; dépendances directes de chaque classe de production, `getDirectDependenciesFromSelf`, rapportées au paquet de premier niveau) : 57 autorisations dans `ALLOWED`, 50 utilisées ; les 7 inutilisées sont exactement celles de l'audit : catalog→audit, impact→vulnintel, mcp→projectintel, regintel→signal, releaseradar→signal, vulnintel→catalog, workspace→audit.
- 1.2 : les 7 autorisations sont retirées (retrait par défaut de Q-1) ; la table compte 50 autorisations, toutes utilisées.
- 1.3 : règle `chaqueClasseResideDansUnModuleDeclare` et test d'exemple `laRegleRefuseUnModuleNonDeclare` (classe factice `architecture/exemples/ClasseHorsModule`, paquet de premier niveau `architecture` absent de la table) ; `ModuleBoundariesTest` : 24 tests, 0 échec.
- 2.1, 2.2 : `npm --prefix e2e run typecheck` reproduit d'abord les 6 erreurs de l'audit (`donnees.ts:9`, `electron.ts:299`, `:319`, `:326`), puis passe sans erreur. Les fonctions passées à `app.evaluate` étant sérialisées vers le processus principal d'Electron, elles ne peuvent appeler aucune fonction du test : les 14 conversions `__argosE2E` sont regroupées derrière la déclaration globale typée `var __argosE2E: DoubluresArgos` plutôt que derrière une fonction ; `grep -c "as unknown as" e2e/support/electron.ts` = 2 (le `this` de `Notification` et de `Tray`, dont les types Electron sont absents de `e2e/`). `@types/node` est ajouté avec `typescript` (types de `node:`).
- 3.1, 3.2 : `argos-desktop/tsconfig.json` non strict (Q-2, porteur du projet, 2026-11-24) ; `npm --prefix argos-desktop run check` : syntaxe valide (8 fichiers), `tsc` sans erreur, 54 tests, 0 échec. Annotations corrigées : `ReturnType<typeof import(...)>` dans `src/canaux.js`, typedefs `Ecoute` et `EtatEcoute` de `src/connexion.js`, type du champ `argos` du `package.json` lu par `src/main.js`. `@types/node` vient d'Electron (Node.js 24).
- 3.3 : `argos-desktop/README.md` n'existe pas : la commande `check` est documentée dans `README.md` (« Vérifications locales ») et arc42 §8.16.
- 4.1, 4.2 : `e2e/support/maven.mjs` (`ARGOS_E2E_MVN`, puis `MAVEN_HOME`/`M2_HOME`, puis emplacements fixes) ; `node --test "e2e/performance/*.test.mjs"` : 16 tests, 0 échec, dont 9 pour la résolution et le lancement (Playwright sans Maven trouvé : script qui échoue avec le message explicite, seulement au démarrage du serveur) ; `grep -n "shell:" e2e/performance/mesurer-p95.mjs` vide ; lancement réel de `mvn.cmd -v` par `C:\Windows\System32\cmd.exe` réussi sous Windows. `playwright.config.ts` construit la commande du serveur à partir du chemin résolu.
- 6.2 : `mvn -B -f argos-server/pom.xml -P integration-continue clean verify` (JDK 17) : BUILD SUCCESS, 656 tests ; `npm --prefix argos-desktop run check`, `npm --prefix e2e run typecheck` et `node scripts/verifier-nommage.mjs` (538 fichiers, 0 violation) verts.
- `openspec validate verifier-types-et-frontieres-modules --strict` : valide ; `node scripts/verifier-docs.mjs` : aucune erreur.

## Matrice de traçabilité

| Constat | Exigence / scénario | Conception / diagramme | Tâche | Test ou preuve | État de la preuve |
|---|---|---|---|---|---|
| AUD-ARC-07 | aucune (`skip_specs`) | design.md DC-1, D1 | 1.1 à 1.4 | `architecture/ModuleBoundariesTest` (règle du module déclaré et test d'exemple) | non exécuté |
| AUD-QUA-19 | aucune | design.md D2 | 2.1 à 2.3, 5.1 | `npm --prefix e2e run typecheck` | non exécuté |
| AUD-TST-26 | aucune | design.md D2 | 3.1 à 3.3, 5.1 | `npm --prefix argos-desktop run check` | non exécuté |
| AUD-SEC-24 | aucune | design.md D3, « Sécurité et isolation » | 4.1 à 4.3 | test de résolution de Maven dans `e2e/performance/` ; absence de `shell:` | à écrire |

## Workflow follow-up

- Après la fusion, signaler au coordinateur de l'audit le passage d'AUD-ARC-07, AUD-QUA-19, AUD-TST-26 et AUD-SEC-24 à « Corrigé » selon les preuves.
- Archiver le changement uniquement sur demande explicite, après revue (`openspec archive verifier-types-et-frontieres-modules`).
- Après archivage, remplacer dans la documentation les liens vers `/openspec/changes/verifier-types-et-frontieres-modules/` par le dossier d'archive, puis exécuter `node scripts/verifier-docs.mjs`.
