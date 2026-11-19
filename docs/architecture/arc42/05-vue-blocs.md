# 5. Vue des blocs

Cette section décrit les niveaux **C4 Container** puis **C4 Component**. La stack est fixée par ADR-0019 : Java 17 minimum, Quarkus, Vue.js 3 et PostgreSQL.

## 5.1 C4 Container

```mermaid
flowchart LR
    USER["«Person»\nUtilisateur ARGOS"]

    subgraph ARGOS["«Software System» ARGOS"]
        WEB["«Container»\nVue.js 3 SPA\nInterface utilisateur"]
        DESK["«Container»\nClient desktop Electron\nemballe la SPA (ADR-0022, proposé)"]
        BACK["«Container»\nQuarkus Backend\nAPI, métier, ingestion, MCP, IA"]
        DB["«database»\nPostgreSQL\nMétier, audit, recherche"]
    end

    EXT["«Software System»\nSources externes\nRSS, Web, GitHub, GitLab, CVE"]
    AI["«Software System»\nOllama, modèle d'IA local\n(ADR-0029, accepté ; facultatif)"]
    IDP["«Software System»\nKeycloak auto-hébergé\n(ADR-0027, accepté)"]

    USER -->|HTTPS| WEB
    USER -->|poste de travail| DESK
    DESK -->|REST/OpenAPI| BACK
    WEB -->|REST/OpenAPI| BACK
    BACK -->|SQL| DB
    BACK -->|HTTPS/webhooks| EXT
    BACK -->|via la passerelle IA| AI
    WEB -->|OIDC code + PKCE| IDP
    DESK -->|OIDC via le navigateur du système| IDP
    BACK -->|valide les identités| IDP
```

### Vue.js 3 SPA

- navigation, tableaux de bord, recherche et préférences ;
- socle de veille d’équipe + veille personnelle ;
- aucune logique métier critique dans l’IHM ;
- session OIDC (exigences ACC-07 à ACC-10 de la capacité [`comptes-acces`](../../specifications/comptes-acces.md), livrées ; ADR-0033, Accepté) : code d’autorisation + PKCE S256 auprès de Keycloak, jetons en mémoire seulement, jeton porteur joint aux appels de `/api/v1` sauf l’identité du serveur ; toute vue sauf l’état du serveur exige une session.

### Client desktop Electron (proposé, ADR-0022)

- même build Vue.js 3 que la SPA, servi par un protocole interne (`app://argos`) ;
- notifications natives et icône de zone de notification ;
- ni base de données ni serveur embarqué : client de l’API REST uniquement ;
- alertes du poste (capacité [`notifications-poste`](../../specifications/notifications-poste.md)) : la couche contenu de l’IHM lit les alertes `DESKTOP` en attente et confirme leur remise ; le processus principal affiche la notification (titre, gravité, nombre de projets), tient le compteur d’alertes ouvertes de la zone de notification et les réglages du poste (notifications du poste, démarrage avec la session désactivé par défaut) ; il n’appelle pas l’API ;
- fenêtre fermée, l’application reste dans la zone de notification ; quittée, plus rien ne tourne (aucun service en arrière-plan) ;
- connexion OIDC ([PROPOSITION], ADR-0033) : l’autorisation s’ouvre dans le navigateur du système ; le processus principal écoute le retour sur `127.0.0.1` (port éphémère, RFC 8252) et le rend à l’IHM, qui échange le code ; la CSP n’ajoute que l’origine du fournisseur d’identité configuré.

### Quarkus Backend

- **Java 17 minimum** ;
- API REST/OpenAPI ;
- règles métier, ingestion, corrélation, alerting, AI Gateway et serveur MCP ;
- monolithe modulaire proposé ;
- aucune dépendance du domaine aux SDK externes.

### PostgreSQL

- persistance principale ;
- JSONB, FTS et RLS ;
- pgvector uniquement si besoin démontré.

## 5.2 C4 Component — Quarkus Backend

```mermaid
flowchart TB
    API["«Component»\nAPI & Security\nQuarkus REST/OpenAPI"]
    WORK["«Component»\nWorkspace\nÉquipes, rôles, socle + veille perso"]
    INGEST["«Component»\nsource-ingestion"]
    CATALOG["«Component»\ncatalog"]
    INVENTORY["«Component»\ninventory / SBOM"]
    SIGNAL["«Component»\nsignal"]
    VULN["«Component»\nvuln-intel"]
    RELEASE["«Component»\nrelease-radar"]
    REG["«Component»\nreg-intel"]
    IMPACT["«Component»\nimpact-engine"]
    ALERT["«Component»\nalerting"]
    MCP["«Component»\nmcp-server\nlecture seule"]
    AI["«Component»\nai-gateway"]
    SCM["«adapter»\nscm-connector"]
    PROJ["«Component»\nproject-intel"]
    AUDIT["«Component»\naudit"]
    DB["«adapter»\nPostgreSQL"]

    API --> WORK
    API --> SIGNAL
    INGEST --> SIGNAL
    SIGNAL --> VULN
    SIGNAL --> RELEASE
    SIGNAL --> REG
    INVENTORY --> IMPACT
    VULN --> IMPACT
    RELEASE --> IMPACT
    IMPACT --> ALERT
    IMPACT --> MCP
    REG --> AI
    INGEST --> REG
    SCM --> REG
    CATALOG --> REG
    REG --> ALERT
    REG --> MCP
    REG --> DB
    IMPACT --> AI
    ALERT --> AI
    SIGNAL --> AI
    AI --> AUDIT
    SCM --> INVENTORY
    SCM --> PROJ
    INVENTORY --> PROJ
    API --> PROJ
    PROJ --> DB
    PROJ -.->|SyntheseIa, adaptateur non livré| AI
    WORK --> ALERT
    WORK --> MCP
    WORK --> DB
    SIGNAL --> DB
    INVENTORY --> DB
    IMPACT --> DB
    AUDIT --> DB
```

## 5.3 Responsabilités et frontières

| Composant | Responsabilité | Ne doit pas |
|---|---|---|
| API & Security | REST/OpenAPI, authn/authz, validation | contenir les règles métier |
| workspace | équipes, rôles, abonnements TEAM/PERSONAL, vues | confondre préférence de veille et autorisation |
| source-ingestion | collecte, idempotence, normalisation | connaître la présentation UI |
| catalog | technologies, standards, domaines métier | dépendre d’un fournisseur externe |
| inventory | projets, dépôts, SBOM, PURL | calculer seul l’impact sécurité |
| release-radar | calendrier public endoflife.date (copie locale, fraîcheur), échéances par technologie du référentiel (`io.argos.releaseradar`, fiche [`radar-standards`](../../specifications/radar-standards.md)) | décider d’un statut du référentiel ; utiliser la correspondance produit d’un autre espace |
| impact-engine | corrélation déterministe ; dette de version et projets touchés par une échéance ou un changement de standard (`RadarProjets`) | déléguer la décision au LLM |
| ai-gateway | point d'appel unique au modèle local Ollama : prompts versionnés, classes de données, plafond et quotas journaliers, sorties validées par schéma, mention IA, journal `ia_appel` via `audit` (`io.argos.aigateway`, fiche [`passerelle-ia`](../../specifications/passerelle-ia.md), ADR-0029 accepté) ; appelé par `alerting` (résumé d'alerte), `signal.web` (résumé d'actualité), `impact` (analyse complémentaire, désactivée par défaut) et `regintel` (résumé de fiche et domaine proposé) | exposer le fournisseur au domaine ; décider d'un impact ; recevoir une donnée CONFIDENTIAL ou RESTRICTED |
| mcp-server | huit outils MCP en lecture seule sur `POST /mcp` (Streamable HTTP : sans session en protocole 2026-07-28 ; session en mémoire, négociations limitées et fermée après 30 minutes d'inactivité, pour une version antérieure ; jeton exigé à chaque requête) ; appelant établi par `api.security`, limitation de débit, journal des appels (`audit`), contenu externe balisé et tronqué (`io.argos.mcp`, fiche [`serveur-mcp`](../../specifications/serveur-mcp.md)) | modifier le domaine ; dépendre de `api` hors `api.security` |
| reg-intel | collecte quotidienne des archives JORF de la DILA, qualification par le dictionnaire de l'espace puis proposition IA marquée, fiche en attente de validation, correction et validation par l'expert, cycle de vie, demande d'évolution GitHub ou GitLab, échéances (`io.argos.regintel`, fiche [`veille-reglementaire`](../../specifications/veille-reglementaire.md), ADR-0030 accepté) | produire un avis juridique ; diffuser une fiche non validée |
| alerting | règles d’alerte et digests ; rappels d’échéance et annonces de standard du radar (`RappelsRadar`) ; rappels des entrées en vigueur réglementaires (`RappelsReglementaires`) | accorder des droits d’accès |
| scm-connector | GitLab/API/webhooks/SBOM ; SBOM et manifestes de la branche principale remis à `inventory`, dépôt introuvable, jeton refusé et quota épuisé distingués d'un fichier absent (`RepositoryFiles`, `DepotIntrouvable`, `AccesRefuse`, `QuotaEpuise`) ; activité GitHub et GitLab en lecture seule, sans identité, quotas (`RepositoryActivity`) | injecter les payloads fournisseurs dans le domaine ; écrire dans un dépôt |
| project-intel | collecte planifiée et agrégation par jour de l'activité des dépôts, avancement des jalons avec dénominateur, synthèse IA marquée par le point d'extension `SyntheseIa`, purge à 13 mois (`io.argos.projectintel`, fiche [`activite-projets`](../../specifications/activite-projets.md)) | stocker ou restituer une identité de personne ; laisser l'IA calculer un indicateur |

## 5.4 Personnalisation

Une `Subscription` possède un `scope` :

- `TEAM` : socle d’équipe, éventuellement obligatoire ;
- `PERSONAL` : veille ajoutée volontairement par l’utilisateur.

Le scope de veille n’accorde **aucun droit supplémentaire** sur les projets. RBAC/RLS reste la source d’autorisation.

### 5.4.1 Profils de veille et mise en page (ADR-0023, ADR-0024 — acceptés)

Un utilisateur choisit sa **mise en page** et un ou plusieurs **profils de veille**. Ces préférences décident de ce qu’il voit parmi ce qu’il a le droit de voir, et de la façon dont il le voit ; elles ne décident jamais d’un droit.

| Composant | Responsabilité | Ne doit pas | Code |
|---|---|---|---|
| catalog | publier les manifestes de profils (format 1, validés au démarrage) et la liste des mises en page ; retirer un élément du catalogue | connaître les préférences d’un utilisateur | `io.argos.catalog.profil`, `io.argos.catalog.miseenpage` |
| workspace | enregistrer la mise en page et les profils actifs de chaque utilisateur ; au moins un profil actif | accorder un droit ; lire le catalogue | `io.argos.workspace.preference` |
| API & Security | confronter les préférences au catalogue (défaut `classique`, profils par défaut) et les exposer avec le catalogue | appliquer une règle d’affichage | `io.argos.api.preference`, `io.argos.api.catalogue` |
| IHM — couche contenu | appeler l’API et construire les modèles de vue avec leurs mentions obligatoires | dépendre d’une mise en page | `argos-ui/src/contenu`, `argos-ui/src/views` |
| IHM — couche mise en page | présenter les modèles de vue selon le style choisi | appeler l’API, filtrer un élément ou omettre une mention | `argos-ui/src/mises-en-page` |

Les préférences ne sont lues que par `api` et `mcp` (`PreferencesIsolationTest`) ; une mise en page n’importe ni le client API ni le reste de la couche contenu (`argos-ui/tests/architecture.test.mjs`). Les quatre mises en page reçoivent le même modèle de données (`argos-ui/src/mises-en-page/modele.ts`) ; le composant affiché est choisi par `MiseEnPage.vue` d’après la préférence. Leurs couleurs sont des jetons dont les contrastes sont contrôlés par les tests de l’IHM, leurs polices sont embarquées (ADR-0023 point 5) ; audit d’accessibilité : [rapport](../quality/audit-accessibilite-mises-en-page.md).

**D-15 — Type : C4 Component. Portée : préférences d’affichage et de veille, du serveur à l’IHM. Statut : implémenté pour le catalogue, les préférences et les couches de l’IHM ; restitution de la veille par profils à venir.**

```mermaid
flowchart LR
    subgraph UI["«Container» argos-ui"]
        CONTENU["«Component»\ncouche contenu\nappels API, modèles de vue,\nmentions obligatoires"]
        MEP["«Component»\ncouche mise en page\nclassique, journal,\nfantaisie, gazette"]
    end
    subgraph BACK["«Container» Quarkus Backend"]
        API["«Component»\nAPI & Security\n/api/v1/preferences\n/api/v1/catalogue"]
        CAT["«Component»\ncatalog\nmanifestes de profils,\nmises en page"]
        PREF["«Component»\nworkspace.preference\nmise en page, profils actifs"]
    end
    DB["«database»\nPostgreSQL\nworkspace_preference_utilisateur"]

    CONTENU -->|REST/OpenAPI| API
    CONTENU -->|modèles de vue| MEP
    API -->|lit les publiés| CAT
    API -->|lit et remplace| PREF
    PREF -->|SQL| DB
```

### 5.4.2 Profil de veille `artiste` (ADR-0024 — accepté)

Le profil `artiste` est un pack du catalogue (changement `ajouter-profil-artiste`, fiche [`profil-artiste`](../../specifications/profil-artiste.md)). Il ajoute des listes fermées d'objets suivis au manifeste (disciplines, territoires) et une stratégie de rapprochement sans identifiant de composant.

| Composant | Responsabilité | Ne doit pas | Code |
|---|---|---|---|
| catalog | publier le manifeste `artiste` : rubriques, listes de disciplines et de territoires, garde-fous `AUCUN_AVIS_ELIGIBILITE` et `SOURCES_VERIFIEES` | connaître les suivis d'un utilisateur | `io.argos.catalog.profil` (`artiste.json`) |
| workspace | enregistrer les objets suivis et les jours de rappel de chaque compte, sous RLS | accorder un droit ; lire le catalogue | `io.argos.workspace.suivi` |
| source-ingestion | lire une réponse de l'API data.culture.gouv.fr en texte brut ; récupérer par le collecteur de la veille web | récupérer une adresse lue dans un contenu | `io.argos.ingestion.culture`, `io.argos.ingestion.web` |
| signal | registre des sources du profil et de leurs conditions d'usage ; collecte des seules sources vérifiées ; opportunités | collecter une source non vérifiée | `io.argos.signal.opportunite` |
| impact-engine | stratégie discipline + territoire + date limite ; gabarit de résumé sans avis d'éligibilité | décider par l'IA ; lire les préférences | `io.argos.impact` (`StrategieDisciplineTerritoire`, `RapprochementOpportunites`, `ResumeOpportunite`) |
| alerting | rappels de date limite aux jours de rappel du compte | accorder un droit | `io.argos.alerting.RappelsOpportunites` |
| API & Security | vérifier que le profil est actif, confronter les suivis aux listes du pack, réserver le registre des sources à l'administrateur plateforme | appliquer la stratégie | `io.argos.api.artiste`, `io.argos.api.preference.RubriquesResource` |

Le paquet `workspace.suivi` n'emprunte au reste de `workspace` que `ContexteBase` et ne lit aucune donnée d'autorisation (`SuiviIsolationTest`). Aucune dépendance de module n'est ajoutée à §5.6.

### 5.4.3 Veille réglementaire (ADR-0030 — accepté)

La veille réglementaire du profil `expert-metier` (changement `suivre-veille-reglementaire`, fiche [`veille-reglementaire`](../../specifications/veille-reglementaire.md)) repose sur les domaines, le dictionnaire et les experts de chaque espace.

| Composant | Responsabilité | Ne doit pas | Code |
|---|---|---|---|
| source-ingestion | lire les archives JORF de la DILA : index, archives `.tar.gz` bornées, XML sans DTD ni entité externe | connaître les espaces ou les domaines | `io.argos.ingestion.legi` |
| catalog | domaines métier, règles de rattachement et experts de chaque espace, sous RLS ; lecture technique des dictionnaires | qualifier un texte | `io.argos.catalog.dictionnaire` |
| reg-intel | collecte et reprise des jours manqués, textes uniques par identifiant officiel, qualification, fiche, validation, cycle de vie, demande d'évolution, échéances ; point d'extension MCP `FichesReglementaires` | diffuser une fiche non validée ; produire un avis juridique | `io.argos.regintel` |
| scm-connector | reconnaître un ticket ou une demande de fusion GitHub ou GitLab et lire son état en lecture seule | écrire dans une forge | `io.argos.scm.DemandesEvolution` |
| alerting | rappels J-90, J-30, J-7 (réglables par espace) aux experts et aux membres au profil `expert-metier` actif | accorder un droit | `io.argos.alerting.RappelsReglementaires` |
| API & Security | réserver la configuration aux administrateurs de l'espace et l'expertise aux experts du domaine ; état et rejeu de la collecte pour l'administrateur plateforme | appliquer les règles de rattachement | `io.argos.api.reglementaire` |

Dépendances de module ajoutées (règles de `ModuleBoundariesTest`) : `regintel` → `ingestion`, `scm` et, dans `workspace`, `ContexteBase` et les erreurs communes seulement (règle `regintelNEmprunteQueRlsEtErreurs`) ; `alerting` → `regintel` ; le paquet `catalog.dictionnaire` rejoint `catalog.standard` parmi les seuls paquets de `catalog` autorisés à utiliser `ContexteBase`.

## 5.5 Règles de dépendance

1. le domaine ne dépend pas des SDK externes ;
2. les adapters dépendent des ports, jamais l’inverse ;
3. l’AI Gateway est le seul composant autorisé à appeler le fournisseur LLM ;
4. les contrôleurs Quarkus ne parlent pas directement à la base ;
5. les événements externes sont normalisés avant usage métier ;
6. les préférences personnelles sont séparées des politiques d’autorisation.

## 5.6 C4 Code

Le socle `argos-server/` matérialise chaque composant de §5.2 par un paquet Java `io.argos.<module>` (par exemple `impact` pour `impact-engine`, `ingestion` pour `source-ingestion`). Les dépendances autorisées entre modules sont vérifiées par `argos-server/src/test/java/io/argos/architecture/ModuleBoundariesTest.java`. Les paquets `ingestion`, `signal`, `vulnintel`, `scm`, `inventory`, `impact`, `workspace`, `audit` et `api` portent la chaîne sécurité du POC (changement `formaliser-chaine-securite-poc`) ainsi que les comptes, les espaces et les rôles (changement `gerer-comptes-et-acces`) ; `catalog`, `workspace.preference` et `api` portent le catalogue des profils et des mises en page et les préférences utilisateur (§5.4.1) ; `mcp` porte le serveur MCP (changement `exposer-serveur-mcp`), qui lit le référentiel des standards, le calendrier des sorties et les fiches réglementaires par les points d'extension `catalog.standard`, `releaseradar` et `regintel` ; `ingestion.web`, `signal.web` et `impact` portent la veille RSS/Atom et les pages surveillées (changement `collecter-veille-rss-web`, ADR-0028) ; `signal` n'emprunte à `workspace` que le contexte RLS `ContexteBase` et les erreurs communes `DonneeInvalide` et `RegleViolee` (règle `signalNEmprunteQueRlsEtErreurs`) ; `alerting` porte les alertes et les résumés (changement `diffuser-alertes-et-digests`) ; `impact` (`RechercheVeille`), `api.recherche` et `workspace.vue` portent la recherche plein texte, ses filtres et les vues enregistrées, et l'outil MCP `search_signals` emploie la même requête (changement `rechercher-et-filtrer`) ; `catalog.standard`, `releaseradar`, `impact` (`RadarProjets`) et `alerting` (`RappelsRadar`) portent le référentiel des standards et le radar des sorties (changement `construire-radar-et-standards`, §8.15) ; `catalog` n’emprunte à `workspace` que `ContexteBase` et les erreurs communes, dans les seuls paquets `catalog.standard` et `catalog.dictionnaire` (règle `catalogNEmprunteQueRlsEtErreurs`), et `impact` lit le référentiel de `catalog` pour la dette de version et les annonces ; `aigateway` porte la passerelle IA (changement `encadrer-appels-ia`), appelée par `alerting`, `signal.web`, `impact` et `regintel` par son seul contrat, l'adaptateur Ollama et les quotas restant internes (règle `seuleLaPasserelleConnaitLeFournisseur`) ; `projectintel`, `scm` et `api` portent l'activité des projets (changement `mesurer-activite-projets`), et `projectintel` n'emprunte à `workspace` que `ContexteBase` et `DonneeInvalide` (règle `projectintelNEmprunteQueRlsEtErreurs`) ; `catalog.profil`, `workspace.suivi`, `ingestion.culture`, `signal.opportunite`, `impact` et `alerting` (`RappelsOpportunites`) portent le profil de veille `artiste` (changement `ajouter-profil-artiste`, §5.4.2) ; `ingestion.legi`, `catalog.dictionnaire`, `regintel`, `scm` (`DemandesEvolution`), `alerting` (`RappelsReglementaires`) et `api.reglementaire` portent la veille réglementaire (changement `suivre-veille-reglementaire`, §5.4.3), et `regintel` n'emprunte à `workspace` que `ContexteBase` et les erreurs communes (règle `regintelNEmprunteQueRlsEtErreurs`) ; les autres paquets ne contiennent aucun code métier.

## 5.7 Modèle métier conceptuel (D-05)

**Type : UML classDiagram. Portée : concepts du domaine nommés en §4.2 (P2), §5.4 et au glossaire (§12). Statut : Hypothèse à valider.**

```mermaid
classDiagram
    class Workspace
    class Project
    class Repository
    class Source
    class IntelligenceItem
    class SecuritySignal
    class ReleaseSignal
    class ProjectActivity
    class ProjectImpact
    class Subscription {
        scope : TEAM ou PERSONAL
    }

    Workspace "1" o-- "0..*" Project : regroupe
    Workspace "1" o-- "0..*" Source : regroupe
    Project "1" -- "0..*" Repository : associé à
    Source "1" --> "0..*" IntelligenceItem : produit
    IntelligenceItem <|-- SecuritySignal
    IntelligenceItem <|-- ReleaseSignal
    Repository "1" --> "0..*" ProjectActivity : émet
    ProjectImpact "0..*" --> "1" IntelligenceItem : élément source
    ProjectImpact "0..*" --> "1" Project : projet affecté
```

Le diagramme ne représente que les concepts et relations écrits dans la documentation. Points non établis, à valider :

- cardinalité `Project` ↔ `Repository` : le glossaire laisse ouverte la question « projet métier, dépôt ou agrégat de plusieurs dépôts » ;
- spécialisation `SecuritySignal` / `ReleaseSignal` de `IntelligenceItem` : §4.2 les cite comme concepts distincts, la généralisation est une hypothèse de modélisation ;
- `Source` rattachée à un `Workspace` selon le glossaire, alors qu’ADR-0016 indique que les signaux sont globaux ;
- `Subscription` n’est reliée à aucune entité d’autorisation (§5.4) ; son propriétaire (équipe ou utilisateur) est détaillé dans la fiche [`abonnements-veille`](../../specifications/abonnements-veille.md).

Les modèles détaillés par capacité sont maintenus dans les fiches de [`docs/specifications/`](../../specifications/README.md).
