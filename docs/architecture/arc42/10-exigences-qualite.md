# 10. Exigences qualité

Les valeurs ci-dessous sont des **cibles initiales à valider**. Elles deviendront contractuelles uniquement après accord des parties prenantes et automatisation de leur vérification.

## 10.1 Tableau des qualités

| Qualité | Priorité | Objectif initial |
|---|---:|---|
| Traçabilité | 1 | chaque alerte/analyse renvoie à sa provenance |
| Sécurité | 1 | aucun accès ou transfert non autorisé |
| Confidentialité | 1 | aucune donnée CONFIDENTIAL/RESTRICTED envoyée au modèle d’IA |
| Fiabilité | 1 | ingestion idempotente et reprenable |
| Maintenabilité | 2 | ajout d’un adapter sans modifier le domaine |
| Observabilité | 2 | tout traitement externe corrélable de bout en bout |
| Performance | 2 | interactions UI courantes p95 < 500 ms hors LLM/externe |
| Disponibilité | 2 | cible à définir selon environnement |
| Coût | 2 | composants gratuits ; appels IA mesurés et plafonnés (par jour, par espace, par compte) |
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
| Vérification | test de charge ; campagne automatisée `npm --prefix e2e run test:performance` (PERF-01, PERF-02, changement `automatiser-chaine-veille`) |
| Résultat | mesuré le 2026-11-27 sur la pile de test locale (révision `496add9` ; 20 projets × 100 composants, 1 000 avis, 20 000 impacts actifs, 500 000 impacts résolus ou écartés ; 200 appels par opération, 4 clients simultanés) : 17 opérations API et MCP sur 17 dans la cible, p95 de 16,9 à 185,5 ms ; `GET /api/v1/me/veille` : p95 = 185,5 ms ([rapport](../quality/mesure-performance-api-mcp.md)) [EXISTANT] |
| Propriétaire | Backend/Frontend à confirmer |

### Q-06 — Maîtrise de la charge IA

| Champ | Valeur |
|---|---|
| Stimulus | volume d’items augmente fortement |
| Environnement | production, passerelle IA active |
| Réponse | le plafond journalier de l’instance et les quotas journaliers par espace et par compte bornent les appels au modèle local ; au-delà, l’appel est refusé et Argos garde son résultat déterministe |
| Mesure | appels par jour pour l’instance, par espace et par compte ; refus pour plafond ou quota |
| Seuil | 1 000 appels par jour pour l’instance, 100 par espace, 20 par compte (`ARGOS_IA_PLAFOND_JOUR`, `ARGOS_IA_QUOTA_ESPACE_JOUR`, `ARGOS_IA_QUOTA_COMPTE_JOUR`) |
| Vérification | tests de la passerelle IA (capacité [`passerelle-ia`](../../specifications/passerelle-ia.md)) ; métriques des appels IA sur `/q/metrics` |
| Propriétaire | porteur du projet |

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
