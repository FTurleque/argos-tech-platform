# Mesure de la latence p95 de l’API et du serveur MCP

<!-- Rapport produit par `npm --prefix e2e run test:performance` (e2e/performance/mesurer-p95.mjs) ; ne pas modifier à la main. -->

Cible : p95 < 500 ms par opération de consultation, hors appels externes et IA (arc42 §8, §10.2 Q-05 ; PERF-01). Exigences : PERF-01, PERF-02 ([fiche](../../specifications/performance-api-mcp.md)).

## Exécution

| Élément | Valeur |
|---|---|
| Date | 2026-11-25T19:36:50.828Z |
| Révision Git | d2d4567 |
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
| GET /api/v1/projects | REST | 200 | 15.4 | 24.8 | 40.5 | 0 | dans la cible |
| GET /api/v1/projects/{id}/inventory/summary | REST | 200 | 16.3 | 28.7 | 57.9 | 0 | dans la cible |
| GET /api/v1/projects/{id}/inventory/components | REST | 200 | 16.4 | 30.8 | 51.8 | 0 | dans la cible |
| GET /api/v1/impacts/{id} | REST | 200 | 14.5 | 19.1 | 21.2 | 0 | dans la cible |
| GET /api/v1/signals/{id}/impacts | REST | 200 | 16.7 | 35.6 | 62.2 | 0 | dans la cible |
| GET /api/v1/me/veille | REST | 200 | 55.5 | 78.1 | 103.1 | 0 | dans la cible |
| GET /api/v1/recherche | REST | 200 | 36.1 | 55 | 61.6 | 0 | dans la cible |
| GET /api/v1/subscriptions | REST | 200 | 14.7 | 18 | 47.4 | 0 | dans la cible |
| GET /api/v1/alertes | REST | 200 | 14.7 | 17.2 | 24.4 | 0 | dans la cible |
| MCP get_project_context | MCP | 200 | 45.9 | 56.2 | 81.7 | 0 | dans la cible |
| MCP get_technology_standard | MCP | 200 | 9.4 | 16.2 | 17.8 | 0 | dans la cible |
| MCP upcoming_releases | MCP | 200 | 108.8 | 152.1 | 228.5 | 0 | dans la cible |
| MCP get_regulatory_changes | MCP | 200 | 13.9 | 20.8 | 43.9 | 0 | dans la cible |
| MCP check_dependency | MCP | 200 | 59.2 | 86 | 100.5 | 0 | dans la cible |
| MCP search_signals | MCP | 200 | 49.5 | 72.2 | 120.4 | 0 | dans la cible |
| MCP get_security_alerts | MCP | 200 | 135.6 | 174.7 | 233.4 | 0 | dans la cible |

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
