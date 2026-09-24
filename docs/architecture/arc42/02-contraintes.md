# 2. Contraintes

Cette section distingue les **contraintes imposées** des **préférences d’architecture**. Une préférence peut devenir une décision après ADR ; elle ne doit pas être présentée comme contrainte tant qu’aucune exigence externe ne l’impose.

## 2.1 Contraintes observées

| Domaine | Contrainte | Nature | Preuve |
|---|---|---|---|
| Documentation | arc42 obligatoire | Imposée par le cadrage | demande d’architecture du projet |
| Modélisation | C4 Context, Container, Component et Code si pertinent | Imposée par le cadrage | demande d’architecture du projet |
| Diagrammes | Mermaid uniquement ; aucun diagramme ASCII ni image binaire comme source | Imposée par le cadrage | demande d’architecture du projet |
| Décisions | ADR Markdown pour les choix structurants | Imposée par le cadrage | demande d’architecture du projet |
| Langue | documentation d’architecture en français | Imposée par le cadrage | demande d’architecture du projet |
| Exactitude | toute information non prouvée doit être marquée `Hypothèse à valider` ou `Non déterminé` | Imposée par le cadrage | demande d’architecture du projet |

## 2.2 Contraintes techniques à confirmer

| Sujet | État | Preuve nécessaire |
|---|---|---|
| Backend Java / Spring Boot | Hypothèse à valider | ADR + manifest de build (`pom.xml` ou équivalent) |
| PostgreSQL | Hypothèse à valider | ADR + configuration datasource/migration |
| Frontend React ou Vue | Non déterminé | ADR + manifest frontend |
| Claude / Anthropic comme fournisseur IA initial | Hypothèse à valider | ADR + configuration AI Gateway |
| FreshRSS pour RSS/Atom | Hypothèse à valider | POC + configuration/connecteur |
| changedetection.io pour surveillance Web | Hypothèse à valider | POC + configuration/connecteur |
| n8n périphérique uniquement | Hypothèse à valider | ADR + workflow réel si adopté |
| Docker/Podman pour POC/MVP | Hypothèse à valider | compose/container manifests |
| Kubernetes/OpenShift en entreprise | Non déterminé | décision d’hébergement de l’organisation |

## 2.3 Contraintes réglementaires et sécurité

### Données personnelles

L’activité GitHub/GitLab peut contenir des données liées à des personnes : auteur d’un commit, assignation, commentaires, review, horodatages.

**Hypothèse à valider :** le traitement devra être qualifié au regard du RGPD et des politiques internes.

**Preuves nécessaires :** registre de traitement, finalités, catégories de données, durées de conservation, politique de confidentialité, éventuelle AIPD.

### IA externe

**Hypothèse à valider :** toutes les données ne seront pas autorisées à sortir vers Claude/Anthropic.

Une classification minimale est proposée :

- PUBLIC ;
- INTERNAL ;
- CONFIDENTIAL ;
- RESTRICTED.

La politique d’envoi vers un LLM externe devra être validée avant mise en production.

## 2.4 Contraintes organisationnelles

| Sujet | État |
|---|---|
| équipe de développement | Non déterminé |
| DevOps disponible | Non déterminé |
| équipe sécurité impliquée | Non déterminé |
| hébergement interne/cloud | Non déterminé |
| IdP d’entreprise | Non déterminé |
| outil de gestion de secrets | Non déterminé |
| politique de sauvegarde/PRA | Non déterminé |

## 2.5 Préférences d’architecture proposées

Ces éléments ne sont **pas** des contraintes imposées :

1. monolithe modulaire avant microservices ;
2. PostgreSQL avant multiplication des moteurs de stockage ;
3. API REST/OpenAPI ;
4. connecteurs/adapters aux frontières ;
5. modèle canonique d’événements ;
6. traitement asynchrone lorsque l’ingestion l’exige ;
7. gratuit/self-hosted d’abord ;
8. AI Gateway interne devant Claude ;
9. observabilité standardisée via OpenTelemetry/Micrometer si stack Java confirmée.

Chacune doit être confirmée par ADR ou POC.

## 2.6 Risques de contrainte

- une politique SI peut interdire certains composants self-hosted ;
- une politique sécurité peut interdire l’envoi de code ou de données projet vers un LLM SaaS ;
- GitHub/GitLab Enterprise peuvent imposer des limites d’API, SSO ou réseau particulières ;
- la licence d’un composant peut empêcher l’usage envisagé ;
- les objectifs RPO/RTO peuvent faire évoluer fortement l’architecture de déploiement.

## 2.7 Fichiers source concernés

- `README.md`
- futurs manifests de build, conteneurs, CI/CD et configuration ;
- futurs ADR.
