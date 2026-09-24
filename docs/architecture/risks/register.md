# Registre des risques ARGOS

Échelle initiale : Probabilité `F/M/E` (faible/moyenne/élevée), Impact `F/M/E/TE` (faible/moyen/élevé/très élevé). L’exposition qualitative doit être recalibrée après POC.

| ID | Risque | Prob. | Impact | Exposition | Mitigation | Indicateur / preuve | Propriétaire | Date cible | Statut |
|---|---|---|---|---|---|---|---|---|---|
| R-01 | fuite de données via Claude | M | TE | Critique | classification, policy AI Gateway, minimisation, audit | tests QS-02 + logs audit | Sécurité | avant données internes | Ouvert |
| R-02 | métrique d’avancement trompeuse | M | E | Élevée | métriques factuelles uniquement, provenance, définition des ratios | revue métier + QS-01 | Produit | MVP | Ouvert |
| R-03 | couplage GitHub/GitLab | M | E | Élevée | modèle canonique + adapters | ADR-0003, contract tests | Backend | MVP | Ouvert |
| R-04 | perte/duplication d’événement | M | E | Élevée | idempotence, curseurs, replay, retry borné | QS-03/QS-04 | Backend | MVP | Ouvert |
| R-05 | coûts Claude non maîtrisés | M | E | Élevée | budgets, cache, batch, préfiltrage, métriques | QS-08 | Produit/FinOps | MVP | Ouvert |
| R-06 | architecture trop complexe trop tôt | E | E | Critique | ADR-0001, scope, critères avant broker/microservices | revue architecture | Architecte | continu | Ouvert |
| R-07 | problème licence composant | M | E | Élevée | SBOM, inventaire licences, revue juridique | rapport licences | Architecture/Juridique | avant prod | Ouvert |
| R-08 | verrouillage Anthropic | M | M | Moyenne | AI Gateway, contrats internes | ADR-0004 | Backend | V1 | Ouvert |
| R-09 | usage des données projet perçu comme surveillance individuelle | M | TE | Critique | finalités, agrégation, minimisation, RGPD/AIPD | revue DPO | DPO/Produit | avant prod | Ouvert |
| R-10 | sauvegarde non restaurable | M | E | Élevée | tests restore automatisés/périodiques | QS-14 | Ops | avant prod | Ouvert |
| R-11 | prompt injection depuis contenu Web/RSS | E | E | Critique | contenu externe traité comme données, prompts cloisonnés, structured output | tests adversariaux | Sécurité/Backend | avant IA prod | Ouvert |
| R-12 | SSRF via surveillance/URLs | M | TE | Critique | allow/deny network, validation DNS/IP, egress contrôlé | tests SSRF | Sécurité | avant Web watch prod | Ouvert |
| R-13 | secret webhook/API exposé | M | TE | Critique | vault, rotation, masquage logs | QS-09/QS-12 | Sécurité/Ops | MVP | Ouvert |
| R-14 | rate limit GitHub/GitLab bloque ingestion | E | M | Élevée | delta sync, ETag/cache, backoff, métriques quota | test charge/connecteur | Backend | MVP | Ouvert |
| R-15 | changement API externe casse adapter | M | M/E | Élevée | contract tests, versioning, monitoring erreurs | CI contract | Backend | continu | Ouvert |
| R-16 | recherche PostgreSQL insuffisante à grande échelle | F/M | M | Moyenne | benchmark avant moteur dédié | résultats benchmark | Backend | après POC | Ouvert |
| R-17 | embeddings/RAG provoquent fuite inter-workspace | M si activé | TE | Critique | isolation tenant, filtres obligatoires, tests suppression | tests isolation | Sécurité/Backend | avant RAG | Ouvert |
| R-18 | dépendance aux administrateurs externes/IdP | M | E | Élevée | mode dégradé contrôlé, runbook, monitoring | test authentification | Ops | avant prod | Ouvert |

## Règles de suivi

- chaque risque critique doit avoir un propriétaire nommé avant passage en production ;
- toute mitigation technique doit pointer vers un test, un ADR ou une métrique ;
- les risques clos restent dans le registre avec preuve de clôture ;
- les nouveaux composants doivent déclencher une revue licence, sécurité et exploitation.

## Questions ouvertes

- qui assume le rôle DPO/sécurité sur le projet ?
- quelle matrice d’acceptation des risques applique l’entreprise ?
- quels risques doivent être suivis dans un outil corporate plutôt que dans Git ?
