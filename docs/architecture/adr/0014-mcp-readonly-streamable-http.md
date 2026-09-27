# ADR-0014 — Intégrer un serveur MCP en lecture seule via Streamable HTTP

- **Statut** : Proposé
- **Date** : 2026-09-27
- **Décideurs** : revue d’architecture ARGOS
- **Remplace** : —
- **Remplacé par** : —

## Contexte

ARGOS doit rendre ses informations consultables depuis Claude Code et les IDE sans exposer de capacité de modification au MVP.

## Critères de décision

1. Compatibilité avec les clients MCP modernes.
2. Authentification et autorisation d’entreprise.
3. Faible complexité d’exploitation.
4. Aucun effet de bord au MVP.

## Options considérées

### Option A — Module MCP intégré, Streamable HTTP, lecture seule

- réutilise les contrats métier du monolithe ;
- déploiement simple ;
- outils bornés et auditables.

### Option B — Serveur MCP séparé ou transport historique HTTP+SSE

- isolement plus fort ;
- déploiement supplémentaire ;
- duplication potentielle des politiques d’accès.

## Décision

Le MVP expose un **module MCP intégré**, en **lecture seule**, via **Streamable HTTP**. L’authentification s’appuie sur OAuth/OIDC et les droits sont identiques à ceux du portail.

## Conséquences positives

- accès IDE direct aux standards, alertes et contextes projet ;
- surface d’action réduite ;
- politique d’accès unifiée.

## Conséquences négatives

- nécessité de tests spécifiques MCP et prompt injection indirecte ;
- dépendance à l’évolution de la spécification MCP.

## Méthode de validation

POC depuis au moins un IDE avec `check_dependency` et `get_security_alerts`, tests d’autorisation, rate limiting et journalisation.

## Traçabilité

- **Exigences** : F10
- **Scénarios qualité** : p95 < 500 ms hors IA ; refus inter-workspace journalisé
- **Tickets/PR** : PR d’alignement v2.3
- **Fichiers source** : `arc42/08-concepts-transverses.md`
