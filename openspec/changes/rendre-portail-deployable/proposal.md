# Proposition : rendre le portail web déployable hors du poste hôte

<!-- Statuts : [EXIGENCE] [EXISTANT] [DÉCISION] [PROPOSITION] [QUESTION] — légende : /docs/specifications/README.md#statuts-des-informations. -->

## Why — Pourquoi

Depuis la PR #28 (`e7ed7f3`), l'image Docker sert l'IHM et la page se charge sans jeton, mais la connexion au portail n'aboutit que sur le poste hôte et seulement sur le port 8088 : l'émetteur OIDC de l'IHM est figé au build sur `http://localhost:8180/realms/argos` (AUD-DEP-21), le royaume importé n'accepte que `http://localhost:8088` en redirection et en origine alors qu'Argos est publié par défaut sur 8080 (AUD-DEP-25), le royaume livré n'a ni politique de mot de passe ni protection contre la force brute et accepte le clair (`sslRequired: none`, AUD-SEC-29), l'IHM qui tient des jetons en mémoire est servie sans aucun en-tête de sécurité (AUD-SEC-05) et les quatre chemins publics ajoutés par la PR #28 ne sont couverts par aucun test (AUD-TST-25) [EXISTANT — `/docs/audit/2026-11-21/findings.md`, constats AUD-DEP-21, AUD-DEP-25, AUD-SEC-05, AUD-SEC-29, AUD-TST-25, vérifiés à la révision `b142695`]. L'audit du 2026-11-21 place ces cinq constats en tête du sprint 6 (« Portail déployable et autorisations »), avec pour critère de sortie une connexion au portail servi depuis l'hôte avec les valeurs par défaut, puis depuis un autre poste en réglant seulement l'adresse d'écoute, l'adresse publique et l'émetteur [PROPOSITION — `/docs/audit/2026-11-21/sprints.md`, « Sprint 6 »]. La cible d'hébergement est le Synology du porteur, consulté depuis un PC (ADR-0026, Accepté), avec un Keycloak auto-hébergé (ADR-0027, Accepté).

## What Changes — Changements

- Le portail servi par l'image lit au démarrage l'émetteur OIDC public et l'identifiant du client dans une réponse publique du serveur, et non plus une valeur figée au build ; l'image n'est plus reconstruite pour changer d'émetteur [DÉCISION — décision du porteur, 2026-11-24 ; design.md D1].
- Le royaume livré pour l'installation déclare des adresses de retour, des origines et des adresses de fin de session exactes, alignées sur l'adresse publique d'Argos (par défaut le port publié `ARGOS_PORT`, 8080), sans joker.
- Le royaume livré active la protection contre la force brute (blocage après 5 échecs, attente incrémentale de 60 s, attente maximale de 15 min), la politique de mot de passe `length(12) and notUsername and notEmail and passwordHistory(3)` et `sslRequired: external` ; `sslRequired: all` est documenté derrière le reverse proxy [DÉCISION — décision du porteur, 2026-11-24].
- L'IHM servie par le serveur porte une politique de sécurité du contenu qui nomme l'émetteur configuré, l'interdiction d'affichage en cadre (`frame-ancestors 'none'`), `X-Content-Type-Options: nosniff` et une politique de référent.
- Tests : chemins publics de l'IHM servis sans jeton et API toujours 401 ; absence d'émetteur `localhost:8180` figé dans le bundle ; clés de sécurité du royaume livré ; réglages emballés du client desktop.
- Documentation : guide `docs/deploiement/synology-et-pc.md` (émetteur de l'IHM, adresse publique, Keycloak joignable depuis un autre poste, passage en HTTPS derrière le reverse proxy, réglages du desktop `apiUrl` et `oidcUrl`).
- **BREAKING** : le royaume livré n'accepte plus `http://localhost:8088` par défaut ; une installation qui publiait Argos sur 8088 doit régler l'adresse publique en conséquence. Un royaume déjà importé n'est pas modifié (import au premier démarrage seulement).

## Capabilities — Capacités

### New Capabilities — Nouvelles capacités

Aucune.

### Modified Capabilities — Capacités modifiées

- `comptes-acces` : exigences ajoutées ACC-11 (connexion au portail servi selon l'émetteur et l'adresse publique configurés), ACC-12 (ressources publiques de l'IHM servie, API protégée et en-têtes de sécurité), ACC-13 (royaume livré durci). ACC-01 à ACC-10 sont inchangées (voir « Écarts constatés » pour ACC-01).

## Périmètre

### Inclus

- L'émetteur et le client OIDC de l'IHM embarquée dans l'image (`argos-ui/src/session/session.ts`, réponse publique `GET /api/v1/system/info`).
- Le profil `idp-local` de `compose.yaml` (adresse publique de Keycloak, `KC_HOSTNAME`, publication du port) et le royaume `deploiement/keycloak/argos-realm.json`.
- Les en-têtes HTTP de sécurité posés par le serveur Quarkus sur l'IHM servie (et, sans inconvénient, sur l'API).
- Les tests de la politique d'accès HTTP (chemins publics), du bundle, du royaume livré et des réglages emballés du desktop (`argos-desktop/src/main.js`).
- Le guide de déploiement et les fiches durables.

### Exclu

- HSTS et terminaison TLS : au reverse proxy de DSM (action d'AUD-SEC-05 ; ADR-0026 point 4), seule la documentation est mise à jour.
- L'OTP obligatoire pour l'administrateur plateforme, que l'audit propose seulement d'« envisager » (AUD-SEC-29) : hors périmètre, à décider (Q-5).
- La sauvegarde de la base Keycloak et le compte PostgreSQL partagé (AUD-DEP-24, AUD-SEC-30, sprint 2) : changement `fiabiliser-sauvegarde-et-roles-base`.
- Le test de fumée de l'image construite (AUD-TST-12) : seul le contrôle du bundle est couvert ici.
- La CSP du client desktop (`argos-desktop/src/ui-protocol.js`), déjà couverte par ACC-10-S4.
- Le test matriciel des rôles (AUD-TST-05) : changement `durcir-autorisations-et-debits`.

## Acteurs et parties prenantes

| Acteur | Rôle dans ce changement | Statut |
|---|---|---|
| Utilisateur du portail | se connecte au portail servi par l'image depuis l'hôte ou un autre poste | [EXIGENCE] — ACC-07 ; [PROPOSITION] — ACC-11 |
| Porteur du projet (exploitant du NAS) | installe Argos selon le guide, règle l'adresse publique et l'émetteur ; a fixé l'origine de l'émetteur et les réglages du royaume ; tranche Q-5 à Q-7 | [DÉCISION] — ADR-0026 (Accepté) ; décision du porteur, 2026-11-24 |
| Administrateur plateforme | compte unique du royaume livré, premier exposé aux essais de mot de passe | [EXISTANT] — `deploiement/keycloak/argos-realm.json:94-108` |
| Keycloak | fournisseur d'identité auto-hébergé, royaume `argos` | [DÉCISION] — ADR-0027 (Accepté) |

## Sources et preuves

| Élément | Statut | Source |
|---|---|---|
| Émetteur de l'IHM lu au build Vite, valeur par défaut `http://localhost:8180/realms/argos` | [EXISTANT] | `argos-ui/src/session/session.ts:12`, `:114-115` ; `Dockerfile:13` (seule variable de build `VITE_ARGOS_API_URL`) ; `compose.yaml:80` (`build: .` sans argument) |
| Keycloak du profil `idp-local` publié sur 127.0.0.1, nom d'hôte figé sur localhost | [EXISTANT] | `compose.yaml:54` (`KC_HOSTNAME: http://localhost:${ARGOS_KEYCLOAK_PORT:-8180}`), `:64` |
| Émetteur du serveur en `idp-local` : adresse interne au réseau du projet | [EXISTANT] | `.env.example:23` (`ARGOS_OIDC_URL=http://keycloak:8080/realms/argos`) ; `application.properties:277` |
| Royaume : redirections et origines `http://localhost:8088`, joker, `sslRequired: none`, ni `bruteForceProtected` ni `passwordPolicy` | [EXISTANT] | `deploiement/keycloak/argos-realm.json:7`, `:39-49` ; `grep -n 'bruteForce\|passwordPolicy'` : rien |
| Port publié par défaut 8080 | [EXISTANT] | `compose.yaml:106` ; `.env.example:13` |
| Substitution de variables d'environnement à l'import du royaume | [EXISTANT] | `deploiement/keycloak/argos-realm.json:95-104` (`${ARGOS_ADMIN_EMAIL}`, `${ARGOS_ADMIN_MOT_DE_PASSE_INITIAL}`) |
| IHM en routage par fragment : adresse de retour du portail = origine + `/` | [EXISTANT] | `argos-ui/src/router.ts:52` (`createWebHashHistory`) ; `argos-ui/src/session/session.ts:40-48` |
| Chemins publics de la politique d'accès HTTP, sans test | [EXISTANT] | `argos-server/src/main/resources/application.properties:294-297` ; `api/comptes/AuthentificationTest.java:16-27` |
| Aucun en-tête de sécurité côté serveur ; CSP seulement dans le desktop | [EXISTANT] | `grep -rn 'quarkus.http.header\|Content-Security-Policy\|ContainerResponseFilter' argos-server/src/main` : rien ; `argos-desktop/src/ui-protocol.js:16-33` |
| Desktop : `apiUrl` et `oidcUrl` emballés, sans test ni documentation de l'émetteur | [EXISTANT] | `argos-desktop/src/main.js:25-34` ; `docs/deploiement/synology-et-pc.md:42-43` |
| Session OIDC de l'IHM en mémoire ; CSP du desktop limitée au serveur et à l'émetteur | [DÉCISION] | ADR-0033 (Accepté) points 2 et 4 |
| Keycloak auto-hébergé, client public `argos-ui` (PKCE S256) ; revue de sécurité du royaume prévue | [DÉCISION] | ADR-0027 (Accepté), « Décision » et « Méthode de validation » |
| Hébergement Synology ; accès distant par VPN, reverse proxy DSM et HTTPS si exposition | [DÉCISION] / [PROPOSITION] | ADR-0026 (Accepté) ; point 4 marqué [Proposé] |
| Émetteur public et identifiant du client lus au démarrage de l'IHM depuis une réponse publique du serveur ; ADR candidat maintenu au statut Proposé | [DÉCISION] | décision du porteur, 2026-11-24 |
| Politique de mot de passe `length(12) and notUsername and notEmail and passwordHistory(3)` | [DÉCISION] | décision du porteur, 2026-11-24 (valeur proposée par l'auditeur, AUD-SEC-29, « Action ») |
| Force brute : 5 échecs, attente incrémentale 60 s, attente maximale 15 min | [DÉCISION] | décision du porteur, 2026-11-24 |
| `sslRequired: external` dans le fichier du royaume, `all` documenté derrière le reverse proxy | [DÉCISION] | décision du porteur, 2026-11-24 |
| En-têtes proposés : CSP alignée sur le desktop, `nosniff`, `Referrer-Policy: no-referrer`, `frame-ancestors 'none'` | [PROPOSITION] | AUD-SEC-05, « Action » |

## Écarts constatés

- **Émetteur public et émetteur du serveur diffèrent en `idp-local`.** L'action d'AUD-DEP-21 suggère d'exposer `quarkus.oidc.auth-server-url` à l'IHM ; or, en `idp-local`, cette valeur vaut `http://keycloak:8080/realms/argos`, adresse du réseau interne que le navigateur ne joint pas, tandis que l'émetteur vu par le navigateur et inscrit dans les jetons est `KC_HOSTNAME` (`compose.yaml:54-55`). La réponse publique du serveur expose donc l'émetteur public, non l'URL d'accès du serveur (design.md D1).
- **Refuser le build si l'émetteur vaut localhost** (action d'AUD-DEP-21) casserait l'installation locale par défaut, qui utilise légitimement `http://localhost:8180/realms/argos`. Le critère retenu est l'absence d'émetteur **figé** dans le bundle (design.md D1, D6).
- **Keycloak injoignable depuis un autre poste.** Le critère de sortie du sprint 6 ne cite que `ARGOS_BIND`, `ARGOS_PUBLIC_URL` et l'émetteur ; or le Keycloak du profil `idp-local` est publié sur 127.0.0.1 avec `KC_HOSTNAME` sur localhost (`compose.yaml:54`, `:64`) : la connexion depuis un autre poste exige aussi de rendre Keycloak joignable (adresse publique de Keycloak, ou reverse proxy). `ARGOS_PUBLIC_URL` n'existe pas encore (`grep ARGOS_PUBLIC_URL` dans le dépôt : rien).
- **ACC-01 et les ressources publiques de l'IHM.** ACC-01 énonce que « seuls l'état de santé et l'identité du serveur restent publics » (`/openspec/specs/comptes-acces/spec.md`), alors que `/`, `/index.html`, `/assets/*` et `/licences-polices.txt` sont publics depuis `e7ed7f3` (`application.properties:294`). ACC-12 précise ces ressources sans modifier ACC-01 ; la reformulation éventuelle d'ACC-01 est Q-6.
- **Preuves déplacées depuis l'audit.** Les lignes du guide citées par AUD-DEP-21 (`synology-et-pc.md:39-42`) sont désormais `:42-43` ; `:37` (adresses de retour en 8088) est inchangée. Les autres preuves relues à `b142695` sont à jour.

## Impact

- **Composants** (noms de arc42 §5) : Vue.js 3 SPA (session OIDC), Quarkus Backend / `API & Security` (en-têtes, politique d'accès, éventuellement `GET /api/v1/system/info`), Client desktop Electron (tests et documentation seulement), Keycloak (royaume livré, profil `idp-local`).
- **Contrats d'interface** : champs ajoutés à la réponse publique de `GET /api/v1/system/info` (émetteur public et identifiant du client, aucune donnée d'utilisateur, ACC-01-S2).
- **Données et persistance** : aucune donnée d'Argos ; le royaume Keycloak n'est importé qu'au premier démarrage (`compose.yaml:43`), donc un royaume existant garde ses réglages (procédure manuelle documentée).
- **Sécurité, confidentialité, droits** : CSP, `frame-ancestors`, `nosniff` et référent sur l'IHM ; politique de mot de passe, force brute et TLS au royaume ; adresses de retour exactes. Aucun droit d'Argos modifié.
- **Exploitation et observabilité** : nouvelles variables d'environnement (adresse publique d'Argos, adresse publique de Keycloak ou émetteur public) dans `compose.yaml` et `.env.example`.
- **Documentation durable à mettre à jour** : fiche `/docs/specifications/comptes-acces.md` (ACC-11 à ACC-13, DF-ACC-2), registre `/docs/specifications/README.md`, arc42 §7 (déploiement), §8.1 et §8.3 (identité, sécurité), §8.12 (déploiement), guide `/docs/deploiement/synology-et-pc.md`, `.env.example`, inventaire des diagrammes si un diagramme est ajouté.
- **ADR** : ADR-0027 et ADR-0033 (Acceptés) inchangés ; la configuration de l'IHM à l'exécution (D1) est une décision candidate à un ADR, maintenu au statut Proposé, car elle fixe un contrat entre l'image et l'IHM pour tous les déploiements.
- **Dépendances entre changements** : aucune dépendance obligatoire. Coordination avec `completer-configuration-conteneurs` (même fichier `compose.yaml` et `.env.example`), `mettre-en-place-integration-continue` (exécution du contrôle du bundle et du royaume en CI) et `durcir-autorisations-et-debits` (ACC-14 ajoutée à la même capacité, identifiants disjoints ; le test matriciel y couvre les rôles authentifiés, ce change-ci les chemins publics).

## Conception requise

complète — changement transverse (image, IHM, serveur, royaume, compose) touchant la sécurité et un contrat entre l'image et l'IHM (D1).

## Questions ouvertes

| ID | Question | Bloquante ? | Destinataire |
|---|---|---|---|
| Q-5 | OTP obligatoire pour l'administrateur plateforme (AUD-SEC-29, « envisager ») : à traiter dans un changement ultérieur ? | non | porteur du projet |
| Q-6 | Reformuler ACC-01 (« seuls l'état de santé et l'identité du serveur restent publics ») à l'archivage pour renvoyer à ACC-12 ? | non | porteur du projet |
| Q-7 | Nom et forme des nouvelles variables : `ARGOS_PUBLIC_URL` (citée par `sprints.md`) et une adresse publique de Keycloak distincte, ou une seule variable d'émetteur public ? | non : proposition en design.md D2 | porteur du projet |
