# 6. Vue d’exécution

Cette section décrit des scénarios dynamiques. Ses participants portent des noms de responsabilités qui ne coïncident pas tous avec les composants de la section 5 ; la table suivante donne la correspondance.

| Participant (§6) | Composant(s) de la section 5 | Remarque |
|---|---|---|
| `API & Security` | `API & Security` | identique |
| `Ingestion` | `source-ingestion` | — |
| `Technology Intelligence` | `signal`, `vuln-intel`, `release-radar` | regroupement de plusieurs composants |
| `Impact Correlation` | `impact-engine` | — |
| `Workspace & Project Core` | `workspace`, `inventory` | regroupement |
| `AI Gateway` | `ai-gateway` | — |
| `Persistence Adapter` | adaptateur `PostgreSQL` | — |
| `Alerting & Digest` | `alerting` | — |
| `External Adapters` | `scm-connector` et adaptateurs des sources | seul `scm-connector` est nommé en §5 |
| `Project Intelligence` | aucun | composant absent de §5 (Project Intelligence complet en phase 3, baseline v2.3 §2.4) |
| `Relais SMTP (Synology)` | aucun | système externe : serveur d’e-mails du NAS (§6.1.1) |
| `Client desktop` | aucun | conteneur `argos-desktop` (ADR-0022), hors du backend (§6.1.1) |
| `Scheduler & Jobs` | aucun | composant absent de §5 ; tâches planifiées Quarkus Scheduler dans les modules qui les portent (`alerting` §6.1.1, collecte de la veille web) ; aucun conteneur séparé (§6.5) |

Les vues métier des fiches de [`docs/specifications/`](../../specifications/README.md) emploient les noms de la section 5.

## 6.1 Scénario nominal — signal technologique vers impact projet

**Type : UML sequence diagram. Portée : traitement d’un `IntelligenceItem`.**

```mermaid
sequenceDiagram
    actor User as Utilisateur ARGOS
    participant API as API & Security
    participant Ingestion as Ingestion
    participant Tech as Technology Intelligence
    participant Corr as Impact Correlation
    participant Core as Workspace & Project Core
    participant AI as AI Gateway
    participant Persist as Persistence Adapter
    participant Alert as Alerting & Digest

    Ingestion->>Persist: enregistrer IntelligenceItem normalisé
    Ingestion->>Tech: soumettre l’item à classification
    Tech->>Persist: charger technologies référencées
    Tech-->>Corr: publier signal enrichi
    Corr->>Core: rechercher projets utilisant les technologies
    Core->>Persist: charger projets et contexte
    Persist-->>Core: retourner projets concernés
    Core-->>Corr: retourner contexte projet

    alt analyse IA autorisée et nécessaire
        Corr->>AI: analyser impact avec contexte contrôlé
        AI->>Persist: charger politique/budget/version prompt
        AI-->>Corr: retourner analyse structurée validée
    else analyse déterministe suffisante
        Corr-->>Corr: calculer impact sans LLM
    end

    Corr->>Persist: enregistrer ProjectImpact et provenance
    Corr->>Alert: évaluer règles d’alerte
    Alert->>Persist: enregistrer alerte/digest éventuel
    User->>API: consulter impacts
    API->>Persist: lire impacts autorisés
    Persist-->>API: retourner résultats + provenance
    API-->>User: afficher impact et sources
```

### Invariants

- aucune synthèse ne remplace la source ;
- toute analyse LLM doit être associée à la version du prompt/modèle ;
- la politique de confidentialité doit être évaluée avant l’appel à `AI Gateway` ;
- l’opération doit être idempotente sur la source + l’identifiant externe.

### 6.1.1 Alerte immédiate et résumé périodique

**Type : UML sequence diagram. Portée : comportement implémenté (capacité [`alertes-digests`](../../specifications/alertes-digests.md)).**

```mermaid
sequenceDiagram
    participant Corr as Impact Correlation
    participant Alert as Alerting & Digest
    participant Core as Workspace & Project Core
    participant IA as Point d'extension ResumeIa
    participant Persist as Persistence Adapter
    participant Mail as Relais SMTP (Synology)
    actor Desk as Client desktop

    Corr->>Alert: ImpactsRecorded (après validation des impacts)
    Alert->>Core: membres des espaces touchés et leurs abonnements actifs
    Alert->>Persist: relire les impacts sous le compte du destinataire (RLS)
    alt règle immédiate satisfaite et impact exact ou probable
        opt implémentation de ResumeIa présente (aucune livrée : état INDISPONIBLE)
            Alert->>IA: résumé du signal (délai de 10 s, demande expirée annulée)
        end
        Alert->>Persist: alerte unique (signal, destinataire, canal)
        Alert->>Mail: e-mail (délai d'envoi de 10 s, nouvelles tentatives bornées, puis EN_ECHEC)
        Desk->>Alert: GET /api/v1/alertes puis POST /remise
    else autre élément concerné
        Alert->>Persist: élément du prochain résumé
    end
    Note over Alert: 08:00 quotidien, lundi 08:00 hebdomadaire (Europe/Paris)
    Alert->>Persist: composer les résumés non vides, hors éléments déjà alertés
    Alert->>Mail: résumé par e-mail
    Desk->>Alert: résumé sur le canal DESKTOP : GET /api/v1/alertes puis POST /remise
```

L’indisponibilité de l’IA ou du serveur d’e-mails ne bloque ni les impacts ni l’enregistrement des alertes ; une alerte en échec d’envoi reste consultable dans Argos. L’évaluation est synchrone dans le fil de la corrélation : chaque signal qui produit une alerte attend au plus le délai du résumé IA (10 s) puis, par alerte e-mail, le délai d’envoi SMTP (`quarkus.mailer.timeout`, 10 s). Aucun verrou ni aucune transaction n’est tenu pendant l’envoi ; chaque tentative est comptée, quelle que soit la cause de l’échec. Le module `alerting` ne dépend pas d’`ai-gateway` (`ModuleBoundariesTest`) : le résumé passe par l’interface `ResumeIa`, sans implémentation livrée.

## 6.2 Scénario nominal — activité GitHub/GitLab vers Project Intelligence

**Type : UML sequence diagram. Portée : réception d’un webhook projet.**

```mermaid
sequenceDiagram
    participant Ext as «Software System» GitHub/GitLab
    participant API as API & Security
    participant Ingestion as Ingestion
    participant Proj as Project Intelligence
    participant Core as Workspace & Project Core
    participant Persist as Persistence Adapter
    participant Alert as Alerting & Digest

    Ext->>API: webhook HTTPS signé
    API->>API: vérifier signature, horodatage et taille
    API->>Ingestion: transmettre événement accepté
    Ingestion->>Persist: vérifier idempotence
    Persist-->>Ingestion: événement inconnu
    Ingestion->>Ingestion: normaliser en ProjectActivity
    Ingestion->>Persist: enregistrer payload/provenance
    Ingestion->>Proj: traiter ProjectActivity
    Proj->>Core: résoudre Repository vers Project
    Core->>Persist: charger configuration projet
    Persist-->>Core: retourner projet
    Core-->>Proj: retourner contexte
    Proj->>Proj: recalculer métriques factuelles concernées
    Proj->>Persist: enregistrer métriques et signaux
    Proj->>Alert: évaluer risques/blocages configurés
```

## 6.3 Scénario d’erreur — API externe indisponible

**Type : UML sequence diagram. Portée : synchronisation planifiée.**

```mermaid
sequenceDiagram
    participant Jobs as Scheduler & Jobs
    participant Ingestion as Ingestion
    participant Ext as External Adapters
    participant Persist as Persistence Adapter

    Jobs->>Ingestion: lancer synchronisation source
    Ingestion->>Ext: demander delta depuis dernier curseur
    Ext--xIngestion: timeout / 429 / 5xx
    Ingestion->>Persist: enregistrer tentative et erreur

    loop retry borné avec backoff
        Jobs->>Ingestion: rejouer la synchronisation
        Ingestion->>Ext: reprendre depuis curseur stable
        alt succès
            Ext-->>Ingestion: retourner événements
            Ingestion->>Persist: stocker événements + nouveau curseur
        else nouvel échec
            Ingestion->>Persist: incrémenter compteur d’échec
        end
    end

    opt seuil d’échec atteint
        Ingestion->>Persist: placer source en état DEGRADED
        Ingestion->>Persist: créer événement d’exploitation
    end
```

### Règles proposées

- ne jamais avancer le curseur avant persistance durable ;
- distinguer erreurs transitoires et permanentes ;
- respecter `Retry-After` et les rate limits ;
- ne pas boucler indéfiniment ;
- rendre visible la source dégradée dans l’observabilité.

## 6.4 Scénario d’exploitation — redémarrage/reprise

**Type : UML sequence diagram. Portée : reprise du `ARGOS Backend`.**

```mermaid
sequenceDiagram
    participant Ops as Exploitant
    participant Back as ARGOS Backend
    participant Jobs as Scheduler & Jobs
    participant Persist as Persistence Adapter
    participant Ext as External Adapters

    Ops->>Back: démarrer / redémarrer le service
    Back->>Persist: vérifier connectivité et migrations
    Persist-->>Back: état base OK
    Back->>Jobs: initialiser planificateur
    Jobs->>Persist: rechercher traitements RUNNING expirés
    Persist-->>Jobs: retourner traitements à reprendre
    Jobs->>Jobs: reclassifier en RETRYABLE
    Jobs->>Ext: reprendre synchronisations depuis curseurs persistés
    Ext-->>Jobs: retourner deltas
    Jobs->>Persist: enregistrer reprise et nouveaux curseurs
    Back-->>Ops: exposer état READY
```

## 6.5 Hypothèses et validation

### Hypothèses à valider

- présence d’un scheduler interne au backend ;
- persistance des curseurs et statuts de job dans PostgreSQL ;
- retry sans broker externe au MVP.

### Preuves nécessaires

- tests d’intégration avec GitHub/GitLab/FreshRSS ;
- tests d’idempotence ;
- test de reprise après arrêt forcé ;
- métriques de retry et backlog ;
- simulation 429/5xx/timeouts.

### Risques

- doubles traitements si clé d’idempotence mal définie ;
- perte d’événement si le curseur est avancé trop tôt ;
- tempête de retry ;
- webhooks reçus dans le désordre.

### Fichiers source concernés

- futurs services d’ingestion et jobs ;
- futurs adapters ;
- futures migrations ;
- futurs tests d’intégration et de résilience.
