# Architecture ARGOS

Cette documentation constitue la **source d’architecture versionnée** du projet ARGOS.

## Référentiels utilisés

- **arc42** : structure du dossier ;
- **C4** : niveaux Context, Container et Component ;
- **Mermaid** : unique langage source des diagrammes ;
- **ADR** : décisions structurantes ;
- notation UML-style dans Mermaid avec stéréotypes explicites : `«Person»`, `«Software System»`, `«Container»`, `«Component»`, `«interface»`, `«adapter»`, `«node»`, `«database»`.

## État de la preuve

Au 24 septembre 2026, le dépôt contient uniquement la documentation initiale. Il n’existe pas encore de code applicatif, manifest de dépendances, Dockerfile, manifest Kubernetes/IaC, pipeline CI/CD, schéma de données, API ou test à analyser.

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

## Questions structurantes ouvertes

1. L’entreprise utilise-t-elle GitHub, GitLab, ou les deux en production ?
2. Quel fournisseur d’identité OIDC/SAML est imposé ?
3. Quelles classes de données peuvent être transmises à Claude/Anthropic ?
4. FreshRSS et changedetection.io seront-ils hébergés par ARGOS ou consommés comme services existants ?
5. Quels SLO sont réellement nécessaires pour le MVP puis la production ?
6. Quel environnement cible : VM, Docker/Podman, Kubernetes/OpenShift, autre ?
7. Quelle politique de conservation des données de veille et d’activité projet ?

Toute réponse devra être reflétée dans un ADR, une exigence qualité ou une contrainte selon sa nature.
