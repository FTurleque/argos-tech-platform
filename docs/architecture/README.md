# Architecture ARGOS

Cette documentation constitue la **source d’architecture versionnée** du projet ARGOS.

> **Baseline active : [v2.3 — 27 septembre 2026](baseline-v2.3.md)**

## Synthèse exécutive

ARGOS est actuellement en **phase de conception / cadrage POC** : aucune implémentation applicative, manifest de dépendances, Dockerfile, pipeline CI/CD, schéma de données ni API n’existe encore dans le dépôt. La documentation décrit donc une **architecture cible proposée**, pas un état implémenté.

L’orientation retenue à ce stade est :

- plateforme de **Technology Intelligence + Project Intelligence** ;
- monolithe modulaire proposé pour limiter la complexité initiale ;
- modèle canonique pour découpler ARGOS des formats GitHub/GitLab/RSS ;
- PostgreSQL proposé comme stockage principal ;
- Claude/Anthropic encapsulé derrière un **AI Gateway** ;
- FreshRSS et changedetection.io consommés comme systèmes spécialisés séparés ;
- n8n limité aux automatisations périphériques ;
- inventaire projet fondé sur des **SBOM CycloneDX** ;
- corrélation signal ↔ projet **déterministe d’abord** ;
- serveur **MCP lecture seule** pour les IDE ;
- veille réglementaire intégrée avec **validation MKP obligatoire** ;
- OIDC vers l’IdP d’entreprise en première intention ;
- arc42 + C4 + ADR + Mermaid comme documentation-as-code.

Les principaux risques concernent la confidentialité des données envoyées au LLM, les faux positifs/faux négatifs de corrélation, la couverture SBOM, l’injection indirecte via contenu externe, la dérive de complexité et la maîtrise des coûts IA.

## État de la preuve

Au 27 septembre 2026 :

- **Observé** : README, licence et documentation d’architecture présents dans le dépôt ;
- **Décision proposée** : choix décrit par un ADR `Proposé`, à confirmer par revue/POC ;
- **Hypothèse à valider** : proposition nécessitant une preuve, une mesure ou une information d’environnement ;
- **Non déterminé** : information absente ;
- **Accepté** : décision explicitement validée et versionnée comme telle dans un ADR.

La baseline v2.3 réconcilie le dossier de proposition avec le dépôt sans renuméroter l’historique existant : **ADR-0001 à ADR-0009 sont conservés**, et les décisions complémentaires commencent à **ADR-0010**.

## Référentiels utilisés

- **arc42** : structure du dossier ;
- **C4** : niveaux Context, Container et Component ;
- **Mermaid** : unique langage source des diagrammes ;
- **ADR** : décisions structurantes ;
- notation UML-style dans Mermaid avec stéréotypes explicites.

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

- [Baseline v2.3](baseline-v2.3.md)
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
| modèle métier | ✅ Conceptuel | à valider par code/migrations |
| ADR structurants | ✅ 18 versionnés | 0005 et 0009 acceptés ; autres proposés |
| baseline POC v2.3 | ✅ | chaîne verticale, preuves, critères go/no-go |
| scénarios qualité | ✅ Initialisés | à aligner sur les KPI v2.3 |
| registre des risques | ✅ Initialisé | à enrichir avec MCP/SBOM/réglementaire |
| CI architecture/doc | ❌ | à créer |
| manifests runtime | ❌ | non implémentés |
| preuves de POC | ❌ | à produire |

## Plan de migration priorisé : documentation → implémentation

### P0 — Fondation

1. accepter/rejeter les ADR structurants nécessaires au POC ;
2. confirmer la stack de build backend ;
3. créer la structure de modules ;
4. créer PostgreSQL local + migrations ;
5. mettre en place les tests d’architecture ;
6. créer une CI minimale.

### P1 — Chaîne sécurité verticale

1. connecter OSV ;
2. normaliser un événement canonique ;
3. importer une SBOM CycloneDX ;
4. corréler PURL/version ;
5. produire un impact et une alerte ciblée ;
6. mesurer délai, précision et idempotence.

### P2 — Portail / MCP / réglementation

1. restitution minimale filtrable ;
2. `check_dependency` et `get_security_alerts` via MCP ;
3. collecte Légifrance pilote ;
4. fiche réglementaire structurée via AI Gateway ;
5. validation MKP avant diffusion.

### P3 — Industrialisation MVP

1. SSO/RBAC/RLS ;
2. observabilité ;
3. backup/restore ;
4. tests sécurité/charge ;
5. notifications Teams/e-mail ;
6. revue RGPD/licences/SBOM ;
7. runbooks.

### Phase 3 produit — Project Intelligence

Les métriques factuelles GitLab, jalons, pipelines, releases et synthèses d’avancement restent volontairement hors du cœur du POC/MVP initial. L’intégration SCM minimale reste toutefois requise dès le POC pour les dépôts, webhooks et SBOM.

## Contrôles CI recommandés

### Documentation

- Markdown lint ;
- validation des liens ;
- rendu/lint de chaque bloc Mermaid ;
- vérification de l’index ADR ;
- contrôle qu’un ADR accepté n’est ni supprimé ni renuméroté ;
- cohérence des références arc42 ↔ ADR ↔ diagrammes.

### Architecture/code lorsque l’implémentation existe

- tests de dépendances entre modules ;
- compilation + tests unitaires/intégration ;
- validation OpenAPI ;
- migrations DB sur base éphémère ;
- contract tests adapters ;
- SAST, dependency/license scanning et secret scanning ;
- génération SBOM ;
- tests de sécurité webhooks/MCP ;
- tests de politiques RBAC/RLS.

### IA

- prompts versionnés ;
- validation des schémas de sortie ;
- tests de politique de confidentialité ;
- budget/token regression ;
- tests adversariaux d’injection indirecte ;
- fonctionnement dégradé sans modèle externe.

## Questions structurantes encore ouvertes

1. Quel fournisseur d’identité OIDC/SAML est imposé ?
2. Quelles classes de données peuvent être transmises à Claude/Anthropic ?
3. FreshRSS et changedetection.io seront-ils hébergés par ARGOS ou consommés comme services existants ?
4. Quel frontend sera retenu après comparaison ?
5. Quels SLO sont réellement nécessaires après le POC ?
6. Docker Compose ou Podman est-il imposé pour la VM pilote ?
7. Quelle politique de conservation des données de veille, d’audit et d’activité projet ?
8. Quelle stack d’observabilité et quel coffre-fort de secrets sont déjà disponibles en interne ?

Toute réponse structurante doit être reflétée dans un ADR, une exigence qualité ou une contrainte selon sa nature.
