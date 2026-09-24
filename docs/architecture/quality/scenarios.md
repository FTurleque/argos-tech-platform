# Scénarios qualité ARGOS

Ce registre complète arc42 section 10. Les seuils sont **Hypothèses à valider** tant qu’ils ne sont pas approuvés et automatisés.

| ID | Type | Stimulus | Environnement | Réponse attendue | Mesure / seuil initial | Vérification | Propriétaire |
|---|---|---|---|---|---|---|---|
| QS-01 | Usage | consultation d’un impact | nominal | source et provenance visibles | 100 % impacts traçables | E2E + intégrité DB | à confirmer |
| QS-02 | Sécurité | contenu CONFIDENTIAL demandé par une analyse | prod | appel LLM bloqué/réduit selon policy | 0 violation | tests policy + audit | Sécurité |
| QS-03 | Défaillance | API GitHub/GitLab retourne 429/5xx | prod | retry borné, curseur stable | 0 perte | chaos/intégration | Backend |
| QS-04 | Défaillance | même webhook reçu plusieurs fois | prod | un seul effet métier | 0 duplication logique | test idempotence | Backend |
| QS-05 | Changement | ajout d’une nouvelle source | dev | nouvel adapter sans couplage domaine | cœur métier inchangé sauf nouveau concept justifié | revue architecture | Architecte |
| QS-06 | Performance | chargement dashboard projet | charge nominale | données pré-calculées rendues sans LLM bloquant | API p95 < 500 ms | test de charge | Backend |
| QS-07 | Exploitation | redémarrage brutal du backend | prod | reprise des jobs/curseurs | 0 perte ; état READY après recovery | test reprise | Ops |
| QS-08 | Coût | hausse x10 du volume d’items | prod | budgets/quotas empêchent emballement LLM | budget workspace respecté | métriques coût | Produit/FinOps |
| QS-09 | Confidentialité | log d’une erreur adapter contenant un token | prod | secret masqué | 0 secret exploitable dans logs | scanner logs | Sécurité/Ops |
| QS-10 | Résilience | Claude indisponible | prod | ingestion continue ; analyse différée ou mode dégradé | aucune perte d’item | test panne fournisseur | Backend |
| QS-11 | Données | suppression d’un workspace | prod | données supprimées/archivées selon politique et embeddings associés traités | 100 % conforme politique | test rétention | DPO/Backend |
| QS-12 | Sécurité | webhook avec signature invalide | prod | rejet avant traitement métier | 100 % rejetés | test sécurité | Sécurité |
| QS-13 | Recherche | requête plein texte standard | prod | résultats pertinents et paginés | p95 cible à définir | benchmark | Backend |
| QS-14 | PRA | corruption/perte instance DB | prod | restauration depuis sauvegarde | RPO/RTO validés | exercice restore | Ops |
| QS-15 | IA | structured output invalide | prod | réponse refusée ou réparée de façon contrôlée | 0 donnée métier persistée sans validation | tests contract IA | Backend |

## Scénarios encore manquants

Les informations suivantes sont nécessaires avant de fixer des seuils :

- volumétrie d’items/jour ;
- nombre de projets et dépôts ;
- nombre d’utilisateurs simultanés ;
- disponibilité attendue ;
- RPO/RTO validés ;
- rétention ;
- temps maximal source → visibilité ;
- seuil acceptable de faux positifs/faux négatifs IA ;
- contraintes d’accessibilité ;
- régions d’hébergement autorisées.

## Règle de gouvernance

Un scénario n’est considéré `Validé` que si :

1. son propriétaire est nommé ;
2. son seuil est accepté ;
3. sa méthode de vérification est exécutable ;
4. un résultat est conservé en CI, monitoring ou rapport de test.
