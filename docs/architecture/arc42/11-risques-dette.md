# 11. Risques et dette technique

## 11.1 Risques

Le registre unique des risques est [`../risks/register.md`](../risks/register.md) : identifiants, probabilité, impact, exposition, mitigation, indicateur, propriétaire et statut. Cette section n'en reprend pas le tableau.

Risques d'exposition critique au registre : R-06 (architecture trop complexe trop tôt), R-09 (données projet perçues comme surveillance individuelle), R-11 (injection d'instructions depuis un contenu externe), R-12 (falsification de requête côté serveur), R-13 (secret exposé), R-17 (fuite inter-espaces par des embeddings, si activés).

## 11.2 Dette technique acceptée

### Acceptable temporairement

- un seul backend déployé ;
- absence de broker si les volumes restent faibles ;
- PostgreSQL FTS avant moteur de recherche spécialisé ;
- table de jobs/retry avant plateforme distribuée ;
- observabilité sans collecteur : journaux JSON, métriques Prometheus lues à la demande et contrôles de santé (ADR-0036), sans traçage distribué ni historique des métriques ;
- secrets dans un fichier `.env` hors dépôt, sans coffre-fort ;
- client desktop non signé.

### Non acceptable

- secrets dans Git ;
- données CONFIDENTIAL ou RESTRICTED envoyées au modèle d'IA ;
- traitement non idempotent ;
- métriques projet non traçables ;
- suppression des erreurs silencieuse ;
- dépendance directe du domaine aux SDK GitHub/GitLab ou au fournisseur d'IA ;
- commit intégré à `main` sans le statut vert de l'intégration continue (§8.11, ADR-0034).

## 11.3 Zones d’incertitude

- volumétrie d'une instance partagée ;
- hébergement en ligne ;
- politique RGPD d'une instance ouverte à d'autres utilisateurs ;
- canaux de notification au-delà de l'e-mail et du client desktop ;
- pile de collecte des métriques et des journaux sur le NAS (Q-3 d'`instrumenter-observabilite`) ;
- besoin réel de RAG/pgvector ;
- exigences de disponibilité.

## 11.4 Points restant à valider sur une installation réelle

1. démarrage et exploitation sur le NAS (recette R-8 de [`recette-poste.md`](/docs/deploiement/recette-poste.md)) ;
2. connexion du portail et du client desktop depuis un second poste ;
3. récupération des SBOM sur un dépôt pilote réel (R-4) ;
4. précision et rappel de la corrélation sur les projets pilotes (R-5) ;
5. qualité des résumés d'un petit modèle local ;
6. FTS PostgreSQL à grande échelle (R-16) ;
7. pertinence ou non de pgvector.

## 11.5 Preuves nécessaires

- incidents/tests chaos ;
- analyse de licences (contrôle des licences du serveur, [`argos-server/README.md`](/argos-server/README.md#licences-des-dépendances)) ;
- revue sécurité ;
- AIPD ou justification documentée ;
- test de restauration (R-6) ;
- mesures de charge et de qualité de l'IA.
