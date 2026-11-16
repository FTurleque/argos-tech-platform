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
| `collecte-avis-securite` | `COL` | [fiche](collecte-avis-securite.md) | absente | `formaliser-chaine-securite-poc` | proposée |
| `inventaire-sbom` | `INV` | [fiche](inventaire-sbom.md) | absente | `formaliser-chaine-securite-poc` | proposée |
| `correlation-impact` | `COR` | [fiche](correlation-impact.md) | absente | `formaliser-chaine-securite-poc` | proposée |
| `abonnements-veille` | `ABO` | [fiche](abonnements-veille.md) | absente | `formaliser-chaine-securite-poc` | proposée |
| `affichage-personnalise` | `AFF` | [fiche](affichage-personnalise.md) | absente | `personnaliser-profils-et-affichage` | proposée |
| `profils-veille` | `PRF` | [fiche](profils-veille.md) | absente | `personnaliser-profils-et-affichage` | proposée |
| `comptes-acces` | `ACC` | [fiche](comptes-acces.md) | absente | `gerer-comptes-et-acces` | proposée |
| `alertes-digests` | `ALR` | [fiche](alertes-digests.md) | [spec](/openspec/specs/alertes-digests/spec.md) | `2026-11-16-diffuser-alertes-et-digests` (archivé) | livrée |
| `notifications-poste` | `NOT` | [fiche](notifications-poste.md) | absente | `notifier-sur-le-poste` | proposée |
| `serveur-mcp` | `MCP` | [fiche](serveur-mcp.md) | absente | `exposer-serveur-mcp` | proposée |
| `passerelle-ia` | `IAG` | [fiche](passerelle-ia.md) | absente | `encadrer-appels-ia` | proposée |
| `veille-reglementaire` | `REG` | [fiche](veille-reglementaire.md) | absente | `suivre-veille-reglementaire` | proposée |
| `radar-standards` | `RAD` | [fiche](radar-standards.md) | absente | `construire-radar-et-standards` | proposée |
| `veille-rss-web` | `WEB` | [fiche](veille-rss-web.md) | [spec](/openspec/specs/veille-rss-web/spec.md) | `2026-11-16-collecter-veille-rss-web` (archivé) | livrée |
| `recherche-veille` | `RCH` | [fiche](recherche-veille.md) | absente | `rechercher-et-filtrer` | proposée |
| `activite-projets` | `PIN` | [fiche](activite-projets.md) | absente | `mesurer-activite-projets` | proposée |
| `profil-artiste` | `ART` | [fiche](profil-artiste.md) | absente | `ajouter-profil-artiste` | proposée |

## Inventaire des diagrammes métier

| ID | Type | Question | Fiche |
|---|---|---|---|
| DF-COL-1 | UML séquence | comment un avis devient-il un signal enrichi, et que se passe-t-il en erreur ? | [collecte-avis-securite](collecte-avis-securite.md#df-col-1--synchronisation-dune-source-de-vulnérabilités) |
| DF-COL-2 | organigramme | quelle décision pour chaque avis reçu ? | [collecte-avis-securite](collecte-avis-securite.md#df-col-2--traitement-dun-avis-reçu) |
| DF-COL-3 | UML états | comment une source passe-t-elle en `DEGRADED` et en sort-elle ? | [collecte-avis-securite](collecte-avis-securite.md#df-col-3--état-de-synchronisation-dune-source) |
| DF-INV-1 | UML séquence | comment une SBOM remplace-t-elle l'inventaire ? | [inventaire-sbom](inventaire-sbom.md#df-inv-1--import-dune-sbom) |
| DF-INV-2 | organigramme | d'où provient l'inventaire et quelle confiance en découle ? | [inventaire-sbom](inventaire-sbom.md#df-inv-2--origine-de-linventaire-dun-projet) |
| DF-COR-1 | UML classes | quels concepts la corrélation relie-t-elle ? | [correlation-impact](correlation-impact.md#df-cor-1--modèle-conceptuel-de-la-corrélation) |
| DF-COR-2 | UML séquence | comment un signal produit-il des impacts, avec ou sans IA ? | [correlation-impact](correlation-impact.md#df-cor-2--corrélation-dun-signal-avec-et-sans-ia) |
| DF-COR-3 | organigramme | quel niveau porte un impact ? | [correlation-impact](correlation-impact.md#df-cor-3--détermination-du-niveau-dun-impact) |
| DF-ABO-1 | UML classes | comment les abonnements se rattachent-ils sans toucher aux droits ? | [abonnements-veille](abonnements-veille.md#df-abo-1--abonnements-équipes-et-autorisations) |
| DF-ABO-2 | UML séquence | dans quel ordre droits et abonnements s'appliquent-ils ? | [abonnements-veille](abonnements-veille.md#df-abo-2--restitution-de-la-veille-dun-utilisateur) |
| DF-AFF-1 | organigramme | quelle mise en page s'affiche à l'ouverture ? | [affichage-personnalise](affichage-personnalise.md#df-aff-1--mise-en-page-appliquée-à-louverture) |
| DF-PRF-1 | UML classes | de quoi un profil de veille est-il composé ? | [profils-veille](profils-veille.md#df-prf-1--ce-que-décrit-un-profil) |
| DF-PRF-2 | organigramme | comment se compose la veille d'un utilisateur à plusieurs profils ? | [profils-veille](profils-veille.md#df-prf-2--veille-dun-utilisateur-à-plusieurs-profils) |
| DF-ACC-1 | organigramme | que se passe-t-il lors de la première connexion d'une personne ? | [comptes-acces](comptes-acces.md#df-acc-1--première-visite-dun-utilisateur) |
| DF-ALR-1 | organigramme | un élément part-il en alerte immédiate ou dans le résumé ? | [alertes-digests](alertes-digests.md#df-alr-1--alerte-immédiate-ou-résumé) |
| DF-NOT-1 | organigramme | quand une alerte s'affiche-t-elle en notification système ? | [notifications-poste](notifications-poste.md#df-not-1--affichage-dune-alerte-sur-le-poste) |
| DF-MCP-1 | organigramme | comment une question du développeur devient-elle une réponse sourcée ? | [serveur-mcp](serveur-mcp.md#df-mcp-1--une-question-posée-à-lassistant) |
| DF-IAG-1 | organigramme | dans quels cas un élément reçoit-il un résumé produit par l'IA ? | [passerelle-ia](passerelle-ia.md#df-iag-1--un-résumé-est-il-produit-) |
| DF-REG-1 | organigramme | quelles étapes un texte suit-il avant d'être diffusé ? | [veille-reglementaire](veille-reglementaire.md#df-reg-1--parcours-dun-texte-officiel) |
| DF-RAD-1 | organigramme | que signifie chaque statut d'une technologie ? | [radar-standards](radar-standards.md#df-rad-1--statuts-du-référentiel) |
| DF-WEB-1 | organigramme | quand une adresse proposée est-elle acceptée ? | [veille-rss-web](veille-rss-web.md#df-web-1--acceptation-dune-adresse) |
| DF-RCH-1 | organigramme | dans quel ordre droits, mots et filtres s'appliquent-ils ? | [recherche-veille](recherche-veille.md#df-rch-1--une-recherche-filtrée) |
| DF-PIN-1 | organigramme | quand un pourcentage d'avancement s'affiche-t-il ? | [activite-projets](activite-projets.md#df-pin-1--affichage-dun-avancement) |
| DF-ART-1 | organigramme | à quelles conditions un appel est-il présenté comme une opportunité ? | [profil-artiste](profil-artiste.md#df-art-1--un-appel-est-il-proposé-à-lartiste-) |

Les diagrammes d'architecture (D-01 à D-13) sont inventoriés dans [`/docs/architecture/diagrams/README.md`](../architecture/diagrams/README.md) ; les diagrammes de conception d'un changement (DC-*) restent dans son `design.md`.

## Bilan de couverture

État au 2026-11-16.

### Inventaire OpenSpec

| Élément | Nombre | Détail |
|---|---:|---|
| Specs de référence | 2 | `alertes-digests`, `veille-rss-web` ; le code des changements actifs des lots 1 et 2 est présent dans `argos-server` et `argos-ui` |
| Changements actifs | 11 | `formaliser-chaine-securite-poc`, `personnaliser-profils-et-affichage` et un changement par capacité restante des lots 2 à 5 (schéma `detailed-uml`) |
| Changements archivés | 2 | `2026-11-16-diffuser-alertes-et-digests`, `2026-11-16-collecter-veille-rss-web` |

Tous les changements utilisent le schéma `detailed-uml` : aucun changement d'un autre schéma n'est à migrer.

### Couverture du lot 1 (`formaliser-chaine-securite-poc`)

| Capacité | Exigences | Scénarios | Diagrammes métier | Questions ouvertes | Tests écrits |
|---|---:|---:|---:|---:|---:|
| `collecte-avis-securite` | 8 | 18 | 3 | 4 | 22 |
| `inventaire-sbom` | 5 | 9 | 2 | 5 | 12 |
| `correlation-impact` | 8 | 16 | 3 | 4 | 27 |
| `abonnements-veille` | 6 | 10 | 2 | 4 | 10 |
| **Total** | **27** | **53** | **10** | **17** | **71** |

Toutes les exigences sont au statut [PROPOSITION] : elles dérivent d'ADR « Proposé » ou de baselines « à valider ». Les tests écrits sont ceux d'`argos-server/src/test/java/io/argos/chain/` (hors `ProfilsDeVeilleTest`) et `architecture/SubscriptionIsolationTest.java`, exécutés par `mvn -B -f argos-server/pom.xml verify` avec des jetons réels du Keycloak des Dev Services et la RLS appliquée sous le rôle `argos_app`. Matrice détaillée : [`tasks.md` du changement](/openspec/changes/formaliser-chaine-securite-poc/tasks.md#matrice-de-traçabilité).

### Couverture de `personnaliser-profils-et-affichage`

| Capacité | Exigences | Scénarios | Diagrammes métier | Questions ouvertes | Tests écrits |
|---|---:|---:|---:|---:|---:|
| `affichage-personnalise` | 5 | 8 | 1 | 1 | 4 |
| `profils-veille` | 6 | 11 | 2 | 4 | 6 |
| **Total** | **11** | **19** | **3** | **5** | **10** |

« Tests écrits » compte les scénarios couverts par un test qui passe (AFF-01-S1, AFF-01-S2, AFF-04-S1, AFF-04-S2, PRF-01-S1 à PRF-01-S3, PRF-03-S1, PRF-03-S2, PRF-05-S1). Les exigences dérivent d'ADR-0023 et ADR-0024 (Proposés) ; les règles dont le code et les tests existent sont marquées [EXISTANT] dans les fiches. Matrice détaillée : [`tasks.md` du changement](/openspec/changes/personnaliser-profils-et-affichage/tasks.md#matrice-de-traçabilité).

### Couverture du lot 2

| Changement | Capacité | Exigences | Scénarios | Diagrammes métier | Questions ouvertes | Tests écrits |
|---|---|---|---|---|---|---|
| [`gerer-comptes-et-acces`](/openspec/changes/gerer-comptes-et-acces/proposal.md) | `comptes-acces` | 6 | 11 | 1 | 0 | 33 |
| [`diffuser-alertes-et-digests`](/openspec/changes/archive/2026-11-16-diffuser-alertes-et-digests/proposal.md) (archivé) | `alertes-digests` | 6 | 9 | 1 | 2 | 26 |
| [`notifier-sur-le-poste`](/openspec/changes/notifier-sur-le-poste/proposal.md) | `notifications-poste` | 6 | 7 | 1 | 2 | 0 |
| **Total** | | **18** | **27** | **3** | **4** | **59** |

Dans ce tableau, « Tests écrits » compte les méthodes de test qui passent : `comptes-acces` (11 scénarios couverts) et `alertes-digests` (9 scénarios couverts, classes `AlertesTest`, `EchecSmtpTest`, `RelaisMuetTest` et `RegleAlerteTest`).

### Couverture du lot 3

| Changement | Capacité | Exigences | Scénarios | Diagrammes métier | Questions ouvertes | Tests écrits |
|---|---|---|---|---|---|---|
| [`exposer-serveur-mcp`](/openspec/changes/exposer-serveur-mcp/proposal.md) | `serveur-mcp` | 7 | 9 | 1 | 0 | 9 |
| [`encadrer-appels-ia`](/openspec/changes/encadrer-appels-ia/proposal.md) | `passerelle-ia` | 5 | 6 | 1 | 4 | 0 |
| [`suivre-veille-reglementaire`](/openspec/changes/suivre-veille-reglementaire/proposal.md) | `veille-reglementaire` | 8 | 13 | 1 | 4 | 0 |
| **Total** | | **20** | **28** | **3** | **8** | **9** |

Pour `exposer-serveur-mcp`, « Tests écrits » compte les scénarios MCP-01-S1 à MCP-07-S1 couverts par un test qui passe (`argos-server/src/test/java/io/argos/mcp/`) ; MCP-02-S1 et MCP-07-S1 s'appuient sur des implémentations simulées du référentiel des standards et des fiches réglementaires, non encore livrés. Matrice détaillée : [`tasks.md` du changement](/openspec/changes/exposer-serveur-mcp/tasks.md#matrice-de-traçabilité).

### Couverture du lot 4

| Changement | Capacité | Exigences | Scénarios | Diagrammes métier | Questions ouvertes | Tests écrits |
|---|---|---|---|---|---|---|
| [`construire-radar-et-standards`](/openspec/changes/construire-radar-et-standards/proposal.md) | `radar-standards` | 6 | 8 | 1 | 3 | 0 |
| [`collecter-veille-rss-web`](/openspec/changes/archive/2026-11-16-collecter-veille-rss-web/proposal.md) (archivé) | `veille-rss-web` | 6 | 8 | 1 | 0 | 8 |
| [`rechercher-et-filtrer`](/openspec/changes/rechercher-et-filtrer/proposal.md) | `recherche-veille` | 5 | 7 | 1 | 2 | 0 |
| **Total** | | **17** | **23** | **3** | **5** | **8** |

### Couverture du lot 5

| Changement | Capacité | Exigences | Scénarios | Diagrammes métier | Questions ouvertes | Tests écrits |
|---|---|---|---|---|---|---|
| [`mesurer-activite-projets`](/openspec/changes/mesurer-activite-projets/proposal.md) | `activite-projets` | 5 | 6 | 1 | 2 | 0 |
| [`ajouter-profil-artiste`](/openspec/changes/ajouter-profil-artiste/proposal.md) | `profil-artiste` | 6 | 8 | 1 | 4 | 0 |
| **Total** | | **11** | **14** | **2** | **6** | **0** |

Toutes ces exigences sont au statut [PROPOSITION] ; la matrice de traçabilité de chaque changement est dans son `tasks.md`.

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
| arc42 §6 annonçait les noms de §5 mais utilise d'autres participants | table de correspondance ajoutée en arc42 §6 ; renommage à décider (tâche 6.1) |
| Inventaire des diagrammes : D-05, D-11, D-12, D-13 annoncés, absents | D-05 ajouté en arc42 §5.7 ; D-11, D-12 et D-13 marqués manquants (tâche 6.2) |
| Glossaire : « impact probable, confirmé, résolu » vs niveaux ADR-0013 | question Q-COR-2 |

## Vérification

```bash
node scripts/verifier-docs.mjs
```

Le script contrôle les liens internes, les blocs Mermaid et les identifiants d'exigences cités ; voir le guide pour le détail et les limites.
