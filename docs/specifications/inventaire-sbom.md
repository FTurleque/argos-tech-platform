# Fiche capacité : `inventaire-sbom`

| Élément | Valeur |
|---|---|
| Préfixe d'exigences | `INV` |
| Spec de référence | `/openspec/specs/inventaire-sbom/spec.md` — absente : capacité non livrée |
| Changement en cours | [`formaliser-chaine-securite-poc`](/openspec/changes/formaliser-chaine-securite-poc/specs/inventaire-sbom/spec.md) — proposé, non archivé |
| Statut de la capacité | **proposée**, implémentée dans `argos-server` (changement non archivé) : paquets `io.argos.scm`, `io.argos.inventory` |
| Composants (arc42 §5) | `inventory`, `scm-connector`, adaptateur PostgreSQL |

Tout le contenu de cette fiche porte le statut [PROPOSITION] (changement `formaliser-chaine-securite-poc`) sauf mention contraire.

## Objectif

Répondre de façon fiable à la question « quels composants, et dans quelles versions, chaque projet embarque-t-il ? », dépendances transitives comprises, pour permettre la corrélation déterministe. Source : ADR-0011 (contexte).

## Acteurs

| Acteur | Interaction | Statut |
|---|---|---|
| CI de la branche principale des projets suivis | produit la SBOM CycloneDX | [PROPOSITION] — ADR-0011 |
| GitLab / GitHub (via `scm-connector`) | donne accès aux SBOM et manifestes | [PROPOSITION] — arc42 §5.3 ; baseline v2.3 §2.4 |
| Utilisateur technique, Tech Lead | consultent l'inventaire et sa fraîcheur | [PROPOSITION] — arc42 §1.3 |

## Périmètre et exclusions

- **Inclus** : import de la SBOM de la branche principale, PURL et versions, dépendances transitives, âge de l'inventaire, repli sur les manifestes, rejet d'une SBOM non conforme.
- **Exclu** : génération de la SBOM (responsabilité de la CI des projets), Dependency-Track (ADR-0012, optionnel), analyse de licences, autres branches que la branche principale.

## Préconditions et déclencheurs

- Le projet est suivi et rattaché à au moins un dépôt (glossaire « Repository »). La relation projet ↔ dépôts n'est pas tranchée [QUESTION — glossaire, termes ambigus].
- Déclencheur de l'import : non documenté (webhook de pipeline, interrogation périodique ou import manuel) [QUESTION Q-INV-1].

## Entrées, sorties et validations

| Entrée / sortie | Contenu | Validation ou contrainte | Statut |
|---|---|---|---|
| Entrée : SBOM CycloneDX | composants, PURL, versions, graphe de dépendances, date | document CycloneDX JSON exploitable, schéma 1.5 ou 1.6 (décision du 2026-11-16, Q-7) | [PROPOSITION] |
| Entrée : manifeste de dépendances | dépendances déclarées | utilisé seulement sans SBOM | [PROPOSITION] |
| Sortie : inventaire du projet | composants, PURL, version, direct/transitif, origine (SBOM ou manifeste), date de la source | un inventaire de référence par projet | [PROPOSITION] |
| Sortie : échec d'import | motif | consultable | [PROPOSITION] |
| Sortie : composants signalés à l'import | nombre total des composants écartés faute de PURL ; nom, groupe, version et motif (`SANS_PURL` : aucun PURL déclaré ; `PURL_INVALIDE` : PURL illisible) des 100 premiers | restitués avec l'inventaire (`ignoredCount`, `ignoredComponents` de `GET /api/v1/projects/{projectId}/inventory`) et affichés dans la vue d'inventaire d'`argos-ui`, web et desktop (section « Composants ignorés ») ; remplacés à chaque import | [DÉCISION] porteur du projet, 2026-11-18 (D6) et 2026-11-19 (E4) |
| Sortie : projets visibles | identifiant, espace (identifiant et nom) et nom de chaque projet des espaces de l'utilisateur, par nom de projet, nom d'espace puis identifiant ; nombre total | `GET /api/v1/projects`, liste paginée (`page` à partir de 0, `size` de 1 à 200, défaut 50, ramenés aux bornes) ; liste de choix « Projet » de la vue d'inventaire d'`argos-ui` | [DÉCISION] porteur du projet, 2026-11-19 (F1) ; modalités [PROPOSITION] |
| Sortie : composants inventoriés paginés | nom (clé du paquet sans `pkg:<type>/`), version, PURL, dépendance directe, transitive ou inconnue, par nom, version puis identifiant ; nombre total | `GET /api/v1/projects/{projectId}/inventory/components`, mêmes bornes ; section « Composants inventoriés » de la vue d'inventaire, pages de 20 | [DÉCISION] porteur du projet, 2026-11-19 (F5) ; modalités [PROPOSITION] |
| Entrée : SBOM récupérée sur la forge | octets et format reconnu à l'en-tête : CycloneDX JSON, CycloneDX XML, SPDX JSON (dont l'export du graphe de dépendances GitHub), SPDX étiquette-valeur, inconnu | seule une SBOM CycloneDX JSON (ou de format inconnu, alors soumise au contrôle CycloneDX) est importée ; un autre format reconnu est refusé avec un motif qui le nomme, l'inventaire en place est conservé | [EXISTANT] — `io.argos.scm.SbomDocument`, `InventoryService.refresh` ; [PROPOSITION] pour le refus nommé des formats SPDX et CycloneDX XML |

## Règles métier

| Règle | Exigence(s) | Source | Statut |
|---|---|---|---|
| La SBOM de la branche principale fait référence | INV-01 | ADR-0011 | [PROPOSITION] |
| L'inventaire reste fondé sur le PURL : un composant de SBOM sans PURL exploitable n'y entre pas et il est signalé à l'utilisateur avec l'import (nom, groupe, version, motif « sans PURL ») | INV-01 (INV-01-S3) | décision du porteur du projet, 2026-11-18 (D6) | [DÉCISION] |
| Les composants ignorés faute de PURL sont affichés dans la vue d'inventaire d'`argos-ui` (web et desktop) : nom, groupe, version et motif, liste bornée comme l'API (100 entrées, champs de 200 caractères), nombre total, état vide | INV-01 (INV-01-S3) | décision du porteur du projet, 2026-11-19 (E4) | [DÉCISION] |
| Les dépendances transitives sont incluses et distinguées | INV-02 | ADR-0011 ; baseline v2.3 §4, §6 | [PROPOSITION] |
| L'âge de la SBOM et l'absence de SBOM sont visibles | INV-03 | ADR-0011 | [PROPOSITION] |
| Sans SBOM, repli sur les manifestes, origine affichée | INV-04 | ADR-0011 | [PROPOSITION] |
| SBOM non conforme refusée, motif visible | INV-05 | arc42 §8.3, §11.2 | [PROPOSITION] |
| La vue d'inventaire propose le choix du projet parmi les projets des espaces de l'utilisateur, lus sous la RLS de `project` ; les liens depuis les alertes restent | INV-06 (INV-06-S1) | décision du porteur du projet, 2026-11-19 (F1) | [DÉCISION] |
| Les composants inventoriés d'un projet (nom, version, PURL, direct ou transitif) sont listés par pages côté serveur et consultables dans la vue | INV-06 (INV-06-S2) | décision du porteur du projet, 2026-11-19 (F5) | [DÉCISION] |
| Pages de taille bornée (1 à 200, défaut 50, valeurs hors bornes ramenées), tri stable (composants : nom, version, identifiant ; projets : nom, espace, identifiant) ; projet inconnu ou d'un autre espace : 404, refus journalisé | INV-06 (INV-06-S2, INV-06-S3) | arc42 §8.10 ; design.md D7 | [PROPOSITION] |
| Un paramètre de pagination non entier est refusé par 400 `DONNEE_INVALIDE`, sur toutes les listes paginées de l'API REST | INV-06 (INV-06-S2) | décision du porteur du projet, 2026-11-19 (G4) | [DÉCISION] |
| La synthèse de l'inventaire d'un projet (nombres, dates, origine, composants ignorés) est fournie sans la liste des composants ; la vue ne charge que la synthèse et la page de composants affichée | INV-06 (INV-06-S4) | décision du porteur du projet, 2026-11-19 (G5) | [DÉCISION] |

## Droits d'accès et restrictions

L'inventaire appartient au projet et suit le cloisonnement par espace (ADR-0016, Proposé) : seuls les utilisateurs autorisés sur le projet le consultent. La liste des projets visibles n'applique aucun filtre d'espace dans sa requête : la RLS de `project` sous le rôle `argos_app` la limite aux espaces dont l'utilisateur est membre (INV-06-S1). Les exigences de cloisonnement des impacts sont portées par COR-08. Le cloisonnement de l'inventaire est appliqué et testé sur les points d'accès de consultation : `GET /api/v1/projects/{projectId}/inventory`, `GET /api/v1/projects/{projectId}/inventory/summary` et `GET /api/v1/projects/{projectId}/inventory/components` répondent 404 à un projet inconnu ou d'un espace dont l'appelant n'est pas membre, le second refus étant journalisé (motif `NON_MEMBRE`), et la RLS de `project`, `inventory` et `component` sous `argos_app` sert de garde-fou (INV-06-S3, `ConsultationInventaireTest`). Les rôles fins au sein d'un espace (ADR-0016, Proposé) relèvent de la capacité de contrôle d'accès.

## Effets de bord observables

- remplacement de l'inventaire de référence à chaque nouvelle SBOM, et du signalement des composants écartés faute de PURL ;
- nouvelle corrélation des signaux existants pour le projet après changement d'inventaire — attendu par la chaîne baseline v2.3 §3, déclenchement non spécifié [QUESTION Q-INV-2] ;
- enregistrement des échecs d'import.

## Qualités mesurables

| Qualité | Cible | Source | Statut |
|---|---|---|---|
| Couverture SBOM des projets pilotes | 2 projets pilotes avec SBOM fraîche, transitives incluses | baseline v2.3 §6 critère 2 | [PROPOSITION] |
| Réponse « projets touchés » | < 5 min | ADR-0011, traçabilité | [QUESTION] — cible non validée |
| Seuil d'obsolescence d'une SBOM | 30 jours après la date de génération | ADR-0011 ; décision du 2026-11-16 (Q-3) | [DÉCISION] |

## Diagrammes

### DF-INV-1 — Import d'une SBOM

- **Question** : comment l'inventaire de référence d'un projet est-il remplacé par une nouvelle SBOM, et que se passe-t-il si elle est invalide ?
- **Type** : UML séquence
- **Portée** : cible proposée ; déclencheur non documenté (Q-INV-1)
- **Exigences** : INV-01, INV-02, INV-05

```mermaid
sequenceDiagram
    participant CI as «Software System» CI du projet (branche principale)
    participant SCM as scm-connector
    participant Inv as inventory
    participant DB as PostgreSQL
    participant Imp as impact-engine

    CI-->>SCM: SBOM CycloneDX disponible (déclencheur à définir)
    SCM->>Inv: transmettre la SBOM et le projet
    Inv->>Inv: valider le document CycloneDX
    alt SBOM exploitable
        Inv->>DB: remplacer l'inventaire de référence (PURL, version, direct ou transitif)
        Inv->>DB: enregistrer la date et l'origine SBOM
        Inv->>DB: signaler les composants sans PURL (nombre, 100 premiers décrits, INV-01-S3)
        Inv-)Imp: inventaire du projet modifié
    else SBOM non conforme
        Inv->>DB: enregistrer l'échec d'import et son motif
        Note over Inv,DB: aucun composant du fichier n'est ajouté (INV-05)
    end
```

L'envoi vers `impact-engine` est asynchrone et matérialise l'effet de bord « nouvelle corrélation » dont le déclenchement reste à spécifier (Q-INV-2). Le devenir de l'inventaire précédent après un import refusé n'est pas documenté (Q-INV-3).

### DF-INV-2 — Origine de l'inventaire d'un projet

- **Question** : d'où provient l'inventaire utilisé pour un projet, et avec quelle conséquence sur la confiance ?
- **Type** : organigramme (flowchart)
- **Portée** : cible proposée
- **Exigences** : INV-01, INV-03, INV-04, COR-06

```mermaid
flowchart TD
    P["Projet suivi"] --> S{"SBOM CycloneDX de la<br/>branche principale importée ?"}
    S -- oui --> O{"SBOM générée depuis<br/>plus de 30 jours ? (Q-3)"}
    O -- non --> R1["Inventaire SBOM<br/>niveau exact possible (COR-02)"]
    O -- oui --> R2["Inventaire SBOM obsolète<br/>niveau autre que exact (COR-06)"]
    S -- non --> M{"Manifeste lisible ?"}
    M -- oui --> R3["Inventaire de repli « manifeste »<br/>niveau autre que exact (INV-04, COR-06)"]
    M -- non --> R4["Aucun inventaire<br/>absence signalée (INV-03-S2)"]
```

Le diagramme relie l'origine de l'inventaire au niveau d'impact défini par `correlation-impact`. Le comportement d'un projet sans aucun inventaire vis-à-vis de la corrélation (aucun impact calculable) est une conséquence logique, non une règle écrite dans les sources.

### DF-INV-3 — Consultation de l'inventaire d'un projet choisi

- **Question** : comment un utilisateur choisit-il un projet et parcourt-il ses composants inventoriés, sans voir ceux d'un autre espace ?
- **Type** : UML séquence
- **Portée** : implémentée (décisions F1, F5 et G5 du 2026-11-19 ; modalités [PROPOSITION], design.md D7)
- **Exigences** : INV-06, INV-03

```mermaid
sequenceDiagram
    actor U as Utilisateur
    participant IHM as argos-ui (vue d'inventaire)
    participant API as api (REST)
    participant Inv as inventory
    participant DB as PostgreSQL (rôle argos_app, RLS)

    U->>IHM: ouvrir « Inventaire »
    IHM->>API: GET /api/v1/projects?page=0&size=200
    API->>Inv: projets lisibles du compte
    Inv->>DB: lire project et espace sous le compte
    DB-->>Inv: projets des espaces dont le compte est membre
    Inv-->>IHM: page de projets (nom, espace, identifiant, total)
    U->>IHM: choisir un projet, « Afficher l'inventaire »
    IHM->>API: GET /api/v1/projects/{id}/inventory/summary
    IHM->>API: GET /api/v1/projects/{id}/inventory/components?page=0&size=20
    alt projet d'un espace de l'utilisateur
        API->>Inv: synthèse sans composants (nombre de composants, composants ignorés)
        Inv->>DB: lire inventory et compter component sous le compte
        Inv-->>IHM: synthèse de l'inventaire de référence
        API->>Inv: page de composants (nom, version, identifiant)
        Inv->>DB: lire component sous le compte
        Inv-->>IHM: composants de la page et nombre total
        U->>IHM: « Page suivante » (clavier ou souris)
        IHM->>API: GET …/inventory/components?page=1&size=20
    else projet inconnu ou d'un autre espace
        API-->>IHM: 404, refus journalisé pour un autre espace
    end
```

La synthèse (origine, âge, obsolescence, nombre de composants) et les composants ignorés sont lus sur `/inventory/summary`, sans la liste des composants (INV-01-S3, INV-03, INV-06-S4 ; décision G5) ; la page de composants est un appel distinct. La réponse de `/inventory`, composants compris, est inchangée et n'est plus lue par la vue.

## Critères d'acceptation

- Scénarios `INV-01-S1` à `INV-05-S1` de la spec, et `INV-06-S1` à `INV-06-S4` (consultation par projet, [PROPOSITION] issue des décisions F1, F5 et G5 du 2026-11-19).
- Baseline v2.3 §4 : « SBOM CycloneDX de deux projets, PURL et versions transitives consultables ».

## Interprétations introduites par un changement

Interprétations validées par le porteur du projet le 2026-11-16 [DÉCISION].

- INV-01-S2 : un composant absent de la nouvelle SBOM disparaît de l'inventaire de référence (remplacement complet) ;
- INV-02 : la distinction direct/transitif est affichée — la source exige la couverture des transitives, pas l'affichage de la distinction.

[DÉCISION — porteur du projet, 2026-11-18] Tâche 3.2 : le test de contrat GitLab/GitHub enregistré se fait sur une capture d'un dépôt pilote réel, notamment de l'artefact SBOM GitLab. Les réponses enregistrées sur des dépôts publics de `api.github.com` et `gitlab.com` restent une preuve partielle ; la tâche reste ouverte jusqu'à cette capture.

Interprétation introduite par l'implémentation, à valider [PROPOSITION] :

- INV-03 : une SBOM sans date de génération est traitée comme obsolète, faute de pouvoir établir sa fraîcheur ; la corrélation lui attribue au mieux le niveau `probable`. La décision Q-3 ne fixe que le seuil de 30 jours après la date de génération.
- INV-04 : le repli sur les manifestes ne s'applique qu'à une SBOM absente d'un dépôt accessible ; un dépôt introuvable, un jeton refusé ou un quota épuisé fait échouer le rafraîchissement sans repli, l'inventaire en place étant conservé.
- INV-01-S3 : la description des composants signalés est bornée à 100 entrées par import et chaque champ à 200 caractères ; au-delà, seul le nombre total est conservé. Un PURL présent mais illisible porte le motif `PURL_INVALIDE`, distinct de `SANS_PURL`.
- INV-01, INV-05 : une SBOM SPDX ou CycloneDX XML trouvée sur la forge est reconnue et refusée avec le motif « SBOM <format> non acceptée : CycloneDX JSON 1.5 ou 1.6 attendu » ; elle n'est ni convertie ni importée, conformément à la décision Q-7. L'import de SPDX demanderait de modifier INV-01.

## Écarts constatés

- Le code de `argos-server` implémente les règles de cette fiche (`io.argos.inventory`, `io.argos.scm` ; tests `argos-server/src/test/java/io/argos/chain/InventoryTest.java` et `DocumentParsingTest.java`). La consultation de l'inventaire s'exécute sous le rôle applicatif `argos_app` : la RLS des tables `project`, `inventory`, `component` et `inventory_import_failure` limite la lecture aux membres de l'espace du projet.
- L'indicateur `obsolete` de la consultation ne vaut vrai que pour une SBOM ; un inventaire de repli (manifestes) n'est pas une SBOM, mais la corrélation ne lui accorde jamais le niveau `exact`.
- Les niveaux des impacts sont calculés à la corrélation et enregistrés : une SBOM qui dépasse ensuite les 30 jours ne fait pas recalculer les impacts existants tant qu'aucun nouveau signal ni inventaire ne déclenche de corrélation (aucune tâche planifiée ne le fait, ADR-0031 accepté).
- `scm-connector` (`io.argos.scm.RepositoryFiles`) récupère la SBOM et les manifestes en lecture seule : GitLab, SBOM = artefact du job désigné sur la branche principale, fichiers par l'API des fichiers bruts, jeton `PRIVATE-TOKEN` ; GitHub, SBOM et fichiers par l'API des contenus en média brut, jeton `Bearer`. Les jetons sont lus dans la configuration (`argos.scm.gitlab.token`, `argos.scm.github.token`, variables d'environnement) ; le délai `argos.sources.http-timeout` borne l'établissement de la connexion et l'attente des en-têtes de réponse, la lecture du corps étant bornée en taille et non en durée. Une réponse 404 sur un fichier d'un dépôt existant signifie « absent » et ouvre le repli sur les manifestes ; un dépôt introuvable (`DepotIntrouvable` : corps `404 Project Not Found` de GitLab ; pour GitHub, dont le 404 est identique pour un fichier et un dépôt absents, un `GET /repos/:owner/:repo` répondant 404), un jeton refusé (401) ou un accès interdit (403 hors quota, `AccesRefuse`), un quota épuisé (`QuotaEpuise` : 429, ou 403 avec `x-ratelimit-remaining: 0`, `Retry-After` ou un message mentionnant une limite de débit, comme la limite secondaire GitHub signalée sans `Retry-After`), un autre statut, une redirection (jamais suivie) ou une réponse au-delà de `argos.inventory.max-document-bytes` font échouer le rafraîchissement (`ERROR`) sans repli ni modification de l'inventaire. Les segments `.` et `..` sont retirés des chemins, un dépôt GitHub hors de la forme `owner/repo` est refusé, le jeton n'apparaît dans aucun message. Les points d'accès utilisés ne sont pas paginés. Au cours d'un même rafraîchissement (`RepositoryFiles.Lecture`), le dépôt GitHub n'est vérifié qu'une fois, quel que soit le nombre de fichiers absents.
- Le contrat de ces appels est vérifié sans accès à Internet, sur un serveur HTTP local qui rejoue des réponses de `api.github.com` et `gitlab.com` enregistrées le 2026-11-18 sur des dépôts publics, en-têtes compris (`argos-server/src/test/resources/fixtures/scm/enregistrees/`, `chain/ScmContratEnregistreTest`) : SBOM CycloneDX 1.6 trouvée et importée, SBOM absente avec repli sur `package.json`, dépôt introuvable, jeton refusé, artefact interdit. Les limites de débit et l'artefact SBOM GitLab servi en 200 sont composés d'après la documentation des forges (`fixtures/NOTICE`) ; `chain/ScmContractTest` complète ces cas sur des réponses composées (formats SPDX et CycloneDX XML, redirection, taille, chemins hostiles). Aucune réponse n'est enregistrée sur un dépôt pilote.
- Les artefacts de job GitLab ne sont pas lisibles anonymement sur les projets publics essayés (403 enregistré) ; la récupération d'un artefact réel n'est vérifiée qu'avec une réponse composée.
- Écart E-2 du [jeu de référence](../architecture/quality/mesure-correlation-jeu-reference.md) (composant décrit sans PURL) : signalé à l'import et affiché dans l'IHM.
- [DÉCISION — porteur du projet, 2026-11-19 ; E4] Composants ignorés faute de PURL : ils sont affichés dans la vue d'inventaire d'`argos-ui` (web et desktop), à partir du champ déjà exposé par `GET /api/v1/projects/{id}/inventory`.
- Les composants signalés à l'import sont restitués par l'API d'inventaire et affichés par la vue d'inventaire d'`argos-ui`, web et desktop (`argos-ui/src/views/InventaireView.vue`, adresses `/inventaire` et `/inventaire/{id}`, entrée « Inventaire » de la navigation) : l'IHM lit la synthèse `GET /api/v1/projects/{projectId}/inventory/summary` par `appelerApi` (`argos-ui/src/api/inventaire.ts`, décision G5), affiche la synthèse de l'inventaire de référence (origine, date et âge de la source, obsolescence, date d'import, nombre de composants inventoriés), puis la section « Composants ignorés » : nombre total, tableau à en-têtes de colonne (nom, groupe, version, motif) borné comme l'API, ou l'état vide (« Aucun composant ignoré au dernier import. ») ; un projet sans inventaire est nommé comme tel. Les valeurs issues de la SBOM sont rendues comme texte. Le détail d'une alerte mène à la vue d'inventaire de chaque projet cité. Le projet se choisit dans la liste des projets visibles ou se désigne par son identifiant (décision F1 ci-dessous). Les outils MCP ne restituent pas le signalement ; le résultat interne de l'import (`InventoryService.ImportResult`) le porte aussi, mais aucun point d'entrée ne le renvoie. Ils sont conservés dans les colonnes `ignored_count` et `ignored_components` de `inventory` (migration V17), sous la RLS de la table.
- [DÉCISION — porteur du projet, 2026-11-19 ; F1] Vue d'inventaire : nouveau point d'accès `GET /api/v1/projects` listant les projets visibles par l'utilisateur (sous RLS, adossé à `InventoryQueries.visibleProjects` ou équivalent existant), et liste de choix du projet dans la vue Inventaire d'`argos-ui` ; les liens depuis les alertes restent.
- [DÉCISION — porteur du projet, 2026-11-19 ; F5] Vue d'inventaire : liste paginée côté serveur des composants inventoriés d'un projet (nom, version, PURL, direct ou transitif), consultable dans la vue.
- Les décisions F1 et F5 sont implémentées (exigence INV-06 [PROPOSITION], tâche 3.8, design.md D7) : `io.argos.api.ProjectResource` (`GET /api/v1/projects`) et `InventoryResource#components` (`GET /api/v1/projects/{projectId}/inventory/components`), adossés à `InventoryQueries.projects` et `InventoryQueries.components`, exécutés sous `argos_app` avec le compte de l'appelant ; `GET /api/v1/projects/{projectId}/inventory` est inchangé. La vue d'inventaire d'`argos-ui` (web et desktop) propose la liste de choix « Projet » (libellé « nom du projet — nom de l'espace », 200 premiers projets ; au-delà, un message renvoie à la désignation par identifiant, conservée sous « Ou identifiant du projet ») et la section « Composants inventoriés » : tableau à en-têtes (nom, version, PURL, dépendance), pages de 20, navigation « Page précédente » / « Page suivante » au clavier, position « Page n sur m », annonce dans la région live, focus sur le tableau après un changement de page, état vide (« Aucun composant inventorié. ») et erreurs avec nouvelle tentative. L'ordre des versions est l'ordre alphabétique de la base, et non l'ordre des versions de l'écosystème [PROPOSITION].
- [DÉCISION — porteur du projet, 2026-11-19 ; G4] Toutes les listes paginées de l'API REST refusent un paramètre de pagination non entier par 400 `DONNEE_INVALIDE` (helper `io.argos.api.ParametresPagination`), contrat OpenAPI à jour.
- [DÉCISION — porteur du projet, 2026-11-19 ; G5] L'API fournit la synthèse d'inventaire d'un projet sans la liste des composants (nombres, date, source, composants ignorés faute de PURL) ; la vue Inventaire ne charge plus l'inventaire complet, seulement la synthèse et la page de composants affichée ; la dette consignée dans D7 est levée.
- [DÉCISION — porteur du projet, 2026-11-19 ; G6] Une seule requête de liste des projets visibles : les outils MCP passent par la requête paginée `InventoryQueries.projects` (ou son équivalent unifié) ; `visibleProjects` disparaît si plus rien ne l'utilise ; comportement des outils MCP inchangé pour l'appelant (cloisonnement, contenu), tests MCP verts.
- Les décisions G4, G5 et G6 sont implémentées (tâche 3.9, design.md D7, révision G) : `GET /api/v1/projects/{projectId}/inventory/summary` (`InventoryResource#summary`, `InventoryQueries.summary`) renvoie `{projectId, status, summary}`, `summary` portant les champs de l'inventaire de référence avec `componentCount` à la place de `components`, mêmes règles d'accès que `/inventory` (404 journalisé hors des espaces de l'appelant, RLS sous `argos_app`) ; `GET /api/v1/projects/{projectId}/inventory` est inchangé pour ses clients. La vue d'inventaire d'`argos-ui` (web et desktop) lit la synthèse et la page de composants affichée, jamais l'inventaire complet. Les paramètres `page`, `size`, `taille` et `limite` des listes paginées de l'API passent par `ParametresPagination` ; les bornes propres à chaque liste sont conservées. Les outils MCP `get_project_context` et `upcoming_releases` lisent les projets par `InventoryQueries.projects`, page après page (`io.argos.mcp.ProjetsVisibles`) ; `InventoryQueries.projects` est la seule requête de liste des projets visibles. Le résumé des projets de `get_project_context` lit la synthèse de l'inventaire et non ses composants.
- ADR-0032 décrit les migrations V1 à V16 à sa date d'acceptation ; la migration V17 figure dans la liste à jour des migrations d'[arc42 §8.7](../architecture/arc42/08-concepts-transverses.md#87-persistance), qui fait foi pour les migrations suivantes. [DÉCISION — porteur du projet, 2026-11-19 ; F4] ADR-0032 n'est pas modifié ; l'écart est levé.
- `InventoryService.refresh` n'a aucun déclencheur en production : ni tâche planifiée, ni point d'entrée REST (Q-INV-1).
- Une forge qui sert durablement une SBOM SPDX ou CycloneDX XML fait refuser chaque rafraîchissement (`REJECTED`) sans repli sur les manifestes : un inventaire existant reste en l'état et n'est plus mis à jour tant que la SBOM n'est ni retirée ni remplacée par une SBOM CycloneDX JSON, comme pour une SBOM CycloneDX invalide.

## Questions ouvertes

| ID | Question | Impact | Destinataire |
|---|---|---|---|
| Q-INV-1 | Qu'est-ce qui déclenche l'import d'une SBOM ? | conception `scm-connector` | Tech Lead |
| Q-INV-2 | Un changement d'inventaire relance-t-il la corrélation des signaux existants ? | COR-01 | architecte |
| Q-INV-3 | Après un import refusé, l'inventaire précédent reste-t-il la référence ? | INV-05 | Tech Lead |
| Q-INV-4 | Quels formats de manifeste le repli doit-il lire ? | INV-04 | Tech Lead |
| Q-3 (changement) | Âge à partir duquel une SBOM est obsolète ? | répondue le 2026-11-16 : obsolète 30 jours après sa date de génération [DÉCISION] | sécurité / Tech Lead |
| Q-7 (changement) | Versions du schéma CycloneDX acceptées ? | répondue le 2026-11-16 : CycloneDX 1.5 et 1.6, format JSON [DÉCISION] | Tech Lead |

## Traçabilité

Voir la matrice du changement : [`tasks.md`](/openspec/changes/formaliser-chaine-securite-poc/tasks.md#matrice-de-traçabilité). Les scénarios INV-01-S1 à INV-05-S1 sont vérifiés par `argos-server/src/test/java/io/argos/chain/InventoryTest.java` et `DocumentParsingTest.java` ; INV-01-S3 l'est aussi par `JeuReferenceCorrelationTest` (composant sans PURL du projet `outil-sans-purl`) et, pour l'affichage dans la vue d'inventaire d'`argos-ui`, par `argos-ui/tests/composants-ignores.test.mjs` (conversion, rendu, règles de l'écran), `argos-ui/tests/inventaire-projet.test.mjs` (synthèse, chargement, erreur et nouvelle tentative, annulation) et les parcours E2E `e2e/tests/inventaire.spec.ts` (portail web) et `e2e/desktop/inventaire.spec.ts` (client desktop). Les scénarios INV-06-S1 à INV-06-S4 sont vérifiés par `argos-server/src/test/java/io/argos/chain/ConsultationInventaireTest.java` (cloisonnement entre espaces, RLS sous `argos_app`, bornes de pagination, paramètres non entiers, tri stable, projet inconnu ou d'un autre espace, synthèse sans composants égale à l'inventaire complet, contrat OpenAPI), `argos-server/src/test/java/io/argos/api/ParametresPaginationTest.java` (paramètres non entiers des autres listes paginées de l'API, décision G4), `argos-server/src/test/java/io/argos/mcp/CloisonnementMcpTest.java` (projets des outils MCP lus page après page, décision G6), `argos-ui/tests/inventaire-listes.test.mjs` (chemins, liste de choix, pages, chargements, rendu du tableau et de la pagination, accessibilité de l'écran), `argos-ui/tests/inventaire-projet.test.mjs` et `argos-ui/tests/composants-ignores.test.mjs` (synthèse lue sur `/inventory/summary`) et les mêmes parcours E2E (choix du projet dans la liste, seconde page atteinte au clavier, synthèse lue sans appel de l'inventaire complet). La récupération GitLab et GitHub est vérifiée par `argos-server/src/test/java/io/argos/chain/ScmContratEnregistreTest.java` (réponses enregistrées) et `ScmContractTest.java` (réponses composées), forges simulées par un serveur HTTP local (tâche 3.2).
