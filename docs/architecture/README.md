# Architecture ARGOS

Cette documentation constitue la **source d’architecture versionnée** du projet ARGOS.

> Baselines de cadrage : [v2.4 — 27 septembre 2026](baseline-v2.4.md) et [v2.3](baseline-v2.3.md). Ce sont des documents datés ; les ADR acceptés qui les précisent ou les remplacent font foi, et arc42 décrit l’architecture livrée.

## Synthèse

ARGOS est un **POC fonctionnel**, produit ouvert à tous les publics porté à titre personnel ([ADR-0025](adr/0025-produit-ouvert-projet-personnel.md)) et hébergé sur le serveur Synology du porteur ([ADR-0026](adr/0026-hebergement-synology.md)). Le serveur `argos-server`, l’IHM `argos-ui` et le client desktop `argos-desktop` portent les capacités des 19 spécifications principales OpenSpec ; l’état de chacune est tenu dans le [registre des spécifications](../specifications/README.md).

Choix structurants, tous fixés par des ADR acceptés :

- **Java 17 minimum**, **Quarkus**, **Vue.js 3** et **PostgreSQL** (ADR-0019) ;
- monolithe modulaire avec un noyau commun du serveur (ADR-0001, ADR-0037) ;
- modèle canonique pour découpler ARGOS des formats externes (ADR-0003) ;
- schéma versionné par des migrations Flyway appliquées au démarrage (ADR-0032) ;
- Keycloak auto-hébergé comme fournisseur d’identité, autorisation fine dans ARGOS par RBAC et RLS (ADR-0008, ADR-0016, ADR-0027) ;
- session OIDC de l’IHM en mémoire, desktop connecté par l’interface de bouclage (ADR-0033) ;
- collecteur RSS/Atom et pages surveillées intégré au serveur (ADR-0028) ;
- passerelle IA servie par un modèle local Ollama, facultatif (ADR-0029) ;
- SBOM CycloneDX et corrélation déterministe (ADR-0011, ADR-0013) ;
- serveur **MCP lecture seule** (ADR-0014) ;
- veille réglementaire validée par l’expert métier, données ouvertes JORF (ADR-0030) ;
- profils de veille cumulables et mises en page sélectionnables (ADR-0023, ADR-0024) ;
- traitements périodiques par `quarkus-scheduler` dans le serveur (ADR-0031) ;
- observabilité sans collecteur : journaux JSON, métriques Prometheus, santé (ADR-0036) ;
- client desktop Electron réutilisant l’IHM (ADR-0022) ;
- arc42 + C4 + ADR + Mermaid comme documentation-as-code (ADR-0005).

Décisions proposées, en attente d’acceptation par le porteur : ADR-0007 (n8n), ADR-0012 (Dependency-Track), ADR-0034 (intégration continue), ADR-0035 (comptes des bases), ADR-0038 (configuration de l’IHM à l’exécution), ADR-0039 (traitements de fond), ADR-0040 (pagination), ADR-0041 (conservation des données).

## Personnalisation

Un utilisateur hérite du socle de veille de son espace et peut ajouter des technologies, thèmes ou sources à titre personnel. Ces préférences n’accordent aucun droit supplémentaire sur les projets ou données internes.

## État de la preuve

- **Observé** ([EXISTANT]) : artefact présent dans le dépôt ;
- **Décision proposée** : ADR `Proposé` ;
- **Hypothèse à valider** : preuve, mesure ou environnement manquant ;
- **Accepté** : ADR validé par le porteur du projet.

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
- [Audit d’accessibilité des mises en page](quality/audit-accessibilite-mises-en-page.md)
- [Mesure de la corrélation sur le jeu de référence annoté](quality/mesure-correlation-jeu-reference.md)
- [Mesure de la latence de l’API et du serveur MCP](quality/mesure-performance-api-mcp.md)
- [Registre des risques](risks/register.md) (registre unique)
- [Inventaire des diagrammes](diagrams/README.md)
- [Spécifications fonctionnelles et couverture des exigences](../specifications/README.md)
- [Guide de déploiement](../deploiement/synology-et-pc.md) et [recette sur le poste installé](../deploiement/recette-poste.md)

## Questions encore ouvertes

1. Quels SLO, RPO et RTO retenir pour une instance partagée, puis pour un hébergement en ligne ?
2. Quelle pile de collecte des métriques et des journaux sur le NAS (Q-3 d’`instrumenter-observabilite`) ?
3. Quelles durées de conservation par défaut pour les données journalisées (Q-9 de la fiche `exploitation-plateforme`) ?
4. Quels canaux de notification ajouter à l’e-mail et au client desktop ?
5. Faut-il chiffrer les archives de sauvegarde (Q-2 de `fiabiliser-sauvegarde-et-roles-base`) ?

Toute réponse structurante doit être reflétée dans un ADR, une exigence qualité ou une contrainte.
