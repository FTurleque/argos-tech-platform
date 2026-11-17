# Tâches : connecter l'IHM et le client desktop au fournisseur d'identité OIDC

## 1. Royaume Keycloak et configuration

- [x] 1.1 Déclarer pour le client `argos-ui` du royaume de test les redirections `http://localhost:5173/*` et `http://127.0.0.1/argos/retour`, les origines web `http://localhost:5173` et `app://argos`, `post.logout.redirect.uris`, flux implicite désactivé, PKCE S256 obligatoire ; retirer `app://argos/*` — couvre ACC-07-S2, ACC-09-S4, ACC-10-S1 — vérification : `argos-server/src/test/java/io/argos/api/comptes/ClientIhmOidcTest.java` (6 tests) passe
- [ ] 1.2 Fixer le port du Keycloak de développement à 8180 (`%dev.quarkus.keycloak.devservices.port`) pour l'émetteur par défaut de l'IHM et du desktop — couvre D6 — vérification : `mvn quarkus:dev` puis `http://localhost:8180/realms/argos/.well-known/openid-configuration` répond (non exécuté : propriété ajoutée, profil `dev` non lancé)

## 2. Session OIDC de l'IHM (`argos-ui`)

- [x] 2.1 Fonctions pures du flux (`src/session/oidc.ts`) : PKCE S256, state, URL d'autorisation, lecture du retour, adresse nettoyée, chemin interne, corps des requêtes de jetons, délai de renouvellement, fin de session, messages de refus fixes — couvre ACC-07-S2 à S4, ACC-08-S2, ACC-08-S4 — vérification : `argos-ui/tests/oidc.test.mjs` passe (15 tests)
- [x] 2.2 `GestionnaireSession` (`src/session/gestionnaire.ts`) : découverte, échange du code, jetons dans un champ privé, renouvellement planifié, expiration, déconnexion, transports web et desktop injectés — couvre ACC-07-S2 à S4, ACC-08-S1 à S4, ACC-09-S3 — vérification : `argos-ui/tests/session.test.mjs` passe (12 tests)
- [x] 2.3 Client REST : `Authorization: Bearer` sur `/api/v1` sauf l'identité du serveur, classement 401 / 403 / `INSCRIPTION_SUR_INVITATION` (`src/api/autorisation.ts`, `src/api/client.ts`), `fetchMoi` — couvre ACC-09-S1 à S5, ACC-07-S5 — vérification : `argos-ui/tests/autorisation.test.mjs` passe (4 tests) ; jeton de l'IHM accepté par `/api/v1/moi` dans `ClientIhmOidcTest`
- [x] 2.4 Garde des vues (`src/session/garde.ts`, `installerSession`), écran de connexion et état « session expirée » (`ConnexionView.vue`), écran « aucun compte ARGOS » sans invitation (`CompteNonCreeView.vue`), message de refus et déconnexion (`App.vue`), suivi des alertes du poste limité à une session ouverte, variables Vite (`.env.example`, `env.d.ts`) — couvre ACC-07-S1, ACC-07-S5, ACC-08-S3, ACC-09-S3 à S5 — vérification : `argos-ui/tests/garde.test.mjs` passe (5 tests) ; `npm --prefix argos-ui run build` (vue-tsc et Vite) réussit
- [x] 2.5 Règles de sécurité vérifiées sur les sources : stockage du navigateur limité à deux modules, seule écriture de session = demande transitoire, aucun cookie, aucun `v-html`, aucun journal dans `session/` et `api/` — couvre ACC-08-S1 — vérification : `argos-ui/tests/securite-session.test.mjs` passe (5 tests)

## 3. Connexion du client desktop (`argos-desktop`)

- [x] 3.1 Écoute de bouclage `127.0.0.1` à port éphémère, retour unique, page statique, délai de cinq minutes, ouverture du navigateur du système limitée aux adresses de l'émetteur avec PKCE S256 et la redirection préparée (`src/connexion.js`) — couvre ACC-10-S1 à S3 — vérification : `argos-desktop/test/connexion.test.js` passe (7 tests, serveur local réel)
- [x] 3.2 Canaux `argos:login-prepare` et `argos:login-authorize` réservés à la fenêtre principale ; pont `preparerConnexion` / `autoriserConnexion` sans autre capacité — couvre ACC-10-S2, ACC-10-S3 — vérification : `argos-desktop/test/canaux.test.js` et `preload.test.js` passent
- [x] 3.3 CSP étendue à la seule origine du fournisseur configuré (`ARGOS_OIDC_URL`), branchement dans `main.js`, contrôle de syntaxe — couvre ACC-10-S4 — vérification : `argos-desktop/test/ui-protocol.test.js` passe ; `npm --prefix argos-desktop run check:syntax` réussit

## 4. Documentation durable et décisions

- [ ] 4.1 Mettre à jour arc42 §5.1 (Keycloak, connexion de l'IHM et du desktop), §8.1 (session de l'IHM) et le glossaire (PKCE, interface de bouclage) — vérification : `node scripts/verifier-docs.mjs` sans erreur hors `docs/audit/`
- [ ] 4.2 Rédiger ADR-0033 (Proposé) et l'indexer dans `docs/architecture/adr/README.md` et arc42 §9 — vérification : `node scripts/verifier-docs.mjs` sans erreur hors `docs/audit/`
- [ ] 4.3 Mettre à jour la fiche `/docs/specifications/comptes-acces.md` (périmètre, règles, DF-ACC-2, questions Q-5 et Q-6, traçabilité) et le registre `/docs/specifications/README.md` — vérification : `node scripts/verifier-docs.mjs` sans erreur hors `docs/audit/`

## 5. Vérifications d'intégration

- [ ] 5.1 Test de bout en bout dans un navigateur : connexion réelle depuis le portail, retour sur la vue demandée, adresse nettoyée, déconnexion, compte non créé — couvre ACC-07-S1, ACC-07-S2, ACC-08-S4, ACC-09-S4 — vérification : à écrire (étape ultérieure du lot 7)
- [ ] 5.2 Test de bout en bout du client desktop Electron : navigateur du système, retour sur `127.0.0.1`, fenêtre au premier plan, CSP — couvre ACC-10-S1, ACC-10-S2, ACC-10-S4 — vérification : à écrire (étape ultérieure du lot 7)
- [ ] 5.3 Valider le changement — vérification : `openspec validate connecter-ihm-oidc --strict` et `openspec validate --all --strict` réussissent
- [ ] 5.4 Vérifications des modules — vérification : `mvn -B -f argos-server/pom.xml verify`, `npm --prefix argos-ui ci && npm --prefix argos-ui test && npm --prefix argos-ui run build`, `npm --prefix argos-desktop ci && npm --prefix argos-desktop test` réussissent

## Matrice de traçabilité

| Exigence | Scénario | Conception / diagramme | Tâche | Test ou preuve | État de la preuve |
|---|---|---|---|---|---|
| ACC-07 | ACC-07-S1 | DC-2, DF-ACC-2 ; D5 | 2.4, 5.1 | `argos-ui/tests/garde.test.mjs` | unitaire exécuté le 2026-11-17 ; E2E à écrire |
| ACC-07 | ACC-07-S2 | DC-2, DC-3, DC-4 ; D1, D4 | 1.1, 2.1, 2.2 | `argos-ui/tests/oidc.test.mjs`, `session.test.mjs` ; `ClientIhmOidcTest#portailWebObtientUnJetonAccepteParLApi` | exécuté le 2026-11-17 |
| ACC-07 | ACC-07-S3 | DC-2, DC-4 ; INV-1 | 2.1, 2.2 | `argos-ui/tests/oidc.test.mjs`, `session.test.mjs` | exécuté le 2026-11-17 |
| ACC-07 | ACC-07-S4 | DC-2, DC-4 | 2.1, 2.2 | `argos-ui/tests/oidc.test.mjs`, `session.test.mjs` | exécuté le 2026-11-17 |
| ACC-07 | ACC-07-S5 | DF-ACC-2 | 2.3, 2.4 | `argos-ui/tests/garde.test.mjs`, `autorisation.test.mjs` ; `AuthentificationTest#identiteDuServeurPublique` | exécuté le 2026-11-17 |
| ACC-08 | ACC-08-S1 | INV-2, INV-3 ; D3, D4 | 2.2, 2.5 | `argos-ui/tests/securite-session.test.mjs`, `session.test.mjs` | exécuté le 2026-11-17 |
| ACC-08 | ACC-08-S2 | DC-4 | 2.1, 2.2 | `argos-ui/tests/oidc.test.mjs`, `session.test.mjs` ; renouvellement réel dans `ClientIhmOidcTest#portailWebObtientUnJetonAccepteParLApi` | exécuté le 2026-11-17 |
| ACC-08 | ACC-08-S3 | DC-4 | 2.2, 2.4 | `argos-ui/tests/session.test.mjs` | exécuté le 2026-11-17 |
| ACC-08 | ACC-08-S4 | DC-3, DC-4 | 2.1, 2.2, 5.1 | `argos-ui/tests/session.test.mjs`, `oidc.test.mjs` | exécuté le 2026-11-17 ; E2E à écrire |
| ACC-09 | ACC-09-S1 | DC-2 ; Contrats d'interface | 2.3 | `argos-ui/tests/autorisation.test.mjs` ; `ClientIhmOidcTest` (jeton de l'IHM accepté par `/api/v1/moi`) | exécuté le 2026-11-17 |
| ACC-09 | ACC-09-S2 | Contrats d'interface | 2.3 | `argos-ui/tests/autorisation.test.mjs` | exécuté le 2026-11-17 |
| ACC-09 | ACC-09-S3 | DC-4 ; D5 | 2.2, 2.3 | `argos-ui/tests/autorisation.test.mjs`, `session.test.mjs` | exécuté le 2026-11-17 |
| ACC-09 | ACC-09-S4 | DC-2, DF-ACC-2 ; D5 | 2.3, 2.4, 5.1 | `argos-ui/tests/autorisation.test.mjs`, `garde.test.mjs` ; `ClientIhmOidcTest#personneSansInvitationRecoitLeRefusDedie` | exécuté le 2026-11-17 ; E2E à écrire |
| ACC-09 | ACC-09-S5 | D5 | 2.3, 2.4 | `argos-ui/tests/autorisation.test.mjs` | exécuté le 2026-11-17 |
| ACC-10 | ACC-10-S1 | DC-1, DC-3 ; D2 | 1.1, 3.1, 5.2 | `argos-desktop/test/connexion.test.js` ; `ClientIhmOidcTest#clientDesktopUtiliseLInterfaceDeBouclage` | exécuté le 2026-11-17 ; parcours Electron à écrire |
| ACC-10 | ACC-10-S2 | DC-3 | 3.1, 3.2, 5.2 | `argos-desktop/test/connexion.test.js`, `canaux.test.js` | exécuté le 2026-11-17 ; parcours Electron à écrire |
| ACC-10 | ACC-10-S3 | DC-3 ; INV-4 | 3.1, 3.2 | `argos-desktop/test/connexion.test.js`, `canaux.test.js`, `preload.test.js` | exécuté le 2026-11-17 |
| ACC-10 | ACC-10-S4 | Sécurité et isolation | 3.3, 5.2 | `argos-desktop/test/ui-protocol.test.js` | exécuté le 2026-11-17 ; parcours Electron à écrire |

## Workflow follow-up

- Écrire les tests de bout en bout (tâches 5.1 et 5.2), puis compléter la matrice.
- Basculer dans la fiche `comptes-acces` les statuts [PROPOSITION] de ce changement en [EXIGENCE] ou [EXISTANT] selon les preuves obtenues.
- Archiver le changement uniquement sur demande explicite, après revue (`openspec archive connecter-ihm-oidc`).
- Après archivage, remplacer dans les fiches et ADR-0033 les liens vers `/openspec/changes/connecter-ihm-oidc/` par la spec principale ou le dossier d'archive, puis exécuter `node scripts/verifier-docs.mjs`.
