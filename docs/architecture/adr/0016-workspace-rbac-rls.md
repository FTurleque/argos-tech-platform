# ADR-0016 — Cloisonner les workspaces avec RBAC et PostgreSQL RLS

- **Statut** : Proposé
- **Date** : 2026-09-27
- **Décideurs** : revue d’architecture ARGOS
- **Remplace** : —
- **Remplacé par** : —

## Contexte

Les signaux sont globaux mais abonnements, impacts, vues et statuts appartiennent à un espace d’équipe. ARGOS doit empêcher toute fuite de périmètre, notamment via REST et MCP.

## Critères de décision

1. Moindre privilège.
2. Politique cohérente portail/API/MCP.
3. Défense en profondeur.
4. Administration simple des rôles.

## Options considérées

### Option A — RBAC applicatif + Row Level Security PostgreSQL

- vérification systématique de l’espace dans les services ;
- RLS comme garde-fou en base ;
- rôles lecteur, contributeur, administrateur d’espace, administrateur plateforme.

### Option B — Une instance ou base par équipe

- isolation forte ;
- coût d’exploitation et duplication disproportionnés au MVP.

## Décision

Utiliser **RBAC dans l’application**, relié aux identités OIDC, et renforcer le cloisonnement des données d’espace avec **PostgreSQL Row Level Security**.

## Conséquences positives

- défense en profondeur ;
- un modèle unique d’autorisation ;
- architecture mutualisée.

## Conséquences négatives

- politiques RLS à tester et maintenir ;
- attention particulière aux traitements batch et comptes techniques.

## Méthode de validation

Tests automatisés inter-workspaces, tests MCP/REST, revue sécurité et journalisation des refus.

## Traçabilité

- **Exigences** : F08, F10, F12
- **Scénarios qualité** : tentative d’accès à un autre espace refusée et journalisée
- **Tickets/PR** : PR d’alignement v2.3
- **Fichiers source** : `arc42/08-concepts-transverses.md`
