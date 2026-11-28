# Ordre de correction — mise à jour du 2026-11-28

Le plan d'action ([plan-action.md](plan-action.md)) classe par urgence ; ce document classe par **ordre d'exécution**, qui prime en cas de divergence. Seuls les constats qui restent à traiter sont affectés à un sprint ; les constats Documentation forment le dernier sprint.

## Vue d'ensemble

| Sprint | Titre | À traiter | dont partiels | Élevée | Moyenne | Faible | Charge (j) |
|---|---|---|---|---|---|---|---|
| 1 | Sécurité et exploitation | 6 | 1 | 2 | 1 | 3 | 2,25 à 8 |
| 2 | Chaîne de publication du client desktop | 7 | 1 | 1 | 2 | 4 | 4,75 à 15 |
| 3 | Frontières d'erreur et file d'enrichissement | 9 | 2 | 0 | 4 | 5 | 6 à 19 |
| 4 | Données, isolation et contrats | 9 | 4 | 0 | 0 | 9 | 6 à 19 |
| 5 | Décisions et cohérence des modules | 3 | 1 | 0 | 0 | 3 | 0,75 à 3 |
| 6 | Documentation alignée | 10 | 4 | 0 | 1 | 9 | 2,5 à 10 |

Charge totale : **22,25 à 74 jours** — enveloppe (S 0,25 à 1 j, M 1 à 3 j, L 3 à 8 j) hors revue, allers-retours de CI et arbitrages écrits. Un constat partiel garde l'effort estimé au 17/11 : le reste à faire est souvent plus court.

## Chemin critique

- **AUD-SEC-34** (M, sprint 1) — Seul défaut exploitable par un compte ordinaire ; une liste blanche de propriétaires de dépôts suffit à le fermer.
- **AUD-DEP-29** (M, sprint 2) — Débloque toute publication du desktop et la documentation de sa configuration (AUD-DOC-04) ; d'ici là, aucune étiquette desktop-v*.
- **AUD-QUA-06** (M, sprint 3) — La migration du JDBC hors noyau vers l'utilitaire commun permet de traduire les SQLException en erreurs du domaine (AUD-QUA-26), puis d'aligner les six boucles (AUD-QUA-25) et l'arc42 §8 (AUD-DOC-25).
- **AUD-PER-30** (S, sprint 1) — Débloque la correction du rafraîchissement des traitements longs (AUD-PER-32) et l'amorçage réel d'OSV sur le NAS.

## Sprint 1 — Sécurité et exploitation

**Pourquoi ici.** Le delta a ouvert une délégation confuse (n'importe quel compte lit des dépôts privés avec les jetons du serveur) et un risque d'arrêt brutal à l'amorçage OSV (archives dans un /tmp en mémoire). Ce lot est indépendant du sprint 2 : les deux peuvent être menés en parallèle.

**Critère de sortie.** Le rattachement d'un dépôt n'est accepté que pour un propriétaire GitHub/GitLab inscrit sur une liste blanche réglée par l'administrateur, vérifié par un test qui tente un dépôt hors liste ; l'amorçage OSV écrit ses archives sous un répertoire disque configurable hors du tmpfs (java.io.tmpdir ou réglage dédié), et le tmpfs /tmp de compose.yaml a une taille fixée ; les imports SBOM et manifestes ont une limite de débit et un plafond de composants, testés ; les contrôles de santé publics ne disent que UP/DOWN, le détail restant réservé à l'administrateur ; npm ci du Dockerfile tourne avec --ignore-scripts ; les sauvegardes sont chiffrées.

**Charge.** 2,25 à 8 jours. Déjà engagés (partiels) : AUD-SEC-07.

**Ordre interne.** AUD-SEC-34 → AUD-PER-30 → AUD-SEC-35 → AUD-SEC-36 → AUD-DEP-32 → AUD-SEC-07.

**Prérequis des sprints antérieurs.** aucun.

| ID | Statut | Sévérité | Axes | Effort | Titre |
|---|---|---|---|---|---|
| AUD-SEC-34 | Ouvert | Élevée | Sécurité | M | Inventaire et activité de projet : tout compte peut créer un espace, y rattacher n'importe quel dépôt GitHub/GitLab et en lire manifestes, SBOM, jalons et versions avec les jetons du serveur |
| AUD-PER-30 | Ouvert | Élevée | Performance | S | L'amorçage OSV écrit jusqu'à trois archives all.zip (1 Gio chacune au plus) dans /tmp, un tmpfs compté dans le plafond mémoire de 1 Gio du conteneur argos, dont le tas peut prendre jusqu'à 60 % |
| AUD-SEC-35 | Ouvert | Moyenne | Sécurité | S | Import de SBOM et de manifestes sans limite de débit ni plafond de composants : corps de 24 Mio lu en mémoire et analysé en arbre JSON à chaque appel, recorrélation déclenchée |
| AUD-SEC-36 | Ouvert | Faible | Sécurité | S | Contrôles de santé publics sans session : état et date de dernière synchronisation de chaque source, file d'envoi des alertes et état du fournisseur IA exposés |
| AUD-DEP-32 | Ouvert | Faible | Dépendances | S | Le Dockerfile installe les dépendances de l'IHM avec les scripts d'installation des paquets (npm ci sans --ignore-scripts), alors que toute la CI et la publication les désactivent |
| AUD-SEC-07 | Partiel | Faible | Sécurité | S | Sauvegardes complètes de la base non chiffrées (permissions 0600/0700 désormais posées) |

## Sprint 2 — Chaîne de publication du client desktop

**Pourquoi ici.** La publication sur étiquette construit un installeur figé sur localhost:8080, sans tests ni contrôle d'origine, puis la mise à jour automatique le distribue à tous les postes sans vérification d'authenticité. Mesure conservatoire : ne poser aucune étiquette desktop-v* avant la fin de ce sprint.

**Critère de sortie.** Le workflow de publication rejoue la CI (tests desktop, npm audit) et n'accepte qu'une étiquette posée sur un commit de main ; l'installeur publié lit l'adresse du serveur et l'émetteur à l'exécution (ou les reçoit à l'emballage) et un test vérifie qu'aucune adresse localhost n'y est figée ; les installeurs sont signés et la mise à jour automatique vérifie la signature (publisherName), ou est désactivée tant qu'ils ne le sont pas ; le job de construction n'a plus contents: write ni id-token: write ; argos-desktop passe tsc en mode strict ; la version de Keycloak de l'e2e est suivie par Renovate. La suite serveur passe sur un poste Windows avec core.autocrlf=true (fins de ligne fixées par .gitattributes).

**Charge.** 4,75 à 15 jours. Déjà engagés (partiels) : AUD-DEP-15.

**Ordre interne.** AUD-DEP-29 → AUD-DEP-30 → AUD-SEC-33 → AUD-DEP-15 → AUD-QUA-27 → AUD-DEP-31 → AUD-TST-32.

**Prérequis des sprints antérieurs.** aucun.

| ID | Statut | Sévérité | Axes | Effort | Titre |
|---|---|---|---|---|---|
| AUD-DEP-29 | Ouvert | Élevée | Dépendances | M | L'installeur desktop publié par la CI est construit sans adresse de serveur (IHM figée sur http://localhost:8080) et remplace par mise à jour automatique tout client emballé pour un serveur distant ; ARGOS_API_URL à l'exécution, présenté comme parade, ne change que la CSP |
| AUD-DEP-30 | Ouvert | Moyenne | Dépendances, Tests | S | Publication du client desktop sans aucun gate : sur une étiquette desktop-v* posée sur n'importe quel commit, l'installeur est construit et publié comme dernière version sans tests, sans npm audit ni vérification que le commit est sur main |
| AUD-SEC-33 | Ouvert | Moyenne | Sécurité | M | Mise à jour automatique du desktop sans authenticité : installeurs non signés, vérification Authenticode sautée faute de publisherName, empreinte SHA-512 servie par la même release |
| AUD-DEP-15 | Partiel | Faible | Dépendances | M | Client desktop distribué sans signature de code ni mise à jour automatique |
| AUD-QUA-27 | Ouvert | Faible | Qualité | M | Le client desktop est vérifié par tsc en checkJs non strict : le mode strict révèle 125 erreurs (paramètres implicitement any) dans 1 172 lignes de JavaScript |
| AUD-DEP-31 | Ouvert | Faible | Dépendances | S | La version de Keycloak de l'e2e (e2e/compose.image.yaml) échappe à Renovate : la CI teste 26.7.4 en dur pendant que compose.yaml et .env.example suivront les montées de version |
| AUD-TST-32 | Ouvert | Faible | Tests | S | ContratClientIhmTest échoue sur le poste du mainteneur : core.autocrlf=true extrait l'instantané openapi.json en CRLF, et .gitattributes ne fixe les fins de ligne que pour *.sh |

## Sprint 3 — Frontières d'erreur et file d'enrichissement

**Pourquoi ici.** Le resserrement des captures a été corrigé pour les PURL, mais la règle s'appuie sur IllegalStateException et six boucles planifiées y échappent ; la file d'enrichissement peut être bloquée par cinq signaux en erreur et son débit fixe retarde de plusieurs jours l'enrichissement après un amorçage.

**Critère de sortie.** Le JDBC hors noyau est migré vers l'utilitaire commun et ses traductions de SQLException lèvent une erreur de la hiérarchie du domaine (ErreurBase) ; les frontières capturent cette hiérarchie et non IllegalStateException ; chaque boucle planifiée a un test d'erreur imprévue (règle 6 d'arc42 §8.5) ; un signal en erreur persistante est reporté puis abandonné, testé ; la file d'enrichissement ignore les signaux sans CVE, traite les avis récents avant l'arriéré (test d'ordre) et ne dépasse pas 5 requêtes NVD par 30 s sans clé (test du limiteur) ; la corrélation ne s'exécute plus dans le fil d'ingestion ; le traitement de synchronisation est rafraîchi pendant les téléchargements et ses transitions exigent l'état RUNNING ; une erreur n'est journalisée qu'une fois avec sa pile et aucune traduction ne perd sa cause.

**Charge.** 6 à 19 jours. Déjà engagés (partiels) : AUD-QUA-06, AUD-PER-02.

**Ordre interne.** AUD-QUA-06 → AUD-QUA-26 → AUD-QUA-25 → AUD-QUA-24 → AUD-PER-31 → AUD-PER-02 → AUD-PER-32 → AUD-QUA-28 → AUD-QUA-29.
Dépendances internes : AUD-QUA-26 après AUD-QUA-06 ; AUD-QUA-25 après AUD-QUA-26.

**Prérequis des sprints antérieurs.** AUD-PER-30 (sprint 1)

| ID | Statut | Sévérité | Axes | Effort | Titre |
|---|---|---|---|---|---|
| AUD-QUA-06 | Partiel | Faible | Qualité | M | Le code JDBC brut reste majoritaire hors des modules cœur : 153 prepareStatement (31 fichiers) et 79 catch SQLException hors de l'utilitaire commun Jdbc, adopté par 35 fichiers ; 4 copies du convertisseur de date et 4 de la troncature subsistent |
| AUD-QUA-26 | Ouvert | Moyenne | Qualité | M | La frontière « panne d'accès aux données » capture IllegalStateException (44 sites) et non ErreurBase : 64 traductions de SQLException lèvent encore une IllegalStateException brute, et des erreurs de programmation de même type sont absorbées sans relance |
| AUD-QUA-25 | Ouvert | Moyenne | Qualité | M | Six boucles planifiées restent hors de la règle des frontières d'erreur (seule la panne d'accès aux données y est capturée) : une erreur imprévue à l'intégration d'une source web la laisse échue et en tête de file, si bien qu'elle interrompt chaque passage |
| AUD-QUA-24 | Ouvert | Moyenne | Qualité | S | La file d'enrichissement ne reporte pas un signal en erreur : il reste en tête de file (ORDER BY prochain_essai_le, lot de 5 toutes les 30 s), sans tentative comptée, et cinq signaux en échec persistant bloquent tout l'enrichissement KEV/NVD |
| AUD-PER-31 | Ouvert | Moyenne | Performance | M | Débit de la file d'enrichissement fixé à 5 signaux toutes les 30 s (14 400 par jour), tous signaux confondus, CVE ou non : un amorçage OSV met des jours à s'écouler et retarde d'autant l'enrichissement (KEV, EPSS) des avis nouveaux |
| AUD-PER-02 | Partiel | Faible | Performance, Qualité | M | L'enrichissement passe par une file persistante traitée hors transaction et rattrapée, mais la corrélation reste synchrone, signal par signal, sur le fil de l'ingestion |
| AUD-PER-32 | Ouvert | Faible | Performance | S | Le traitement de synchronisation n'est pas rafraîchi pendant la lecture des index et le téléchargement des archives, et ses écritures finales n'exigent pas l'état RUNNING : au-delà de 30 minutes, un second lancement peut reprendre la même source en parallèle |
| AUD-QUA-28 | Ouvert | Faible | Qualité | S | Une erreur imprévue d'une tâche planifiée est journalisée au moins deux fois avec sa pile : dans la boucle (LOG.errorf par unité) puis par IntercepteurTaches, qui la journalise à nouveau avant de la relancer au planificateur |
| AUD-QUA-29 | Ouvert | Faible | Qualité | S | Deux traductions d'IOException en erreur de source abandonnent la cause : ScmException(…, null) pour une réponse de forge illisible et SourceIndisponible sans constructeur à cause pour endoflife.date |

## Sprint 4 — Données, isolation et contrats

**Pourquoi ici.** Il reste des listes non bornées, des purges sur colonnes non indexées, des tables exclues de la conservation, une matrice d'autorisations qui ne prouve pas l'isolation entre espaces, et des types IHM non confrontés à l'OpenAPI.

**Critère de sortie.** Toutes les listes de l'API, les abonnements PROJECT et les lectures d'impacts par projet sont paginés ou bornés, et « Ma veille » annonce un total exact ; chaque colonne filtrée par une purge est indexée ; signal_version, alerte et les impacts résolus ont une rétention écrite et appliquée ; la matrice des rôles prouve l'isolation entre deux espaces pour chaque opération sur objet (y compris MCP) ; les types de requête et de réponse de l'IHM sont générés depuis /q/openapi ou vérifiés contre lui, et chaque point d'entrée du profil artiste a un client ; les lectures SQL étrangères passent par un service de lecture ou une vue (ADR-0037).

**Charge.** 6 à 19 jours. Déjà engagés (partiels) : AUD-PER-10, AUD-ARC-12, AUD-ARC-15, AUD-ARC-06.

**Ordre interne.** AUD-PER-10 → AUD-PER-33 → AUD-PER-37 → AUD-PER-34 → AUD-PER-35 → AUD-SEC-37 → AUD-ARC-12 → AUD-ARC-15 → AUD-ARC-06.
Dépendances internes : AUD-PER-37 après AUD-PER-10.

**Prérequis des sprints antérieurs.** aucun.

| ID | Statut | Sévérité | Axes | Effort | Titre |
|---|---|---|---|---|---|
| AUD-PER-10 | Partiel | Faible | Performance | S | Listes renvoyées en entier sans pagination : listes d'exploitation (sources rejetées, événements, échecs d'import) et impacts d'un projet (GET /projects/{id}/impacts) |
| AUD-PER-33 | Ouvert | Faible | Performance | S | Listes encore non bornées malgré ADR-0040 : dettes de version d'un espace, GET /api/v1/me/watch, et ImpactQueries.forProject sans page, qui charge tous les impacts actifs d'un projet pour les compter ou en garder 50 en Java (MCP get_project_context, get_security_alerts, abonnements PROJECT) |
| AUD-PER-37 | Ouvert | Faible | Performance | S | « Ma veille » tronque en silence au-delà de 200 impacts et annonce un total faux |
| AUD-PER-34 | Ouvert | Faible | Performance | S | Les purges de conservation parcourent des colonnes d'horodatage non indexées (ingestion_failure.received_at, ops_event.occurred_at, inventory_import_failure.received_at, opportunite.recu_le) : chaque lot de 5 000 relance un parcours de table |
| AUD-PER-35 | Ouvert | Faible | Performance | M | La liste fermée de la conservation exclut des tables qui croissent sans limite : toutes les versions d'avis (signal_version), les alertes (alerte) et les impacts résolus ou écartés (project_impact, 500 000 lignes dans le jeu de mesure) |
| AUD-SEC-37 | Ouvert | Faible | Sécurité, Tests | M | MatriceRolesTest ne vérifie pas l'isolation par objet : 49 opérations sur 103 rendent le même code à tous les rôles authentifiés, dont 19 appelées sur un identifiant inexistant (404 pour tous) |
| AUD-ARC-12 | Partiel | Faible | Architecture, Qualité, Tests | M | Le contrat client de l'IHM est vérifié contre l'OpenAPI servi (instantané et test des appels), mais ses types restent écrits à la main ; 5 classes REST sur 23 sans appel de l'IHM |
| AUD-ARC-15 | Partiel | Faible | Architecture, Qualité | M | Profil artiste livré sans IHM : six points d'entrée et la rubrique des préférences ne sont appelés par aucun client |
| AUD-ARC-06 | Partiel | Faible | Architecture, Qualité | M | Couplage par le schéma SQL : 12 tables partagées et 13 lectures étrangères directes admises par ProprieteTablesTest, contre la règle d'ADR-0037 (service de lecture ou vue) ; l'écriture réservée au propriétaire n'est pas vérifiée |

## Sprint 5 — Décisions et cohérence des modules

**Pourquoi ici.** Plusieurs ADR appliqués restent « Proposé », les package-info ne décrivent plus la table des dépendances, et deux changements OpenSpec actifs sont déjà fusionnés. La structure du code (recherche dans signal, langue par concept, seuils PMD) a été réglée par la PR #80.

**Critère de sortie.** Les ADR 0034, 0035 et 0038 à 0041 ont le statut Accepté ; les changements OpenSpec fusionnés sont archivés ; chaque module de premier niveau a un package-info qui concorde avec la table ModuleBoundariesTest, vérifié par un test.

**Charge.** 0,75 à 3 jours. Déjà engagés (partiels) : AUD-ARC-14.

**Ordre interne.** AUD-ARC-14 → AUD-DEP-33 → AUD-ARC-25.

**Prérequis des sprints antérieurs.** aucun.

| ID | Statut | Sévérité | Axes | Effort | Titre |
|---|---|---|---|---|---|
| AUD-ARC-14 | Partiel | Faible | Architecture, Documentation | S | Six ADR appliqués par le code et cités par ses tests restent « Proposé » (0034, 0035, 0038 à 0041, sur 42) ; les ADR fondateurs sont acceptés |
| AUD-DEP-33 | Ouvert | Faible | Dépendances | S | Les deux changements OpenSpec « actifs » sont déjà fusionnés dans le code (toutes les tâches cochées sauf la pile sur le NAS) mais la fiche exploitation-plateforme garde EXP-07 à EXP-12 et EXP-16 en [PROPOSITION] |
| AUD-ARC-25 | Ouvert | Faible | Architecture | S | Neuf package-info sur 16 décrivent encore l'ancienne table ALLOWED (dont une règle d'exception supprimée) et les quatre modules transverses execution, horloge, observabilite et commun n'en ont pas |

## Sprint 6 — Documentation alignée

**Pourquoi ici.** La documentation a été réalignée au sprint 12, mais des écarts subsistent : sources citant un dossier d'audit non versionné, preuve de test supprimée, arc42 §8 et §9 en retard, versions des README dépassées, diagrammes d'archives qui ne se rendent pas.

**Critère de sortie.** Le dossier docs/audit est versionné et les lignes « Source » des spécifications pointent vers un fichier existant ; verifier-docs contrôle les ancres de méthode de test et échoue sur une méthode inexistante ; arc42 §8.7 et §9.1 listent toutes les migrations et tous les ADR ; les README des sous-projets ne citent pas de version que Renovate a dépassée (ou renvoient au pom et aux lockfiles) ; tous les blocs Mermaid du dépôt, archives comprises, se rendent ; le registre des spécifications concorde avec les tasks.md. Le guide et un README d'argos-desktop décrivent la configuration réelle du client (adresse du serveur et émetteur) ; la section Licence du README renvoie à ADR-0025 ; les ADR acceptés n'ont plus de clause conditionnelle caduque.

**Charge.** 2,5 à 10 jours. Déjà engagés (partiels) : AUD-DOC-04, AUD-DOC-01, AUD-DOC-08, AUD-DOC-07.

**Ordre interne.** AUD-DOC-29 → AUD-DOC-27 → AUD-DOC-04 → AUD-DOC-01 → AUD-DOC-08 → AUD-DOC-07 → AUD-DOC-24 → AUD-DOC-25 → AUD-DOC-26 → AUD-DOC-28.

**Prérequis des sprints antérieurs.** AUD-DEP-29 (sprint 2), AUD-QUA-25 (sprint 3)

| ID | Statut | Sévérité | Axes | Effort | Titre |
|---|---|---|---|---|---|
| AUD-DOC-29 | Ouvert | Faible | Documentation | S | Vingt lignes « Source » de dix spécifications principales (et neuf passages des changements actifs) citent le rapport d'audit docs/audit/2026-11-21/, absent du dépôt et exempté du contrôle documentaire |
| AUD-DOC-27 | Ouvert | Faible | Documentation | S | La fiche comptes-acces cite comme preuve de QS-02 une méthode de test supprimée par la PR #79 (ParcoursIaTest#iag02S1DonneeConfidentielleRefuseeEtJournalisee) ; verifier-docs ne contrôle pas les ancres des chemins cités |
| AUD-DOC-04 | Partiel | Moyenne | Documentation | S | Client desktop : le guide documente désormais l'URL du serveur fixée à l'emballage (argos.apiUrl), mais ni l'émetteur OIDC (VITE_ARGOS_OIDC_ISSUER au build, argos.oidcUrl ou ARGOS_OIDC_URL à l'exécution) ni leur obligation de cohérence ; la section « viser le NAS » est inchangée |
| AUD-DOC-01 | Partiel | Faible | Documentation | S | Les points d'entrée (README racine, docs/architecture/README.md, openspec/config.yaml) décrivent encore le cadrage de septembre : socle « sans fonctionnalité métier », Claude derrière un AI Gateway, questions ouvertes déjà tranchées par des ADR acceptés |
| AUD-DOC-08 | Partiel | Faible | Documentation | S | Neuf ADR acceptés (0023, 0024, 0027 à 0033) gardent une décision rédigée comme une proposition, et ADR-0029 se dit encore subordonné à sa propre acceptation |
| AUD-DOC-07 | Partiel | Faible | Documentation | S | Le registre des spécifications (paragraphe du lot 18) et la fiche profils-veille contredisent encore la livraison : « Tâche 3.1 décochée … P-13 à valider » contre une tâche cochée, « pas de capacité regintel » contre une veille réglementaire livrée qui sert le profil expert-metier |
| AUD-DOC-24 | Ouvert | Faible | Documentation | S | ADR-0042 (Accepté, distribution du client desktop) manque à arc42 §9.1 : 41 lignes pour 42 ADR |
| AUD-DOC-25 | Ouvert | Faible | Documentation | S | arc42 §8 a pris du retard sur le code : tableau des migrations sans V25 ni V26 (« état au 2026-11-24 »), « 15 tâches » planifiées annoncées pour 16 |
| AUD-DOC-26 | Ouvert | Faible | Documentation | S | Versions citées par les README de sous-projets dépassées par Renovate : Electron 45.3.2 et Playwright 1.61.1 (e2e), exec-maven-plugin 3.6.4 (serveur) contre 45.4.1, 1.65.1 et 3.7.0 |
| AUD-DOC-28 | Ouvert | Faible | Documentation | S | Quatre diagrammes de conception de changements archivés les 25-27 novembre ne se rendent pas, par le même « ; » que D-19 : le correctif n'a pas été généralisé et l'archive échappe au contrôle |

## Hors sprint

Constats Info (ce qui tient), décisions documentées et constats fermés. Ceux marqués **borne un risque** expliquent pourquoi un risque est déjà limité : ne pas les « corriger » par erreur.

- AUD-ARC-02 — Fermé — L'IHM et le desktop joignent le jeton OIDC et l'IHM embarquée est servie sans session (chemins statiques publics), sans test qui le prouve
- AUD-QUA-01 — Fermé avec réserve — La création de projet, l'ajout de dépôt et l'import manuel (SBOM, manifestes) n'ont toujours aucun point d'entrée en production ; seul l'import planifié des dépôts déjà configurés est branché
- AUD-QUA-02 — Fermé avec réserve — Le script de sauvegarde ne détecte pas l'échec de pg_dump (pas de pipefail) et sa rotation peut supprimer les bonnes sauvegardes
- AUD-QUA-20 — Fermé avec réserve — Les captures aux frontières isolent puis relancent les erreurs imprévues : un PURL mal encodé est déclaré illisible et la synchronisation continue (testé)
- AUD-TST-01 — Fermé avec réserve — Intégration continue en place (7 jobs sur push main et pull request) ; protection de main et exécutions non vérifiables depuis l'audit
- AUD-DEP-01 — Fermé avec réserve — compose.yaml ne transmet pas au conteneur 28 des 37 variables lues en production (SMTP, ARGOS_PUBLIC_URL, jetons de forge, audience OIDC, quotas IA, planifications) et 27 manquent dans .env.example : e-mails vers localhost:587, liens vers localhost:5173
- AUD-DEP-21 — Fermé avec réserve — L'image Docker ne fige plus l'émetteur OIDC (lu dans /api/v1/system/info) ; le desktop résout apiUrl/oidcUrl à l'emballage, mais l'adresse API de son IHM reste figée au build (AUD-DEP-29)
- AUD-DEP-24 — Fermé avec réserve — La base keycloak ajoutée dans le même PostgreSQL n'est ni sauvegardée ni restaurée : après une reprise, le royaume réimporté émet de nouveaux sujets OIDC et les comptes Argos (sujet_oidc UNIQUE) deviennent orphelins
- AUD-ARC-04 — Fermé — Le protocole RLS « mécanisme unique » est réimplémenté quatre fois, avec des variantes divergentes
- AUD-ARC-05 — Fermé avec réserve — Un noyau partagé implicite (RLS, erreurs du domaine, appelant) est logé dans workspace et api.security, d'où cinq règles ArchUnit d'exception au cas par cas
- AUD-ARC-22 — Fermé — Les liens des alertes (signaux, radar, opportunités) visent /signaux/…, /espaces/…/radar et /profils/artiste/… : routes absentes de l'IHM (historique hash) et chemins non publics (401) ; aucune vue n'est atteignable depuis un courriel
- AUD-QUA-04 — Fermé avec réserve — Un seul modèle transactionnel JTA : plus aucun setAutoCommit en production (six au 21/11), 99 @Transactional et 21 QuarkusTransaction, verrouillé par TransactionsJtaTest
- AUD-QUA-17 — Fermé avec réserve — Fonctionnalités atteintes seulement par les tests : suggestions IA du radar jamais produites alors que leur décision est exposée en REST, collecte immédiate d'activité, trois méthodes sans appelant
- AUD-SEC-01 — Fermé avec réserve — Veille web : contrôle SSRF contournable par rebinding DNS (résolution vérifiée distincte de la résolution de connexion)
- AUD-SEC-03 — Fermé avec réserve — Journaux d'audit sans rétention ni bornage : un compte du fournisseur d'identité non invité écrit une ligne par requête
- AUD-SEC-05 — Fermé avec réserve — Aucun en-tête de sécurité HTTP sur l'IHM et l'API servies par Quarkus
- AUD-SEC-22 — Fermé avec réserve — Demande d'évolution : tout compte peut faire interroger GitHub/GitLab avec les jetons du serveur avant tout contrôle d'expertise (oracle d'existence sur dépôts privés, 10 appels/min/compte)
- AUD-SEC-29 — Fermé avec réserve — Royaume Keycloak livré pour l'installation (PR #28) sans protection contre la force brute ni politique de mot de passe, avec sslRequired « none » et des redirections à joker en http
- AUD-SEC-30 — Fermé avec réserve — Keycloak et Argos partagent le même compte PostgreSQL, superutilisateur de l'instance : la compromission de l'un donne la base de l'autre, clés de signature du royaume comprises, et la RLS d'Argos ne tient qu'au SET LOCAL ROLE
- AUD-TST-05 — Fermé avec réserve — Matrice point d'entrée × rôle versionnée (103 opérations, 6 rôles) tirée de /q/openapi
- AUD-PER-01 — Fermé avec réserve — Lancement manuel en tâche de fond (202) et amorçage OSV par archive ; les trois index modified_id.csv restent relus chaque heure
- AUD-PER-03 — Fermé — Corrélation par projet en N+1 aggravé : une transaction et une relecture du signal par signal, puis 3 à 5 instructions SQL par composant correspondant (rattachement, état précédent, upsert, transitions)
- AUD-PER-04 — Fermé avec réserve — Évaluation des alertes en N+1 par destinataire : 3 transactions et au moins 3 requêtes par membre, plus une requête par canal, synchrones dans le fil de corrélation
- AUD-PER-06 — Fermé avec réserve — « Ma veille » paginée et indexée : p95 185,5 ms sur le jeu avec abonnements et trois espaces (campagne du 2026-11-27) ; la page est extraite en mémoire d'une union bornée
- AUD-PER-18 — Fermé avec réserve — Opportunités sans date limite (toutes celles des flux RSS) restituées indéfiniment : ni LIMIT, ni pagination, ni purge
- AUD-PER-25 — Fermé — La corrélation réécrit chaque impact à chaque passage (updated_at = now() sans condition) : chaque nuit, mises à jour non HOT dans les 8 index de project_impact, et ordre « récents » de Ma veille faussé
- AUD-DEP-03 — Fermé avec réserve — Procédure de restauration documentée non testée et non idempotente : dump texte sans --clean rejoué par psql sans ON_ERROR_STOP sur une base déjà migrée
- AUD-DEP-07 — Fermé avec réserve — Observabilité minimale : ni journaux structurés, ni métriques, ni traçage, ni contrôle de santé métier
- AUD-DEP-25 — Fermé avec réserve — Le royaume Keycloak importé n'autorise que http://localhost:8088 en redirection et en origine, alors qu'Argos est publié par défaut sur le port 8080 : connexion refusée avec la configuration par défaut
- AUD-ARC-07 — Fermé avec réserve — La table ALLOWED est fermée (règle du module déclaré testée) ; une autorisation sur 46 (inventory → workspace) ne sert qu'à une lecture SQL non commentée
- AUD-ARC-09 — Fermé avec réserve — Deux styles de propagation d'événements CDI coexistent ; la chaîne radar s'exécute dans la requête HTTP
- AUD-ARC-10 — Fermé avec réserve — La collecte HTTP est dispersée dans sept clients répartis sur cinq modules
- AUD-ARC-11 — Fermé — Aucun objet-dieu (max. 586 lignes, InventoryQueries) ; impact (3 434 l.) porte aussi la recherche plein texte que le package-info attribue à signal
- AUD-ARC-19 — Fermé avec réserve — L'événement AlerteEmise est publié en cinq points sans aucun observateur
- AUD-QUA-08 — Fermé avec réserve — Le typage entre modules repose encore sur des chaînes : niveau d'impact en String alors que l'énumération existe, clé composite découpée par split(":"), statuts d'import et de corrélation en chaînes avec défaut silencieux
- AUD-QUA-09 — Fermé avec réserve — Le nommage mélange français et anglais sans convention, jusque dans un même concept et un même identifiant
- AUD-QUA-10 — Fermé avec réserve — Horloge commune injectée : plus aucun now() Java hors du paquet horloge (63 au 21/11), règle ArchUnit HorlogeInjecteeTest ; une expiration d'invitation reste calculée par now() SQL et comparée à l'horloge Java
- AUD-QUA-11 — Fermé avec réserve — Des exécuteurs de threads statiques, créés hors du cycle de vie CDI, ne sont jamais arrêtés
- AUD-QUA-18 — Fermé avec réserve — La taille du serveur progresse encore (22 fichiers Java de plus de 300 lignes, 21 au 21/11, 34 337 lignes) ; 7 méthodes de complexité ≥ 15, dont ValidateurManifeste.erreurs (CC 32, 74 lignes) inchangée, et aucun seuil vérifié
- AUD-QUA-19 — Fermé — Les fichiers TypeScript de e2e/ sont vérifiés en mode strict par la CI (tsc sans erreur, 3 « as unknown as » contre 17)
- AUD-QUA-23 — Fermé avec réserve — Le résumé IA indisponible est journalisé sans sa pile : MoteurAlertes passe la cause en argument de format, à l'inverse de la règle suivie par le reste du lot superviseur
- AUD-SEC-04 — Fermé — Alertes e-mail envoyées à l'adresse du jeton même non vérifiée
- AUD-SEC-06 — Fermé avec réserve — Mot de passe de base de données par défaut « argos » si .env absent
- AUD-SEC-09 — Fermé — Recherche plein texte et veille personnelle (/me/veille) sans limite de débit
- AUD-SEC-23 — Fermé — Desktop : l'écoute de bouclage 127.0.0.1 accepte le premier GET /argos/retour sans comparer le state et se ferme (interruption de connexion par un processus local)
- AUD-SEC-24 — Fermé — Campagne de performance e2e : serveur lancé par « mvn.cmd » via le PATH avec shell: true sous Windows, contrairement à la règle des chemins absolus
- AUD-SEC-25 — Fermé avec réserve — Image : code de l'application modifiable par l'utilisateur d'exécution et conteneurs sans durcissement (read_only, cap_drop, no-new-privileges)
- AUD-TST-02 — Fermé avec réserve — Seuil de couverture JaCoCo bloquant en CI (85 % des lignes, 70 % des branches, global et par module)
- AUD-TST-03 — Fermé avec réserve — Pyramide redressée : 49,8 % des méthodes de test serveur (507 sur 1 018) exigent encore Docker ; profil sans-docker non exécuté en CI
- AUD-TST-04 — Fermé avec réserve — Couverture par module contrôlée en CI ; tous les modules ont des classes de test dédiées
- AUD-TST-06 — Fermé avec réserve — Assertions dépendantes de l'horloge réelle et de délais fixes (sleep 5 s simulé, sleep 1 s, attente active 5 s, marge de 900 ms sur le délai IA)
- AUD-TST-08 — Fermé avec réserve — Isolation entre tests par liste TRUNCATE codée en dur ; six tables jamais vidées
- AUD-TST-09 — Fermé avec réserve — IHM : client API (10 tests) et 13 vues sur 13 testés en rendu (Vitest + happy-dom), en plus de 218 tests node --test
- AUD-TST-10 — Fermé — Desktop : main.js, fenêtre et réglages d'emballage testés (main.test.js, fenetre.test.js, reglages-emballes.test.js)
- AUD-TST-12 — Fermé avec réserve — Pas de tests d'intégration sur l'artefact empaqueté (skipITs=true, aucun *IT ni @QuarkusIntegrationTest)
- AUD-TST-19 — Fermé avec réserve — Build incrémental non fiable et tests exécutés sur un JDK différent de la production (JDK 24 local, JRE 17 en image)
- AUD-TST-20 — Fermé avec réserve — Campagne E2E (37 tests, 15 fichiers) automatisée en CI contre l'image construite ; aucune preuve d'exécution consultable
- AUD-TST-23 — Fermé avec réserve — L'audit d'accessibilité n'a pas suivi les écrans livrés après le 17/11 : vue d'inventaire, actions sur les abonnements et traitement des impacts sans mesure de contraste ni revue
- AUD-TST-25 — Fermé — PR #28 : quatre chemins publics ajoutés à la politique d'accès HTTP (/, /index.html, /assets/*, /licences-polices.txt) sans aucun test, ni de l'ouverture ni du maintien du 401 sur l'API
- AUD-TST-26 — Fermé avec réserve — argos-desktop sans vérification de types : check:syntax ne contrôle que la syntaxe ; un tsc --checkJs non strict relève 8 erreurs, dont 2 annotations JSDoc invalides sur les dépendances des canaux IPC
- AUD-PER-05 — Fermé — Le radar charge, pour chaque espace, les composants de tous les espaces puis filtre en Java, et lit le calendrier composant par composant
- AUD-PER-07 — Fermé avec réserve — Recherche plein texte : bonne conception (tsvector généré + GIN), mais toute la sélection est matérialisée, classée et comptée à chaque page
- AUD-PER-09 — Fermé avec réserve — Index manquants : clés étrangères component_id (suppressions en cascade) et colonnes de filtre des listes d'exploitation
- AUD-PER-11 — Fermé avec réserve — Réponses des sources d'avis et d'enrichissement lues en mémoire sans plafond de taille
- AUD-PER-12 — Fermé avec réserve — Réglages JVM et pool : heap à 75 % d'une limite de 1 Gio, sans ExitOnOutOfMemoryError, et pool de connexions laissé aux valeurs par défaut
- AUD-PER-13 — Fermé — Collecte d'activité des dépôts : lecture de l'échéance projet par projet et appels de forge séquentiels
- AUD-PER-19 — Fermé — L'import quotidien réécrit et recorrèle chaque nuit tout l'inventaire de chaque projet à dépôt, même si la SBOM ou les manifestes n'ont pas changé
- AUD-PER-20 — Fermé avec réserve — Régression de l'import d'inventaire : les composants sont insérés un par un (un aller-retour SQL par composant), alors qu'ils l'étaient par lot à l'audit initial
- AUD-PER-26 — Fermé — L'index V21 (updated_at DESC, id) ne porte ni l'espace ni l'état : pour un appelant qui a peu d'impacts actifs, « Ma veille » parcourt les impacts de tous les espaces, résolus compris (22,9 ms mesurés pour 40 000 lignes écartées, coût croissant avec la table)
- AUD-PER-27 — Fermé avec réserve — Pendant l'attente de reprise (jusqu'à PT5M, 5 tentatives), le run de synchronisation passe RETRYABLE et sort de l'exclusion sync_run_one_running : un lancement manuel ou l'échéance horaire le reprend pendant que le premier fil dort, puis les deux fils synchronisent la même source sur le même run
- AUD-PER-28 — Fermé avec réserve — Profil idp-local : Keycloak ajoute un conteneur de 1 Gio et partage le PostgreSQL plafonné à 512 Mio, non réglé (max_connections par défaut). La somme des plafonds atteint 5,5 Gio avec le profil ia (2,5 Gio sans) sur un DS1522+ livré avec 8 Go
- AUD-PER-29 — Fermé — project_impact porte 8 index, dont 2 redondants : project_impact_signal (signal_id) et project_impact_project (project_id) sont couverts par project_impact_signal_etat et project_impact_projet_etat, ajoutés par V20 sans retirer les anciens
- AUD-DEP-05 — Fermé avec réserve — Veille automatisée par Renovate en place ; Electron 45.4.1 (45.5.0 publiée le 24/11), aucun scan CVE des dépendances Maven
- AUD-DEP-06 — Décision documentée — Images Docker en tags flottants sans digest : node:22-slim, maven:3.9-eclipse-temurin-17, eclipse-temurin:17-jre, postgres:18
- AUD-DEP-08 — Fermé avec réserve — Conteneurs argos et ollama sans healthcheck, pas de HEALTHCHECK dans le Dockerfile ni de rotation des journaux Docker
- AUD-DEP-19 — Fermé — Licences : contrôle automatisé et avis livrés pour le serveur ; IHM livrée (vue, vue-router) sans avis MIT dans l'image ni l'installeur, aucun contrôle npm
- AUD-DEP-20 — Fermé avec réserve — Migration V13 modifiée sur place avant fusion, sans garde contre la réécriture d'une migration déjà fusionnée
- AUD-DEP-22 — Fermé — Le script de contrôle des licences (440 lignes, refactorisé deux fois dans le delta) n'a aucun test automatisé et ne s'exécute qu'au build de l'image, hors de « mvn verify »
- AUD-DEP-27 — Fermé avec réserve — Profil idp-local sans garde sur ses variables obligatoires : mots de passe initial et d'administration Keycloak vides par défaut et seulement commentés dans .env.example, 3 variables Keycloak de compose.yaml absentes de .env.example, import du royaume exécuté une seule fois
- AUD-ARC-01 — Info — Graphe inter-modules acyclique (45 arêtes métier, 46 vers les cinq modules transverses) et conforme à la table ModuleBoundariesTest
- AUD-ARC-08 — Info — Le module api reste une couche d'exposition mince, sans accès SQL
- AUD-ARC-13 — Info — Les couches de l'IHM et la séparation processus principal/IHM du desktop sont respectées
- AUD-ARC-18 — Info — Les quatre nouveaux sous-paquets et les nouvelles arêtes du lot réglementaire sont couverts par ModuleBoundariesTest, déclarés « table d'abord » — **borne un risque**
- AUD-ARC-24 — Info — L'ouverture de l'IHM servie se limite à quatre chemins statiques ; l'API et /q/openapi restent protégés — **borne un risque**
- AUD-ARC-26 — Info — Le sprint 5 a ajouté cinq garde-fous d'architecture testés : protocole RLS unique, propriété des tables, styles d'événements, fabrique HTTP unique et contrat client IHM — **borne un risque**
- AUD-QUA-14 — Info — La gestion d'erreurs côté serveur est saine : aucun catch vide, aucun printStackTrace, interruptions rétablies, messages exposés assainis
- AUD-QUA-15 — Info — Les ressources JDBC sont systématiquement fermées (try-with-resources), y compris le cas particulier du verrou consultatif
- AUD-QUA-16 — Info — Typage TypeScript strict tenu dans argos-ui (vue-tsc sans erreur, un seul contournement)
- AUD-QUA-30 — Info — Ce qui tient sur le delta : aucune exception nouvellement avalée ni cause nouvellement perdue sur 232 commits, horloge, transactions, exécuteurs et code mort verrouillés par des tests d'architecture, typage strict de argos-ui et e2e en CI — **borne un risque**
- AUD-SEC-10 — Info — Aucun secret réel versionné ni dans l'historique git
- AUD-SEC-11 — Info — Requêtes SQL paramétrées ; les seules concaténations portent sur des constantes
- AUD-SEC-12 — Info — Authentification : jeton OIDC obligatoire partout, audience vérifiée, aucun mode sans authentification — **borne un risque**
- AUD-SEC-13 — Info — Autorisation par espace vérifiée dans chaque ressource REST, doublée par la RLS PostgreSQL (pas d'IDOR trouvé) — **borne un risque**
- AUD-SEC-14 — Info — Garde SSRF de la veille web complète hors rebinding (plages, IPv6 embarquées, ports, redirections, tailles) — **borne un risque**
- AUD-SEC-15 — Info — XXE neutralisé dans la lecture RSS/Atom — **borne un risque**
- AUD-SEC-16 — Info — Serveur MCP : authentification, limitation et balisage du contenu externe en place (8 outils) — **borne un risque**
- AUD-SEC-17 — Info — Passerelle IA : données filtrées, sortie validée, fournisseur non pilotable par l'utilisateur — **borne un risque**
- AUD-SEC-18 — Info — Client Electron durci — **borne un risque**
- AUD-SEC-19 — Info — IHM Vue sans rendu HTML brut des contenus de veille
- AUD-SEC-20 — Info — Conteneurs et exposition réseau raisonnables ; CSRF non applicable — **borne un risque**
- AUD-SEC-21 — Info — Delta du lot 4 conforme : RLS sur les suivis, sources réservées à l'administrateur et collectées après vérification des conditions, hôtes data.culture filtrés, liens externes assainis — **borne un risque**
- AUD-SEC-26 — Info — Flux OIDC Authorization Code + PKCE de l'IHM et du desktop conforme (ADR-0033) : PKCE S256, state vérifié en temps constant, iss contrôlé, jetons en mémoire, redirections internes filtrées — **borne un risque**
- AUD-SEC-27 — Info — Veille réglementaire et inventaire : archives JORF bornées sans XXE ni écriture disque, routes réglementaires et impacts doublés par la RLS, aucune route d'import de SBOM — **borne un risque**
- AUD-SEC-28 — Info — Outillage e2e confiné à la boucle locale : secrets de test seulement, sources simulées sur 127.0.0.1/::1 sans traversée, docker/git/taskkill par chemin absolu — **borne un risque**
- AUD-SEC-32 — Info — Installation locale Keycloak (PR #28, #30) : aucun mot de passe par défaut pour l'administration Keycloak ni pour le compte initial, inscription libre désactivée, ports publiés sur 127.0.0.1, interface de gestion non publiée, règle publique Quarkus limitée aux fichiers statiques — **borne un risque**
- AUD-SEC-38 — Info — Workflows GitHub Actions conformes : jeton en lecture seule par défaut, 7 actions toutes épinglées par empreinte, aucun pull_request_target, aucune expression d'événement dans un script — **borne un risque**
- AUD-SEC-39 — Info — Configuration de l'IHM à l'exécution (ADR-0038) sans surface d'injection : émetteur et client lus en JSON depuis /api/v1/system/info, valeurs de l'exploitant, aucun rendu HTML — **borne un risque**
- AUD-TST-13 — Info — argos-ui : npm test passe (276/276 : 218 node --test, 58 Vitest), vue-tsc et le build passent
- AUD-TST-14 — Info — argos-desktop : npm run check passe (syntaxe, types, 92/92)
- AUD-TST-15 — Info — Aucun test désactivé ni focalisé ; un seul test conditionnel, volontaire et documenté
- AUD-TST-16 — Info — Hygiène des tests serveur solide : ports aléatoires, sources externes simulées, attente et délais simulés, courrier simulé
- AUD-TST-17 — Info — Sécurité, MCP, alerting et ingestion disposent de tests ciblés nombreux (MCP 47, comptes 59, alerting 56, ingestion web 41)
- AUD-TST-18 — Info — ModuleBoundariesTest couvre les 21 modules avec liste fermée ; exécuté par la CI (mvn verify)
- AUD-TST-21 — Info — Campagne d'accessibilité des mises en page rejouée hors du poste du mainteneur : 29/29 en 50 s, sans serveur ni base ; lancée par la CI
- AUD-TST-22 — Info — Nouveaux tests structurants dans le delta : couverture RLS générique, contrats SCM enregistrés, migrations, pont preload et fonctions pures de la campagne de performance
- AUD-TST-27 — Info — Tests du delta du 21/11 toujours actifs : règle ArchUnit de lecture groupée, comparaison de « Ma veille » à un calcul de référence, contrôle de nommage testé (75 tests de scripts)
- AUD-TST-31 — Info — Les gates déclarés par la CI sont atteignables : chaque étape appelle une commande ou un script versionné, et tous les gates Node rejoués hors du poste du mainteneur passent — **borne un risque**
- AUD-PER-14 — Info — Les tâches planifiées sont protégées contre le chevauchement et idempotentes en base — **borne un risque**
- AUD-PER-15 — Info — Les appels IA sont bornés : exécuteurs dédiés à file bornée, délais maximaux, taille de réponse plafonnée, Ollama configuré à un modèle et une requête à la fois — **borne un risque**
- AUD-PER-16 — Info — Clients HTTP sortants : délais de connexion et de requête configurés partout, sans redirection ; caches KEV et CERT-FR avec durée de vie — **borne un risque**
- AUD-PER-17 — Info — Bundle de l'IHM encore léger : 240,6 kB, dont 79,0 kB gzippé, servi compressé
- AUD-PER-23 — Info — L'empreinte du contenu normalisé évite les versions et les événements superflus : une resynchronisation horaire d'un avis inchangé ne relance ni l'enrichissement, ni la corrélation, ni les alertes — **borne un risque**
- AUD-PER-24 — Info — La collecte JORF est bornée : archive de 50 Mio au plus, décompression en flux plafonnée à 500 Mio et 50 000 entrées, fichier XML de 5 Mio au plus, débit limité à 10 requêtes par minute — **borne un risque**
- AUD-PER-36 — Info — Garde-fous de performance actifs : nombre d'instructions SQL constant vérifié en CI pour la corrélation, les alertes et le radar, et campagne p95 versionnée sur un jeu à trois espaces, 500 000 impacts résolus et abonnements, entièrement dans la cible — **borne un risque**
- AUD-DEP-11 — Info — npm audit argos-desktop : 6 vulnérabilités modérées (minimatch GHSA-g3hv-75wg-m249, déni de service) via electron-builder, outil de build uniquement — **borne un risque**
- AUD-DEP-12 — Info — argos-ui : aucune vulnérabilité dans les dépendances livrées (vue 3.5.46, vue-router 5.5.1) ; 5 modérées dans l'outillage de test (@vue/test-utils → glob/minimatch)
- AUD-DEP-13 — Info — Quarkus 3.40.1.2 sur le flux LTS courant (3.40) ; Java 17 compatible jusqu'à la fin des 3.x
- AUD-DEP-16 — Info — Conteneurisation et assemblage Compose solides : multi-étapes, JRE seul, utilisateur non root, mem_limit, restart, healthcheck PostgreSQL, ports locaux par défaut
- AUD-DEP-17 — Info — Configuration par profils correcte : prod sans valeurs par défaut pour la base et l'OIDC, mailer simulé seulement en dev/test, STARTTLS exigé, aucun secret réel versionné — **borne un risque**
- AUD-DEP-18 — Info — Licences des dépendances livrées compatibles avec ADR-0021 (MIT/Apache-2.0/BSD) ; licences restrictives uniquement dans l'outillage de build
- AUD-DEP-23 — Info — Le script de contrôle des licences rejoué sur des inventaires factices donne les bons codes de sortie (1 GPL ou licence absente, 2 BOM manquant, 0 conforme)
- AUD-DEP-28 — Info — Profil Keycloak local bien assemblé : image épinglée par version exacte, healthcheck conforme à la recette officielle (bash /dev/tcp sur le port de gestion 9000), port lié à 127.0.0.1, mémoire bornée, dépendance facultative d'Argos, scripts montés forcés en LF — **borne un risque**
- AUD-DEP-34 — Info — CI et Renovate en place et atteignables : actions épinglées par empreinte, jeton en lecture seule, bash -eo pipefail, gates réels (mvn clean verify avec JaCoCo, tests IHM/desktop/scripts, npm audit high, licences npm et serveur, parité de configuration, royaume, migrations, e2e sur l'image) — **borne un risque**
- AUD-DOC-02 — Fermé avec réserve — arc42 (§1, §2, §3 D-01, §5.2, §7, §10, §11) et les deux registres de risques divergents ne suivent pas les ADR 0025 à 0033 ; §3 et §7 citent encore Claude API et l'IdP d'entreprise
- AUD-DOC-03 — Fermé avec réserve — Le guide de déploiement mène encore à une installation qui ne démarre pas hors du profil idp-local : parcours Synology et parcours PC de base sans étape d'identité, tableau des réglages sans ARGOS_OIDC_URL ni ARGOS_ADMIN_EMAIL, phrase « l'authentification n'est pas en place » maintenue
- AUD-DOC-19 — Fermé avec réserve — Après l'archivage des cinq derniers changements (#29), le registre des spécifications et sept fiches décrivent encore ces capacités comme « proposées », « non archivées », sans spec de référence ; l'inventaire OpenSpec annonce 11 specs, 5 changements actifs et 11 archivés contre 18, 0 et 16
- AUD-DOC-05 — Fermé avec réserve — README racine, démarrage local : Node.js 22.12 annoncé contre ^22.18.0 exigé, Docker présenté comme requis pour les seuls tests alors que quarkus:dev démarre Keycloak, aucune mention de la connexion (royaume de test, comptes), e2e réduit au « portail web »
- AUD-DOC-06 — Fermé avec réserve — Le diagramme D-19 (arc42 §6.6, veille réglementaire) ne se rend pas : un « ; » dans un message de séquence casse l'analyse Mermaid, et le contrôle documentaire passe parce que le rendu est facultatif
- AUD-DOC-10 — Fermé avec réserve — Le contrôle documentaire laisse passer des chemins périmés : références en code en ligne vers trois changements archivés (ADR-0027, ADR-0028), README d'argos-server et d'e2e hors périmètre, aucune exécution automatique
- AUD-DOC-11 — Fermé avec réserve — Reliquat d'outillage Archgate : un second dossier d'ADR avec l'exemple non rempli GEN-001 et une documentation de règles de lint renvoyant à un fichier absent
- AUD-DOC-20 — Fermé avec réserve — arc42 §10 (Q-05) et le scénario QS-06 donnent encore GET /api/v1/me/veille hors cible (p95 = 533,8 ms, 15 opérations sur 16) alors que le rapport de campagne du 2026-11-20 mesure 85 ms et 16 opérations sur 16 dans la cible
- AUD-DOC-23 — Fermé — La PR #28 n'est pas reportée dans l'architecture : D-11 dit Keycloak hors de compose.yaml, D-11 et le guide (« Deux conteneurs démarrent ») ignorent keycloak, le README ne cite ni deploiement/ ni le profil idp-local
- AUD-DOC-13 — Info — Liens et ancres sains dans tout le dépôt (213 fichiers, 512 liens) ; le contrôle documentaire passe à b142695 sans l'option --rendu (avec : 1 erreur, AUD-DOC-06)
- AUD-DOC-14 — Info — Statuts des 33 ADR identiques dans les en-têtes, l'index adr/README.md et arc42 §9.1 ; §9.3 reporte les questions tranchées par ADR-0031 et ADR-0032
- AUD-DOC-15 — Info — Schéma et tâches planifiées documentés à jour : migrations V1 à V21 listées par arc42 §8.7 et D-13, treize tâches @Scheduled décrites
- AUD-DOC-16 — Info — e2e/README.md et argos-server/README.md exacts par rapport au code
- AUD-DOC-17 — Info — Contrats cités : outils MCP cohérents ; chiffres de performance et inventaire OpenSpec devenus incohérents (AUD-DOC-19, AUD-DOC-20)
- AUD-DOC-18 — Info — Traçabilité OpenSpec transparente : les cinq tâches ouvertes des changements archivés le 2026-11-20 portent une décision datée et une recette R-1 à R-5
