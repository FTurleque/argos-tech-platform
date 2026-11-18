# Tâches : rechercher et filtrer la veille, et enregistrer des vues

<!-- Les tâches supposent le socle argos-server / argos-ui et les changes dont dépend celui-ci (voir la proposition). Une tâche n'est cochée que si sa vérification a réussi. -->

## 1. Recherche

- [x] 1.1 Créer l'index plein texte et la requête de recherche sous RLS — couvre RCH-01-S1, RCH-01-S2, RCH-03-S1 — vérification : tests d'intégration passent
- [x] 1.2 Ajouter les filtres combinables — couvre RCH-02-S1 — vérification : tests d'intégration passent
- [x] 1.3 Brancher l'outil MCP `search_signals` sur la même requête — couvre RCH-01, RCH-03 — vérification : test de contrat MCP passe

## 2. Vues et performance

- [x] 2.1 Enregistrer, rappeler et supprimer des vues personnelles — couvre RCH-04-S1, RCH-04-S2 — vérification : tests d'intégration passent
- [x] 2.2 Mesurer le temps de réponse sur le volume cible — couvre RCH-05-S1 — vérification : rapport QS-13 versionné

## 3. Documentation durable et décisions

- [x] 3.1 Mettre à jour la fiche `recherche-veille`, les sections arc42 §8.10, le glossaire et l'inventaire `/docs/architecture/diagrams/README.md` — vérification : `node scripts/verifier-docs.mjs` sans erreur

## 5. Écran de recherche dans argos-ui

<!-- Groupe numéroté 5 pour garder les références aux tâches 4.1 et 4.2 ; placé avant le groupe des vérifications d'intégration, qui reste le dernier. -->

- [x] 5.1 Appeler l'API de recherche et des vues par le point d'appel commun `appelerApi` du client REST (`argos-ui/src/api/recherche.ts`, `argos-ui/src/api/recherche-criteres.ts`) — couvre RCH-01, RCH-02, RCH-04 — vérification : `argos-ui/tests/recherche.test.mjs` passe (`npm --prefix argos-ui test`)
- [x] 5.2 Écran de recherche `RechercheView` (route `/recherche`) : mots, filtres période (dont « 30 derniers jours »), gravité, technologies, type de contenu et projet, résultats paginés avec liens sources filtrés, état vide et erreurs — couvre RCH-01-S1, RCH-01-S2, RCH-02-S1 — vérification : `argos-ui/tests/recherche.test.mjs` passe et `npm --prefix argos-ui run build` (contrôle de types `vue-tsc`) réussit
- [x] 5.3 Enregistrer la saisie en vue nommée, lister, rappeler (route `/recherche/vues/:id`) et supprimer les vues — couvre RCH-04-S1 — vérification : `argos-ui/tests/recherche.test.mjs` (corps `NouvelleVue`, aller-retour des critères, identifiants) passe et `npm --prefix argos-ui run build` réussit
- [x] 5.4 Accès depuis le portail et le desktop : lien « Recherche » de la navigation principale, accessibilité de base (libellés, `fieldset`, focus, annonces `aria-live`) — couvre RCH-01, RCH-04 — vérification : `argos-ui/tests/recherche.test.mjs` (routes, lien, libellés, annonces, absence de `v-html`) passe
- [x] 5.5 Mettre à jour la conception (écran de recherche, D8 à D10) et la fiche `recherche-veille` — vérification : `node scripts/verifier-docs.mjs` sans erreur hors `docs/audit/` et `openspec validate rechercher-et-filtrer --strict` réussit

## 4. Vérifications d'intégration

- [x] 4.1 Valider le changement — vérification : `openspec validate rechercher-et-filtrer --strict` réussit
- [x] 4.2 Parcours : recherche « Oracle » filtrée sur un projet et 30 jours, enregistrée en vue, rappelée depuis le desktop — vérification : scénario E2E consigné avec ses résultats

- Portail web, 2026-11-17 : `npm --prefix e2e test`, parcours `e2e/tests/recherche.spec.ts` réussi dans Chromium contre `quarkus:dev`, le Keycloak de test et une base jetable (jeu de données `e2e/donnees/recherche.sql` : projets « Paie » et « Comptabilité », quatre signaux). Connexion d'`alice` ; « Oracle » sur 30 jours : deux résultats, l'avis de 60 jours écarté ; projet « Paie » coché parmi les projets cités : un seul résultat, « Oracle Database : faille corrigée » ; vue enregistrée, rappelée par son lien avec mots, période et projet restitués et le même résultat ; rappelée encore après rechargement de la page, dans une nouvelle session. Rapport [`e2e/resultats/rapport-2026-11-17.json`](/e2e/resultats/rapport-2026-11-17.json), capture [`e2e/resultats/recherche-oracle-paie-30j.png`](/e2e/resultats/recherche-oracle-paie-30j.png).
- Portail web puis desktop, 2026-11-17 : `npm --prefix e2e test`, deux exécutions complètes consécutives réussies (27 tests, 0 échec chacune), parcours « 4.2 (RCH-02-S1, RCH-04-S1) » de `e2e/desktop/recherche.spec.ts`. Sur le portail (Chromium), `alice` cherche « Oracle » sur 30 jours (deux résultats), coche le projet « Paie » (un seul résultat, « Oracle Database : faille corrigée ») et enregistre la recherche en vue nommée ; la vue est lue sur le serveur au nom d'`alice`. L'application `argos-desktop` réelle (Electron 45.3.2, IHM servie par `app://argos` avec sa CSP, `contextIsolation` et `sandbox` actifs) se connecte avec le même compte par le navigateur du système et l'interface de bouclage `127.0.0.1` ; son écran de recherche s'ouvre vierge, la vue figure dans « Mes vues », et son rappel mène à `#/recherche/vues/<identifiant de la vue enregistrée sur le web>` avec « Oracle », « 30 derniers jours » et « Paie » restitués et le même résultat unique. Le journal de la session du desktop montre `GET /api/v1/vues`, `/api/v1/vues/<id>` et `/api/v1/vues/<id>/resultats` aboutis avec le jeton porteur, et aucune requête vers un hôte hors de la boucle locale. Rapport [`e2e/resultats/rapport-lot9-2026-11-17.json`](/e2e/resultats/rapport-lot9-2026-11-17.json), capture [`e2e/resultats/desktop-recherche-vue-rappelee.jpg`](/e2e/resultats/desktop-recherche-vue-rappelee.jpg).
- Simulé : le navigateur du système est une page Chromium de Playwright (second contexte, sans la session du portail), alimentée par une doublure de `shell.openExternal` installée par `electronApp.evaluate` dans le processus principal ; aucun code livré n'est modifié. Reste manuel : la même connexion avec le navigateur réellement installé sur le poste.
- Par l'API REST, le même parcours est vérifié par `RechercheVeilleTest.rch04S1VueRappelee` (recherche « Oracle », projet, 30 jours, vue enregistrée puis rappelée : mêmes résultats que la recherche directe).

## Preuves

- `mvn -B -f argos-server/pom.xml verify` du 2026-11-16 : BUILD SUCCESS, 318 tests, 0 échec, 1 ignoré (`MesurePerformanceRechercheTest`, exécuté à la demande).
- `RechercheVeilleTest` (10 tests) et `RechercheMcpTest` (3 tests) passent ; `OutilsConsultationMcpTest`, `ContenuExterneMcpTest` et `ContratOutilsMcpTest` passent avec `search_signals` branché sur la recherche.
- `MesurePerformanceRechercheTest -Dargos.performance=true` du 2026-11-16 : campagne p95 = 250 ms (cible Q-05 : 500 ms) ; [rapport QS-13](/docs/architecture/quality/mesure-qs13-recherche.md).
- `node scripts/verifier-docs.mjs` : aucune erreur ; `openspec validate rechercher-et-filtrer --strict` : valide.
- Écran de recherche, 2026-11-17 : `npm --prefix argos-ui test` : 42 tests, 0 échec, dont les 14 de `argos-ui/tests/recherche.test.mjs` ; `npm --prefix argos-ui run build` (`vue-tsc --noEmit` puis `vite build`) réussit. L'écran n'a été exercé ni dans un navigateur ni dans le client desktop contre un serveur réel (tâche 4.2).
- Intégration du lot 7 (`lot7/integration`, avec `connecter-ihm-oidc`), 2026-11-17 : `npm --prefix argos-ui test` : 87 tests, 0 échec, dont 15 dans `argos-ui/tests/recherche.test.mjs` (délai de recherche, annulation, suppression de la vue affichée, messages 403 et 429) ; `npm --prefix argos-ui run build` réussit ; `mvn -B -f argos-server/pom.xml verify` : BUILD SUCCESS, 494 tests, 1 ignoré ; `openspec validate --all --strict` valide. Affichage réel toujours non exercé (tâche 4.2).

- Parcours E2E du portail web, 2026-11-17 : `npm --prefix e2e ci && npm --prefix e2e test` : 9 parcours sur 9 réussis, dont `e2e/tests/recherche.spec.ts` ; rapport [`e2e/resultats/rapport-2026-11-17.json`](/e2e/resultats/rapport-2026-11-17.json). Observation sans correction : cliquer sur le lien de la vue déjà affichée ne la recharge pas (même adresse) ; le parcours rappelle la vue depuis une recherche vierge.

## Matrice de traçabilité

État au 2026-11-17.

| Exigence | Scénario | Conception / diagramme | Tâche | Test ou preuve | État de la preuve |
|---|---|---|---|---|---|
| RCH-01 | RCH-01-S1 | Composants, D1, DF-RCH-1, Écran de recherche | 1.1, 1.3, 5.1, 5.2 | `RechercheVeilleTest.rch01S1MotTrouve`, `rch01RechercheBilingue` ; `RechercheMcpTest.rch01MotsEtPertinence` ; écran : `recherche.test.mjs` (paramètres `q`, encodage des mots) | passe (API) ; écran : E2E web `e2e/tests/recherche.spec.ts` passe (2026-11-17) |
| RCH-01 | RCH-01-S2 | Composants, Écran de recherche | 1.1, 1.3, 5.2 | `RechercheVeilleTest.rch01S2AucunResultat` ; `RechercheMcpTest.rch01MotsEtPertinence` ; écran : message `Resultats.message` affiché (`RechercheView.vue`), annonce « Aucun résultat. » (`recherche.test.mjs`) | passe (API) ; écran : logique vérifiée, affichage réel non exercé |
| RCH-02 | RCH-02-S1 | Contrats d'interface, D4, D5, D8, D10 | 1.2, 5.2, 4.2 | `RechercheVeilleTest.rch02S1FiltresCombines` ; écran : `recherche.test.mjs` (filtres répétés, période 30 jours) ; E2E web `e2e/tests/recherche.spec.ts` (mots, projet, 30 jours) ; E2E desktop `e2e/desktop/recherche.spec.ts` (filtres restitués par la vue rappelée) | passe (dont E2E web et desktop) |
| RCH-03 | RCH-03-S1 | Sécurité et isolation, D2 | 1.1, 1.3 | `RechercheVeilleTest.rch03S1MotPresentDansUnAutreEspace`, `rch03CloisonnementSousArgosApp` ; `RechercheMcpTest.rch03MotPresentDansUnAutreEspace` | passe |
| RCH-04 | RCH-04-S1 | Persistance et intégrité, Écran de recherche | 2.1, 5.3, 4.2 | `RechercheVeilleTest.rch04S1VueRappelee` ; écran : `recherche.test.mjs` (corps `NouvelleVue`, aller-retour des critères) ; E2E web `e2e/tests/recherche.spec.ts` ; E2E web puis desktop `e2e/desktop/recherche.spec.ts` (vue enregistrée sur le web, rappelée dans l'application Electron) | passe (API, logique de l'écran, E2E web, E2E desktop : deux exécutions consécutives, 2026-11-17) |
| RCH-04 | RCH-04-S2 | Sécurité et isolation | 2.1 | `RechercheVeilleTest.rch04S2VueDUnAutreUtilisateur` | passe |
| RCH-05 | RCH-05-S1 | Performance et stratégie de mesure | 2.2 | `MesurePerformanceRechercheTest` ; [rapport QS-13](/docs/architecture/quality/mesure-qs13-recherche.md) | passe (p95 = 250 ms) |

## Workflow follow-up

- Basculer dans la fiche durable les statuts [PROPOSITION] de ce changement en [EXIGENCE] ou [EXISTANT] selon les preuves obtenues.
- Archiver le changement uniquement sur demande explicite, après revue (`openspec archive rechercher-et-filtrer`).
- Après archivage, vérifier que la section Purpose de la spec principale contient le lien vers la fiche durable.
- Après archivage, remplacer dans la fiche les liens vers `/openspec/changes/rechercher-et-filtrer/` par la spec principale ou le dossier d'archive, puis exécuter `node scripts/verifier-docs.mjs`.
