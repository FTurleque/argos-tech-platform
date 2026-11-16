# Conception : mesurer l'activité factuelle des projets (Project Intelligence)

<!-- Statuts : [EXIGENCE] [EXISTANT] [DÉCISION] [PROPOSITION] [QUESTION] (légende : /docs/specifications/README.md#statuts-des-informations). -->

**Niveau de conception** : Allégée : agrégation de données d'API existantes, sans cycle de vie nouveau.

## Contexte technique

Le module `scm` lit les SBOM et les manifestes de GitHub et GitLab (`RepositoryFiles`, `RepositoryRef`, `ScmException`, configuration `argos.scm.*`) ; `inventory` tient les projets et leur dépôt (`ProjectDirectory`) ; `projectintel` ne contenait qu'un `package-info.java`. La RLS par espace passe par `workspace.ContexteBase` sous `argos_app` ; les tâches planifiées emploient `quarkus-scheduler` (`alerting`, `signal.web`). Motivation : voir `proposal.md`.

## Exigences couvertes

| Exigence | Section(s) de conception | Diagramme(s) |
|---|---|---|
| PIN-01 | Composants, Traitements, Persistance | Composants, DF-PIN-1 |
| PIN-02 | Composants, D3 | DF-PIN-1 |
| PIN-03 | Persistance, Sécurité, D1 | Composants |
| PIN-04 | Contrats d'interface, D4 | Composants |
| PIN-05 | Gestion des erreurs, D2 | Composants |

## Objectifs et non-objectifs

**Objectifs :**
- agrégats sans identité de personne
- chiffres vérifiables à la source

**Non-objectifs :**
- mesure de productivité individuelle
- prévision de date de fin
- écran de l'activité dans `argos-ui`

## Composants impactés, responsabilités et frontières

| Composant | Responsabilité dans ce changement | Ne doit pas | Statut |
|---|---|---|---|
| `project-intel` (`io.argos.projectintel`) | collecte planifiée (`CollecteActivite`), agrégation par jour (`Agregation`), écriture (`ActiviteStore`), consultation sous RLS (`ActiviteQueries`), avancement (`Avancement`), synthèse (`SyntheseActivite`, point d'extension `SyntheseIa`), purge (`PurgeActivite`) | stocker ou exposer une identité de personne ; laisser l'IA calculer un indicateur | modifié |
| `scm-connector` (`io.argos.scm`) | lire les API GitHub et GitLab en lecture seule (`RepositoryActivity`), client HTTP commun avec `RepositoryFiles` (`ForgeHttp`), quotas (`QuotaEpuise`) | écrire dans un dépôt ; laisser sortir un objet propre à une forge ou une identité | modifié |
| `inventory` | lister les projets reliés à un dépôt (`ProjectDirectory.withRepository`) | — | modifié |
| `API & Security` (`io.argos.api.ActiviteResource`) | exposer l'activité et la synthèse aux membres de l'espace ; limiter le débit des synthèses | contenir une règle de calcul | modifié |
| `ai-gateway` | implémenter `SyntheseIa` | calculer un indicateur | existant (proposé), aucune implémentation livrée |

## Modèle de domaine et invariants

- Un compteur est un triplet (projet, jour, type) et une valeur positive ou nulle ; aucun attribut ne désigne une personne.
- Un avancement n'existe que pour un jalon dont le total est strictement positif et au moins égal au nombre de tickets fermés.
- La date de dernière mise à jour ne change qu'à une collecte réussie.

## Traitements et interactions

1. Le planificateur (`argos.activite.cadence`, 1 h) parcourt les projets reliés à un dépôt ; un projet est échu s'il n'a jamais été collecté ou si sa prochaine collecte est passée.
2. Pour un projet échu, `RepositoryActivity` lit les éléments modifiés depuis le début de la fenêtre (`argos.activite.fenetre-jours`, 30 jours, jours au fuseau `argos.alerting.fuseau`).
3. `Agregation` compte les événements par jour et par type ; `ActiviteStore` remplace les compteurs des jours de la fenêtre, remplace l'état des jalons et marque la collecte `A_JOUR` (prochaine collecte 24 h plus tard).
4. La consultation additionne les compteurs des jours de la période demandée, sous `argos_app`.
5. La synthèse est demandée à la consultation (`/activite/synthese`), avec un délai borné (20 s), à partir de la vue restituée.

## États et transitions

État de collecte d'un projet : absent (`JAMAIS_COLLECTE` à la restitution) → `A_JOUR`, `REPORTEE_QUOTA` ou `ECHEC` ; chaque collecte réussie ramène à `A_JOUR`. Aucun autre cycle de vie.

## Contrats d'interface

- `GET /api/v1/projects/{projectId}/activite?jours=30` : `periode`, `compteurs`, `jalons` (avec `avancement` ou `null`), `fraicheur`. `jours` de 1 à 397, sinon 400 `PERIODE_INVALIDE`.
- `GET /api/v1/projects/{projectId}/activite/synthese?jours=30` : `etat` (`DISPONIBLE`, `INDISPONIBLE`, `PLAFOND_ATTEINT`, `REFUSEE`, `SANS_DONNEES`), `texte`, `mention` (« produite par l'IA »), `periode`, `indicateurs` ; 429 au-delà de la limite de débit.
- 401 sans jeton ; 404 pour un projet inconnu ou d'un autre espace (refus journalisé).
- Point d'extension `SyntheseIa` : `budgetDisponible()`, `synthetiser(VueActivite)` (texte brut ; vide = refus ; exception = indisponible).

## Persistance et intégrité

Migration `V14__activite_projets.sql` : `activite_compteur` (projet, jour, type, valeur), `activite_jalon` (projet, référence, titre, état, échéance, tickets fermés, total, date de relevé), `activite_collecte` (état, dernière mise à jour, prochaine collecte, complétude, premier jour couvert, motif, date de tentative). Aucune colonne d'identité. Clés étrangères vers `project` avec suppression en cascade. Historique conservé 13 mois (Q-2) : `PurgeActivite` supprime chaque jour (04:30) les compteurs et les jalons plus anciens.

## Transactions, concurrence et idempotence

L'enregistrement d'une collecte est une transaction : suppression des compteurs de la fenêtre, insertion, remplacement des jalons, état de collecte. Rejouée sur la même fenêtre, elle produit le même résultat. Le planificateur ne lance pas deux passages simultanés (`ConcurrentExecution.SKIP`).

## Sécurité et isolation

- Lecture seule : `ForgeHttp` n'émet que des requêtes `GET` et ne suit aucune redirection (le jeton ne part jamais vers un autre hôte) ; jetons hors dépôt, absents des messages.
- Entrées non fiables : identifiants de dépôt encodés dans l'adresse (GitHub `owner/repo` validé), titres de jalons encodés pour `issues_statistics`, nettoyés des caractères de contrôle et de mise en forme, tronqués à 200 caractères ; réponses bornées (5 Mio, 10 pages, 50 jalons). Les adresses des forges viennent de la configuration, jamais d'un utilisateur (SSRF).
- Agrégation avant stockage pour ne conserver aucune identité (R-09) : les structures de lecture ne déclarent aucun champ d'identité.
- RLS : lecture sous `argos_app` par `ContexteBase` ; politiques `argos_projet_lisible` ; collecte et purge sous l'utilisateur de connexion (traitements techniques).
- Injection d'instructions : la synthèse reçoit une vue typée ; les titres de jalons sont des données, à placer par la passerelle hors des instructions ; la réponse est du texte brut, bornée, refusée si elle cite un nombre absent des indicateurs.

## Gestion des erreurs et reprise

- Quota épuisé (429, ou 403 avec `x-ratelimit-remaining: 0`) : `QuotaEpuise` ; collecte `REPORTEE_QUOTA` jusqu'à la reprise annoncée (`Retry-After`, `x-ratelimit-reset`, `RateLimit-Reset`, bornée à 24 h, 1 h par défaut) ; projets suivants de la même forge reportés sans appel.
- Autre erreur de forge : `ECHEC`, nouvelle tentative après `argos.activite.delai-apres-echec` (1 h).
- Dans tous les cas, les données déjà enregistrées et leur date de dernière mise à jour restent affichées.

## Observabilité

Journaux de collecte (report, échec) et de purge, sans jeton ni identité ; état de collecte consultable par projet. Aucun suivi individuel des utilisateurs.

## Performance et stratégie de mesure

[QUESTION] Aucune cible propre à ce changement dans les sources ; la cible de consultation de arc42 §10 (Q-05) s'applique quand elle est pertinente.

## Compatibilité, migration et retour arrière

Aucune donnée ni aucun contrat existants. Retour arrière : suppression des trois tables de la migration V14. `RepositoryFiles` emploie le client commun `ForgeHttp` sans changer son contrat ; un quota épuisé y lève désormais `QuotaEpuise`, sous-classe de `ScmException`.

## Décisions, alternatives et compromis

### D1 — Agréger avant de stocker

- **Statut** : [PROPOSITION] — implémentée
- **Choix** : seuls des compteurs par projet, jour et type sont conservés
- **Justification** : impossible de produire un indicateur par personne même par erreur
- **Alternatives écartées** : stocker les événements bruts — risque R-09
- **Compromis** : pas d'analyse fine a posteriori
- **ADR** : non : décision locale

### D2 — Collecte planifiée et report sur quota

- **Statut** : [PROPOSITION]
- **Choix** : *polling* toutes les heures des projets échus, une collecte par projet et par 24 h sur une fenêtre glissante de 30 jours ; report à la réinitialisation annoncée par la forge
- **Justification** : respect des quotas (R-14) sans webhook à exposer ni secret de signature à gérer
- **Alternatives écartées** : webhooks (arc42 §6.2 initial) — exposition publique et vérification de signature, hors périmètre ; collecte à chaque consultation — quotas consommés par les lectures
- **Compromis** : données vieilles d'au plus 24 h, âge affiché
- **ADR** : non : décision locale

### D3 — Pourcentage arrondi à l'entier inférieur

- **Statut** : [PROPOSITION]
- **Choix** : `fermés × 100 / total`, arrondi à l'entier inférieur
- **Justification** : 100 % seulement quand tout est fermé ; aucune surestimation (R-02)
- **Alternatives écartées** : arrondi au plus proche — « 100 % » possible avec un ticket ouvert
- **Compromis** : légère sous-estimation
- **ADR** : non

### D4 — Synthèse par point d'extension et contrôle des nombres

- **Statut** : [PROPOSITION]
- **Choix** : `project-intel` déclare `SyntheseIa`, implémenté par `ai-gateway` (même schéma que `alerting.ResumeIa`) ; toute synthèse dont un nombre ne figure pas parmi les indicateurs affichés est refusée
- **Justification** : l'IA ne calcule aucun indicateur (PIN-02) ; aucun appel direct à un fournisseur hors de la passerelle (ADR-0004)
- **Alternatives écartées** : dépendance de `projectintel` vers `aigateway` — interdite par `ModuleBoundariesTest`, et la passerelle n'est pas implémentée
- **Compromis** : une synthèse qui reformule un nombre (« une dizaine ») est acceptée si elle ne contient pas de chiffre ; une synthèse qui cite une date hors des indicateurs est refusée
- **ADR** : non

### D5 — RLS par le contexte commun

- **Statut** : [PROPOSITION]
- **Choix** : `projectintel` emprunte à `workspace` le seul `ContexteBase` et l'erreur `DonneeInvalide` (règle `projectintelNEmprunteQueRlsEtErreurs`, comme `signal` et `catalog`)
- **Justification** : un seul mécanisme de RLS (ADR-0016)
- **Alternatives écartées** : lecture hors RLS — perte de la défense en profondeur
- **Compromis** : une dépendance de module de plus, bornée par un test
- **ADR** : non

## Risques

- Métrique trompeuse (R-02) → dénominateur explicite obligatoire, arrondi inférieur, collecte incomplète signalée.
- Surveillance perçue (R-09) → aucune identité lue ni stockée.
- Quotas (R-14) → report et pagination bornée.

## Écarts code / tests / specs / documentation

- PIN-04 : aucune implémentation réelle de `SyntheseIa` tant que `encadrer-appels-ia` n'est pas implémenté ; la synthèse vaut alors `INDISPONIBLE`.
- arc42 §6.2 décrivait une réception de webhooks ; la collecte implémentée est planifiée (D2) : §6.2 décrit désormais la collecte planifiée.

## Stratégie de vérification

| Exigence / scénario | Niveau de test | Moyen de preuve | État |
|---|---|---|---|
| PIN-01-S1 | intégration | `ActiviteProjetsTest` (GitHub et GitLab simulés par `StubServer`) | passe |
| PIN-02-S1, PIN-02-S2 | unitaire + intégration | `AvancementTest`, `ActiviteProjetsTest.pin02S1EtS2AvancementAvecDenominateurSeulement` | passe |
| PIN-03-S1 | intégration + revue outillée du schéma | `ActiviteProjetsTest.pin03S1AucunIndicateurParPersonne`, `AgregationEtIdentiteTest` | passe |
| PIN-04-S1 | intégration | `ActiviteProjetsTest.pin04S1SyntheseMarqueeEtFondeeSurLesIndicateurs` (synthèse simulée) | passe |
| PIN-05-S1 | intégration | `ActiviteProjetsTest.pin05S1QuotaEpuiseCollecteReporteeEtAgeAffiche` | passe |
| Purge à 13 mois | intégration | `ActiviteProjetsTest.purgeDeLHistoriqueAuDelaDeTreizeMois` | passe |
| RLS sous `argos_app` | intégration | `ActiviteProjetsTest.cloisonnementSousArgosAppEtRefusJournalise` | passe |

## Documentation durable impactée

- `/docs/specifications/activite-projets.md`, registre `/docs/specifications/README.md`
- arc42 §5 (composant `project-intel`), §6 (correspondance et §6.2), glossaire, inventaire des diagrammes

## Questions ouvertes

Aucune autre que celles de la proposition.
