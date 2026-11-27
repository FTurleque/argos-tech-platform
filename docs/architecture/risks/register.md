# Registre des risques ARGOS

Registre unique des risques d'ARGOS ; arc42 §11 y renvoie. Échelle initiale : Probabilité `F/M/E` (faible/moyenne/élevée), Impact `F/M/E/TE` (faible/moyen/élevé/très élevé). L’exposition qualitative doit être recalibrée après POC.

| ID | Risque | Prob. | Impact | Exposition | Mitigation | Indicateur / preuve | Propriétaire | Date cible | Statut |
|---|---|---|---|---|---|---|---|---|---|
| R-01 | fuite de données vers le modèle d'IA | F | TE | Moyenne | modèle local par défaut (ADR-0029) ; classes de données, refus des entrées CONFIDENTIAL et RESTRICTED et des secrets, masquage des données personnelles, journal `ia_appel` | tests QS-02, QS-09 de la passerelle IA | porteur du projet | avant toute IA distante | Ouvert |
| R-02 | métrique d’avancement trompeuse | M | E | Élevée | métriques factuelles uniquement, provenance, définition des ratios | revue métier + QS-01 | porteur du projet | MVP | Ouvert |
| R-03 | couplage GitHub/GitLab | M | E | Élevée | modèle canonique + adapters | ADR-0003, contract tests | porteur du projet | MVP | Ouvert |
| R-04 | perte/duplication d’événement | M | E | Élevée | idempotence, curseurs, replay, retry borné | QS-03/QS-04 | porteur du projet | MVP | Ouvert |
| R-05 | charge IA non maîtrisée sur le NAS | M | M | Moyenne | modèle local, plafond journalier de l'instance, quotas par espace et par compte, un modèle chargé et une requête à la fois | Q-06, QS-08, métriques des appels IA | porteur du projet | avant activation de l'IA | Ouvert |
| R-06 | architecture trop complexe trop tôt | E | E | Critique | ADR-0001, scope, critères avant broker/microservices | revue architecture | porteur du projet | continu | Ouvert |
| R-07 | problème licence composant | M | E | Élevée | SBOM, inventaire licences, revue juridique | rapport licences | porteur du projet | avant prod | Ouvert |
| R-08 | verrouillage sur un fournisseur d'IA | F | M | Faible | contrat interne de la passerelle indépendant du fournisseur ; fournisseur local Ollama (MIT) | ADR-0029 | porteur du projet | — | Clos (ADR-0029 : fournisseur local remplaçable par un autre adaptateur) |
| R-09 | usage des données projet perçu comme surveillance individuelle | M | TE | Critique | finalités, agrégation, minimisation, RGPD/AIPD | revue DPO | porteur du projet | avant prod | Ouvert |
| R-10 | sauvegarde non restaurable | M | E | Élevée | sauvegarde vérifiée des bases `argos` et `keycloak`, échec signalé, rotation après succès ; restauration rejouable par script ; essai automatisé (`scripts/essai-sauvegarde-restauration.sh`) et exercice de bout en bout (recette R-6) ; périodicité de l'exercice à fixer (Q-4 de `fiabiliser-sauvegarde-et-roles-base`) | QS-14 | porteur du projet | avant prod | Ouvert |
| R-11 | prompt injection depuis contenu Web/RSS | E | E | Critique | contenu externe traité comme données, prompts cloisonnés, structured output | tests adversariaux | porteur du projet | avant IA prod | Ouvert |
| R-12 | SSRF via surveillance/URLs | M | TE | Critique | allow/deny network, validation DNS/IP à chaque collecte et redirection, connexion à l'adresse IP contrôlée sans nouvelle résolution (rebinding DNS, WEB-07) ; egress contrôlé non en place (filtrage de sortie au déploiement) | tests SSRF et de rebinding (`RecuperateurWebTest`, `TransportEpingleTest`) | porteur du projet | avant Web watch prod | Ouvert |
| R-13 | secret webhook/API exposé | M | TE | Critique | secrets dans `.env` hors dépôt, jetons de forge en lecture seule, masquage des jetons dans les journaux | QS-09/QS-12 | porteur du projet | MVP | Ouvert |
| R-14 | rate limit GitHub/GitLab bloque ingestion | E | M | Élevée | delta sync, ETag/cache, backoff, métriques quota | test charge/connecteur | porteur du projet | MVP | Ouvert |
| R-15 | changement API externe casse adapter | M | M/E | Élevée | contract tests, versioning, monitoring erreurs | CI contract | porteur du projet | continu | Ouvert |
| R-16 | recherche PostgreSQL insuffisante à grande échelle | F/M | M | Moyenne | benchmark avant moteur dédié | résultats benchmark | porteur du projet | après POC | Ouvert |
| R-17 | embeddings/RAG provoquent fuite inter-workspace | M si activé | TE | Critique | isolation tenant, filtres obligatoires, tests suppression | tests isolation | porteur du projet | avant RAG | Ouvert |
| R-18 | indisponibilité ou perte du Keycloak auto-hébergé | M | E | Élevée | Keycloak dans le même projet Compose, contrôle de santé, base `keycloak` sauvegardée et restaurée avant son démarrage (ADR-0027, EXP-01 à EXP-04) | recette R-6, test d'authentification | porteur du projet | avant prod | Ouvert |

## Règles de suivi

- chaque risque critique a un propriétaire nommé ; le porteur du projet l’est par défaut (ADR-0025) ;
- toute mitigation technique doit pointer vers un test, un ADR ou une métrique ;
- les risques clos restent dans le registre avec preuve de clôture ;
- les nouveaux composants doivent déclencher une revue licence, sécurité et exploitation.

## Questions ouvertes

- quelle matrice d’acceptation des risques retenir pour une instance ouverte à d’autres utilisateurs que le porteur ?
