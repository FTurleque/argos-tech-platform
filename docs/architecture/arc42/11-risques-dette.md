# 11. Risques et dette technique

## 11.1 Risques prioritaires

| ID | Risque | Probabilité | Impact | Exposition | Mitigation | Propriétaire | Cible |
|---|---|---:|---:|---:|---|---|---|
| R-01 | envoi de données sensibles à Claude | M | Très élevé | Critique | classification, policy enforcement, audit, minimisation | Sécurité à confirmer | avant POC données internes |
| R-02 | faux sentiment d’avancement projet | M | Élevé | Élevée | métriques factuelles, provenance, pas de % IA arbitraire | Produit/Tech Lead | MVP |
| R-03 | couplage aux formats GitHub/GitLab | M | Élevé | Élevée | adapters + modèle canonique | Backend | MVP |
| R-04 | perte/duplication d’événements | M | Élevé | Élevée | idempotence, curseurs durables, replay tests | Backend | MVP |
| R-05 | coût LLM incontrôlé | M | Moyen/Élevé | Élevée | quotas, cache, batch, préfiltrage, métriques | Produit/FinOps | MVP |
| R-06 | dérive vers une plateforme trop complexe | Élevée | Élevé | Critique | monolithe modulaire, scope MVP, ADR | Architecte/PO | continu |
| R-07 | licence/compliance composant mal comprise | M | Élevé | Élevée | revue licences/SBOM, validation juridique | Architecture/Juridique | avant prod |
| R-08 | dépendance excessive à Claude | M | Moyen | Moyenne | AI Gateway, structured output, fallback déterministe | Backend | V1 |
| R-09 | métriques projet interprétées comme surveillance individuelle | M | Très élevé | Critique | finalité explicite, agrégation, RGPD, gouvernance | Produit/DPO | avant prod |
| R-10 | restauration non testée | M | Élevé | Élevée | runbook + exercices restore | Ops | avant prod |

M = moyenne. Les probabilités doivent être recalibrées après POC.

## 11.2 Dette technique acceptée au MVP

### Acceptable temporairement

- un seul backend déployé ;
- absence de broker si les volumes restent faibles ;
- PostgreSQL FTS avant moteur de recherche spécialisé ;
- table de jobs/retry avant plateforme distribuée ;
- FreshRSS/changedetection sur le même hôte si environnement POC ;
- observabilité minimale mais structurée.

### Non acceptable même au MVP

- secrets dans Git ;
- données CONFIDENTIAL envoyées au LLM sans politique ;
- traitement non idempotent ;
- métriques projet non traçables ;
- suppression des erreurs silencieuse ;
- dépendance directe du domaine aux SDK GitHub/GitLab/Anthropic.

## 11.3 Zones d’incertitude

- volumétrie ;
- fournisseurs SCM réels ;
- IdP ;
- hébergement ;
- classification des données ;
- politique RGPD ;
- canaux de notification ;
- stack d’observabilité ;
- besoin réel de RAG/pgvector ;
- exigences de disponibilité.

## 11.4 Points à valider par POC

1. ingestion RSS via FreshRSS ;
2. changedetection.io → ARGOS ;
3. webhook + resynchronisation GitHub/GitLab ;
4. idempotence/replay ;
5. modèle canonique ;
6. classification technologique ;
7. corrélation projet ↔ technologie ;
8. structured output Claude ;
9. enforcement de politique de données ;
10. coût/token par cas d’usage ;
11. FTS PostgreSQL ;
12. pertinence ou non de pgvector.

## 11.5 Registre opérationnel

Le registre détaillé et vivant est maintenu dans [`../risks/register.md`](../risks/register.md).

## 11.6 Preuves nécessaires

- résultats POC ;
- incidents/tests chaos ;
- analyse de licences ;
- revue sécurité ;
- AIPD ou justification documentée ;
- test de restauration ;
- métriques de coût et qualité IA.
