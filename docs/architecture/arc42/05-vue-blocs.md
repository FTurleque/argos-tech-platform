# 5. Vue des blocs

Cette section décrit les niveaux **C4 Container** puis **C4 Component**. Les noms définis ici doivent être réutilisés dans les scénarios d’exécution.

## 5.1 C4 Container

**Type : C4 Container. Portée : intérieur du système ARGOS.**

```mermaid
flowchart LR
    USER["«Person»\nUtilisateur ARGOS"]

    subgraph ARGOS["«Software System» ARGOS"]
        WEB["«Container»\nWeb Application\nInterface utilisateur"]
        BACK["«Container»\nARGOS Backend\nAPI, métier, ingestion, IA"]
        DB["«database»\nPostgreSQL\nDonnées métier, audit, recherche"]
    end

    EXT["«Software System»\nSources externes\nRSS, Web, GitHub, GitLab, CVE"]
    AI["«Software System»\nClaude / Anthropic"]
    IDP["«Software System»\nIdP entreprise"]

    USER -->|utilise via HTTPS| WEB
    WEB -->|appelle REST/HTTPS| BACK
    BACK -->|lit et écrit via SQL/TLS| DB
    BACK -->|collecte via HTTPS/webhooks| EXT
    BACK -->|analyse via HTTPS| AI
    WEB -->|redirige l’authentification| IDP
    BACK -->|valide les identités OIDC| IDP
```

### Web Application

- **Responsabilité** : navigation, tableaux de bord, recherche, administration fonctionnelle.
- **Interfaces** : REST/OpenAPI vers `ARGOS Backend`, OIDC vers l’IdP.
- **Technologie** : **Non déterminé** (React/Vue à comparer).
- **Code source** : absent au moment de l’analyse.

### ARGOS Backend

- **Responsabilité** : API, règles métier, ingestion, corrélation, analyses IA, alertes, orchestration.
- **Interfaces** : REST, webhooks, APIs externes, SQL.
- **Technologie proposée** : Java + Spring Boot.
- **Code source** : absent au moment de l’analyse.

### PostgreSQL

- **Responsabilité** : persistance principale, audit, JSONB, FTS ; pgvector seulement si validé.
- **Interface** : SQL.
- **Code/configuration** : absent au moment de l’analyse.

## 5.2 C4 Component — ARGOS Backend

**Type : C4 Component. Portée : `ARGOS Backend`.**

```mermaid
flowchart TB
    API["«Component»\nAPI & Security\nExpose REST et webhooks"]
    INGEST["«Component»\nIngestion\nOrchestre collecte et normalisation"]
    TECH["«Component»\nTechnology Intelligence\nClasse et enrichit les signaux techno"]
    PROJ["«Component»\nProject Intelligence\nCalcule activité et métriques factuelles"]
    CORR["«Component»\nImpact Correlation\nRelie signaux, technologies et projets"]
    AI["«Component»\nAI Gateway\nEncadre les appels Claude"]
    SEARCH["«Component»\nSearch\nRecherche et navigation"]
    ALERT["«Component»\nAlerting & Digest\nPrépare notifications et synthèses"]
    CORE["«Component»\nWorkspace & Project Core\nGère workspaces, projets et technologies"]
    JOBS["«Component»\nScheduler & Jobs\nPlanifie synchronisations et reprises"]
    PORTS["«interface»\nApplication Ports"]
    EXT["«adapter»\nExternal Adapters\nRSS, Web, GitHub, GitLab, CVE, Claude"]
    PERSIST["«adapter»\nPersistence Adapter\nPostgreSQL"]

    API -->|appelle| CORE
    API -->|déclenche| INGEST
    INGEST -->|produit des items| TECH
    INGEST -->|produit des activités| PROJ
    TECH -->|demande corrélation| CORR
    PROJ -->|fournit contexte projet| CORR
    CORR -->|demande analyse si nécessaire| AI
    ALERT -->|lit résultats| TECH
    ALERT -->|lit résultats| PROJ
    SEARCH -->|interroge| PORTS
    JOBS -->|planifie| INGEST

    CORE -->|utilise| PORTS
    INGEST -->|utilise| PORTS
    TECH -->|utilise| PORTS
    PROJ -->|utilise| PORTS
    CORR -->|utilise| PORTS
    AI -->|utilise| PORTS
    ALERT -->|utilise| PORTS

    EXT -->|implémente| PORTS
    PERSIST -->|implémente| PORTS
```

## 5.3 Responsabilités et frontières

| Composant | Responsabilité | Ne doit pas |
|---|---|---|
| API & Security | exposition REST/webhooks, authn/authz, validation | contenir les règles métier |
| Ingestion | collecte, idempotence, normalisation, statut de traitement | connaître la présentation UI |
| Technology Intelligence | classifier/enrichir signaux technologiques | appeler directement un SDK externe |
| Project Intelligence | interpréter données projet factuelles | inventer un avancement subjectif |
| Impact Correlation | relier techno ↔ projets ↔ impacts | devenir dépendant de GitHub/GitLab |
| AI Gateway | prompts, modèles, quotas, structured output, audit | exposer Claude directement au domaine |
| Search | requêtes de recherche et navigation | imposer un moteur externe prématurément |
| Alerting & Digest | règles d’alerte et génération de digests | porter la logique de collecte |
| Workspace & Project Core | modèle central et ownership | dépendre des adapters |
| Scheduler & Jobs | orchestration temporelle/retry | devenir une plateforme workflow générale |

## 5.4 Modèle métier critique — UML-style classDiagram

**Type : UML class diagram. Portée : noyau métier conceptuel.**

```mermaid
classDiagram
    class Workspace {
        <<aggregate>>
        +UUID id
        +String name
    }
    class Project {
        <<aggregate>>
        +UUID id
        +String name
        +ProjectStatus status
    }
    class Repository {
        <<entity>>
        +UUID id
        +RepositoryProvider provider
        +String externalId
        +String url
    }
    class Technology {
        <<entity>>
        +UUID id
        +String name
        +String versionConstraint
    }
    class Source {
        <<entity>>
        +UUID id
        +SourceType type
        +String externalRef
    }
    class IntelligenceItem {
        <<aggregate>>
        +UUID id
        +String title
        +URI canonicalUrl
        +Instant publishedAt
        +ProcessingStatus status
    }
    class ProjectActivity {
        <<entity>>
        +UUID id
        +ActivityType type
        +Instant occurredAt
        +String externalId
    }
    class ProjectImpact {
        <<aggregate>>
        +UUID id
        +ImpactLevel level
        +ImpactStatus status
        +Decimal confidence
    }
    class Analysis {
        <<entity>>
        +UUID id
        +AnalysisType type
        +String model
        +String promptVersion
    }
    class Alert {
        <<entity>>
        +UUID id
        +AlertSeverity severity
        +AlertStatus status
    }

    Workspace "1" --> "*" Project : contient
    Workspace "1" --> "*" Source : configure
    Project "1" --> "*" Repository : référence
    Project "*" --> "*" Technology : utilise
    Source "1" --> "*" IntelligenceItem : produit
    Repository "1" --> "*" ProjectActivity : produit
    IntelligenceItem "*" --> "*" Technology : concerne
    Project "1" --> "*" ProjectImpact : reçoit
    IntelligenceItem "1" --> "*" ProjectImpact : déclenche
    ProjectImpact "1" --> "*" Analysis : est expliqué par
    ProjectImpact "1" --> "*" Alert : peut générer
```

Le diagramme est conceptuel. Les attributs et cardinalités restent **Hypothèses à valider** jusqu’à création du modèle de données et du code.

## 5.5 Règles de dépendance proposées

1. le domaine ne dépend pas des SDK externes ;
2. les adapters dépendent des ports, jamais l’inverse ;
3. `Project Intelligence` ne dépend pas de `Technology Intelligence` ;
4. `Impact Correlation` peut consommer les deux ;
5. `AI Gateway` est le seul composant autorisé à appeler un fournisseur LLM ;
6. les contrôleurs Web ne parlent pas directement à la base ;
7. les événements externes sont normalisés avant usage métier.

## 5.6 C4 Code

**Non pertinent à ce stade.** Aucun code n’existe encore. Un niveau Code ne devra être ajouté que pour une zone réellement complexe et stable, pas pour documenter chaque classe.

## 5.7 Preuves, hypothèses et risques

### Observé

- absence de code ;
- principes décrits dans le README.

### Hypothèses à valider

- existence de trois conteneurs principaux Web/Backend/PostgreSQL ;
- découpage des composants backend ;
- modèle de classes proposé.

### Preuves nécessaires

- packages/modules réels ;
- tests d’architecture ;
- spécification API ;
- schéma de données ;
- manifests de déploiement.

### Risques

- frontières de modules non respectées à l’implémentation ;
- `AI Gateway` contourné ;
- modèle canonique insuffisant ;
- sur-modélisation précoce.
