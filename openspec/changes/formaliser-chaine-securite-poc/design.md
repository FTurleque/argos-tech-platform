# Conception : formaliser la chaîne sécurité du POC et la personnalisation de la veille

<!-- Statuts : [EXIGENCE] [EXISTANT] [DÉCISION] [PROPOSITION] [QUESTION]. -->

**Niveau de conception** : complète — quatre modules du monolithe, modèle de données nouveau, cloisonnement de sécurité et reprise sur erreur (voir proposal.md, « Conception requise »). Aucune décision de cette conception n'est acceptée : toutes sont [PROPOSITION] tant que les ADR sources restent « Proposé ».

## Contexte technique

- [EXISTANT] Le socle `argos-server/` (Quarkus 3.40, Flyway provisoire) porte l'implémentation de ce lot : migrations `V3__collecte_avis_securite.sql` à `V6__abonnements_veille.sql` (après `V2__comptes_et_acces.sql` du changement gerer-comptes-et-acces, qui crée les tables `compte`, `espace` et `refus_acces`), paquets `io.argos.<module>` et tests d'intégration sur PostgreSQL réel (voir tasks.md, matrice de traçabilité).
- [DÉCISION] Java 17+, Quarkus, Vue.js 3, PostgreSQL (ADR-0019, ADR-0002 acceptés) ; REST + OpenAPI.
- [PROPOSITION] Monolithe modulaire aux frontières vérifiées par ArchUnit (ADR-0001) ; modèle canonique (ADR-0003) ; aucun broker au MVP (arc42 §8.9).
- Motivation : voir proposal.md, « Why — Pourquoi ».

## Exigences couvertes

| Exigence | Section(s) de conception | Diagramme(s) |
|---|---|---|
| COL-01, COL-02 | Traitements ; Persistance | DF-COL-1, DF-COL-2, DC-2 |
| COL-03 | Transactions, concurrence et idempotence | DF-COL-2 |
| COL-04 | Traitements ; Gestion des erreurs | DF-COL-1 |
| COL-05, COL-06, COL-07 | Gestion des erreurs et reprise ; États | DF-COL-1, DF-COL-3, DC-3 |
| COL-08 | Gestion des erreurs et reprise | DF-COL-2 |
| INV-01 à INV-05 | Traitements ; Persistance ; Décision D6 (INV-01-S3) | DF-INV-1, DF-INV-2, DC-2 |
| COR-01, COR-02, COR-06, COR-07 | Traitements ; Transactions ; Décision D5 (COR-01-S5) | DF-COR-1, DF-COR-2, DF-COR-3 |
| COR-03 | Persistance (provenance) ; Observabilité | DF-COR-1, DC-2 |
| COR-04, COR-05 | Décision D3 | DF-COR-2 |
| COR-08, ABO-05 | Sécurité et isolation | DF-ABO-2 |
| ABO-01 à ABO-04 | Contrats d'interface ; Persistance | DF-ABO-1 |

Les diagrammes `DF-*` sont des vues métier maintenues dans les fiches `/docs/specifications/` ; les diagrammes `DC-*` ci-dessous sont des vues de conception interne.

## Objectifs et non-objectifs

**Objectifs :**
- répartir les exigences entre les composants de arc42 §5 sans créer de nouveau module ;
- fixer les invariants testables (unicité, provenance, curseur) avant l'implémentation ;
- isoler les décisions qui exigent un arbitrage.

**Non-objectifs :**
- fixer les chemins d'API, les noms de tables ou l'outil de migration (Flyway ou Liquibase : question ouverte de `docs/architecture/README.md`) ;
- concevoir l'alerting, l'AI Gateway ou le serveur MCP (lots ultérieurs).

## Composants impactés, responsabilités et frontières

| Composant | Responsabilité dans ce changement | Ne doit pas | Statut |
|---|---|---|---|
| `source-ingestion` | synchroniser OSV, gérer curseur, retry, état de source, zone d'échec ; normaliser | connaître les règles de corrélation | à créer |
| `signal` | porter le signal normalisé, son historique et sa provenance | dépendre du format OSV | à créer |
| `vuln-intel` | enrichir (NVD, KEV, EPSS, CERT-FR), dédupliquer par alias | bloquer l'enregistrement d'un signal | à créer |
| `scm-connector` | récupérer SBOM et manifestes des dépôts | injecter des payloads GitLab/GitHub dans le domaine | à créer |
| `inventory` | valider et importer SBOM, repli manifestes, âge | calculer l'impact | à créer |
| `impact-engine` | corrélation déterministe, niveau, provenance, idempotence | déléguer la décision au LLM | à créer |
| `workspace` | abonnements `TEAM`/`PERSONAL`, restitution filtrée | confondre préférence et autorisation | à créer |
| `API & Security` | authentification, autorisation, journalisation des refus | contenir les règles métier | à créer |
| `audit` | conserver payloads bruts, refus d'accès | — | à créer |

### DC-1 — Frontières des composants du lot

- **Question** : quelles dépendances entre composants le lot introduit-il ?
- **Type** : vue de composants à stéréotypes C4 (flowchart), sous-ensemble de D-04
- **Portée** : cible proposée
- **Exigences** : toutes

```mermaid
flowchart LR
    OSV["«Software System»\nOSV"]
    SEC["«Software System»\nNVD / KEV / EPSS / CERT-FR"]
    SCMX["«Software System»\nGitLab / GitHub"]

    subgraph BACK["«Container» Quarkus Backend"]
        API["«Component»\nAPI & Security"]
        ING["«Component»\nsource-ingestion"]
        SIG["«Component»\nsignal"]
        VULN["«Component»\nvuln-intel"]
        SCM["«adapter»\nscm-connector"]
        INV["«Component»\ninventory"]
        IMP["«Component»\nimpact-engine"]
        WORK["«Component»\nworkspace"]
        AUD["«Component»\naudit"]
    end
    DB["«database»\nPostgreSQL"]

    ING -->|lit les avis via HTTPS| OSV
    VULN -->|lit les enrichissements via HTTPS| SEC
    SCM -->|lit SBOM et manifestes via API HTTPS| SCMX
    ING -->|crée ou historise| SIG
    SIG -->|demande l'enrichissement| VULN
    SCM -->|transmet SBOM| INV
    VULN -->|signal enrichi| IMP
    INV -->|inventaire modifié| IMP
    API -->|consultations| WORK
    API -->|consultations impacts| IMP
    API -->|journalise les refus| AUD
    ING -->|SQL| DB
    SIG -->|SQL| DB
    INV -->|SQL| DB
    IMP -->|SQL| DB
    WORK -->|SQL| DB
    AUD -->|SQL| DB
```

Les relations respectent arc42 §5.5 : les adapters externes dépendent des ports, le domaine ne dépend d'aucun SDK, les contrôleurs ne parlent pas à la base. L'arc D-04 `SCM --> INVENTORY` est repris tel quel ; `SIG --> VULN` reprend `SIGNAL --> VULN`.

## Modèle de domaine et invariants

Le modèle conceptuel est DF-COR-1 (corrélation) et DF-ABO-1 (abonnements). Invariants testables :

- **INV-D1** : (source, identifiant externe) identifie au plus un signal (COL-03).
- **INV-D2** : tout signal a une provenance complète ; toute version d'avis est rattachée à un signal (COL-02, COL-03).
- **INV-D3** : (signal, projet, composant) identifie au plus un impact (COR-07).
- **INV-D4** : tout impact référence un signal, un projet, un composant, un niveau et une méthode (COR-02, COR-03).
- **INV-D5** : le curseur persisté d'une source ne dépasse jamais le dernier avis enregistré durablement (COL-05).
- **INV-D6** : un abonnement a exactement une portée et un propriétaire du type correspondant (ABO-01, ABO-02).
- **INV-D7** : aucune table d'autorisation n'est lue ni écrite par le module `workspace` lors de la gestion des abonnements (ABO-05).

## Traitements et interactions

- Collecte et enrichissement : DF-COL-1, décision par avis : DF-COL-2.
- Import SBOM : DF-INV-1 ; origine de l'inventaire : DF-INV-2.
- Corrélation : DF-COR-2 ; niveau : DF-COR-3.
- Restitution de la veille : DF-ABO-2.
- Ces vues nomment leurs participants comme les composants de §5, à l'image des diagrammes d'arc42 §6.

## États et transitions

- État de synchronisation d'une source : DF-COL-3 (`DEGRADED` documenté ; `Nominale` et `En échec` proposés).
- État d'un traitement de synchronisation, repris de arc42 §6.4 :

### DC-3 — Traitement de synchronisation et reprise

- **Question** : comment un traitement interrompu par un arrêt du backend est-il repris ?
- **Type** : UML états (stateDiagram-v2)
- **Portée** : cible proposée ; `RUNNING` et `RETRYABLE` viennent de arc42 §6.4, `SUCCEEDED` et `FAILED` sont proposés par ce changement
- **Exigences** : COL-05, COL-06

```mermaid
stateDiagram-v2
    [*] --> RUNNING: synchronisation lancée
    RUNNING --> SUCCEEDED: lot persisté, curseur avancé
    RUNNING --> RETRYABLE: erreur transitoire
    RUNNING --> RETRYABLE: arrêt du backend détecté au redémarrage (RUNNING expiré)
    RUNNING --> FAILED: erreur permanente
    RETRYABLE --> RUNNING: délai écoulé et tentatives restantes
    RETRYABLE --> FAILED: nombre maximal de tentatives atteint
    SUCCEEDED --> [*]
    FAILED --> [*]
```

Au redémarrage, les traitements `RUNNING` expirés sont reclassés `RETRYABLE` (arc42 §6.4) et reprennent depuis le curseur persisté, ce qui garantit COL-05-S2. La durée d'expiration est le paramètre d'exploitation `argos.ingestion.running-expiry` (30 minutes par défaut).

## Contrats d'interface

Le contrat REST est décrit en OpenAPI générée par Quarkus (ADR-0019), sous `/api/v1`. Opérations réalisées [PROPOSITION] :

| Opération | Autorisation | Erreurs observables exigées | Exigences |
|---|---|---|---|
| Consulter un signal et sa provenance | utilisateur authentifié | — | COL-02-S1 |
| Consulter l'état des sources | rôle `OPS` | — | COL-07-S1 |
| Consulter l'inventaire d'un projet, son âge et les composants ignorés faute de PURL | utilisateur autorisé sur le projet | — | INV-01-S3, INV-02-S1, INV-03 |
| Consulter les impacts d'un signal (liste paginée, arc42 §8.10) | filtrée par espace | — | COR-08-S1 |
| Consulter un impact | utilisateur autorisé sur le projet | refus sans divulgation, journalisé | COR-03-S1, COR-08-S2 |
| Créer un abonnement | `PERSONAL` : l'appelant ; `TEAM` : administrateur de l'espace (Q-5, ABO-06) | refus nommant l'attribut manquant (400) ; refus journalisé (403) | ABO-01-S1, ABO-02, ABO-06 |
| Consulter sa veille | utilisateur authentifié | — | ABO-03, ABO-04, ABO-05 |

Un accès à une ressource d'un autre espace reçoit la même réponse 404 qu'une ressource inexistante, sans contenu, et il est journalisé (COR-08-S2) ; ce choix répond provisoirement à Q-D1. L'appelant est établi depuis le jeton OIDC et les appartenances aux espaces du changement gerer-comptes-et-acces (`OidcCallerResolver`) ; sans jeton valide, toute consultation reçoit 401. Les refus sont journalisés dans la table unique `refus_acces`.

## Persistance et intégrité

### DC-2 — Modèle de persistance proposé

- **Question** : quelles entités et contraintes d'unicité portent les invariants INV-D1 à INV-D6 ?
- **Type** : modèle entité-association (erDiagram), pas un diagramme UML
- **Portée** : cible proposée ; noms de travail, aucun schéma SQL n'existe
- **Exigences** : COL-02, COL-03, COL-05, COL-08, INV-01, INV-04, COR-03, COR-07, ABO-02

```mermaid
erDiagram
    SOURCE ||--o{ SIGNAL : "publie"
    SOURCE ||--|| SYNC_CURSOR : "position"
    SOURCE ||--o{ INGESTION_FAILURE : "rejets"
    SIGNAL ||--|{ SIGNAL_VERSION : "historique"
    SIGNAL ||--o{ ENRICHMENT : "enrichi par"
    PROJECT ||--o| INVENTORY : "référence"
    INVENTORY ||--|{ COMPONENT : "contient"
    SIGNAL ||--o{ PROJECT_IMPACT : "source"
    PROJECT ||--o{ PROJECT_IMPACT : "concerné"
    COMPONENT ||--o{ PROJECT_IMPACT : "rapproché"
    WORKSPACE ||--o{ PROJECT : "regroupe"
    SUBSCRIPTION }o--|| SUBSCRIPTION_OWNER : "propriétaire"

    SIGNAL {
        uuid id PK
        string source "UK avec external_id"
        string external_id "UK avec source"
        timestamp collected_at
        string raw_ref "contenu brut ou référence d'audit"
    }
    SYNC_CURSOR {
        string source_id FK
        string position
        timestamp updated_at
    }
    INVENTORY {
        uuid project_id FK
        string origin "SBOM ou MANIFEST"
        timestamp source_date
        int ignored_count "composants sans PURL"
        jsonb ignored_components "100 au plus"
    }
    COMPONENT {
        string purl
        string version
        boolean transitive
    }
    PROJECT_IMPACT {
        uuid signal_id "UK avec project_id, component_id"
        uuid project_id "UK"
        uuid component_id "UK"
        string level "exact, probable, a_verifier"
        string method
        string provenance
    }
    SUBSCRIPTION {
        string scope "TEAM ou PERSONAL"
        string filters
        string channel
        string frequency
        boolean mandatory
        timestamp created_at
        string state
    }
```

Contraintes proposées : unicité (source, external_id) pour INV-D1 et (signal_id, project_id, component_id) pour INV-D3 ; clés étrangères non nulles sur `PROJECT_IMPACT` pour INV-D4 ; `PROJECT_IMPACT` porte la politique RLS de l'espace du projet (ADR-0016). `SUBSCRIPTION_OWNER` représente l'alternative équipe ou utilisateur ; sa réalisation (deux colonnes exclusives ou table d'association) est laissée à l'implémentation. Migrations : outil non choisi [QUESTION, ADR à venir].

## Transactions, concurrence et idempotence

- Collecte : enregistrement des signaux d'un lot et avancement du curseur dans la même transaction, ou curseur avancé après validation de la transaction des signaux (INV-D5). Les deux options satisfont COL-05 ; choix à l'implémentation.
- Idempotence : contrainte d'unicité + insertion conditionnelle (« insérer si absent ») pour signaux et impacts ; la contrainte de base est l'arbitre en cas de collectes concurrentes (COL-03-S3), pas un verrou applicatif.
- Ordre : une version plus ancienne reçue après une plus récente ne remplace pas la version courante [PROPOSITION] — règle de comparaison liée à Q-COL-2.

## Sécurité et isolation

- Signaux globaux ; impacts, inventaires, abonnements et vues rattachés à un espace (ADR-0016).
- Double contrôle : vérification d'espace dans les services, RLS en base comme garde-fou ; traitements batch (collecte, import, corrélation, contrôle d'intégrité) sous l'utilisateur de connexion, propriétaire des tables, les consultations sous le rôle applicatif `argos_app` avec le compte de l'appelant (ADR-0016, conséquences négatives ; arc42 §8.1).
- Abonnements : le module `workspace` ne lit ni n'écrit les rôles (INV-D7) ; la restitution passe par les mêmes requêtes RLS que les autres consultations (DF-ABO-2).
- Entrées externes (avis, SBOM, manifestes) traitées comme données non fiables : validation de structure, taille maximale configurable (`argos.inventory.max-document-bytes`, 20 Mio par défaut), aucune redirection ni URL issue d'un avis ou d'un flux suivie (risque SSRF R-12).
- Aucun appel IA dans ce lot ; toute analyse future passe par `ai-gateway` et sa politique de classification (arc42 §8.3).

## Gestion des erreurs et reprise

- Erreurs transitoires : timeout, 429, 5xx → `RETRYABLE`, délai croissant avec gigue (arc42 §8.5), `Retry-After` prioritaire.
- Erreurs permanentes : 401, 403, 400 → `FAILED`, événement d'exploitation.
- Seuil d'échec → source `DEGRADED` (DF-COL-3). Valeurs : Q-4.
- Avis non normalisable → `INGESTION_FAILURE` avec motif et provenance ; rejeu contrôlé possible après correction (arc42 §8.5, « replay contrôlé »).
- Indisponibilité d'une source d'enrichissement : signal conservé, enrichissement retenté au prochain cycle [PROPOSITION].

## Observabilité

Métriques Micrometer / OpenTelemetry (arc42 §8.6) : fraîcheur par source, nombre de tentatives et d'échecs, sources `DEGRADED`, avis mis à l'écart, signaux créés/historisés, impacts créés par niveau, refus d'accès. Corrélation des journaux par identifiant de synchronisation et identifiant de signal. Aucun secret ni jeton dans les journaux (QS-09).

## Performance et stratégie de mesure

- Consultation : API p95 < 500 ms hors appels externes et IA (arc42 §8.10, cible initiale [PROPOSITION]) — mesure par test de charge (QS-06).
- Délai publication source → signal (« < 1 h », ADR-0010) : cible de qualité mesurée pendant le POC, sans être un critère d'acceptation ([DÉCISION] Q-6 — porteur du projet, 2026-11-18). « Projets touchés < 5 min » : cible non validée, à mesurer pendant le POC sans en faire un critère d'acceptation.
- Volumétrie d'avis et de projets : non documentée [QUESTION, arc42 §10.4].

## Compatibilité, migration et retour arrière

Non applicable — aucune donnée ni aucun contrat existant à migrer. Le retour arrière d'une future livraison se limite au retrait des migrations du lot ; la stratégie dépend de l'outil de migration choisi. La migration V17 (décision D6) ajoute à `inventory` deux colonnes dotées d'une valeur par défaut : un inventaire importé avant elle signale zéro composant ignoré jusqu'à son prochain import ; les champs ajoutés à la réponse de l'API d'inventaire laissent les champs existants inchangés.

## Décisions, alternatives et compromis

### D1 — Signal normalisé distinct des versions d'avis

- **Statut** : [PROPOSITION]
- **Choix** : un signal logique par (source, identifiant externe) et une table d'historique des versions.
- **Justification** : satisfait l'idempotence (COL-03-S1) et l'historisation (COL-03-S2) avec une contrainte d'unicité simple.
- **Alternatives écartées** : un enregistrement par collecte (duplication logique, contraire à Q-03) ; écrasement sans historique (perte de traçabilité, contraire à baseline v2.3 §4).
- **Compromis** : volume d'historique à purger selon une politique de rétention non définie.
- **ADR** : non — décision locale, cohérente avec ADR-0003.

### D2 — Inventaire de référence remplacé à chaque SBOM

- **Statut** : [PROPOSITION]
- **Choix** : un inventaire de référence par projet, remplacé en bloc ; l'origine (`SBOM`, `MANIFEST`) et la date de la source sont des attributs de l'inventaire.
- **Justification** : la corrélation n'a besoin que de l'état courant (COR-01) ; l'âge et l'origine alimentent COR-06 et INV-03.
- **Alternatives écartées** : inventaire versionné par SBOM (utile pour l'historique, non exigé) ; fusion incrémentale (risque de composants fantômes).
- **Compromis** : pas d'historique d'inventaire ; à reconsidérer si un besoin d'audit apparaît.
- **ADR** : non — décision locale.

### D3 — Analyse IA après l'enregistrement des impacts déterministes

- **Statut** : [PROPOSITION]
- **Choix** : `impact-engine` enregistre les impacts déterministes avant toute sollicitation de `ai-gateway`.
- **Justification** : rend COR-05 vrai par construction et applique COR-04.
- **Alternatives écartées** : appel IA dans la même transaction (panne IA bloquante) ; IA en premier (contraire à ADR-0013).
- **Compromis** : la suggestion IA arrive après coup ; aucune alerte critique n'en dépend.
- **ADR** : déjà couvert par ADR-0013 (Proposé).

### D4 — Aucun nouvel ADR pour ce lot

- **Statut** : [PROPOSITION]
- **Choix** : s'appuyer sur ADR-0010, 0011, 0013, 0016 (Proposés) ; signaler comme candidats à ADR, hors de ce changement, le mécanisme de planification/jobs et l'outil de migration (arc42 §9.3, points 1 et 3).
- **ADR** : candidats signalés, rédigés hors de ce changement : ADR-0031 (planification) et ADR-0032 (migrations Flyway), Acceptés ([DÉCISION] — porteur du projet, 2026-11-18).

### D5 — Ordre Maven des qualificatifs inconnus

- **Statut** : [DÉCISION] du porteur du projet, 2026-11-18 (écart E-1 du jeu de référence, question Q-COR-3 de la fiche `correlation-impact`).
- **Choix** : pour l'écosystème Maven seulement, quand l'ordre générique de deux versions dépend d'un qualificatif inconnu (`jre`, `android`…), l'ordre de `ComparableVersion` de Maven tranche : un qualificatif inconnu suit la version publiée et `sp`, et les qualificatifs inconnus s'ordonnent entre eux par ordre lexical. L'algorithme est une adaptation de `ComparableVersion` (Maven Artifact 3.9.16, Apache-2.0) dans le dépôt (`io.argos.impact.MavenVersionOrder`), sans dépendance, avec attribution et texte de licence dans `argos-server/NOTICE` et `argos-server/licenses/Apache-2.0.txt` ; il reçoit les versions sans préfixe `v` ni métadonnées `+…`, et une version qui contient `_` reste `à vérifier`. L'ordre générique et ses qualificatifs connus (`dev`, `pre`, `preview`, `post`, `rev`, `r`, `a`, `b`, `c`, `m` sans chiffre compris) restent appliqués en premier : pour ces qualificatifs, l'ordre retenu diffère de `ComparableVersion`, et le mélange des deux ordres peut ne pas être transitif (`1.0-dev` < `1.0-alpha` < `1.0-abc` < `1.0-dev`) [PROPOSITION : application aux seuls qualificatifs inconnus de l'ordre générique, à trancher par le porteur du projet face à `ComparableVersion` intégral pour Maven]. Les autres écosystèmes gardent le niveau `à vérifier` pour une plage dont l'ordre dépend d'un qualificatif inconnu.
- **Justification** : les avis OSV de l'écosystème Maven expriment leurs bornes dans l'ordre de Maven ; `32.0.0-jre` suit la borne `32.0.0-android` et n'est pas affectée (COR-01-S5).
- **Alternatives écartées** : niveau `à vérifier` pour tout qualificatif inconnu (faux positif E-1) ; dépendance `maven-artifact` (Apache-2.0, ajoute `plexus-utils`) pour un algorithme court.
- **Compromis** : fidélité à `ComparableVersion` à maintenir ; elle est vérifiée par `MavenVersionOrderTest`. Code dérivé d'Apache-2.0, intégré selon ADR-0021 points 3 et 4 [PROPOSITION : adaptation attribuée plutôt que dépendance `maven-artifact` ou réécriture indépendante, à trancher par le porteur du projet].
- **ADR** : non — règle locale de comparaison, dans le cadre d'ADR-0013.

### D6 — Composants sans PURL signalés à l'import

- **Statut** : [DÉCISION] du porteur du projet, 2026-11-18 (écart E-2 du jeu de référence).
- **Choix** : l'inventaire reste fondé sur le PURL (INV-01) ; un composant de SBOM sans PURL exploitable est écarté et signalé : nombre total et description (nom, groupe, version, motif `SANS_PURL` ou `PURL_INVALIDE`) des 100 premiers, dans l'inventaire restitué par `GET /api/v1/projects/{projectId}/inventory` (champs `ignoredCount` et `ignoredComponents`), seul canal exposé à l'utilisateur : `argos-ui` n'a pas de vue de l'inventaire, les outils MCP ne restituent pas le signalement et le résultat interne de l'import (`InventoryService.ImportResult`) n'est renvoyé par aucun point d'entrée [PROPOSITION : l'API suffit à rendre le signalement visible, à confirmer par le porteur du projet]. Le signalement est remplacé à chaque import (D2) et conservé dans les colonnes `ignored_count` et `ignored_components` de `inventory` (migration V17, ADR-0032), sous la RLS de la table.
- **Justification** : un composant ignoré en silence masque un faux négatif de la corrélation ; le signalement le rend visible sans créer de correspondance incertaine.
- **Alternatives écartées** : clé `à vérifier` déduite du groupe et du nom Maven (hors de la décision D6) ; table dédiée (aucune requête ne porte sur un composant ignoré isolé).
- **Compromis** : liste bornée à 100 entrées et champs bornés à 200 caractères [PROPOSITION] ; aucune vue de l'inventaire n'existe dans `argos-ui`, la restitution passe par l'API.
- **ADR** : non — décision locale.

## Risques

- Plages de versions hétérogènes selon les écosystèmes → faux positifs/négatifs ; atténuation : jeu de référence (baseline v2.3 §6 critère 3), registre R-15.
- Rate limit des sources → retard de collecte ; atténuation : `Retry-After`, delta par curseur (R-14).
- Fuite inter-espaces par une requête oubliant le filtre → RLS en garde-fou, tests inter-espaces (R-17 pour la partie RAG, ADR-0016).
- SSRF via URL d'avis → aucune URL d'avis suivie automatiquement (R-12).

## Écarts code / tests / specs / documentation

- Code et tests alignés sur les scénarios : voir tasks.md, matrice de traçabilité.
- arc42 §6 / §5 : les participants des diagrammes d'arc42 §6 portent les noms des composants de §5 ([DÉCISION] — porteur du projet, 2026-11-18, tâche 6.1).
- Inventaire des diagrammes : D-05, D-11, D-12, D-13 annoncés mais absents — D-05 ajouté en arc42 §5.7 ; D-11, D-12 et D-13 marqués manquants.
- `F01`–`F16` cités par les ADR sans définition dans le dépôt (proposal.md, Q-1).

## Stratégie de vérification

| Exigence / scénario | Niveau de test | Moyen de preuve | État |
|---|---|---|---|
| COL-01 à COL-04, COL-08 | intégration (PostgreSQL réel) + contrat (réponses OSV/NVD enregistrées) | tests du module `source-ingestion` / `vuln-intel` | à écrire |
| COL-03-S3, COR-07 | intégration concurrente | test parallèle sur contrainte d'unicité | à écrire |
| COL-05, COL-06, COL-07 | résilience (simulation 429/5xx/timeout, arrêt forcé) | QS-03, QS-07 | à écrire |
| INV-01 à INV-05 | intégration avec SBOM CycloneDX de test | jeux de SBOM valides/invalides | à écrire |
| COR-01 à COR-06 | unitaire (règles) + jeu de référence annoté | précision/rappel (baseline v2.3 §6 critère 3) | `MatchRulesTest`, `CorrelationTest`, `JeuReferenceCorrelationTest` ; jeu composé mesuré le 2026-11-18, inventaires des projets pilotes à ajouter (tâches 4.7 et 7.1) |
| COR-08, ABO-05 | intégration inter-espaces REST + RLS | test ADR-0016 | à écrire |
| ABO-01 à ABO-04 | intégration API | tests du module `workspace` | à écrire |
| Frontières de modules | architecture | ArchUnit (ADR-0001) | à écrire |

## Documentation durable impactée

- `/docs/specifications/README.md` et les fiches `collecte-avis-securite`, `inventaire-sbom`, `correlation-impact`, `abonnements-veille` (créées avec ce changement, statut [PROPOSITION]).
- `/docs/architecture/arc42/05-vue-blocs.md` (D-05), `/docs/architecture/arc42/06-vue-execution.md` (correspondance), `/docs/architecture/diagrams/README.md`.
- Glossaire arc42 §12 : `Inventaire`, `Composant`, `Signal de sécurité` à ajouter lors de l'implémentation.

## Questions ouvertes

- Q-D1 : code HTTP d'un accès hors périmètre — 404 sans contenu retenu provisoirement, à confirmer.
- Durée d'expiration d'un traitement `RUNNING` — paramètre d'exploitation, 30 minutes par défaut, à valider.
- Taille maximale d'une SBOM ou d'un manifeste acceptée — paramètre de sécurité, 20 Mio par défaut, à valider.

Les questions qui changent ce qui sera construit (Q-2, Q-3, Q-4, Q-5, Q-7 de la proposition) sont traitées par le groupe 1 des tâches avant toute implémentation.
