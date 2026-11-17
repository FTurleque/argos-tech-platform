# Proposition : connecter l'IHM et le client desktop au fournisseur d'identité OIDC

<!-- Statuts : [EXIGENCE] [EXISTANT] [DÉCISION] [PROPOSITION] [QUESTION] — légende : /docs/specifications/README.md#statuts-des-informations. -->

## Why — Pourquoi

L'API `/api/v1` exige un jeton OIDC depuis le lot 6 [EXISTANT — `argos-server/src/test/java/io/argos/api/comptes/AuthentificationTest.java`], mais l'IHM `argos-ui` appelle l'API sans jeton [EXISTANT — `argos-ui/src/api/client.ts` avant ce changement] : hors de l'état du serveur, aucune vue ne fonctionne. La fiche `comptes-acces` exclut explicitement l'écran de connexion et de déconnexion de l'IHM et du client desktop, et signale que le « message explicite » d'ACC-02-S2 n'est produit par aucun écran [EXISTANT — `/docs/specifications/comptes-acces.md`, Écarts constatés]. Le lot 7 demande de brancher l'IHM et le client desktop sur Keycloak (royaume `argos`, client public `argos-ui`, Authorization Code + PKCE S256) [PROPOSITION — demande du lot 7 ; ADR-0027 (Proposé)].

## What Changes — Changements

- Connexion OIDC de l'IHM : code d'autorisation + PKCE S256, vérification du state, nettoyage de l'adresse au retour, configuration par variables Vite (émetteur, client).
- Jetons en mémoire seulement, renouvelés avant expiration ; état « session expirée » ; déconnexion avec fin de session chez le fournisseur.
- Garde des vues : toute vue sauf l'état du serveur exige une session ; écran de connexion ; écran de refus quand le compte n'est pas créé faute d'invitation (aucune invitation proposée).
- Client REST : `Authorization: Bearer` sur tout `/api/v1` sauf l'identité du serveur ; un 401 relance la connexion, un 403 affiche le refus.
- Client desktop : autorisation dans le navigateur du système avec redirection vers l'interface de bouclage `127.0.0.1` sur un port éphémère (RFC 8252) ; CSP stricte étendue à la seule origine du fournisseur configuré.
- Royaume Keycloak de test : redirections et origines web du client `argos-ui` pour le portail (`http://localhost:5173`) et le desktop (`http://127.0.0.1/argos/retour`, origine `app://argos`) ; la redirection `app://argos/*` est retirée.
- **BREAKING** : l'IHM n'affiche plus les vues de données sans session (elles échouaient déjà en 401 depuis le lot 6) ; la redirection `app://argos/*` n'est plus acceptée par le royaume de test.

## Capabilities — Capacités

### New Capabilities — Nouvelles capacités

Aucune.

### Modified Capabilities — Capacités modifiées

- `comptes-acces` : ajout d'ACC-07 (connexion à l'IHM), ACC-08 (conservation et cycle de vie des jetons), ACC-09 (jeton porteur et refus présentés par l'IHM), ACC-10 (connexion du client desktop). ACC-01 à ACC-06 sont inchangées.

## Périmètre

### Inclus

- `argos-ui` : session OIDC, garde des vues, écrans de connexion et de refus, client REST.
- `argos-desktop` : écoute de bouclage, ouverture du navigateur du système, pont `window.argosDesktop`, CSP.
- Royaume Keycloak de test (`argos-server/src/test/resources/keycloak/argos-realm.json`) et port fixe du Keycloak de développement.
- Tests `node:test` de la logique pure ; test d'intégration du client `argos-ui` contre le Keycloak des Dev Services.

### Exclu

- Les tests de bout en bout dans un navigateur et dans Electron : étape ultérieure du lot 7 (tâches 5.x laissées non cochées).
- Le déploiement de Keycloak sur le Synology et le royaume de production (ADR-0026, ADR-0027).
- La persistance de la session entre deux lancements du client desktop (`safeStorage`) : écartée par ADR-0033 (Proposé).
- Toute règle métier dans l'IHM : les refus sont décidés par le serveur (arc42 §5.1).

## Acteurs et parties prenantes

| Acteur | Rôle dans ce changement | Statut |
|---|---|---|
| Utilisateur du portail web | se connecte, se déconnecte, voit les refus | [PROPOSITION] — ADR-0027 (Proposé) |
| Utilisateur du client desktop | se connecte dans le navigateur du système | [PROPOSITION] — ADR-0022 (Proposé) |
| Personne authentifiée sans invitation | reçoit un refus explicite | [DÉCISION] — Q-2 de `gerer-comptes-et-acces` (2026-11-16) |
| Fournisseur d'identité Keycloak | authentifie, émet et renouvelle les jetons | [PROPOSITION] — ADR-0027 (Proposé) |

## Sources et preuves

| Élément | Statut | Source |
|---|---|---|
| Client public `argos-ui`, Authorization Code + PKCE S256, audience `argos-server` | [PROPOSITION] | `/docs/architecture/adr/0027-keycloak-idp-auto-heberge.md` (Proposé) |
| Desktop : navigateur du système et redirection locale (RFC 8252) | [PROPOSITION] | `/docs/architecture/adr/0022-client-desktop-electron.md` point 6 (Proposé) |
| CSP du desktop limitée au serveur Argos | [EXISTANT] | `argos-desktop/src/ui-protocol.js`, `argos-desktop/test/ui-protocol.test.js` |
| Routes publiques : `/api/v1/system/info`, `/q/health` | [EXISTANT] | `argos-server/src/main/resources/application.properties` ; `AuthentificationTest` |
| 403 `INSCRIPTION_SUR_INVITATION` sur `/api/v1/moi` sans invitation | [EXISTANT] | `PremiereConnexionTest#inscriptionSansInvitationRefusee` |
| Licences permissives seulement, dépendances recensées | [PROPOSITION] | `/docs/architecture/adr/0021-double-licence.md` (Proposé) |
| Vue.js 3 pour l'IHM | [DÉCISION] | `/docs/architecture/adr/0019-stack-java17-quarkus-vue3-postgresql.md` (Accepté) |

## Écarts constatés

- ADR-0022 point 6 prévoit de conserver les jetons du desktop « via le magasin sécurisé du système (`safeStorage`) » ; la demande du lot 7 impose des jetons en mémoire seulement. ADR-0033 (Proposé) retient la mémoire seule ; ADR-0022 n'est pas modifié.
- arc42 §5.1 représente encore un « IdP entreprise » ; ADR-0027 (Proposé) retient Keycloak auto-hébergé. Le libellé du diagramme est mis à jour (tâche 4.1).
- Le royaume de test déclarait `app://argos/*` comme redirection, ce qui supposait une page du fournisseur affichée dans l'application (agent utilisateur intégré, proscrit par RFC 8252 §8.12) ; elle est retirée.

## Impact

- **Composants** (arc42 §5) : `Vue.js 3 SPA` (couche contenu : session, client REST), `Client desktop Electron` (processus principal, pont), `API & Security` inchangé (configuration de développement seulement).
- **Contrats d'interface** : aucun changement de l'OpenAPI ; pont `window.argosDesktop` étendu de `preparerConnexion` et `autoriserConnexion` ; variables `VITE_ARGOS_OIDC_ISSUER`, `VITE_ARGOS_OIDC_CLIENT_ID`, `ARGOS_OIDC_URL` (desktop).
- **Données et persistance** : aucune ; aucune migration.
- **Sécurité, confidentialité, droits** : cœur du changement : PKCE, state, jetons en mémoire, nettoyage de l'adresse, CSP, refus.
- **Exploitation et observabilité** : Keycloak de développement sur le port 8180 ; aucun journal de jeton.
- **Documentation durable à mettre à jour** : `/docs/specifications/comptes-acces.md`, registre `/docs/specifications/README.md`, arc42 §5.1, §8.1, §9, glossaire.
- **ADR** : ADR-0033 (Proposé) — session OIDC de l'IHM : jetons en mémoire, bouclage pour le desktop ; ADR-0022 et ADR-0027 (Proposés) inchangés.

## Conception requise

Complète : le changement porte la sécurité de l'authentification côté clients, touche deux modules et le royaume, et tranche des alternatives (bibliothèque, redirection du desktop, stockage).

## Questions ouvertes

| ID | Question | Bloquante ? | Destinataire |
|---|---|---|---|
| Q-1 | Le client desktop doit-il conserver la session entre deux lancements (jeton de renouvellement chiffré par `safeStorage`, ADR-0022 point 6) ? | non : la mémoire seule est retenue en [PROPOSITION] (ADR-0033) ; la persistance pourra s'ajouter sans changer les specs ACC-07 à ACC-10 | porteur du projet / sécurité |
| Q-2 | Quelles redirections et origines déclarer dans le royaume de production (adresse publique du portail) ? | non : dépend du déploiement de Keycloak sur le Synology (ADR-0026, ADR-0027) | porteur du projet |
