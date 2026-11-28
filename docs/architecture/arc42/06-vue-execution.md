# 6. Vue d’exécution

Cette section décrit des scénarios dynamiques. Les participants des diagrammes portent les noms des composants de la section 5 ; une tâche planifiée figure comme un appel du module qui la porte (`quarkus-scheduler`, ADR-0031).

Les vues métier des fiches de [`docs/specifications/`](../../specifications/README.md) emploient aussi les noms de la section 5.

## 6.1 Scénario nominal — signal technologique vers impact projet

**Type : UML sequence diagram. Portée : traitement d’un `IntelligenceItem`.**

```mermaid
sequenceDiagram
    actor User as Utilisateur ARGOS
    participant API as API & Security
    participant Ingestion as source-ingestion
    participant Tech as signal / vuln-intel / release-radar
    participant Corr as impact-engine
    participant Core as inventory / workspace
    participant AI as ai-gateway
    participant Persist as PostgreSQL
    participant Alert as alerting

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
    Corr--)Alert: publier les impacts (asynchrone, ADR-0039)
    Alert->>Persist: enregistrer alerte/digest éventuel
    User->>API: consulter impacts
    API->>Persist: lire impacts autorisés
    Persist-->>API: retourner résultats + provenance
    API-->>User: afficher impact et sources
```

### Invariants

- aucune synthèse ne remplace la source ;
- toute analyse LLM doit être associée à la version du prompt/modèle ;
- la politique de confidentialité doit être évaluée avant l’appel à `ai-gateway` ;
- l’opération doit être idempotente sur la source + l’identifiant externe.
- l’enrichissement par les sources secondaires et l’évaluation des alertes ne s’exécutent ni dans une transaction ouverte ni dans le fil d’une requête (file d’enrichissement, observateur asynchrone : ADR-0039, arc42 §8).

### 6.1.1 Alerte immédiate et résumé périodique

**Type : UML sequence diagram. Portée : comportement implémenté (capacité [`alertes-digests`](../../specifications/alertes-digests.md)).**

```mermaid
sequenceDiagram
    participant Corr as impact-engine
    participant Alert as alerting
    participant Core as workspace
    participant IA as Point d'extension ResumeIa
    participant Persist as PostgreSQL
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

L’indisponibilité de l’IA ou du serveur d’e-mails ne bloque ni les impacts ni l’enregistrement des alertes ; une alerte en échec d’envoi reste consultable dans Argos. L’évaluation est synchrone dans le fil de la corrélation : chaque signal qui produit une alerte attend au plus le délai du résumé IA (10 s) puis, par alerte e-mail, le délai d’envoi SMTP (`quarkus.mailer.timeout`, 10 s). Aucun verrou ni aucune transaction n’est tenu pendant l’envoi ; chaque tentative est comptée, quelle que soit la cause de l’échec. Le résumé passe par l’interface `ResumeIa`, implémentée par `ResumeIaPasserelle` sur le contrat d’`ai-gateway` (changement `encadrer-appels-ia`) : appel imputé au premier espace concerné dont le quota n’est pas épuisé, alerte envoyée sans résumé si le plafond ou les quotas sont atteints, si Ollama est injoignable ou si la sortie est rejetée.

### 6.1.2 Alerte sur le poste, clic et reprise après coupure

**Type : UML sequence diagram. Portée : comportement implémenté (capacité [`notifications-poste`](../../specifications/notifications-poste.md)).**

```mermaid
sequenceDiagram
    participant Alert as alerting
    participant UI as Client desktop (IHM, couche contenu)
    participant Main as Client desktop (processus principal)
    actor U as Utilisateur

    loop toutes les 30 s, fenêtre visible ou masquée
        UI->>Alert: GET /api/v1/alertes?canal=DESKTOP&etat=EN_ATTENTE
        Alert-->>UI: alertes de l'appelant (RLS)
        opt alerte immédiate pas encore traitée sur ce poste
            UI->>Main: identifiant, gravité, nombre de projets
            Main-->>U: notification système (titre générique) si le poste et le système l'autorisent
        end
        UI->>Alert: POST /api/v1/alertes/{id}/remise
        UI->>Main: compteur d'alertes ouvertes
    end
    U->>Main: clic sur la notification
    Main->>UI: fenêtre au premier plan sur la vue de l'alerte
    Note over UI,Alert: coupure : « Hors ligne », nouvelle tentative après 5 s puis attente doublée (5 min au plus), puis au retour les alertes restées EN_ATTENTE sont notifiées et leur nombre affiché
```

Une alerte non confirmée reste `EN_ATTENTE` côté serveur : elle revient à la lecture suivante, sans perte ; les identifiants déjà traités, gardés dans la mémoire locale de l'IHM, évitent une seconde notification, y compris après un rechargement de la fenêtre. La fenêtre masquée garde ses minuteries (`backgroundThrottling` désactivé).

## 6.2 Scénario nominal — activité GitHub/GitLab vers Project Intelligence

**Type : UML sequence diagram. Portée : collecte planifiée de l’activité d’un projet relié à un dépôt, puis consultation (changement `mesurer-activite-projets`).**

```mermaid
sequenceDiagram
    actor User as Utilisateur ARGOS
    participant Proj as project-intel
    participant Core as inventory
    participant Ext as scm-connector
    participant Forge as «Software System» GitHub/GitLab
    participant API as API & Security
    participant Persist as PostgreSQL

    Proj->>Proj: tâche planifiée horaire : chercher les projets échus
    Proj->>Core: projets reliés à un dépôt
    Core-->>Proj: projets et références de dépôt
    Proj->>Ext: activité depuis le début de la fenêtre (30 jours)
    Ext->>Forge: requêtes GET en lecture seule, pagination bornée
    alt quota épuisé (429 ou 403 avec x-ratelimit-remaining à 0)
        Forge-->>Ext: date de réinitialisation
        Ext-->>Proj: quota épuisé
        Proj->>Persist: collecte REPORTEE_QUOTA jusqu’à la réinitialisation, données conservées
    else réponse lue
        Forge-->>Ext: tickets, demandes de fusion, jalons, pipelines, versions
        Ext-->>Proj: dates et états, sans identité de personne
        Proj->>Proj: agréger par jour et par type
        Proj->>Persist: remplacer les compteurs de la fenêtre et les jalons, collecte A_JOUR
    end
    User->>API: GET /api/v1/projects/{id}/activite?jours=30
    API->>Proj: membre de l’espace du projet
    Proj->>Persist: lire sous argos_app (RLS)
    Persist-->>Proj: compteurs, jalons, fraîcheur
    Proj-->>API: indicateurs, avancement « fermés sur total », date de dernière mise à jour
```

La collecte remplace les compteurs des jours de sa fenêtre : rejouée, elle produit le même résultat. Aucune donnée d’identité n’est conservée ; l’historique est supprimé au-delà de 13 mois par une tâche quotidienne.

## 6.3 Scénario d’erreur — API externe indisponible

**Type : UML sequence diagram. Portée : synchronisation d’une source lancée par l’exploitant (`POST /api/v1/ops/sources/{id}/synchronizations`) ou par la tâche planifiée `signal-synchronisation-sources` du module `signal` (toutes les heures par défaut, `argos.ingestion.planification.cadence` ; COL-09, changement `automatiser-chaine-veille`, 2026-11-19 ; ADR-0031).**

```mermaid
sequenceDiagram
    participant Ops as Exploitant
    participant API as API & Security
    participant Plan as signal (SynchronisationPlanifiee)
    participant Ingestion as source-ingestion
    participant Ext as «Software System» Source externe
    participant Persist as PostgreSQL

    alt lancement par l’exploitant
        Ops->>API: lancer la synchronisation d’une source
        API->>Ingestion: synchroniser la source
    else échéance horaire (quarkus-scheduler)
        Plan->>Ingestion: synchroniser chaque source enregistrée
    end
    Ingestion->>Persist: réserver le traitement (index sync_run_one_running)
    opt traitement déjà en cours pour cette source
        Persist-->>Ingestion: réservation refusée
        Ingestion-->>Plan: source sautée (COL-09-S2)
    end
    Ingestion->>Ext: demander delta depuis dernier curseur
    Ext--xIngestion: timeout / 429 / 5xx
    Ingestion->>Persist: enregistrer tentative et erreur

    loop retry borné avec backoff
        Ingestion->>Ingestion: attendre le délai, puis rejouer la synchronisation
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
- rendre visible la source dégradée dans l’observabilité ;
- à chaque échéance planifiée, rafraîchir les index d’enrichissement (CERT-FR, KEV) sans bloquer la synchronisation, retenter une source `DEGRADED` et ne jamais lancer une seconde synchronisation d’une source déjà en cours (COL-09).

## 6.4 Scénario d’exploitation — redémarrage/reprise

**Type : UML sequence diagram. Portée : reprise du conteneur `Quarkus Backend`.**

```mermaid
sequenceDiagram
    participant Ops as Exploitant
    participant Back as Quarkus Backend
    participant Ingestion as source-ingestion
    participant Persist as PostgreSQL
    participant Ext as «Software System» Sources externes

    Ops->>Back: démarrer / redémarrer le service
    Back->>Persist: vérifier connectivité et migrations
    Persist-->>Back: état base OK
    Back->>Ingestion: notifier le démarrage
    Ingestion->>Persist: rechercher traitements RUNNING expirés
    Persist-->>Ingestion: retourner traitements à reprendre
    Ingestion->>Persist: reclasser en RETRYABLE
    Back-->>Ops: exposer état READY
    Ops->>Back: lancer la synchronisation d’une source
    Back->>Ingestion: synchroniser la source
    Ingestion->>Persist: reprendre le traitement RETRYABLE
    Ingestion->>Ext: reprendre depuis le curseur persisté
    Ext-->>Ingestion: retourner deltas
    Ingestion->>Persist: enregistrer reprise et nouveau curseur
```

## 6.5 Hypothèses et validation

### Hypothèses à valider

- présence d’un scheduler interne au backend ;
- persistance des curseurs et statuts de job dans PostgreSQL ;
- retry sans broker externe au MVP.

### Preuves nécessaires

- tests d’intégration avec GitHub, GitLab et les flux RSS/Atom ;
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

## 6.6 Scénario nominal — texte officiel vers fiche validée

**Type : UML sequence diagram. Portée : collecte quotidienne du Journal officiel, qualification, fiche, validation par l’expert et rappel (changement `suivre-veille-reglementaire`, ADR-0030 accepté).**

```mermaid
sequenceDiagram
    participant Ingestion as source-ingestion
    participant DILA as «Software System» DILA (archives JORF)
    participant Reg as reg-intel
    participant Cat as catalog
    participant AI as ai-gateway
    actor Expert as Expert métier
    participant API as API & Security
    participant Alert as alerting
    participant Persist as PostgreSQL

    Reg->>Reg: tâche planifiée 06:30 : collecte des 3 derniers jours et des jours manqués
    Reg->>Ingestion: archives non encore lues de chaque jour
    Ingestion->>DILA: GET index et archives (sans redirection, tailles bornées)
    alt source indisponible
        DILA--xIngestion: erreur HTTP ou délai dépassé
        Reg->>Persist: jour EN_ECHEC, source DEGRADE, repris à la collecte suivante, abandonné après 5 échecs de ses archives
    else archives lues
        DILA-->>Ingestion: JORF_AAAAMMJJ-HHMMSS.tar.gz
        Ingestion-->>Reg: textes lus (XML sans DTD ni entité externe)
        Reg->>Persist: texte enregistré une fois par identifiant officiel, dates extraites
        Reg->>Cat: règles du dictionnaire de chaque espace
        opt aucune règle ne s’applique
            Reg->>AI: domaine proposé (facultatif)
        end
        Reg->>AI: résumé de la fiche (facultatif)
        Reg->>Persist: fiche EN_ATTENTE_VALIDATION, historique
    end
    Expert->>API: corrige puis valide la fiche
    API->>Reg: expert d’un des domaines
    Reg->>Persist: sous argos_app (RLS) : fiche VALIDEE, texte EN_ANALYSE, historique
    Alert->>Alert: tâche planifiée 07:15 : rappels J-90, J-30, J-7
    Alert->>Reg: échéances des fiches validées
    Alert->>Persist: rappel par destinataire, clé d’idempotence
```

Une fiche en attente n’est restituée qu’aux experts de ses domaines, par la base elle-même ; l’API REST, le serveur MCP et les rappels ne voient que des fiches validées pour les autres utilisateurs. Sans IA, la fiche est préparée sans résumé et l’expert le rédige avant de valider.

## 6.7 Scénario nominal — import quotidien des inventaires et recorrélation

**Type : UML sequence diagram. Portée : import planifié des inventaires depuis les dépôts GitHub/GitLab de chaque projet, puis recorrélation (changement `automatiser-chaine-veille`, volet B ; décisions J3, J4 et J6 du porteur du projet, 2026-11-19).**

```mermaid
sequenceDiagram
    participant Core as inventory
    participant Ext as scm-connector
    participant Forge as «Software System» GitHub/GitLab
    participant Persist as PostgreSQL
    participant Impact as impact-engine

    Core->>Core: tâche planifiée quotidienne (03:00, argos.inventory.import.cron)
    Core->>Persist: projets au marqueur de recorrélation resté posé
    Core-)Impact: relancer leur recorrélation
    loop pour chaque projet ayant au moins un dépôt
        loop pour chaque dépôt du projet
            Core->>Ext: SBOM, sinon manifestes, de la branche principale
            Ext->>Forge: lecture seule (jeton en configuration)
            Forge-->>Ext: document ou erreur
            Ext-->>Core: octets et format reconnu
            opt refus, erreur de forge ou quota épuisé
                Core->>Persist: échec enregistré avec le dépôt, sans jeton
            end
        end
        opt au moins un dépôt importé
            Core->>Persist: verrou du projet, occurrences des dépôts importés remplacées, union recalculée
            Core->>Persist: marqueur de recorrélation posé
            Core-)Impact: InventoryReplaced (après validation)
            Impact->>Persist: impacts recalculés pour le projet, marqueur effacé
        end
    end
```

Aucun flux n'entre depuis une forge ou une CI : seule la tâche planifiée, ou un import manuel, déclenche un import. L'import manuel prend le même verrou consultatif par projet, si bien que deux imports d'un même projet s'exécutent l'un après l'autre. Un dépôt en échec garde son dernier import réussi ; une recorrélation en échec laisse le marqueur posé, et le passage suivant la relance. La tâche est désactivée dans le profil de test et dans la pile E2E.

## 6.8 Scénario nominal — import manuel d'une SBOM par l'API

**Type : UML sequence diagram. Portée : import manuel d'une SBOM CycloneDX JSON depuis la vue d'inventaire de l'IHM (INV-13, changement `alimenter-inventaire-et-completer-ihm`, DC-1 de sa conception ; décision du porteur du projet, 2026-11-26).**

```mermaid
sequenceDiagram
    actor Contributeur as Contributeur de l'espace
    participant UI as Vue.js 3 SPA (vue d'inventaire)
    participant API as API & Security (InventoryResource, Access)
    participant Core as inventory (InventoryService)
    participant Persist as PostgreSQL
    participant Impact as impact-engine

    Contributeur->>UI: choisit un fichier SBOM
    UI->>API: POST /api/v1/projects/{id}/inventory/sbom (JSON, jeton)
    API->>API: contributeur ou administrateur de l'espace du projet ?
    alt projet inconnu ou appelant non membre
        API-->>UI: 404 (refus journalisé NON_MEMBRE pour un non-membre)
    else lecteur
        API-->>UI: 403 (refus journalisé ROLE_INSUFFISANT)
    else autorisé
        API->>Core: importSbom(projet, document, "import manuel")
        Core->>Core: contrôle CycloneDX 1.5 ou 1.6, taille (20 Mio)
        alt document refusé
            Core->>Persist: échec enregistré, inventaire en place conservé
            Core-->>API: REJECTED et motif
        else document conforme
            Core->>Persist: verrou du projet, inventaire du dépôt principal remplacé
            Core-)Impact: InventoryReplaced (après validation)
            Core-->>API: IMPORTED, origine SBOM, nombre de composants
        end
        API-->>UI: 200 et issue de l'import
    end
```

L'API contrôle le rôle, le module `inventory` écrit : le traitement est celui de l'import planifié (§6.7), avec le même verrou par projet et la même recorrélation après validation (INV-08, INV-09). Un refus de rôle n'écrit rien. L'import de manifestes (`…/inventory/manifests`), la relance depuis les dépôts (`…/inventory/refresh`, 409 `PROJET_SANS_DEPOT` sans dépôt) et la collecte immédiate de l'activité (`…/activite/collecte`) suivent la même séquence de contrôle ; la création d'un projet et le rattachement ou le détachement d'un dépôt exigent le rôle d'administrateur de l'espace (INV-12). Un corps HTTP au-delà de 24 Mio (`quarkus.http.limits.max-body-size`) est refusé par le serveur HTTP (413) avant l'API.

## 6.9 Scénario nominal — suggestions de l'IA sur le référentiel des standards

**Type : UML sequence diagram. Portée : production des suggestions `HOLD` par la tâche quotidienne du radar (RAD-08, changement `alimenter-inventaire-et-completer-ihm`, DC-2 de sa conception ; ADR-0029).**

```mermaid
sequenceDiagram
    participant Alert as alerting (RappelsRadar)
    participant Impact as impact-engine (RadarProjets, SuggestionsRadar)
    participant Release as release-radar (CalendrierSorties)
    participant AI as ai-gateway
    participant Cat as catalog (ReferentielEspace)

    Alert->>Alert: tâche planifiée argos-radar-quotidien (06:00)
    Alert->>Impact: actualiser (cycles de vie, dettes de version)
    Alert->>Impact: produire les suggestions du jour
    loop technologie recommandée, reliée au calendrier, hors HOLD
        Impact->>Release: fin de support de la version recommandée
        opt fin de support atteinte et aucune suggestion en attente
            Impact->>AI: prompt suggestion-standard (données publiques)
            alt sortie validée, avis 1
                Impact->>Cat: suggerer(espace, clé, HOLD, version, justification)
            else passerelle indisponible, sortie refusée ou avis 2
                Impact->>Impact: journaliser, technologie suivante
            end
        end
    end
    Alert->>Alert: rappels d'échéance
```

La suggestion est enregistrée `EN_ATTENTE` et n'a d'effet qu'acceptée par un administrateur de l'espace, depuis l'API ou la vue radar de l'IHM (RAD-02, RAD-09) ; le référentiel n'est jamais modifié par la tâche. Une erreur d'accès aux données d'un espace est journalisée et n'arrête que cet espace ; une erreur imprévue est retenue dans le bilan de la tâche et relancée en fin de passage (§8.5).

