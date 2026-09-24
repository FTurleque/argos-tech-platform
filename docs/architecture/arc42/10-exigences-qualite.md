# 10. Exigences qualité

Les valeurs ci-dessous sont des **cibles initiales à valider**. Elles deviendront contractuelles uniquement après accord des parties prenantes et automatisation de leur vérification.

## 10.1 Tableau des qualités

| Qualité | Priorité | Objectif initial |
|---|---:|---|
| Traçabilité | 1 | chaque alerte/analyse renvoie à sa provenance |
| Sécurité | 1 | aucun accès ou transfert non autorisé |
| Confidentialité | 1 | aucune donnée CONFIDENTIAL/RESTRICTED envoyée au LLM par défaut |
| Fiabilité | 1 | ingestion idempotente et reprenable |
| Maintenabilité | 2 | ajout d’un adapter sans modifier le domaine |
| Observabilité | 2 | tout traitement externe corrélable de bout en bout |
| Performance | 2 | interactions UI courantes p95 < 500 ms hors LLM/externe |
| Disponibilité | 2 | cible à définir selon environnement |
| Coût | 2 | appels IA mesurés, plafonnables et optimisés |
| Portabilité | 3 | déploiement conteneurisé sans dépendance propriétaire obligatoire |

## 10.2 Scénarios principaux

### Q-01 — Traçabilité d’une analyse d’impact

| Champ | Valeur |
|---|---|
| Stimulus | un utilisateur ouvre un impact généré |
| Environnement | production normale |
| Réponse | ARGOS affiche source, item, projet, méthode d’analyse et provenance |
| Mesure | 100 % des `ProjectImpact` disposent d’une provenance exploitable |
| Seuil | aucun impact orphelin |
| Vérification | test d’intégrité + test E2E |
| Propriétaire | Tech Lead / Product Owner à confirmer |

### Q-02 — Confidentialité LLM

| Champ | Valeur |
|---|---|
| Stimulus | une analyse requiert un contenu classé CONFIDENTIAL |
| Environnement | production |
| Réponse | AI Gateway bloque ou réduit/anonymise selon politique explicite |
| Mesure | nombre de violations de politique |
| Seuil | 0 |
| Vérification | tests de policy enforcement + audit |
| Propriétaire | Sécurité à confirmer |

### Q-03 — Reprise d’ingestion

| Champ | Valeur |
|---|---|
| Stimulus | API GitHub/GitLab échoue après réception partielle |
| Environnement | source dégradée |
| Réponse | reprise depuis dernier curseur durable sans duplication métier |
| Mesure | événements perdus/dupliqués |
| Seuil | 0 perte ; duplication logique 0 après déduplication |
| Vérification | test chaos/intégration |
| Propriétaire | Backend/DevOps à confirmer |

### Q-04 — Ajout d’une source

| Champ | Valeur |
|---|---|
| Stimulus | ajout d’un nouveau fournisseur de veille |
| Environnement | développement |
| Réponse | création d’un adapter et mapping canonique sans modifier les règles de domaine existantes |
| Mesure | modules cœur modifiés |
| Seuil | aucun changement structurel du domaine sauf nouveau concept justifié |
| Vérification | revue d’architecture + tests |
| Propriétaire | Architecte/Tech Lead à confirmer |

### Q-05 — Performance consultation

| Champ | Valeur |
|---|---|
| Stimulus | utilisateur charge une vue projet standard |
| Environnement | charge nominale |
| Réponse | données déjà calculées rendues sans appel LLM synchrone |
| Mesure | latence API p95 |
| Seuil | < 500 ms, cible initiale |
| Vérification | test de charge |
| Propriétaire | Backend/Frontend à confirmer |

### Q-06 — Maîtrise des coûts Claude

| Champ | Valeur |
|---|---|
| Stimulus | volume d’items augmente fortement |
| Environnement | production |
| Réponse | quotas, préfiltrage, cache et routage limitent les appels |
| Mesure | coût estimé par workspace/mois et tokens par use case |
| Seuil | budget configurable ; valeur exacte Non déterminée |
| Vérification | métriques + alerte budget |
| Propriétaire | Product Owner / FinOps à confirmer |

## 10.3 Scénarios d’usage, changement et défaillance

La liste complète est maintenue dans [`../quality/scenarios.md`](../quality/scenarios.md).

## 10.4 Scénarios manquants à définir

- disponibilité cible par environnement ;
- RPO/RTO définitifs ;
- volumétrie sources/items/jour ;
- nombre de workspaces/projets/utilisateurs ;
- rétention ;
- objectif de fraîcheur d’ingestion ;
- temps maximal de génération d’un digest ;
- qualité minimale des classifications IA ;
- taux de faux positifs acceptable pour les risques projet ;
- accessibilité frontend.

## 10.5 Preuves nécessaires

- tests automatisés ;
- dashboards SLO ;
- résultats de charge ;
- évaluation IA sur jeu de référence ;
- exigences validées par parties prenantes.
