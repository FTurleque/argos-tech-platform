# Fiche capacité : `performance-api-mcp`

| Élément | Valeur |
|---|---|
| Préfixe d'exigences | `PERF` |
| Spec de référence | `/openspec/specs/performance-api-mcp/spec.md` — absente tant qu'aucun changement n'est archivé |
| Changements en cours | [`automatiser-chaine-veille`](/openspec/changes/automatiser-chaine-veille/specs/performance-api-mcp/spec.md) — proposé, non implémenté |
| Statut de la capacité | proposée |
| Composants (arc42 §5) | `API & Security`, serveur MCP (`mcp`), `e2e/` (campagne de mesure) |

Tout le contenu de cette fiche porte le statut [PROPOSITION] (changement `automatiser-chaine-veille`) sauf mention contraire.

## Objectif

Vérifier, par une mesure automatisée et gratuite, que les consultations de l'API REST et les outils du serveur MCP respectent la cible de latence d'arc42, et en garder une preuve datée et versionnée. Source : décision J8 du porteur du projet (2026-11-19) ; arc42 §8 (cibles initiales) ; arc42 §10.2 Q-05.

## Acteurs

| Acteur | Interaction | Statut |
|---|---|---|
| Développeur, exploitant | lance la campagne par une commande dédiée et lit le rapport | [PROPOSITION] — J8 |
| Pile de test locale (PostgreSQL de `compose.yaml`, serveur sur le port 8090, Keycloak des Dev Services) | reçoit les requêtes mesurées | [EXISTANT] — `e2e/README.md` |
| Client MCP de test | appelle les huit outils en lecture seule | [EXISTANT] — `e2e/support/mcp.ts` |

## Périmètre et exclusions

- **Inclus** : opérations de consultation de l'API REST (projets, inventaire, impacts, veille, recherche, abonnements, alertes) et outils MCP en lecture seule ; calcul de p50, p95 et maximum par opération ; rapport JSON et Markdown versionnés ; verdict par rapport à 500 ms.
- **Exclu** : opérations qui appellent une source externe ou un modèle d'IA (synchronisations, collectes, résumés IA), listées comme exclues dans le rapport ; environnement de production (Synology) ; outils sous licence non permissive (k6, AGPL) ou payants (J8, ADR-0021).

## Préconditions et déclencheurs

- Docker, JDK et Node.js disponibles, comme pour `npm --prefix e2e test` ; aucun accès Internet nécessaire.
- Déclencheur : la commande dédiée `npm --prefix e2e run test:performance` (proposition P-9), jamais le build ni les tests par défaut (PERF-02-S2).
- La pile E2E du poste n'est pas déjà utilisée par un autre lancement (verrou du dépôt).

## Entrées, sorties et validations

| Entrée / sortie | Contenu | Validation ou contrainte | Statut |
|---|---|---|---|
| Entrée : jeu de données de référence | espaces, projets, inventaires, signaux, impacts, éléments de veille | volume non fixé [QUESTION Q-PERF-1] ; valeur retenue écrite dans le rapport | [PROPOSITION] |
| Entrée : paramètres de campagne | nombre d'appels par opération, concurrence, échauffement | concurrence non fixée [QUESTION Q-PERF-2] ; valeurs écrites dans le rapport | [PROPOSITION] |
| Sortie : rapport JSON | date, révision Git, environnement, volume, concurrence, mesures par opération, opérations exclues, verdict | `e2e/resultats/performance/` | [PROPOSITION] |
| Sortie : rapport Markdown | mêmes informations, lisibles | `docs/architecture/quality/mesure-performance-api-mcp.md` | [PROPOSITION] |
| Sortie : code de sortie | 0 si tous les p95 < 500 ms, non nul sinon ou en cas de requête hors du poste | PERF-01-S2, PERF-02-S3 | [PROPOSITION] |

## Règles métier

| Règle | Exigence(s) | Source | Statut |
|---|---|---|---|
| p95 < 500 ms par opération de consultation API ou outil MCP, hors appels externes et IA, sur la pile de test locale | PERF-01 | décision J8 du porteur du projet, 2026-11-19 ; arc42 §8 ; arc42 §10.2 Q-05 | [DÉCISION] pour la vérification de la cible ; cible arc42 « initiale » [PROPOSITION] |
| Commande dédiée, hors build par défaut ; outils gratuits sous licence permissive ; aucune requête hors du poste ; rapport versionné | PERF-02 | décision J8 du porteur du projet, 2026-11-19 ; ADR-0021 | [DÉCISION] |
| Outil retenu : script Node sans dépendance (`fetch`, `node:perf_hooks`) ; autocannon (MIT) ou Gatling (Apache-2.0) restent des alternatives permises | PERF-02 | proposition P-9 du changement | [PROPOSITION] |

## Droits d'accès et restrictions

La campagne s'authentifie avec les comptes du royaume de test du dépôt (`argos-server/src/test/resources/keycloak/argos-realm.json`), jamais avec un secret réel ; elle mesure les opérations sous les mêmes contrôles (RBAC, RLS) que l'IHM.

## Effets de bord observables

- écriture des rapports JSON et Markdown ;
- démarrage puis arrêt de la pile de test locale lancée par la campagne, et d'elle seule.

## Qualités mesurables

| Qualité | Cible | Source | Statut |
|---|---|---|---|
| Latence API et MCP, p95, hors appels externes et IA | < 500 ms | arc42 §8 ; arc42 §10.2 Q-05 ; QS-06 | [PROPOSITION] — cible initiale ; mesure de la recherche seule : p95 = 250 ms le 2026-11-16 (QS-13, [rapport](../architecture/quality/mesure-qs13-recherche.md)) [EXISTANT] |

## Diagrammes

### DF-PERF-1 — Une campagne de mesure

- **Question** : comment une campagne aboutit-elle à un verdict et à un rapport, sans quitter le poste ?
- **Type** : organigramme (flowchart)
- **Portée** : cible proposée
- **Exigences** : PERF-01, PERF-02

```mermaid
flowchart TD
    A["Commande dédiée"] --> B["Pile de test locale<br/>et jeu de données de référence"]
    B --> C["Appels mesurés par opération<br/>(API en lecture, outils MCP)"]
    C --> D{"Requête vers un hôte<br/>hors du poste ?"}
    D -- oui --> E["Échec, hôte nommé,<br/>aucun rapport valide"]
    D -- non --> F["p50, p95, max par opération,<br/>opérations exclues listées"]
    F --> G{"Chaque p95 < 500 ms ?"}
    G -- oui --> H["Rapport versionné, code 0"]
    G -- non --> I["Rapport versionné,<br/>opérations « hors cible », code non nul"]
```

La campagne ne mesure que des opérations sans appel externe ni IA : les autres sont listées avec le motif de leur exclusion (PERF-01-S3). Le rapport conserve les paramètres de la campagne (volume, concurrence), faute de valeurs fixées par les sources (Q-PERF-1, Q-PERF-2).

## Critères d'acceptation

- Scénarios `PERF-01-S1` à `PERF-02-S3` de la spec.
- QS-06 (« API p95 < 500 ms ») du registre `/docs/architecture/quality/scenarios.md`.

## Interprétations introduites par un changement

Propositions du changement `automatiser-chaine-veille`, à valider [PROPOSITION] :

- P-9 : script Node sans dépendance, commande `npm --prefix e2e run test:performance`, rapports dans `e2e/resultats/performance/` et `docs/architecture/quality/` ;
- une opération mesurée au-dessus de la cible rend la campagne en échec (code de sortie non nul), sans bloquer le build par défaut.

## Écarts constatés

- Aucune campagne couvrant l'ensemble de l'API et du MCP n'existe : seule la recherche est mesurée (`MesurePerformanceRechercheTest`, QS-13).

## Questions ouvertes

| ID | Question | Impact | Destinataire |
|---|---|---|---|
| Q-PERF-1 | Quel volume de données de référence pour la campagne ? | représentativité de PERF-01 | porteur du projet |
| Q-PERF-2 | Quelle concurrence (clients simultanés) pour mesurer le p95 ? | représentativité de PERF-01 | porteur du projet |

## Traçabilité

| Exigence | Scénarios | Conception / diagramme | Tâche | Test ou preuve |
|---|---|---|---|---|
| PERF-01 | PERF-01-S1, PERF-01-S2, PERF-01-S3 | design.md D8, DC-8 ; DF-PERF-1 | 3.6, 3.7 | rapport `docs/architecture/quality/mesure-performance-api-mcp.md` — preuve manquante |
| PERF-02 | PERF-02-S1, PERF-02-S2, PERF-02-S3 | design.md D8, DC-8 ; DF-PERF-1 | 3.6, 3.7 | `node --test e2e/performance/` — à écrire |

Matrice détaillée : [`tasks.md` du changement](/openspec/changes/automatiser-chaine-veille/tasks.md#matrice-de-traçabilité).
