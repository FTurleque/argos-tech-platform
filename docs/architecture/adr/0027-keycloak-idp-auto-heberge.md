# ADR-0027 — Authentifier les utilisateurs avec un Keycloak auto-hébergé

- **Statut** : Accepté
- **Date** : 2026-11-18 (acceptation ; proposition du 2026-11-16)
- **Décideurs** : porteur du projet (acceptation du 2026-11-18)
- **Complète** : [ADR-0008](0008-iam-oidc.md) (Proposé), sans en changer le protocole (OIDC)
- **Remplace** : —
- **Remplacé par** : —

## Contexte

- ADR-0008 propose OIDC vers l'IdP d'une entreprise. Argos est désormais un produit ouvert à tous les publics, porté à titre personnel ([ADR-0025](0025-produit-ouvert-projet-personnel.md), Accepté) et hébergé sur le Synology du porteur ([ADR-0026](0026-hebergement-synology.md), Accepté) : aucun IdP d'entreprise n'existe.
- Le porteur a choisi Keycloak le 2026-11-16 (question Q-1 du changement [`gerer-comptes-et-acces`](/openspec/changes/archive/2026-11-17-gerer-comptes-et-acces/proposal.md)), avec une inscription sur invitation (Q-2).
- ADR-0021 point 4 : toute dépendance livrée doit avoir une licence permissive. Keycloak est publié sous Apache-2.0 et s'exécute comme système séparé (point 5) ; l'extension `quarkus-oidc` intégrée au serveur est sous Apache-2.0.
- Le code d'Argos (`argos-server`) valide les jetons avec `quarkus-oidc` et ses tests utilisent le Keycloak des Dev Services Quarkus ([`application.properties`](/argos-server/src/main/resources/application.properties), [tests d'intégration](/argos-server/src/test/java/io/argos/api/comptes/)).

## Critères de décision

1. Gratuit et sous licence permissive (ADR-0021).
2. Auto-hébergeable en conteneur sur le Synology (ADR-0026), avec une empreinte mémoire compatible avec 8 Go partagés.
3. OIDC standard : Authorization Code + PKCE pour l'IHM et le client desktop (ADR-0022), jetons porteurs pour l'API.
4. Inscription désactivable et comptes créés par invitation.
5. Tests automatisés sans service externe.

## Options considérées

### Option A — SSO Server de DSM

- avantages : déjà présent sur le NAS, aucune installation ;
- inconvénients : lié au NAS, donc à réécrire pour l'hébergement en ligne ; aucune image de conteneur pour les tests ; fonctions OIDC et MFA moins documentées.

### Option B — Keycloak

- avantages : Apache-2.0 ; image officielle ; prise en charge native par Quarkus (Dev Services, `quarkus-test-keycloak-server`) ; royaumes importables en JSON ; inscription libre désactivable ; MFA, réinitialisation de mot de passe et fédération disponibles ;
- inconvénients : JVM gourmande en mémoire (**Hypothèse à valider** : de l'ordre de 500 Mo à 1 Go) ; administration plus riche, donc plus longue à maîtriser.

### Option C — Authentik

- avantages : interface moderne, flux d'invitation intégrés ;
- inconvénients : deux conteneurs supplémentaires (serveur et worker) en plus de sa base ; aucun Dev Services Quarkus, donc un environnement de test à construire.

## Décision

**Proposition : option B.** Argos délègue l'authentification à un Keycloak auto-hébergé, royaume `argos` :

- inscription libre désactivée dans Keycloak ; Argos ne crée un compte, à la première connexion, que pour une adresse e-mail vérifiée destinataire d'une invitation en attente, ou pour l'administrateur plateforme initial (`ARGOS_ADMIN_EMAIL`) ;
- client public `argos-ui` (Authorization Code + PKCE S256) pour l'IHM et le client desktop ; l'API (`quarkus.oidc.application-type=service`) vérifie signature, émetteur, audience `argos-server` et expiration de chaque jeton ;
- l'autorisation (espaces, rôles, cloisonnement) reste dans Argos (ADR-0016) ; Keycloak ne porte aucun rôle d'espace.

## Conséquences positives

- même fournisseur en développement, en test et en exploitation ;
- MFA et gestion des mots de passe hors du code d'Argos ;
- passage à un hébergement en ligne sans changer le code.

## Conséquences négatives

- un conteneur de plus sur le Synology, avec sa sauvegarde ;
- disponibilité d'Argos liée à celle de Keycloak (R-18) : les jetons déjà émis restent valides jusqu'à expiration ;
- une image Keycloak à reconstruire à chaque changement de version (`docker compose --profile idp-local up -d --build`).

## Conséquences neutres / compromis

- ADR-0008 reste la référence pour le protocole ; le présent ADR fixe le fournisseur.
- Sur le PC et le NAS, Keycloak est le service du profil `idp-local` de `compose.yaml` ([guide de déploiement](/docs/deploiement/synology-et-pc.md)). Il s'exécute depuis une image optimisée construite localement ([`deploiement/keycloak/Dockerfile`](/deploiement/keycloak/Dockerfile), `kc.sh build` puis `start --optimized`), seule forme compatible avec un système de fichiers racine en lecture seule (EXP-16, décision du porteur du 2026-11-24 sur Q-1 du changement `durcir-conteneurs-de-service`). Argos lit l'URL du royaume dans `ARGOS_OIDC_URL`.

## Méthode de validation

- tests d'intégration avec le Keycloak des Dev Services : jeton absent, invalide, d'une autre audience ; première connexion ; refus du fournisseur (`argos-server/src/test/java/io/argos/api/comptes/`) ;
- déploiement sur le Synology et mesure de la mémoire consommée ;
- revue de sécurité de la configuration du royaume (durées des jetons, MFA, politique de mot de passe).

## Traçabilité

- **Exigences** : ACC-01, ACC-02 (`openspec/changes/gerer-comptes-et-acces/specs/comptes-acces/spec.md`)
- **Scénarios qualité** : aucun scénario du registre `docs/architecture/quality/scenarios.md` ne porte sur l'authentification ; risque R-18
- **Diagrammes** : DF-ACC-1 (`docs/specifications/comptes-acces.md`)
- **Tickets/PR** : Non déterminé
- **Fichiers source** : `argos-server/src/main/resources/application.properties`, `argos-server/src/test/resources/keycloak/argos-realm.json`
