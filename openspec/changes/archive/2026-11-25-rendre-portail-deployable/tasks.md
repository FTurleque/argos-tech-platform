# Tâches : rendre le portail web déployable hors du poste hôte

<!--
Constats traités : AUD-DEP-21, AUD-DEP-25, AUD-SEC-05, AUD-SEC-29, AUD-TST-25 (/docs/audit/2026-11-21/findings.md), ordre interne du sprint 6 : AUD-DEP-21 → AUD-DEP-25 → AUD-SEC-05 → AUD-SEC-29 → AUD-TST-25.
Décisions du porteur, 2026-11-24 : émetteur et client lus au démarrage depuis une réponse publique du serveur (design.md D1) ; politique de mot de passe, seuils de force brute et sslRequired fixés (design.md D5).
Règles : aucune dépendance nouvelle (ADR-0021) ; aucune requête vers Internet dans les tests ; ne pas exposer Keycloak hors 127.0.0.1 avant le groupe 3.
-->

## 1. Émetteur de l'IHM selon l'instance

- [x] 1.1 Exposer l'émetteur public et l'identifiant du client dans la réponse publique d'identité du serveur et les faire lire par la session de l'IHM avant toute connexion ; retirer la valeur par défaut du bundle de production — couvre ACC-11, ACC-11-S3, AUD-DEP-21 — vérification : tests `argos-ui/tests/session.test.mjs` et test d'intégration de `GET /api/v1/system/info` (champs présents, aucune donnée d'utilisateur, ACC-01-S2)
- [x] 1.2 Contrôle du bundle : construire l'IHM comme l'étage Docker et échouer si `localhost:8180` y figure — couvre ACC-11-S3, AUD-DEP-21 — vérification : le contrôle échoue sur la révision `b142695` et passe après 1.1
- [x] 1.3 Tests des réglages emballés du desktop : priorité variable d'environnement > champ `argos` du `package.json` > défaut, pour `apiUrl` et `oidcUrl` — couvre AUD-DEP-21 (réserve « sans test ») — vérification : `npm --prefix argos-desktop test` passe avec les nouveaux cas
- [x] 1.4 Documenter l'émetteur de l'IHM et les réglages du desktop (`VITE_ARGOS_API_URL`, `apiUrl`, `oidcUrl`, `ARGOS_API_URL`, `ARGOS_OIDC_URL`) dans `/docs/deploiement/synology-et-pc.md`, `.env.example` et `argos-ui/.env.example` — couvre ACC-11, AUD-DEP-21 — vérification : relecture ; `node scripts/verifier-docs.mjs` sans erreur

## 2. Adresses du royaume alignées sur l'adresse publique

- [x] 2.1 Ajouter l'adresse publique d'Argos (nom selon Q-7, défaut `http://localhost:${ARGOS_PORT:-8080}`) à `compose.yaml` et `.env.example`, la transmettre au conteneur Keycloak et la substituer dans `redirectUris`, `webOrigins` et `post.logout.redirect.uris` du client `argos-ui`, sans joker ; conserver le retour de bouclage du desktop — couvre ACC-11-S1, ACC-11-S4, ACC-13, AUD-DEP-25 — vérification : test d'intégration avec un Keycloak de test important le royaume livré (retour accepté à l'adresse publique, refusé à une autre adresse)
- [x] 2.2 Rendre l'adresse publique de Keycloak réglable (`KC_HOSTNAME`, publication du port) et en dériver l'émetteur public de l'IHM et de la CSP — couvre ACC-11-S2, AUD-DEP-21 (réserve sur `ARGOS_KEYCLOAK_PORT`) — vérification : `docker compose --profile idp-local config` montre les valeurs dérivées ; recette 4.2
- [x] 2.3 Mettre à jour le guide : adresses de l'instance (DC-2), suppression de la mention 8088, procédure pour un royaume déjà importé — couvre ACC-11, AUD-DEP-25 — vérification : `node scripts/verifier-docs.mjs` sans erreur

## 3. Royaume livré durci

- [x] 3.1 Activer dans `deploiement/keycloak/argos-realm.json` la protection contre la force brute (5 échecs, attente incrémentale 60 s, attente maximale 15 min), la politique `length(12) and notUsername and notEmail and passwordHistory(3)` et `sslRequired: external` — couvre ACC-13-S1, ACC-13-S2, ACC-13-S3, AUD-SEC-29 — vérification : test d'intégration avec un Keycloak de test important le royaume livré (blocage après 5 échecs, mot de passe de 11 caractères refusé)
- [x] 3.2 Contrôle automatique du fichier du royaume (force brute, politique non vide, TLS différent de « aucune », aucun joker dans les adresses d'`argos-ui`) — couvre ACC-13-S4, AUD-SEC-29 — vérification : le contrôle échoue sur le royaume de `b142695` et passe après 3.1
- [x] 3.3 Documenter le passage en HTTPS derrière le reverse proxy de DSM (`KC_HOSTNAME` en https, `sslRequired: all`, HSTS au proxy) et l'ordre « durcir avant d'exposer » — couvre ACC-13, AUD-SEC-29, AUD-SEC-05 (HSTS) — vérification : `node scripts/verifier-docs.mjs` sans erreur

## 4. IHM servie : ressources publiques et en-têtes

- [x] 4.1 Poser les en-têtes de sécurité (CSP alignée sur le desktop avec l'origine de l'émetteur public, `frame-ancestors 'none'`, `nosniff`, `Referrer-Policy`) — couvre ACC-12-S3, ACC-12-S4, AUD-SEC-05 — vérification : test d'intégration des en-têtes de la réponse de `/` et d'une ressource `/assets/*`
- [x] 4.2 Tests de la politique d'accès HTTP : ressource statique de test sous `src/test/resources/META-INF/resources`, chemins publics sans session (non 401), routes d'API et `/q/` protégées (401), contrôle qu'aucune route `/api/` ni `/q/` hors santé et identité ne figure dans les chemins publics ; Javadoc d'`AuthentificationTest` mise à jour — couvre ACC-12-S1, ACC-12-S2, AUD-TST-25 — vérification : `mvn -B -f argos-server/pom.xml verify` passe
- [x] 4.3 Recette du portail servi par l'image : avec `.env.example` et `idp-local` depuis l'hôte, puis depuis un second poste avec les réglages du guide ; console du navigateur sans violation de CSP — couvre ACC-11-S1, ACC-11-S2, ACC-12-S3 — vérification : recette consignée dans `/docs/deploiement/recette-poste.md`

## 5. Documentation durable et décisions

- [x] 5.1 Fiche `/docs/specifications/comptes-acces.md` (ACC-11 à ACC-13 en [PROPOSITION] avec l'identifiant du changement, DF-ACC-2 complété par la lecture de la configuration au démarrage, écarts dont ACC-01, questions) et registre `/docs/specifications/README.md` — vérification : `node scripts/verifier-docs.mjs` sans erreur
- [x] 5.2 arc42 §7, §8.1, §8.3 et §8.12 ; diagramme des adresses de l'instance (DC-2) reporté et inventorié dans `/docs/architecture/diagrams/README.md` — vérification : `node scripts/verifier-docs.mjs` sans erreur
- [x] 5.3 Rédiger l'ADR « configuration de l'IHM à l'exécution » (statut Proposé) et l'inscrire à l'index arc42 §9 — vérification : `node scripts/verifier-docs.mjs` sans erreur

## 6. Vérifications d'intégration

- [x] 6.1 Valider le changement et la documentation — vérification : `openspec validate rendre-portail-deployable --strict` réussit et `node scripts/verifier-docs.mjs` sans erreur
- [x] 6.2 Critère de sortie du sprint 6 pour ce périmètre (`/docs/audit/2026-11-21/sprints.md`) : connexion depuis l'hôte et depuis un autre poste, bundle sans `localhost:8180`, 200 sur `/` et `/assets/*` sans jeton et 401 sur `/api/v1/*`, en-têtes testés, royaume durci — vérification : preuves des tâches 1.2, 3.2, 4.1, 4.2, 4.3

## Matrice de traçabilité

| Exigence | Scénario | Constat | Conception / diagramme | Tâche | Test ou preuve | État de la preuve |
|---|---|---|---|---|---|---|
| ACC-11 | ACC-11-S1 | AUD-DEP-25, AUD-DEP-21 | D1, D2 ; DC-1, DC-2 | 2.1, 4.3 | test d'intégration du royaume livré ; recette `/docs/deploiement/recette-poste.md` | non exécuté |
| ACC-11 | ACC-11-S2 | AUD-DEP-21 | D1, D2 ; DC-2 | 1.1, 2.2, 4.3 | recette depuis un second poste | non exécuté |
| ACC-11 | ACC-11-S3 | AUD-DEP-21 | D1 ; DC-1 | 1.1, 1.2 | contrôle du bundle ; redémarrage avec un autre émetteur | non exécuté |
| ACC-11 | ACC-11-S4 | AUD-DEP-25, AUD-SEC-29 | D2 ; DC-1 | 2.1 | test d'intégration du royaume livré (`redirect_uri` étrangère) | non exécuté |
| ACC-12 | ACC-12-S1 | AUD-TST-25 | D4 | 4.2 | `api/comptes/AuthentificationTest` (chemins publics) | non exécuté |
| ACC-12 | ACC-12-S2 | AUD-TST-25 | D4 | 4.2 | `api/comptes/AuthentificationTest` (401, contrôle des chemins publics) | non exécuté |
| ACC-12 | ACC-12-S3 | AUD-SEC-05 | D3 ; DC-1 | 4.1, 4.3 | test des en-têtes ; recette navigateur | non exécuté |
| ACC-12 | ACC-12-S4 | AUD-SEC-05 | D3 | 4.1 | test de `frame-ancestors 'none'` | non exécuté |
| ACC-13 | ACC-13-S1 | AUD-SEC-29 | D5 | 3.1 | test d'intégration Keycloak (blocage) | non exécuté |
| ACC-13 | ACC-13-S2 | AUD-SEC-29 | D5 | 3.1 | test d'intégration Keycloak (politique) | non exécuté |
| ACC-13 | ACC-13-S3 | AUD-SEC-29 | D5 | 3.1, 3.3 | test d'intégration ou recette (`sslRequired`) | non exécuté |
| ACC-13 | ACC-13-S4 | AUD-SEC-29, AUD-DEP-25 | D5 | 3.2 | contrôle automatique du fichier du royaume | non exécuté |

Constats sans exigence propre : réglages emballés du desktop (AUD-DEP-21, tâches 1.3 et 1.4), HSTS au reverse proxy (AUD-SEC-05, tâche 3.3, documentation seulement).

## Workflow follow-up

- Basculer dans la fiche `comptes-acces` les statuts [PROPOSITION] d'ACC-11 à ACC-13 selon les preuves obtenues.
- Trancher Q-6 (reformulation d'ACC-01) avant l'archivage.
- Archiver le changement uniquement sur demande explicite, après revue (`openspec archive rendre-portail-deployable`).
- Après archivage, remplacer dans les fiches les liens vers `/openspec/changes/rendre-portail-deployable/` par la spec principale ou le dossier d'archive, puis exécuter `node scripts/verifier-docs.mjs`.
