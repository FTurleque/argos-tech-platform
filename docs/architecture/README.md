# Architecture ARGOS

Cette documentation constitue la **source d’architecture versionnée** du projet ARGOS.

> **Baseline active : [v2.4 — 27 septembre 2026](baseline-v2.4.md)**  
> Baseline précédente : [v2.3](baseline-v2.3.md)

## Synthèse exécutive

ARGOS est actuellement en **phase de conception / cadrage POC** : aucune implémentation applicative n’est encore présente dans le dépôt. La documentation décrit donc une architecture cible.

La v2.4 fixe désormais la stack applicative :

- **Java 17 minimum** ;
- **Quarkus** pour le backend et les API ;
- **Vue.js 3** pour l’IHM ;
- **PostgreSQL** comme stockage principal ;
- monolithe modulaire proposé pour limiter la complexité initiale ;
- modèle canonique pour découpler ARGOS des formats externes ;
- Claude/Anthropic encapsulé derrière un **AI Gateway** ;
- FreshRSS et changedetection.io séparés ;
- n8n limité aux automatisations périphériques ;
- SBOM CycloneDX et corrélation déterministe ;
- serveur **MCP lecture seule** ;
- veille réglementaire avec **validation MKP obligatoire** ;
- personnalisation à deux niveaux : **socle d’équipe + veille personnelle** ;
- arc42 + C4 + ADR + Mermaid comme documentation-as-code.

## POC v2.4

Le POC représente **≈ 20–24 j.h de travail effectif**, soit environ un mois concentré. Une démonstration peut démarrer localement via **Docker Compose sur le poste de développement**. Si VM, proxy/SSO, sécurité et équipes pilotes sont sur le chemin critique, prévoir **6 à 10 semaines calendaires**.

Le MVP est désormais estimé à **≈ 50–60 j.h cumulés**, avec une durée calendaire potentiellement plus longue à cause des dépendances inter-équipes et des urgences opérationnelles.

## Personnalisation

Un développeur hérite du socle de veille de son équipe et peut ajouter des technologies, thèmes ou sources à titre personnel pour apprendre ou préparer de futurs besoins. Ces préférences n’accordent aucun droit supplémentaire sur les projets ou données internes.

## IA : deux budgets distincts

- développement : ne pas dimensionner le projet sur un plan Claude Pro seul ; prévoir une capacité supérieure ou des crédits d’usage, puis mesurer ;
- runtime ARGOS : Claude API/Console est facturée séparément et passe par l’AI Gateway avec quotas et plafond.

## État de la preuve

- **Observé** : artefact présent dans le dépôt ;
- **Décision proposée** : ADR `Proposé` ;
- **Hypothèse à valider** : preuve/mesure/environnement manquant ;
- **Accepté** : ADR validé.

ADR acceptés structurants à ce stade :

- ADR-0002 — PostgreSQL ;
- ADR-0005 — Mermaid ;
- ADR-0009 — licence PolyForm Internal Use ;
- ADR-0019 — Java 17+ / Quarkus / Vue.js 3 / PostgreSQL.

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

- [Baseline v2.4](baseline-v2.4.md)
- [ADR](adr/README.md)
- [Scénarios qualité](quality/scenarios.md)
- [Registre des risques](risks/register.md)
- [Inventaire des diagrammes](diagrams/README.md)

## Priorités avant le premier commit applicatif

1. initialiser Maven/Quarkus en Java 17 ;
2. initialiser l’IHM Vue.js 3 ;
3. créer PostgreSQL local + migrations ;
4. créer Docker Compose local ;
5. mettre en place les frontières de modules et tests ArchUnit ;
6. connecter OSV puis importer une SBOM CycloneDX ;
7. produire la première corrélation et alerte ;
8. prototyper deux outils MCP lecture seule ;
9. engager en parallèle les demandes VM/proxy/SSO/RSSI/DPO ;
10. mesurer charge, qualité et coûts IA avant le go/no-go MVP.

## Questions encore ouvertes

1. Quel fournisseur d’identité OIDC/SAML est imposé ?
2. Quelles classes de données peuvent être transmises à Claude/Anthropic ?
3. FreshRSS et changedetection.io seront-ils hébergés par ARGOS ou consommés comme services existants ?
4. Flyway ou Liquibase ?
5. Quels SLO sont réellement nécessaires après le POC ?
6. Quelle stack d’observabilité et quel coffre-fort de secrets sont disponibles en interne ?
7. Quel mécanisme Teams/e-mail est disponible ?
8. Quelle politique de conservation des données de veille, d’audit et d’activité projet ?

Toute réponse structurante doit être reflétée dans un ADR, une exigence qualité ou une contrainte.
