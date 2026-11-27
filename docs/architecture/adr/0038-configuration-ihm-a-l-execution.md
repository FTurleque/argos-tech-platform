---
id: ADR-0038
title: "Configuration de l'IHM lue à l'exécution dans la réponse publique du serveur"
domain: architecture
rules: false
---

# ADR-0038 — Configuration de l'IHM lue à l'exécution dans la réponse publique du serveur

- **Statut** : Proposé
- **Date** : 2026-11-25
- **Décideurs** : Non déterminé (décision de principe du porteur du projet du 2026-11-24, à confirmer par l'acceptation)
- **Remplace** : —
- **Remplacé par** : —

## Contexte

- Constat AUD-DEP-21 de l'audit du 2026-11-21 : l'IHM servie par l'image Docker mène la connexion auprès d'un émetteur OIDC que Vite fige à la compilation ; la connexion n'aboutit que sur le poste hôte.
- L'image unique ([ADR-0026](0026-hebergement-synology.md)) sert le PC et le Synology ; l'émetteur est une donnée de l'instance, comme l'adresse publique d'Argos.
- Le navigateur et les jetons connaissent l'émetteur **public** du royaume ([ADR-0027](0027-keycloak-idp-auto-heberge.md)) ; le serveur peut joindre le royaume par une adresse interne au réseau du projet (profil `idp-local`).
- La session OIDC de l'IHM est tenue en mémoire et le desktop est connecté par l'interface de bouclage ([ADR-0033](0033-session-oidc-ihm-memoire-bouclage.md)) ; la politique de sécurité du contenu du serveur doit nommer le même émetteur que l'IHM.

## Critères de décision

1. Une image unique, sans reconstruction pour changer d'émetteur.
2. Une seule source de l'émetteur pour l'IHM, la politique de sécurité du contenu et Keycloak.
3. Aucun émetteur par défaut dans le bundle de production.
4. Aucune information sensible ajoutée à la réponse publique.

## Options considérées

### Option A — Lecture au démarrage dans la réponse publique du serveur

- avantages : image unique ; l'émetteur change par la configuration et un redémarrage ; une variable d'environnement alimente l'IHM et la politique de sécurité du contenu ;
- inconvénients : une requête de plus au chargement de la page ; sans réponse du serveur, aucune connexion n'est possible ;
- risques : une réponse inattendue du serveur ; l'IHM refuse alors toute connexion plutôt que de se rabattre sur une valeur par défaut ;
- coûts/complexité : faibles.

### Option B — Arguments de construction de l'image

- avantages : aucun changement de l'IHM ni de l'API ;
- inconvénients : reconstruction à chaque changement d'émetteur ; image liée à une instance ; seconde copie de la valeur pour la politique de sécurité du contenu ;
- risques : désaccord entre la copie du bundle et celle du serveur ;
- coûts/complexité : moyens à l'usage.

## Décision

Option A :

1. La réponse publique `GET /api/v1/system/info` porte `oidcIssuer` (émetteur public) et `oidcClientId` (client public de l'IHM). Le serveur les lit dans `argos.ui.oidc.emetteur` (`ARGOS_OIDC_ISSUER_PUBLIC`, par défaut `ARGOS_OIDC_URL`) et `argos.ui.oidc.client-id`.
2. L'IHM lit ces deux valeurs avant toute connexion ; elle ne conserve aucun émetteur par défaut. Sans réponse valable, elle affiche l'état d'erreur de la connexion et ne tente rien.
3. La politique de sécurité du contenu posée par le serveur nomme l'origine de ce même émetteur.
4. Un test construit le bundle de production et échoue s'il contient une adresse d'émetteur figée.
5. Le client desktop garde ses réglages d'emballage (`apiUrl`, `oidcUrl`) pour sa politique de sécurité du contenu et la destination de l'autorisation ; ils désignent le même émetteur que le serveur.

## Conséquences positives

- Le même artefact se déploie sur le PC et sur le NAS ; changer d'émetteur demande `.env` et un redémarrage.
- L'émetteur de l'IHM et celui de la politique de sécurité du contenu ne peuvent plus diverger.

## Conséquences négatives

- La page dépend d'une requête au serveur avant de proposer la connexion.
- Le client desktop doit recevoir l'émetteur à l'emballage et à l'exécution, en plus du serveur.

## Conséquences neutres / compromis

- Les deux valeurs sont déjà visibles de quiconque lance une connexion ; leur exposition n'ajoute aucune donnée sensible (le client `argos-ui` est public).

## Méthode de validation

- `argos-ui/tests/session.test.mjs` : lecture de la configuration, échec sans repli ; `argos-ui/tests/bundle.test.mjs` : bundle sans émetteur figé.
- `AuthentificationTest` (réponse publique) et `EnTetesSecuriteTest` (origine de l'émetteur dans la politique) du serveur.
- Recette du portail servi par l'image, depuis l'hôte puis depuis un autre poste (`docs/deploiement/recette-poste.md`).

## Traçabilité

- **Exigences** : ACC-11, ACC-12 (`comptes-acces`).
- **Scénarios qualité** : changement d'émetteur sans reconstruction (ACC-11-S3).
- **Diagrammes** : DC-1 et DC-2 du changement `rendre-portail-deployable`.
- **Tickets/PR** : Non déterminé
- **Fichiers source** : `argos-ui/src/session/configuration.ts`, `argos-ui/src/session/session.ts`, `argos-server/src/main/java/io/argos/api/SystemInfoResource.java`, `argos-server/src/main/java/io/argos/api/security/EnTetesSecurite.java`.

## Notes

Complète ADR-0033 : l'origine de l'émetteur de l'IHM web vient du serveur, celle du desktop de son emballage.
