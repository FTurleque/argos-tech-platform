# Spécifications fonctionnelles ARGOS

Ce dossier contient la **documentation fonctionnelle durable** d'ARGOS : une fiche par capacité, ses diagrammes métier et le suivi de couverture des exigences. Il complète OpenSpec et arc42 sans les dupliquer.

## Répartition des responsabilités

| Support | Contenu | Emplacement |
|---|---|---|
| Specs de référence OpenSpec | exigences normatives **livrées**, scénarios | `/openspec/specs/<capability-path>/spec.md` |
| Changements OpenSpec | deltas d'exigences **proposés**, proposition, conception propre au changement, tâches, traçabilité | `/openspec/changes/<change-id>/` |
| Fiches capacité (ce dossier) | contexte fonctionnel durable : acteurs, règles, droits, diagrammes métier, questions | `/docs/specifications/<capability-path>.md` |
| Architecture consolidée | arc42, vues C4, diagrammes de conception maintenus | `/docs/architecture/` |
| ADR | décisions structurantes et leurs conséquences | `/docs/architecture/adr/` |

Règles :

- une exigence normative n'est écrite qu'une fois, dans OpenSpec ; les fiches la citent par identifiant ;
- la synchronisation ou l'archivage OpenSpec ne met à jour ni les fiches, ni arc42, ni les diagrammes : les tâches de chaque changement le prévoient ;
- un contenu introduit par un changement non archivé porte le statut [PROPOSITION] et l'identifiant du changement ;
- le guide d'utilisation du schéma est [`guide-openspec-detailed-uml.md`](guide-openspec-detailed-uml.md) ; le modèle de fiche est [`_modele-fiche-capacite.md`](_modele-fiche-capacite.md).

## Statuts des informations

| Statut | Signification | Équivalent arc42 |
|---|---|---|
| [EXIGENCE] | comportement attendu issu d'une exigence validée ou d'un ADR accepté | Accepté |
| [EXISTANT] | comportement existant vérifié par une preuve versionnée | Observé |
| [DÉCISION] | décision de conception prise | Accepté |
| [PROPOSITION] | proposition à valider, y compris ADR « Proposé » | Décision proposée / Hypothèse à valider |
| [QUESTION] | question ouverte | Non déterminé |

## Registre des capacités et des préfixes

Un préfixe n'est jamais réattribué ; un identifiant d'exigence retiré n'est jamais réutilisé.

| Capacité | Préfixe | Fiche | Spec de référence | Changement | Statut |
|---|---|---|---|---|---|
| `collecte-avis-securite` | `COL` | [fiche](collecte-avis-securite.md) | [spec](/openspec/specs/collecte-avis-securite/spec.md) | `formaliser-chaine-securite-poc` ; `automatiser-chaine-veille` (COL-09, COL-10) ; `2026-11-25-isoler-erreurs-aux-frontieres` (archivé, COL-11) ; `rendre-ingestion-asynchrone` (COL-04 modifiée, COL-12) | proposée |
| `inventaire-sbom` | `INV` | [fiche](inventaire-sbom.md) | [spec](/openspec/specs/inventaire-sbom/spec.md) | `formaliser-chaine-securite-poc` ; `automatiser-chaine-veille` (INV-07 à INV-10) ; `2026-11-25-isoler-erreurs-aux-frontieres` (archivé, INV-11) ; `alimenter-inventaire-et-completer-ihm` (INV-12, INV-13) | proposée |
| `correlation-impact` | `COR` | [fiche](correlation-impact.md) | absente | `formaliser-chaine-securite-poc` ; `automatiser-chaine-veille` (COR-09 à COR-12) | proposée |
| `abonnements-veille` | `ABO` | [fiche](abonnements-veille.md) | absente | `formaliser-chaine-securite-poc` ; `automatiser-chaine-veille` (ABO-07, ABO-08) | proposée |
| `performance-api-mcp` | `PERF` | [fiche](performance-api-mcp.md) | absente | `automatiser-chaine-veille` | proposée |
| `affichage-personnalise` | `AFF` | [fiche](affichage-personnalise.md) | [spec](/openspec/specs/affichage-personnalise/spec.md) | `2026-11-19-personnaliser-profils-et-affichage` (archivé) | livrée |
| `profils-veille` | `PRF` | [fiche](profils-veille.md) | [spec](/openspec/specs/profils-veille/spec.md) | `2026-11-19-personnaliser-profils-et-affichage` (archivé) ; `optimiser-ma-veille` (PRF-07) ; `durcir-autorisations-et-debits` (PRF-08) | livrée, dont PRF-07 et PRF-08 |
| `comptes-acces` | `ACC` | [fiche](comptes-acces.md) | [spec](/openspec/specs/comptes-acces/spec.md) | `2026-11-17-gerer-comptes-et-acces` (archivé) ; `2026-11-18-connecter-ihm-oidc` (archivé, ACC-07 à ACC-10) ; `rendre-portail-deployable` (ACC-11 à ACC-13) ; `durcir-autorisations-et-debits` (ACC-14) | livrée, dont ACC-11 à ACC-14 |
| `alertes-digests` | `ALR` | [fiche](alertes-digests.md) | [spec](/openspec/specs/alertes-digests/spec.md) | `2026-11-16-diffuser-alertes-et-digests` (archivé) ; `automatiser-chaine-veille` (ALR-07, proposé) ; `durcir-autorisations-et-debits` (ALR-08) ; `alimenter-inventaire-et-completer-ihm` (ALR-09) | livrée, dont ALR-08 ; ALR-09 implémentée, non archivée |
| `notifications-poste` | `NOT` | [fiche](notifications-poste.md) | absente | `notifier-sur-le-poste` | implémentée, non archivée |
| `serveur-mcp` | `MCP` | [fiche](serveur-mcp.md) | [spec](/openspec/specs/serveur-mcp/spec.md) | `exposer-serveur-mcp` ; `2026-11-25-isoler-erreurs-aux-frontieres` (archivé, MCP-08) | proposée |
| `passerelle-ia` | `IAG` | [fiche](passerelle-ia.md) | [spec](/openspec/specs/passerelle-ia/spec.md) | `2026-11-17-encadrer-appels-ia` (archivé) | livrée |
| `veille-reglementaire` | `REG` | [fiche](veille-reglementaire.md) | [spec](/openspec/specs/veille-reglementaire/spec.md) | `2026-11-17-suivre-veille-reglementaire` (archivé) ; `durcir-autorisations-et-debits` (REG-09) | livrée, dont REG-09 |
| `radar-standards` | `RAD` | [fiche](radar-standards.md) | [spec](/openspec/specs/radar-standards/spec.md) | `2026-11-16-construire-radar-et-standards` (archivé) ; `2026-11-25-isoler-erreurs-aux-frontieres` (archivé, RAD-07) ; `rendre-ingestion-asynchrone` (RAD-07 modifiée) ; `alimenter-inventaire-et-completer-ihm` (RAD-08, RAD-09) | livrée ; RAD-08 et RAD-09 implémentées, non archivées |
| `veille-rss-web` | `WEB` | [fiche](veille-rss-web.md) | [spec](/openspec/specs/veille-rss-web/spec.md) | `2026-11-16-collecter-veille-rss-web` (archivé) ; `2026-11-25-epingler-adresse-veille-web` (archivé, WEB-07) | livrée |
| `recherche-veille` | `RCH` | [fiche](recherche-veille.md) | [spec](/openspec/specs/recherche-veille/spec.md) | `2026-11-18-rechercher-et-filtrer` (archivé) ; `durcir-autorisations-et-debits` (RCH-06) | livrée, dont RCH-06 |
| `activite-projets` | `PIN` | [fiche](activite-projets.md) | [spec](/openspec/specs/activite-projets/spec.md) | `2026-11-17-mesurer-activite-projets` (archivé) ; `alimenter-inventaire-et-completer-ihm` (PIN-06) | livrée ; PIN-06 implémentée, non archivée |
| `profil-artiste` | `ART` | [fiche](profil-artiste.md) | [spec](/openspec/specs/profil-artiste/spec.md) | `2026-11-17-ajouter-profil-artiste` (archivé) ; `alimenter-inventaire-et-completer-ihm` (ART-07) | livrée ; ART-07 implémentée, non archivée |
| `exploitation-plateforme` | `EXP` | [fiche](exploitation-plateforme.md) | [spec](/openspec/specs/exploitation-plateforme/spec.md) | `2026-11-24-fiabiliser-sauvegarde-et-roles-base` (archivé, EXP-01 à EXP-06) ; `completer-configuration-conteneurs` ; `2026-11-25-instrumenter-observabilite` (archivé, EXP-13 à EXP-15) ; `durcir-conteneurs-de-service` | livrée (EXP-01 à EXP-06, EXP-13 à EXP-15) ; suite implémentée ou proposée, non archivée |

## Inventaire des diagrammes métier

| ID | Type | Question | Fiche |
|---|---|---|---|
| DF-COL-1 | UML séquence | comment un avis devient-il un signal enrichi, et que se passe-t-il en erreur ? | [collecte-avis-securite](collecte-avis-securite.md#df-col-1--synchronisation-dune-source-de-vulnérabilités) |
| DF-COL-2 | organigramme | quelle décision pour chaque avis reçu ? | [collecte-avis-securite](collecte-avis-securite.md#df-col-2--traitement-dun-avis-reçu) |
| DF-COL-3 | UML états | comment une source passe-t-elle en `DEGRADED` et en sort-elle ? | [collecte-avis-securite](collecte-avis-securite.md#df-col-3--état-de-synchronisation-dune-source) |
| DF-INV-1 | UML séquence | comment une SBOM remplace-t-elle l'inventaire ? | [inventaire-sbom](inventaire-sbom.md#df-inv-1--import-dune-sbom) |
| DF-INV-2 | organigramme | d'où provient l'inventaire et quelle confiance en découle ? | [inventaire-sbom](inventaire-sbom.md#df-inv-2--origine-de-linventaire-dun-projet) |
| DF-INV-3 | UML séquence | comment choisir un projet et parcourir ses composants inventoriés sans voir un autre espace ? | [inventaire-sbom](inventaire-sbom.md#df-inv-3--consultation-de-linventaire-dun-projet-choisi) |
| DF-INV-4 | UML classes | comment un projet regroupe-t-il ses dépôts, et comment son inventaire se déduit-il des leurs ? | [inventaire-sbom](inventaire-sbom.md#df-inv-4--projet-à-plusieurs-dépôts) |
| DF-COR-1 | UML classes | quels concepts la corrélation relie-t-elle ? | [correlation-impact](correlation-impact.md#df-cor-1--modèle-conceptuel-de-la-corrélation) |
| DF-COR-2 | UML séquence | comment un signal produit-il des impacts, avec ou sans IA ? | [correlation-impact](correlation-impact.md#df-cor-2--corrélation-dun-signal-avec-et-sans-ia) |
| DF-COR-3 | organigramme | quel niveau porte un impact ? | [correlation-impact](correlation-impact.md#df-cor-3--détermination-du-niveau-dun-impact) |
| DF-COR-4 | UML états | quels états de traitement un impact traverse-t-il, et qui fait chaque transition ? | [correlation-impact](correlation-impact.md#df-cor-4--état-de-traitement-dun-impact) |
| DF-ABO-1 | UML classes | comment les abonnements se rattachent-ils sans toucher aux droits ? | [abonnements-veille](abonnements-veille.md#df-abo-1--abonnements-équipes-et-autorisations) |
| DF-ABO-2 | UML séquence | dans quel ordre droits et abonnements s'appliquent-ils ? | [abonnements-veille](abonnements-veille.md#df-abo-2--restitution-de-la-veille-dun-utilisateur) |
| DF-ABO-3 | UML états | quels états un abonnement connaît-il, et qu'est-ce qui distingue suspension et suppression ? | [abonnements-veille](abonnements-veille.md#df-abo-3--état-dun-abonnement) |
| DF-PERF-1 | organigramme | comment une campagne de mesure aboutit-elle à un verdict et à un rapport, sans quitter le poste ? | [performance-api-mcp](performance-api-mcp.md#df-perf-1--une-campagne-de-mesure) |
| DF-AFF-1 | organigramme | quelle mise en page s'affiche à l'ouverture ? | [affichage-personnalise](affichage-personnalise.md#df-aff-1--mise-en-page-appliquée-à-louverture) |
| DF-PRF-1 | UML classes | de quoi un profil de veille est-il composé ? | [profils-veille](profils-veille.md#df-prf-1--ce-que-décrit-un-profil) |
| DF-PRF-2 | organigramme | comment se compose la veille d'un utilisateur à plusieurs profils ? | [profils-veille](profils-veille.md#df-prf-2--veille-dun-utilisateur-à-plusieurs-profils) |
| DF-ACC-1 | organigramme | que se passe-t-il lors de la première connexion d'une personne ? | [comptes-acces](comptes-acces.md#df-acc-1--première-visite-dun-utilisateur) |
| DF-ACC-2 | organigramme | que voit une personne qui ouvre une vue de l'IHM, selon sa session et la réponse de l'API ? | [comptes-acces](comptes-acces.md#df-acc-2--ouverture-dune-vue-de-lihm) |
| DF-ACC-3 | diagramme d'états | quand l'écoute de bouclage du client desktop se ferme-t-elle ? | [comptes-acces](comptes-acces.md#df-acc-3--écoute-de-bouclage-du-client-desktop) |
| DF-ALR-1 | organigramme | un élément part-il en alerte immédiate ou dans le résumé ? | [alertes-digests](alertes-digests.md#df-alr-1--alerte-immédiate-ou-résumé) |
| DF-NOT-1 | organigramme | quand une alerte s'affiche-t-elle en notification système ? | [notifications-poste](notifications-poste.md#df-not-1--affichage-dune-alerte-sur-le-poste) |
| DF-MCP-1 | organigramme | comment une question du développeur devient-elle une réponse sourcée ? | [serveur-mcp](serveur-mcp.md#df-mcp-1--une-question-posée-à-lassistant) |
| DF-IAG-1 | organigramme | dans quels cas un élément reçoit-il un résumé produit par l'IA ? | [passerelle-ia](passerelle-ia.md#df-iag-1--un-résumé-est-il-produit-) |
| DF-REG-1 | organigramme | quelles étapes un texte suit-il avant d'être diffusé ? | [veille-reglementaire](veille-reglementaire.md#df-reg-1--parcours-dun-texte-officiel) |
| DF-REG-2 | séquence | dans quel ordre les contrôles et l'appel à la forge s'enchaînent-ils pour une demande d'évolution ? | [veille-reglementaire](veille-reglementaire.md#df-reg-2--association-dune-demande-dévolution) |
| DF-RAD-1 | organigramme | que signifie chaque statut d'une technologie ? | [radar-standards](radar-standards.md#df-rad-1--statuts-du-référentiel) |
| DF-WEB-1 | organigramme | quand une adresse proposée est-elle acceptée ? | [veille-rss-web](veille-rss-web.md#df-web-1--acceptation-dune-adresse) |
| DF-RCH-1 | organigramme | dans quel ordre droits, mots et filtres s'appliquent-ils ? | [recherche-veille](recherche-veille.md#df-rch-1--une-recherche-filtrée) |
| DF-PIN-1 | organigramme | quand un pourcentage d'avancement s'affiche-t-il ? | [activite-projets](activite-projets.md#df-pin-1--affichage-dun-avancement) |
| DF-ART-1 | organigramme | à quelles conditions un appel est-il présenté comme une opportunité ? | [profil-artiste](profil-artiste.md#df-art-1--un-appel-est-il-proposé-à-lartiste-) |
| DF-EXP-1 | organigramme | quand une sauvegarde est-elle conservée, et que devient la rotation en cas d'échec ? | [exploitation-plateforme](exploitation-plateforme.md#df-exp-1--une-sauvegarde) |
| DF-EXP-2 | organigramme | dans quel ordre restaurer pour qu'un utilisateur retrouve son compte et ses espaces ? | [exploitation-plateforme](exploitation-plateforme.md#df-exp-2--une-restauration) |
| DF-EXP-3 | organigramme | quels contrôles composent `/q/health/ready` et lesquels peuvent rendre le serveur indisponible ? | [exploitation-plateforme](exploitation-plateforme.md#df-exp-3--composition-du-contrôle-de-disponibilité) |

Les diagrammes d'architecture (D-01 à D-22) sont inventoriés dans [`/docs/architecture/diagrams/README.md`](../architecture/diagrams/README.md) ; les diagrammes de conception d'un changement (DC-*) restent dans son `design.md`.

## Bilan de couverture

État au 2026-11-20.

### Inventaire OpenSpec

| Élément | Nombre | Détail |
|---|---:|---|
| Specs de référence | 11 | `comptes-acces`, `recherche-veille`, `affichage-personnalise`, `profils-veille`, `alertes-digests`, `veille-rss-web`, `radar-standards`, `passerelle-ia`, `activite-projets`, `profil-artiste`, `veille-reglementaire` ; le code des changements actifs est présent dans `argos-server`, `argos-ui` et `argos-desktop` |
| Changements actifs | 5 | `formaliser-chaine-securite-poc`, `notifier-sur-le-poste`, `exposer-serveur-mcp`, `automatiser-chaine-veille`, `optimiser-ma-veille` (schéma `detailed-uml`) |
| Changements archivés | 11 | `2026-11-16-diffuser-alertes-et-digests`, `2026-11-16-collecter-veille-rss-web`, `2026-11-16-construire-radar-et-standards`, `2026-11-17-encadrer-appels-ia`, `2026-11-17-mesurer-activite-projets`, `2026-11-17-ajouter-profil-artiste`, `2026-11-17-suivre-veille-reglementaire`, `2026-11-17-gerer-comptes-et-acces`, `2026-11-18-connecter-ihm-oidc`, `2026-11-18-rechercher-et-filtrer`, `2026-11-19-personnaliser-profils-et-affichage` |

Tous les changements utilisent le schéma `detailed-uml` : aucun changement d'un autre schéma n'est à migrer.

### Couverture du lot 1 (`formaliser-chaine-securite-poc`)

| Capacité | Exigences | Scénarios | Diagrammes métier | Questions ouvertes | Tests écrits |
|---|---:|---:|---:|---:|---:|
| `collecte-avis-securite` | 8 | 18 | 3 | 2 | 22 |
| `inventaire-sbom` | 6 | 14 | 3 | 3 | 27 |
| `correlation-impact` | 8 | 17 | 3 | 3 | 37 |
| `abonnements-veille` | 6 | 10 | 2 | 3 | 10 |
| **Total** | **28** | **59** | **11** | **11** | **96** |

« Questions ouvertes » : Q-COL-1, Q-COL-2, Q-INV-1, Q-INV-2, Q-COR-2 et Q-ABO-1 sont levées par les décisions J1 à J7 du porteur du projet (2026-11-19), reprises par le changement `automatiser-chaine-veille` (voir « Couverture du lot 18 »).

Toutes les exigences sont au statut [PROPOSITION] : elles dérivent d'ADR « Proposé » ou de baselines « à valider » ; la formulation d'INV-06 dérive des décisions F1, F5 et G5 du porteur du projet (2026-11-19). Les tests écrits sont ceux d'`argos-server/src/test/java/io/argos/chain/` (hors `ProfilsDeVeilleTest`) et `architecture/SubscriptionIsolationTest.java`, exécutés par `mvn -B -f argos-server/pom.xml verify` avec des jetons réels du Keycloak des Dev Services et la RLS appliquée sous le rôle `argos_app`. Matrice détaillée : [`tasks.md` du changement](/openspec/changes/archive/2026-11-20-formaliser-chaine-securite-poc/tasks.md#matrice-de-traçabilité).

### Couverture de [`personnaliser-profils-et-affichage`](/openspec/changes/archive/2026-11-19-personnaliser-profils-et-affichage/proposal.md) (archivé)

| Capacité | Exigences | Scénarios | Diagrammes métier | Questions ouvertes | Tests écrits |
|---|---:|---:|---:|---:|---:|
| `affichage-personnalise` | 5 | 8 | 1 | 0 | 8 |
| `profils-veille` | 6 | 13 | 2 | 1 | 13 |
| **Total** | **11** | **21** | **3** | **1** | **21** |

Les exigences AFF-01 à AFF-05 et PRF-01 à PRF-06 sont au statut [EXIGENCE] dans les specs de référence `affichage-personnalise` et `profils-veille` (ADR-0023 et ADR-0024 Acceptés). « Tests écrits » compte les scénarios couverts par au moins un test ou un E2E qui passe : tous les scénarios AFF-01-S1 à AFF-05-S1 et PRF-01-S1 à PRF-06-S2 ; PRF-02-S1 l'est par `UnionProfilsTest`, `ProfilDemonstrationTest` et le parcours E2E « 3.4, 3.9 », preuve acceptée par le porteur du projet le 2026-11-19 (révision de D8). « Questions ouvertes » compte les questions sans réponse : Q-5 (publication d'un profil au catalogue). Matrice détaillée : [`tasks.md` du changement](/openspec/changes/archive/2026-11-19-personnaliser-profils-et-affichage/tasks.md#matrice-de-traçabilité).

### Couverture du lot 2

| Changement | Capacité | Exigences | Scénarios | Diagrammes métier | Questions ouvertes | Tests écrits |
|---|---|---|---|---|---|---|
| [`gerer-comptes-et-acces`](/openspec/changes/archive/2026-11-17-gerer-comptes-et-acces/proposal.md) (archivé) | `comptes-acces` | 6 | 11 | 1 | 0 | 37 |
| [`diffuser-alertes-et-digests`](/openspec/changes/archive/2026-11-16-diffuser-alertes-et-digests/proposal.md) (archivé) | `alertes-digests` | 6 | 9 | 1 | 2 | 26 |
| [`notifier-sur-le-poste`](/openspec/changes/archive/2026-11-20-notifier-sur-le-poste/proposal.md) | `notifications-poste` | 6 | 7 | 1 | 0 | 56 |
| **Total** | | **18** | **27** | **3** | **2** | **119** |

Dans ce tableau, « Tests écrits » compte les méthodes de test qui passent : `comptes-acces` (11 scénarios couverts, dont la couverture RLS du schéma par `CouvertureRlsTest`), `alertes-digests` (9 scénarios couverts, classes `AlertesTest`, `EchecSmtpTest`, `RelaisMuetTest` et `RegleAlerteTest`) et `notifications-poste` (7 scénarios couverts par des tests unitaires et d'intégration simulée : `argos-desktop/test/`, `argos-ui/tests/alertes-poste.test.mjs` et `argos-ui/tests/reprise-poste.test.mjs`, et 4 parcours E2E de l'application Electron réelle, `e2e/desktop/notifications.spec.ts`).

### Couverture du lot 3

| Changement | Capacité | Exigences | Scénarios | Diagrammes métier | Questions ouvertes | Tests écrits |
|---|---|---|---|---|---|---|
| [`exposer-serveur-mcp`](/openspec/changes/archive/2026-11-20-exposer-serveur-mcp/proposal.md) | `serveur-mcp` | 7 | 9 | 1 | 0 | 9 |
| [`encadrer-appels-ia`](/openspec/changes/archive/2026-11-17-encadrer-appels-ia/proposal.md) (archivé) | `passerelle-ia` | 5 | 6 | 1 | 0 | 6 |
| [`suivre-veille-reglementaire`](/openspec/changes/archive/2026-11-17-suivre-veille-reglementaire/proposal.md) (archivé) | `veille-reglementaire` | 8 | 13 | 1 | 0 | 13 |
| **Total** | | **20** | **28** | **3** | **0** | **28** |

Pour `exposer-serveur-mcp`, « Tests écrits » compte les scénarios MCP-01-S1 à MCP-07-S1 couverts par un test qui passe (`argos-server/src/test/java/io/argos/mcp/`) ; MCP-02-S1 s'appuie sur le référentiel des standards et le calendrier réels (`construire-radar-et-standards`) ; MCP-07-S1 sur les fiches réglementaires réelles (`suivre-veille-reglementaire`). MCP-02-S1, MCP-03-S1, MCP-04-S1 et MCP-05-S1 sont aussi parcourus de bout en bout, et le journal des appels de MCP-06 y est contrôlé, au niveau du protocole par `e2e/mcp/parcours-mcp.spec.ts` (client configuré par `e2e/donnees/mcp.json`) ; la tâche 5.2, qui exige Claude Code réel, reste ouverte. Matrice détaillée : [`tasks.md` du changement](/openspec/changes/archive/2026-11-20-exposer-serveur-mcp/tasks.md#matrice-de-traçabilité).

Pour `suivre-veille-reglementaire`, « Tests écrits » compte les scénarios REG-01-S1 à REG-08-S1 couverts par un test qui passe, avec les archives JORF, l'API Ollama et l'API GitHub simulées par un serveur HTTP local (`LecteurJorfTest`, `CollecteReglementaireTest`, `QualificationEtDatesTest`, `VeilleReglementaireTest`, `ParcoursReglementaireTest`) ; les quatre questions sont répondues (2026-11-17). Matrice détaillée : [`tasks.md` du changement](/openspec/changes/archive/2026-11-17-suivre-veille-reglementaire/tasks.md#matrice-de-traçabilité).

Pour `encadrer-appels-ia`, « Tests écrits » compte les scénarios IAG-01-S1 à IAG-05-S1 couverts par un test qui passe, avec l'API Ollama simulée par un serveur HTTP local (`PasserelleIaTest`, `ParcoursIaTest`, `argos-ui/tests/resume-ia.test.mjs`) ; aucun modèle réel n'est exécuté. Matrice détaillée : [`tasks.md` du changement](/openspec/changes/archive/2026-11-17-encadrer-appels-ia/tasks.md#matrice-de-traçabilité).

### Couverture du lot 4

| Changement | Capacité | Exigences | Scénarios | Diagrammes métier | Questions ouvertes | Tests écrits |
|---|---|---|---|---|---|---|
| [`construire-radar-et-standards`](/openspec/changes/archive/2026-11-16-construire-radar-et-standards/proposal.md) (archivé) | `radar-standards` | 6 | 8 | 1 | 3 | 8 |
| [`collecter-veille-rss-web`](/openspec/changes/archive/2026-11-16-collecter-veille-rss-web/proposal.md) (archivé) | `veille-rss-web` | 6 | 8 | 1 | 0 | 8 |
| [`rechercher-et-filtrer`](/openspec/changes/archive/2026-11-18-rechercher-et-filtrer/proposal.md) (archivé) | `recherche-veille` | 5 | 7 | 1 | 0 | 7 |
| **Total** | | **17** | **23** | **3** | **3** | **23** |

Les exigences RCH-01 à RCH-05 sont au statut [EXIGENCE] dans la spec de référence `recherche-veille`. Pour `rechercher-et-filtrer`, « Tests écrits » compte les scénarios RCH-01-S1 à RCH-05-S1 couverts par un test qui passe (`argos-server/src/test/java/io/argos/api/recherche/`, `argos-server/src/test/java/io/argos/mcp/RechercheMcpTest.java`) ; RCH-05-S1 l'est par la mesure à la demande `MesurePerformanceRechercheTest`. Matrice détaillée : [`tasks.md` du changement](/openspec/changes/archive/2026-11-18-rechercher-et-filtrer/tasks.md#matrice-de-traçabilité).

### Couverture du lot 5

| Changement | Capacité | Exigences | Scénarios | Diagrammes métier | Questions ouvertes | Tests écrits |
|---|---|---|---|---|---|---|
| [`mesurer-activite-projets`](/openspec/changes/archive/2026-11-17-mesurer-activite-projets/proposal.md) (archivé) | `activite-projets` | 5 | 6 | 1 | 0 | 6 |
| [`ajouter-profil-artiste`](/openspec/changes/archive/2026-11-17-ajouter-profil-artiste/proposal.md) (archivé) | `profil-artiste` | 6 | 8 | 1 | 4 | 8 |
| **Total** | | **11** | **14** | **2** | **4** | **14** |

Les exigences des deux changements archivés de ce lot sont au statut [EXIGENCE] dans leur spec de référence ; la matrice de traçabilité de chaque changement est dans son `tasks.md`. Pour `ajouter-profil-artiste`, « Tests écrits » compte les scénarios ART-01-S1 à ART-06-S1 couverts par un test qui passe (`argos-server/src/test/java/io/argos/chain/ProfilArtisteTest.java`, `alerting/RappelsOpportunitesTest.java`, `impact/StrategieDisciplineTerritoireTest.java`) ; le parcours de la tâche 5.2 est `chain/ParcoursArtisteTest.java`. Matrice détaillée : [`tasks.md` du changement](/openspec/changes/archive/2026-11-17-ajouter-profil-artiste/tasks.md#matrice-de-traçabilité).

### Couverture du lot 7

| Changement | Capacité | Exigences | Scénarios | Diagrammes métier | Questions ouvertes | Tests écrits |
|---|---|---|---|---|---|---|
| [`connecter-ihm-oidc`](/openspec/changes/archive/2026-11-18-connecter-ihm-oidc/proposal.md) (archivé) | `comptes-acces` (ACC-07 à ACC-10) | 4 | 18 | 1 | 2 | 18 |

Les exigences ACC-07 à ACC-10 sont au statut [EXIGENCE] dans la spec de référence `comptes-acces`. Pour `connecter-ihm-oidc`, « Tests écrits » compte les scénarios couverts par au moins un test qui passe : tests `node:test` d'`argos-ui/tests/` (`oidc`, `session`, `autorisation`, `garde`, `securite-session`) et d'`argos-desktop/test/` (`connexion`, `canaux`, `preload`, `ui-protocol`), et parcours réel du client `argos-ui` contre le Keycloak des Dev Services (`ClientIhmOidcTest`). Les parcours de bout en bout du portail web (`e2e/tests/connexion.spec.ts`) et du client desktop Electron (`e2e/desktop/connexion.spec.ts`, ACC-10-S1, S2 et S4) passent avec `npm --prefix e2e test`. Matrice détaillée : [`tasks.md` du changement](/openspec/changes/archive/2026-11-18-connecter-ihm-oidc/tasks.md#matrice-de-traçabilité).

### Couverture du lot 18

| Changement | Capacité | Exigences | Scénarios | Diagrammes métier | Questions ouvertes | Tests écrits |
|---|---|---|---|---|---|---|
| [`automatiser-chaine-veille`](/openspec/changes/archive/2026-11-20-automatiser-chaine-veille/proposal.md) | `collecte-avis-securite` (COL-09, COL-10) | 2 | 9 | 0 (DF-COL-1, DF-COL-2 mis à jour) | 0 | 9 |
| | `inventaire-sbom` (INV-07 à INV-10) | 4 | 14 | 1 (DF-INV-4 ; DF-INV-1 mis à jour) | 0 | 14 |
| | `correlation-impact` (COR-09 à COR-12) | 4 | 15 | 1 (DF-COR-4) | 0 | 15 |
| | `abonnements-veille` (ABO-07, ABO-08) | 2 | 7 | 1 (DF-ABO-3) | 0 | 7 |
| | `alertes-digests` (ALR-07) | 1 | 2 | 0 | 0 | 2 |
| | `performance-api-mcp` (PERF-01, PERF-02) | 2 | 6 | 1 (DF-PERF-1) | 2 | 5 |
| **Total** | | **15** | **53** | **4** | **2** | **52** |

Exigences au statut [PROPOSITION], issues des décisions J1 à J8 du porteur du projet (2026-11-19). Volets A (collecte et abonnements), B (inventaire) et C (impacts et performance) implémentés puis intégrés le 2026-11-19 (branche `lot18/integration`, migrations V18 à V20) ; vérifications d'intégration faites : `mvn verify` (649 tests), tests de l'IHM, parcours E2E `e2e/tests/chaine-automatisee.spec.ts` (sources et forges simulées) et deux exécutions complètes de `npm --prefix e2e test`. « Tests écrits » compte les scénarios couverts par au moins un test qui passe ; PERF-01-S1 n'est pas atteint : `GET /api/v1/me/veille` reste hors cible (p95 = 533,8 ms sur la branche intégrée, [rapport](../architecture/quality/mesure-performance-api-mcp.md), [PROPOSITION] P-18). Tâche 3.1 décochée : la contrainte INV-C3 n'est pas créée ([PROPOSITION] P-13, à valider). « Questions ouvertes » : Q-PERF-1 et Q-PERF-2 (volume et concurrence de la campagne). Matrice détaillée : [`tasks.md` du changement](/openspec/changes/archive/2026-11-20-automatiser-chaine-veille/tasks.md#matrice-de-traçabilité).

### Couverture du lot 19

| Changement | Capacité | Exigences | Scénarios | Diagrammes métier | Questions ouvertes | Tests écrits |
|---|---|---|---|---|---|---|
| [`optimiser-ma-veille`](/openspec/changes/archive/2026-11-20-optimiser-ma-veille/proposal.md) | `profils-veille` (PRF-07) | 1 | 2 | 0 | 0 | 2 |
| **Total** | | **1** | **2** | **0** | **0** | **2** |

Exigence au statut [PROPOSITION], issue de la décision K2 du porteur du projet (2026-11-19), avec le jeu de référence de la décision K3. Implémentée le 2026-11-20 (branche `lot19/optimisation`, migration V21) : lecture groupée des signaux de « Ma veille » et index des impacts récents. « Tests écrits » compte les scénarios couverts par au moins un test qui passe : PRF-07-S1 par `LectureGroupeeSignauxTest` (absence de lecture d'un signal par avis) et par une mesure de diagnostic du 2026-11-20 (p95 = 60,1 à 82,7 ms, jeu K3), PRF-07-S2 par `VeilleLectureGroupeeTest`. La campagne de référence du 2026-11-20 (révision `7e5e36d`) le confirme : p95 = 85 ms, seize opérations sur seize dans la cible. Matrice détaillée : [`tasks.md` du changement](/openspec/changes/archive/2026-11-20-optimiser-ma-veille/tasks.md#matrice-de-traçabilité).

### Couverture du sprint 4 de l'audit du 2026-11-21

| Changement | Capacité | Exigences | Scénarios | Diagrammes métier | Questions ouvertes | Tests écrits |
|---|---|---|---|---|---|---|
| [`instrumenter-observabilite`](/openspec/changes/archive/2026-11-25-instrumenter-observabilite/proposal.md) | `exploitation-plateforme` (EXP-13 à EXP-15) | 3 | 11 | 1 | 1 | 11 |
| [`isoler-erreurs-aux-frontieres`](/openspec/changes/archive/2026-11-25-isoler-erreurs-aux-frontieres/proposal.md) | `collecte-avis-securite` (COL-11) | 1 | 3 | 0 | 0 | 4 |
| | `inventaire-sbom` (INV-11) | 1 | 2 | 0 | 0 | 2 |
| | `radar-standards` (RAD-07) | 1 | 3 | 0 | 0 | 3 |
| | `serveur-mcp` (MCP-08) | 1 | 2 | 0 | 0 | 2 |
| **Total** | | **7** | **21** | **1** | **1** | **22** |

Exigences au statut [PROPOSITION], implémentées le 2026-11-24, changements archivés le 2026-11-25. « Tests écrits » compte les méthodes de test qui couvrent un scénario : `observabilite/JournauxJsonTest`, `MetriquesEtSanteTest` et `BaseInjoignableTest` pour EXP-13 à EXP-15 ; `chain/FrontieresCollecteTest`, `ResilienceTest`, `ImportPlanifieTest`, `RadarStandardsTest` et `mcp/ErreursOutilsMcpTest` pour COL-11, INV-11, RAD-07 et MCP-08. Question ouverte : Q-3 d'`instrumenter-observabilite` (pile de collecte des métriques sur le NAS). Décisions du porteur du projet du 2026-11-24 : `/q/metrics` réservé à l'administrateur plateforme, retard d'une tâche au-delà de deux intervalles, identifiant de corrélation créé par le serveur, frontières d'erreur « isoler puis relancer », pas de type de panne dédié, composant au PURL mal encodé écarté.

### Couverture du sprint 7 de l'audit du 2026-11-21

| Changement | Capacité | Exigences | Scénarios | Diagrammes métier | Questions ouvertes | Tests écrits |
|---|---|---|---|---|---|---|
| [`alimenter-inventaire-et-completer-ihm`](/openspec/changes/alimenter-inventaire-et-completer-ihm/proposal.md) | `inventaire-sbom` (INV-12, INV-13) | 2 | 12 | 0 | 0 | 12 |
| | `radar-standards` (RAD-08, RAD-09) | 2 | 6 | 0 | 0 | 6 |
| | `activite-projets` (PIN-06) | 1 | 3 | 0 | 1 | 3 |
| | `profil-artiste` (ART-07) | 1 | 3 | 0 | 0 | 3 |
| | `alertes-digests` (ALR-09) | 1 | 3 | 0 | 0 | 3 |
| **Total** | | **7** | **27** | **0** | **1** | **27** |

Exigences au statut [PROPOSITION], issues des décisions du porteur du projet du 2026-11-26 (droits, producteur planifié des suggestions, test de contrat, Vitest) ; changement implémenté, non archivé. « Tests écrits » compte les scénarios couverts par au moins un test : `api/inventaire/AdministrationProjetsTest`, `ImportManuelTest`, `impact/SuggestionsRadarTest`, `projectintel/CollecteImmediateTest`, `alerting/LiensIhmTest` et `AlertesTest` côté serveur ; tests Vitest d'`argos-ui/tests/vues/` (inventaire, radar, profil artiste, signal) et `argos-ui/tests/liens-alertes.test.mjs` côté IHM ; PIN-06-S2 n'est couvert que pour un projet sans dépôt. Le client de l'IHM est vérifié contre l'instantané `argos-ui/src/api/openapi.json` (`argos-ui/tests/contrat-api.test.mjs`), lui-même comparé au schéma servi (`ContratClientIhmTest`) ; `MethodesAtteintesEnProductionTest` refuse toute méthode publique de `src/main` atteinte seulement par les tests. Diagrammes : séquences d'arc42 §6.8 (import manuel, D-21) et §6.9 (suggestions de l'IA, D-22). « Questions ouvertes » : Q-1 du changement (rôle minimal de la collecte immédiate, contributeur appliqué) ; Q-2 (seuil de couverture par module) ne porte sur aucune capacité. Matrice détaillée : [`tasks.md` du changement](/openspec/changes/alimenter-inventaire-et-completer-ihm/tasks.md#matrice-de-traçabilité).

### Couverture du sprint 8 de l'audit du 2026-11-21

| Changement | Capacité | Exigences | Scénarios | Diagrammes métier | Questions ouvertes | Tests écrits |
|---|---|---|---|---|---|---|
| [`rendre-ingestion-asynchrone`](/openspec/changes/rendre-ingestion-asynchrone/proposal.md) | `collecte-avis-securite` (COL-04 modifiée, COL-12) | 2 | 7 | 0 (DF-COL-1 mis à jour) | 2 | 7 |
| | `radar-standards` (RAD-07 modifiée) | 1 | 4 | 0 | 0 | 4 |
| **Total** | | **3** | **11** | **0** | **2** | **11** |

Exigences au statut [PROPOSITION], implémentées le 2026-11-26, changement non archivé. « Tests écrits » compte les méthodes de test qui couvrent un scénario : `chain/EnrichmentTest` (COL-04-S1 à S4), `chain/LancementSynchronisationTest` (COL-12-S1 à S3), `chain/RadarStandardsTest` (RAD-07-S1 à S4). Questions ouvertes : Q-1 (clé d'API NVD) et Q-2 (seuil de l'archive OSV) du changement. Décisions du porteur du projet du 2026-11-26 : observateurs asynchrones pour la chaîne radar et l'évaluation des alertes, suppression de l'événement `AlerteEmise`, réponse 409 pour une synchronisation en cours, ADR-0039 au statut Proposé.

### Capacités restant à spécifier

| Lot | Capacité pressentie | Composant(s) | Sources disponibles | Manques principaux |
|---|---|---|---|---|
| 5 | profil `comedien` | `catalog`, `impact-engine` | ADR-0024 ; fiche `profils-veille` | rubriques et sources (castings, droits voisins) |
| 5 | profil `politique` | `catalog`, `impact-engine` | ADR-0024 ; fiche `profils-veille` | sources officielles (Assemblée, Sénat, collectivités), neutralité |
| — | version hébergée en ligne | exploitation | ADR-0026 (Synology d'abord) | décision reportée par le porteur |

### Écarts documentaires relevés

| Écart | Traitement |
|---|---|
| Les ADR citent des exigences `F01`–`F16` absentes du dépôt | question Q-1 du changement ; aucune correspondance inventée |
| arc42 §6 annonçait les noms de §5 mais utilise d'autres participants | participants des diagrammes de §6 renommés avec les noms des composants de §5 ([DÉCISION] — porteur du projet, 2026-11-18 ; tâche 6.1) |
| Inventaire des diagrammes : D-05, D-11, D-12, D-13 annoncés, absents | D-05 ajouté en arc42 §5.7 ; D-11, D-12 et D-13 produits dans `docs/architecture/diagrams/` (tâche 6.2) |
| Glossaire : « impact probable, confirmé, résolu » vs niveaux ADR-0013 | levé : deux axes distincts, niveau de confiance et état de traitement ([DÉCISION] — porteur du projet, 2026-11-19, J5) ; glossaire aligné, changement `automatiser-chaine-veille` |
| Glossaire : « projet » parmi les termes ambigus | levé : un projet peut regrouper plusieurs dépôts, son inventaire est l'union des leurs ([DÉCISION] — porteur du projet, 2026-11-19, J6) ; glossaire aligné |

## Vérification

```bash
node scripts/verifier-docs.mjs
```

Le script contrôle les liens internes, les blocs Mermaid et les identifiants d'exigences cités ; voir le guide pour le détail et les limites.

### Couverture : déploiement du portail et durcissement des autorisations

| Changement | Capacité | Exigences | Scénarios | Diagrammes | Tests écrits |
|---|---|---|---|---|---|
| [`rendre-portail-deployable`](/openspec/changes/archive/2026-11-25-rendre-portail-deployable/proposal.md) | `comptes-acces` | ACC-11 à ACC-13 | 12 | DC-1, DC-2 (arc42 §7.3, D-20) | tous les scénarios sauf ACC-11-S1 et ACC-11-S2, dont la recette est consignée dans [`recette-poste.md`](../deploiement/recette-poste.md) (R-7) |
| [`durcir-autorisations-et-debits`](/openspec/changes/archive/2026-11-25-durcir-autorisations-et-debits/proposal.md) | `veille-reglementaire`, `alertes-digests`, `recherche-veille`, `profils-veille`, `comptes-acces` | REG-09, ALR-08, RCH-06, PRF-08, ACC-14 | 16 | DF-REG-2, DF-ACC-3 | tous les scénarios ; test matriciel point d'entrée × rôle (`MatriceRolesTest`, AUD-TST-05) |

Ces exigences sont au statut [PROPOSITION] dans leurs fiches.
