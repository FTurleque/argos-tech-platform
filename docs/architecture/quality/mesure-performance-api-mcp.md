# Mesure de la latence p95 de l’API et du serveur MCP

<!-- Rapport produit par `npm --prefix e2e run test:performance` (e2e/performance/mesurer-p95.mjs) ; ne pas modifier à la main. -->

Cible : p95 < 500 ms par opération de consultation, hors appels externes et IA (arc42 §8, §10.2 Q-05 ; PERF-01). Exigences : PERF-01, PERF-02 ([fiche](../../specifications/performance-api-mcp.md)).

## Exécution

| Élément | Valeur |
|---|---|
| Date | 2026-11-25T17:23:30.639Z |
| Révision Git | 36662c7 |
| Environnement | pile de test locale (PostgreSQL de e2e/compose.yaml, quarkus:dev http://localhost:8090, Keycloak des Dev Services) |
| Système | win32 10.0.19045 x64, 16 cœurs, 48 Gio |
| Node.js | v24.18.0 |
| Serveur | démarré par la campagne (tâches externes et IA désactivées) |
| Jeu de données | 20 projets × 100 composants, 1000 avis, 20000 impacts (un espace) |
| Appels par opération | 200 (après 20 appels d’échauffement) |
| Concurrence | 4 clients simultanés |
| Verdict | toutes les opérations dans la cible |
| Code de sortie | 0 |

## Mesures par opération

| Opération | Type | Appels | p50 (ms) | p95 (ms) | max (ms) | Erreurs | Verdict |
|---|---|---|---|---|---|---|---|
| GET /api/v1/projects | REST | 200 | 14.6 | 24.3 | 41.9 | 0 | dans la cible |
| GET /api/v1/projects/{id}/inventory/summary | REST | 200 | 15.6 | 29.5 | 40.3 | 0 | dans la cible |
| GET /api/v1/projects/{id}/inventory/components | REST | 200 | 16.4 | 27.6 | 29 | 0 | dans la cible |
| GET /api/v1/impacts/{id} | REST | 200 | 14.7 | 19.5 | 24.8 | 0 | dans la cible |
| GET /api/v1/signals/{id}/impacts | REST | 200 | 14.9 | 19 | 23.5 | 0 | dans la cible |
| GET /api/v1/me/veille | REST | 200 | 49.6 | 72.2 | 82.5 | 0 | dans la cible |
| GET /api/v1/recherche | REST | 200 | 28.7 | 36.8 | 42 | 0 | dans la cible |
| GET /api/v1/subscriptions | REST | 200 | 15 | 17 | 35.1 | 0 | dans la cible |
| GET /api/v1/alertes | REST | 200 | 12.7 | 18.8 | 25.7 | 0 | dans la cible |
| MCP get_project_context | MCP | 200 | 47.2 | 61.2 | 69.3 | 0 | dans la cible |
| MCP get_technology_standard | MCP | 200 | 9.3 | 16.7 | 24.6 | 0 | dans la cible |
| MCP upcoming_releases | MCP | 200 | 105.2 | 117.1 | 131.5 | 0 | dans la cible |
| MCP get_regulatory_changes | MCP | 200 | 12 | 18 | 20.8 | 0 | dans la cible |
| MCP check_dependency | MCP | 200 | 44.9 | 52.8 | 69.1 | 0 | dans la cible |
| MCP search_signals | MCP | 200 | 29.7 | 36.2 | 49.7 | 0 | dans la cible |
| MCP get_security_alerts | MCP | 200 | 89.9 | 101 | 119.1 | 0 | dans la cible |

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
- Limites : le serveur tourne en mode développement (`quarkus:dev`), dont les latences sont plus élevées qu’un binaire de production ; la garde réseau porte sur les requêtes du client de mesure et ne prouve pas à elle seule l’absence d’appel externe côté serveur, garantie par la désactivation des tâches externes et de l’IA au démarrage ; la révision indiquée est celle du dépôt, modifications non validées éventuelles non comprises.
- Outillage : Node.js seul (`fetch`, `node:perf_hooks`), aucune dépendance ajoutée (ADR-0021). La campagne n’est lancée ni par `mvn verify`, ni par `npm --prefix argos-ui test`, ni par `npm --prefix e2e test` (PERF-02-S2).
