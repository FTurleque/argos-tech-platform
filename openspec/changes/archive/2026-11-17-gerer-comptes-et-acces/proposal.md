# Proposition : gérer les comptes, les espaces et le contrôle d'accès

<!-- Statuts : [EXIGENCE] [EXISTANT] [DÉCISION] [PROPOSITION] [QUESTION] — légende : /docs/specifications/README.md#statuts-des-informations. -->

## Why — Pourquoi

Toutes les capacités proposées (`abonnements-veille`, `correlation-impact`, `profils-veille`, `affichage-personnalise`) supposent un utilisateur authentifié et des espaces cloisonnés, sans qu'aucune capacité ne le spécifie [PROPOSITION — `/docs/specifications/README.md`, lot 2 « contrôle d'accès par espace »]. Argos devient un produit ouvert à tous les publics, déployé d'abord sur le Synology du porteur [DÉCISION — ADR-0025, ADR-0026 (Acceptés)] : les comptes ne viennent plus d'un annuaire d'entreprise mais d'un fournisseur d'identité auto-hébergé, et chaque utilisateur doit pouvoir disposer de son propre espace.

## What Changes — Changements

- Authentification OIDC obligatoire pour toute donnée de veille.
- Création du compte à la première connexion, sans accès à un espace par défaut.
- Espaces, rôles (lecteur, contributeur, administrateur d'espace, administrateur plateforme) et invitations.
- Cloisonnement des données par espace, contrôlé dans les services et en base.
- Journalisation des refus d'accès.
- Aucune rupture de contrat (**BREAKING** : aucun) : aucune spec de référence n'existe.

## Capabilities — Capacités

### New Capabilities — Nouvelles capacités

- `comptes-acces` (préfixe `ACC`) : authentification OIDC, création de compte, espaces et rôles, cloisonnement, invitations, journal des refus.

### Modified Capabilities — Capacités modifiées

Aucune : aucune spec de référence n'existe.

## Périmètre

### Inclus

- Connexion, déconnexion, création de compte, espaces, rôles, invitations, révocation.
- Contrôle d'accès de l'API REST ; le serveur MCP réutilise ces règles (change `exposer-serveur-mcp`).

### Exclu

- Le choix du fournisseur d'identité (question Q-1).
- La facturation et les offres payantes : aucune décision (ADR-0021).
- La suppression de compte et l'export des données (RGPD) : changement dédié à créer.

## Acteurs et parties prenantes

| Acteur | Rôle dans ce changement | Statut |
|---|---|---|
| Utilisateur | se connecte, crée son espace, rejoint un espace sur invitation | [PROPOSITION] — ADR-0025 (Accepté) |
| Administrateur d'espace | invite, attribue et révoque les rôles de son espace | [PROPOSITION] — ADR-0016 (Proposé) |
| Administrateur plateforme | gère les catalogues et l'exploitation, sans accès implicite aux données des espaces | [PROPOSITION] — arc42 §8.1 |
| Fournisseur d'identité OIDC | authentifie les utilisateurs | [PROPOSITION] — ADR-0008 (Proposé), ADR-0026 (Accepté) |

## Sources et preuves

| Élément | Statut | Source |
|---|---|---|
| Authentification OIDC | [PROPOSITION] | `/docs/architecture/adr/0008-iam-oidc.md` (Proposé) |
| RBAC + RLS par espace, refus journalisés | [PROPOSITION] | `/docs/architecture/adr/0016-workspace-rbac-rls.md` (Proposé) ; arc42 §8.1 |
| Produit ouvert à tous les publics | [DÉCISION] | `/docs/architecture/adr/0025-produit-ouvert-projet-personnel.md` (Accepté) |
| IdP auto-hébergé sur le Synology | [DÉCISION] | `/docs/architecture/adr/0026-hebergement-synology.md` (Accepté, modalités proposées) |
| Préférences sans effet sur les droits | [PROPOSITION] | exigences ABO-05, PRF-05 |
| Socle applicatif sans code métier | [EXISTANT] | `README.md` §Statut ; `argos-server/src/main/java/io/argos/` |

## Écarts constatés

- ADR-0008 vise l'IdP d'une entreprise ; ADR-0025 et ADR-0026 imposent un IdP auto-hébergé : ADR-0008 est à compléter ou remplacer (tâche 4.2).

## Impact

- **Composants** (arc42 §5) : `API & Security`, `workspace`, `audit`
- **Contrats d'interface** : OpenAPI : session, compte courant, espaces, membres, invitations
- **Données et persistance** : comptes, espaces, appartenances et rôles, invitations, journal des refus ; politiques RLS
- **Sécurité, confidentialité, droits** : cœur du changement : authentification, autorisation, cloisonnement
- **Exploitation et observabilité** : journal des refus ; compte administrateur plateforme initial
- **Documentation durable à mettre à jour** : `/docs/specifications/comptes-acces.md`, registre `/docs/specifications/README.md`, arc42 §8.1, glossaire
- **ADR** : ADR-0008 à compléter pour un IdP auto-hébergé ; ADR-0016 (Proposé) reste la référence

## Conception requise

Complète : le changement porte la sécurité de toute l'application et introduit le modèle des comptes et des espaces.

## Questions ouvertes

| ID | Question | Bloquante ? | Destinataire |
|---|---|---|---|
| Q-1 | Quel fournisseur d'identité : SSO Server de DSM, Keycloak ou Authentik ? | **Répondue le 2026-11-16** : Keycloak (gratuit, Apache-2.0, Dev Services Quarkus) [DÉCISION] | porteur du projet |
| Q-2 | L'inscription est-elle ouverte à tous ou sur invitation au démarrage ? | **Répondue le 2026-11-16** : sur invitation [DÉCISION] | porteur du projet |
| Q-3 | Quel profil de veille est activé à la création d'un compte (PRF-01 impose au moins un profil) ? | **Répondue le 2026-11-16** : profil `developpeur` [DÉCISION] | Product Owner |
| Q-4 | Un accès refusé à une ressource d'un autre espace répond-il « interdit » ou « introuvable » ? | **Répondue le 2026-11-16** : « introuvable » (HTTP 404), qui ne révèle pas l'existence de la ressource [DÉCISION] | architecte / sécurité |
