# Architecture ARGOS

Cette documentation constitue la **source d’architecture versionnée** du projet ARGOS.

## Synthèse exécutive

ARGOS est actuellement en **phase de conception** : aucune implémentation applicative, manifest de dépendances, Dockerfile, pipeline CI/CD, schéma de données ni API n’existe encore dans le dépôt. La documentation décrit donc une **architecture cible proposée**, pas un état implémenté.

L’orientation retenue à ce stade est :

- plateforme de **Technology Intelligence + Project Intelligence** ;
- monolithe modulaire proposé pour limiter la complexité initiale ;
- modèle canonique pour découpler ARGOS des formats GitHub/GitLab/RSS ;
- PostgreSQL proposé comme stockage principal ;
- Claude/Anthropic encapsulé derrière un **AI Gateway** ;
- FreshRSS et changedetection.io consommés comme systèmes spécialisés séparés ;
- n8n limité aux automatisations périphériques ;
- OIDC vers l’IdP d’entreprise en première intention ;
- arc42 + C4 + ADR + Mermaid comme documentation-as-code.

Les principaux risques concernent la confidentialité des données envoyées au LLM, l’interprétation abusive de métriques projet, l’idempotence de l’ingestion, la dérive de complexité et la maîtrise des coûts IA.

## Référentiels utilisés

- **arc42** : structure du dossier ;
- **C4** : niveaux Context, Container et Component ;
- **Mermaid** : unique langage source des diagrammes ;
- **ADR** : décisions structurantes ;
- notation UML-style dans Mermaid avec stéréotypes explicites : `«Person»`, `«Software System»`, `«Container»`, `«Component»`, `«interface»`, `«adapter»`, `«node»`, `«database»`.

## État de la preuve

Au 24 septembre 2026, le dépôt contient le README produit et la documentation d’architecture initiale. Il n’existe pas encore de code applicatif, manifest de dépendances, Dockerfile, manifest Kubernetes/IaC, pipeline CI/CD, schéma de données, API ou test à analyser.

En conséquence :

- **Observé** : README et fichiers de documentation présents dans le dépôt ;
- **Hypothèse à valider** : proposition d’architecture nécessitant ADR, POC ou preuve d’implémentation ;
- **Non déterminé** : information non disponible.

## Navigation arc42

1. [Introduction et objectifs](arc42/01-introduction-objectifs.md)
2. [Contraintes](arc42/02-contraintes.md)
3. [Contexte et périmètre](arc42/03-contexte-perimetre.md)
4. [Stratégie de solution](arc42/04-strategie-solution.md)
5. [Vue des blocs](arc42/05-vue-blocs.md)
6. [Vue d’exécution](arc42/06-vue-execution.md)
7. [Vue de déploiement](arc42/07-vue-deploiement.md)
8. [Concepts transverses](arc42/08-concepts-transverses.md)
9. [Décisions](arc42/09-decisions.md)
10. [Exigences qualité](arc42/10-exigences-qualite.md)
11. [Risques et dette](arc42/11-risques-dette.md)
12. [Glossaire](arc42/12-glossaire.md)

## Registres associés

- [ADR](adr/README.md)
- [Scénarios qualité](quality/scenarios.md)
- [Registre des risques](risks/register.md)
- [Inventaire des diagrammes](diagrams/README.md)

## Checklist de complétude

| Élément | État | Commentaire |
|---|---|---|
| arc42 sections 1 à 12 | ✅ Initialisé | à faire évoluer avec le code réel |
| C4 Context | ✅ | Mermaid |
| C4 Container | ✅ | Mermaid |
| C4 Component | ✅ | backend proposé |
| C4 Code | ⏳ Non pertinent | aucun code critique existant |
| séquences nominales | ✅ | veille + activité projet |
| scénario d’erreur | ✅ | API externe indisponible |
| scénario reprise/exploitation | ✅ | redémarrage/reprise |
| déploiement MVP | ✅ Proposé | non implémenté |
| déploiement entreprise | ✅ Proposé | non implémenté |
| modèle métier | ✅ Conceptuel | à valider par code/migrations |
| ADR structurants | ✅ 8 initiaux | 7 proposés, Mermaid accepté |
| scénarios qualité | ✅ Initialisés | plusieurs seuils à définir |
| registre des risques | ✅ Initialisé | propriétaires à confirmer |
| CI architecture/doc | ❌ | à créer |
| manifests runtime | ❌ | non implémentés |
| preuves de POC | ❌ | à produire |

## ADR à créer ensuite

- choix frontend ;
- mécanisme scheduler/jobs ;
- broker ou absence de broker après mesure ;
- stratégie de recherche au-delà de PostgreSQL FTS ;
- activation ou non de pgvector/RAG ;
- plateforme de déploiement production ;
- secrets management ;
- observabilité ;
- source(s) CVE ;
- stratégie de sauvegarde/PRA ;
- licence ARGOS.

## Scénarios qualité manquants

- volumétrie nominale et pic ;
- disponibilité/SLO ;
- RPO/RTO validés ;
- temps source → visibilité ;
- rétention et suppression ;
- qualité minimale des classifications IA ;
- faux positifs/faux négatifs des signaux de risque ;
- accessibilité UI ;
- contraintes de résidence des données.

## Incohérences détectées entre code, déploiement et documentation

Aucune incohérence technique ne peut encore être constatée car **le code et le déploiement n’existent pas**. L’écart actuel est volontaire : la documentation décrit une cible à transformer progressivement en architecture réellement implémentée.

À surveiller dès les premiers commits :

1. packages ne respectant pas les modules documentés ;
2. accès direct à Claude hors `AI Gateway` ;
3. dépendances directes aux SDK GitHub/GitLab dans le domaine ;
4. schéma SQL divergeant du modèle documenté ;
5. manifests de déploiement divergeant de la vue section 7 ;
6. secrets ou URLs sensibles versionnés ;
7. métriques projet non traçables ou pourcentage global inventé.

## Plan de migration priorisé : documentation → implémentation

### P0 — Fondation

1. accepter/rejeter ADR-0001 à ADR-0004 ;
2. choisir stack de build backend ;
3. créer structure de modules ;
4. créer PostgreSQL local + migrations ;
5. mettre en place tests d’architecture ;
6. créer CI minimale.

### P1 — POC Technology Intelligence

1. connecter FreshRSS ;
2. normaliser un `IntelligenceItem` ;
3. persister provenance et déduplication ;
4. construire recherche/consultation minimale ;
5. mesurer volumétrie et latence.

### P2 — POC Project Intelligence

1. intégrer un fournisseur SCM réel ;
2. webhook + resynchronisation API ;
3. normaliser `ProjectActivity` ;
4. calculer métriques factuelles ;
5. tester idempotence et rate limits.

### P3 — Corrélation et Claude

1. lier technologies et projets ;
2. implémenter `AI Gateway` ;
3. appliquer classification des données ;
4. valider structured outputs ;
5. mesurer qualité/tokens/coût ;
6. implémenter `ProjectImpact` traçable.

### P4 — Industrialisation

1. SSO/RBAC ;
2. observabilité ;
3. backup/restore ;
4. tests sécurité/charge ;
5. déploiement préprod/prod ;
6. revue RGPD/licences/SBOM ;
7. runbooks et PRA.

## Contrôles CI recommandés

### Documentation

- Markdown lint ;
- validation des liens ;
- rendu/lint de chaque bloc Mermaid ;
- vérification de l’index ADR ;
- contrôle qu’un ADR accepté n’est pas supprimé ;
- détection de fichiers PlantUML si la règle Mermaid reste obligatoire.

### Architecture/code lorsque l’implémentation existe

- tests de dépendances entre modules ;
- compilation + tests unitaires/intégration ;
- validation OpenAPI ;
- migrations DB sur base éphémère ;
- contract tests adapters ;
- SAST ;
- dependency/license scanning ;
- secret scanning ;
- génération SBOM ;
- tests conteneurs/IaC ;
- tests de sécurité des webhooks ;
- seuils de couverture uniquement s’ils servent une exigence, pas comme métrique isolée.

### IA

- jeu de tests de prompts versionnés ;
- validation des schémas de sortie ;
- tests de policy confidentiality ;
- budget/token regression ;
- tests adversariaux prompt injection pour contenu externe.

## Questions structurantes ouvertes

1. L’entreprise utilise-t-elle GitHub, GitLab, ou les deux en production ?
2. Quel fournisseur d’identité OIDC/SAML est imposé ?
3. Quelles classes de données peuvent être transmises à Claude/Anthropic ?
4. FreshRSS et changedetection.io seront-ils hébergés par ARGOS ou consommés comme services existants ?
5. Quels SLO sont réellement nécessaires pour le MVP puis la production ?
6. Quel environnement cible : VM, Docker/Podman, Kubernetes/OpenShift, autre ?
7. Quelle politique de conservation des données de veille et d’activité projet ?

Toute réponse devra être reflétée dans un ADR, une exigence qualité ou une contrainte selon sa nature.
