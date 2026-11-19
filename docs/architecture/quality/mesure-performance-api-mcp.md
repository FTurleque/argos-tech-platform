# Mesure de la latence p95 de l’API et du serveur MCP

<!-- Rapport produit par `npm --prefix e2e run test:performance` (e2e/performance/mesurer-p95.mjs) ; ne pas modifier à la main. -->

Cible : p95 < 500 ms par opération de consultation, hors appels externes et IA (arc42 §8, §10.2 Q-05 ; PERF-01). Exigences : PERF-01, PERF-02 ([fiche](../../specifications/performance-api-mcp.md)).

## Exécution

| Élément | Valeur |
|---|---|
| Date | 2026-11-19T18:38:53.741Z |
| Révision Git | fbb04c9 |
| Environnement | pile de test locale (PostgreSQL de e2e/compose.yaml, quarkus:dev http://localhost:8090, Keycloak des Dev Services) |
| Système | win32 10.0.19045 x64, 16 cœurs, 48 Gio |
| Node.js | v24.18.0 |
| Jeu de données | 20 projets × 100 composants, 1000 avis, 20000 impacts (un espace) |
| Appels par opération | 200 (après 20 appels d’échauffement) |
| Concurrence | 4 clients simultanés |
| Verdict | hors cible : GET /api/v1/me/veille |
| Code de sortie | 1 |

## Mesures par opération

| Opération | Type | Appels | p50 (ms) | p95 (ms) | max (ms) | Erreurs | Verdict |
|---|---|---|---|---|---|---|---|
| GET /api/v1/projects | REST | 200 | 12.9 | 20 | 36.7 | 0 | dans la cible |
| GET /api/v1/projects/{id}/inventory/summary | REST | 200 | 16 | 29.5 | 47.1 | 0 | dans la cible |
| GET /api/v1/projects/{id}/inventory/components | REST | 200 | 15.3 | 21.5 | 27.9 | 0 | dans la cible |
| GET /api/v1/impacts/{id} | REST | 200 | 12.8 | 18.9 | 27.8 | 0 | dans la cible |
| GET /api/v1/signals/{id}/impacts | REST | 200 | 14.6 | 18.5 | 24.6 | 0 | dans la cible |
| GET /api/v1/me/veille | REST | 200 | 510.7 | 618.2 | 827.5 | 0 | **hors cible** |
| GET /api/v1/recherche | REST | 200 | 30 | 38.5 | 45.8 | 0 | dans la cible |
| GET /api/v1/subscriptions | REST | 200 | 13.8 | 18.9 | 27.8 | 0 | dans la cible |
| GET /api/v1/alertes | REST | 200 | 14.2 | 20.1 | 22.9 | 0 | dans la cible |
| MCP get_project_context | MCP | 200 | 47.3 | 62.3 | 84 | 0 | dans la cible |
| MCP get_technology_standard | MCP | 200 | 9 | 15.6 | 18.3 | 0 | dans la cible |
| MCP upcoming_releases | MCP | 200 | 121.5 | 222.2 | 351.1 | 0 | dans la cible |
| MCP get_regulatory_changes | MCP | 200 | 11.1 | 16.4 | 17.9 | 0 | dans la cible |
| MCP check_dependency | MCP | 200 | 46.7 | 57.7 | 67.9 | 0 | dans la cible |
| MCP search_signals | MCP | 200 | 30.3 | 41.2 | 59.3 | 0 | dans la cible |
| MCP get_security_alerts | MCP | 200 | 90.7 | 102.6 | 113.2 | 0 | dans la cible |

## Opérations exclues

| Opération | Motif de l’exclusion |
|---|---|
| POST /api/v1/ops/sources/{id}/synchronizations | synchronisation d’une source externe (OSV) |
| collectes planifiées (réglementaire, artistes, radar, activité, veille web) | appels à des sources externes ; tâches désactivées pendant la campagne |
| résumés IA des alertes, synthèses IA des fiches, analyse complémentaire | appel à un modèle d’IA (passerelle IA absente pendant la campagne) |
| GET /api/v1/veille-web/… (récupération d’une page à la demande) | récupération d’une page externe |
| MCP explain_change | synthèse d’un changement par le modèle d’IA |
| écritures (transitions d’impact, abonnements, préférences, vues, imports) | hors du périmètre « consultation » de la cible |

## Méthode

- Pile de test locale : PostgreSQL jetable de `e2e/compose.yaml`, serveur `quarkus:dev` sur le port 8090 avec le Keycloak des Dev Services (royaume de test du dépôt), tâches planifiées qui joindraient une source externe désactivées, aucune passerelle IA.
- Garde réseau : toute requête hors de la boucle locale (`localhost`, `127.0.0.1`, `[::1]`) fait échouer la campagne en nommant l’hôte (PERF-02-S3).
- Jetons : comptes du royaume de test (flux par mot de passe du client de test `argos-server`), aucun secret réel.
- Durée d’un appel : de l’envoi de la requête à la lecture complète de la réponse, mesurée par `performance.now()` côté client ; centiles par la méthode du rang le plus proche.
- Outillage : Node.js seul (`fetch`, `node:perf_hooks`), aucune dépendance ajoutée (ADR-0021). La campagne n’est lancée ni par `mvn verify`, ni par `npm --prefix argos-ui test`, ni par `npm --prefix e2e test` (PERF-02-S2).
