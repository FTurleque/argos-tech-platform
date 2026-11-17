# Conception : rechercher et filtrer la veille, et enregistrer des vues

<!-- Statuts : [EXIGENCE] [EXISTANT] [DÉCISION] [PROPOSITION] [QUESTION] (légende : /docs/specifications/README.md#statuts-des-informations). -->

**Niveau de conception** : Allégée : recherche sur la base existante, sans nouvelle dépendance.

## Contexte technique

La base est PostgreSQL 18 (`compose.yaml`) [EXISTANT]. Les éléments de veille sont les signaux de sécurité (table `signal`, données publiques, migration V3) et les actualités web (table `actualite`, cloisonnée par espace ou par compte, migration V10) ; les impacts (`project_impact`) et les rapprochements (`actualite_impact`) relient ces éléments aux projets d'un espace [EXISTANT]. Motivation : voir `proposal.md`.

## Exigences couvertes

| Exigence | Section(s) de conception | Diagramme(s) |
|---|---|---|
| RCH-01 | Composants, Contrats d'interface, Écran de recherche, D1 | DF-RCH-1 (fiche) |
| RCH-02 | Contrats d'interface, Écran de recherche, D4, D5, D8, D10 | DF-RCH-1 (fiche) |
| RCH-03 | Sécurité et isolation, D2 | DF-RCH-1 (fiche) |
| RCH-04 | Persistance et intégrité, Sécurité et isolation, Écran de recherche | — |
| RCH-05 | Performance et stratégie de mesure | — |

## Objectifs et non-objectifs

**Objectifs :**
- recherche et filtres en une requête respectant la RLS
- vues personnelles simples
- écran de recherche dans `argos-ui`, commun au portail web et au client desktop

**Non-objectifs :**
- recherche sémantique (ADR-0015)
- connexion OIDC de l'IHM (obtention du jeton) : changement `connecter-ihm-oidc` ; l'écran passe par le point d'appel commun du client REST, qui ajoute le jeton porteur, et ses routes sont protégées par la garde de session (D9)

## Composants impactés, responsabilités et frontières

| Composant | Responsabilité dans ce changement | Ne doit pas | Statut |
|---|---|---|---|
| `signal` | index plein texte des signaux et des actualités (colonnes `recherche`, index GIN) ; correspondance technologie → écosystèmes (`SignalQueries.ecosystemsOf`, `technologyTerms`) | contourner la RLS | modifié |
| `impact-engine` (`io.argos.impact.RechercheVeille`) | requête unique de recherche et de filtres, restitution relue sous `argos_app` avec les projets visibles | décider d'un droit | modifié [PROPOSITION — D3] |
| `API & Security` (`io.argos.api.recherche`) | validation des mots et filtres (entrées non fiables), traduction profil → types, gravité → gravités admises, `GET /api/v1/recherche`, `/api/v1/vues` | appliquer une règle de visibilité | modifié |
| `workspace` (`io.argos.workspace.vue`) | vues enregistrées sous RLS, refus journalisé d'une vue d'un autre compte | partager une vue sans décision explicite | modifié |
| `mcp-server` | `search_signals` branché sur `RechercheVeille`, signaux seulement | restituer une actualité web | modifié [PROPOSITION — D6] |
| `alerting` | rangs de gravité réutilisés par le filtre (`Gravite.auMoins`) | — | modifié |
| IHM — couche contenu (`argos-ui/src/api/recherche.ts`, `argos-ui/src/api/recherche-criteres.ts`, `argos-ui/src/views/RechercheView.vue`) | saisie des mots et des filtres, appel de `/api/v1/recherche` et `/api/v1/vues`, affichage paginé des résultats et des vues | valider un filtre au-delà de son format, décider d'un droit, rendre un titre, un extrait ou un lien externe en HTML | modifié |

## Modèle de domaine et invariants

- Un élément de veille a un type : `AVIS_SECURITE` (signal), `ARTICLE` ou `CHANGEMENT_PAGE` (actualité web).
- Une actualité est restituée une fois par empreinte (WEB-05) : la ligne d'un espace de préférence, puis la plus ancienne reçue.
- Une vue appartient à un seul compte ; son nom est unique pour ce compte ; ses critères sont un document JSON validé par l'API.

## Traitements et interactions

1. L'API valide les mots et les filtres (`TraductionCriteres`) ; un projet d'un autre espace est refusé (404 journalisé).
2. `RechercheVeille` ouvre une transaction sous `argos_app` avec le compte de l'appelant, appelle `argos_rechercher_veille`, matérialise la sélection, en compte le total et en garde la page (tri par pertinence, puis date, puis identifiant).
3. Les éléments de la page sont relus sous la RLS (signaux, actualités), avec les projets visibles des impacts et des rapprochements.
4. Une vue rappelée repasse par les étapes 1 à 3 avec ses critères enregistrés, sous les droits courants.
5. L'écran de recherche de `argos-ui` envoie les mots et les filtres saisis tels quels (rognés, dédoublonnés, filtres vides omis) et affiche la réponse ; un refus de l'API est affiché avec le message du serveur (400, 409) ou un message d'écran (401, 403, 404, 429, autres statuts) ; un délai dépassé a son propre message (D11).

## Écran de recherche

| Route (historique « hash ») | Contenu | Appels |
|---|---|---|
| `/recherche` | formulaire (mots, période, gravité minimale, technologies, type de contenu, projets), résultats paginés, vues de l'utilisateur, enregistrement de la saisie en vue | `GET /api/v1/recherche`, `GET /api/v1/vues`, `POST /api/v1/vues`, `DELETE /api/v1/vues/{id}` |
| `/recherche/vues/:id` | même écran, formulaire prérempli avec les critères de la vue, résultats de la vue | `GET /api/v1/vues/{id}`, `GET /api/v1/vues/{id}/resultats` |

- Le lien « Recherche » de la navigation principale ouvre l'écran dans le portail comme dans le client desktop (même build, ADR-0022).
- Une saisie modifiée puis lancée depuis une vue rappelée quitte la vue : la recherche directe remplace ses résultats.
- Les profils de veille d'une vue rappelée sont conservés et peuvent être retirés ; ils ne se saisissent pas dans l'écran.
- Accessibilité : chaque champ a un libellé, les groupes de cases un `fieldset` et une légende ; le nombre de résultats est annoncé dans une zone `aria-live` ; le focus passe au titre des résultats après une recherche ou un changement de page ; les erreurs sont en `role="alert"`.

## États et transitions

Non applicable : une recherche n'a pas d'état ; une vue est créée puis supprimée, sans autre transition.

## Contrats d'interface

| Point d'accès | Entrée | Sortie | Erreurs |
|---|---|---|---|
| `GET /api/v1/recherche` | `q`, `projet`*, `technologie`*, `type`*, `gravite`, `horizon`, `profil`*, `page`, `taille` (* répétable) | `Resultats` : `elements[]`, `page`, `taille`, `total`, `message` | 400 `CRITERE_INVALIDE`, 401, 404 (projet) |
| `GET /api/v1/vues` | — | vues de l'appelant | 401 |
| `POST /api/v1/vues` | `{nom, criteres}` | vue créée (201) | 400, 401, 404 (projet), 409 `VUE_EXISTANTE` / `LIMITE_VUES` |
| `GET /api/v1/vues/{id}` | — | vue | 401, 404 |
| `GET /api/v1/vues/{id}/resultats` | `page`, `taille` | `Resultats` | 400 (profil retiré), 401, 404 |
| `DELETE /api/v1/vues/{id}` | — | 204 | 401, 404 |
| MCP `search_signals` | `query`, `technologies`, `packages`, `sources`, `project_ids`, `severity`, `horizon_days`, `limit` | `Signaux` : `nombre`, `signaux[]` (champs existants, plus `pertinence` et `projets`) | `PARAMETRES_INVALIDES`, `INTROUVABLE` |

Les paramètres ajoutés à `search_signals` sont facultatifs, mais le contrat de l'outil change pour les appels existants : c'est une rupture assumée du contrat MCP (D6). Avant, `SignalQueries.search` combinait les critères `technologies`, `packages` et `sources` par OU et triait par date de modification décroissante ; désormais, tous les critères se combinent par ET, le tri se fait par pertinence puis par date (publication, sinon modification), et une technologie correspond aussi à un mot du titre ou du résumé. Un appel limité à `technologies` peut donc renvoyer d'autres signaux, et un appel qui combine `technologies` et `sources` en renvoie moins.

## Persistance et intégrité

Migration `V11__recherche_veille.sql` :

- fonctions `argos_vecteur_recherche` et `argos_requete_recherche` (IMMUTABLE) ;
- colonnes générées `signal.recherche` et `actualite.recherche` (`tsvector` STORED), index GIN, index de date des signaux, index `project_impact (project_id)` et `actualite_impact (project_id)` ;
- `toast_tuple_target = 256` sur `signal` et `actualite` ;
- fonction `argos_rechercher_veille` (SECURITY DEFINER, exécutable par `argos_app` seulement) ;
- table `workspace_vue` (`nom` varchar(120) unique par compte, `criteres` jsonb objet), RLS `compte_id = argos_compte_courant()`.

## Transactions, concurrence et idempotence

La recherche est en lecture seule, dans une transaction par appel. Deux créations simultanées du même nom de vue : la contrainte d'unicité l'emporte, la seconde reçoit 409 `VUE_EXISTANTE`.

## Sécurité et isolation

- La recherche s'exécute avec le compte courant sous `argos_app` : un extrait d'un autre espace ne peut pas être renvoyé (R-17). Voir D2 pour la sélection.
- Mots saisis liés comme paramètres ; `websearch_to_tsquery` n'échoue sur aucune saisie ; le SQL dynamique de la fonction est un texte constant exécuté avec `EXECUTE … USING`.
- Titre, extrait et lien sont des données externes ; seuls les liens `http` et `https` sont restitués (XSS) ; côté MCP, titre et résumé restent des `TexteExterne` (MCP-05).
- Aucune requête réseau : la recherche ne lit que la base.
- IHM : titre, extrait et nom de source sont interpolés comme texte (aucun `v-html`) ; un lien n'est cliquable qu'après `urlSure` (http et https seulement), ouvert avec `rel="noopener noreferrer"` ; seuls des identifiants UUID servent à construire l'URL d'une vue ou un filtre projet ; les mots sont encodés par `URLSearchParams` ; l'écran ne garde rien dans `localStorage`, `sessionStorage` ni un cookie. La CSP du client desktop est inchangée.

## Gestion des erreurs et reprise

Filtre invalide : 400 avec l'attribut en cause. Projet ou vue hors périmètre : 404 et refus journalisé (`JournalRefus`, via `GestionErreurs`). Vue inexistante : 404 non journalisé. Erreur de base : 500, aucune donnée partielle.

## Observabilité

Compteurs agrégés et journaux d'audit selon arc42 §8.6 ; aucun suivi individuel des recherches ; les appels MCP restent journalisés par `GardeAppels` (MCP-06).

## Performance et stratégie de mesure

Cible : celle de arc42 §10.2 Q-05 (API p95 < 500 ms). Mesure par la campagne `MesurePerformanceRechercheTest` sur le volume Q-2 (100 000 actualités par espace, deux espaces, 20 000 signaux publics), exécution de QS-13 : p95 = 250 ms le 2026-11-16 ([rapport](/docs/architecture/quality/mesure-qs13-recherche.md)). Un mot présent dans tous les éléments reste hors cible (≈ 3,7 s), le classement de 120 000 éléments dominant.

## Compatibilité, migration et retour arrière

La migration ajoute des colonnes générées et réécrit `signal` et `actualite` ; aucun contrat REST existant ne change. Le contrat de l'outil MCP `search_signals` change (combinaison par ET, tri par pertinence, voir Contrats d'interface et D6). Retour arrière : supprimer la table, les fonctions, les index et les colonnes de V11.

## Décisions, alternatives et compromis

### D1 — Recherche plein texte PostgreSQL

- **Statut** : [DÉCISION — Q-1 du 2026-11-16]
- **Choix** : index plein texte dans la base existante, configurations `french` et `english` (plus `simple` pour les identifiants et les noms de projets)
- **Justification** : aucune dépendance de plus, RLS appliquée
- **Alternatives écartées** : moteur dédié — synchronisation et droits à dupliquer
- **Compromis** : pertinence moindre à grande échelle (R-16)
- **ADR** : ADR-0015 (Proposé)

### D2 — Sélection par une fonction SECURITY DEFINER, restitution sous RLS

- **Statut** : [PROPOSITION]
- **Choix** : `argos_rechercher_veille` applique explicitement, de façon ensembliste, les règles de visibilité des politiques V2, V5 et V10 ; la page est relue sous `argos_app`
- **Justification** : `ts_match_vq` n'est pas « leakproof » : sous la RLS, l'index GIN d'`actualite` serait inutilisable et chaque ligne évaluerait sa politique (secondes sur 100 000 lignes)
- **Alternatives écartées** : recherche directe sous RLS (trop lente) ; fonction marquée LEAKPROOF (exige un superutilisateur, fausse garantie)
- **Compromis** : règles de visibilité exprimées deux fois (politiques et fonction) ; le test `rch03CloisonnementSousArgosApp` les confronte

### D3 — Requête de recherche dans `impact-engine`

- **Statut** : [PROPOSITION]
- **Choix** : `io.argos.impact.RechercheVeille`, appelée par `api` et `mcp`
- **Justification** : `impact` relie déjà signaux, actualités et projets (`ImpactQueries`, `RapprochementActualites`) et `mcp` ne peut emprunter à `api` que `api.security` (ModuleBoundariesTest) ; contexte RLS par `RlsSession`, le mécanisme existant du module
- **Alternatives écartées** : requête dans `api` (inaccessible à `mcp`) ; dans `signal` (ignore les impacts)

### D4 — Horizon : éléments des N derniers jours

- **Statut** : [PROPOSITION]
- **Choix** : date de publication, sinon de modification (signal) ou de réception (actualité), postérieure à maintenant moins N jours
- **Justification** : les éléments cherchés sont datés dans le passé ; les échéances à venir relèvent du radar (RAD-03)

### D5 — Profil : types relevant des catégories de sources du profil

- **Statut** : [PROPOSITION]
- **Choix** : `AVIS_SECURITE` relève de `avis-securite` ; les actualités web ne relèvent d'aucune catégorie publiée ; plusieurs profils s'unissent
- **Compromis** : `expert-metier` ne retient aucun élément tant que `regintel` n'existe pas

### D6 — `search_signals` restitue les signaux seulement

- **Statut** : [PROPOSITION]
- **Choix** : même requête, type fixé à `AVIS_SECURITE` ; les critères se combinent par ET et les résultats sont triés par pertinence, comme dans l'API REST
- **Justification** : nom et contrat de l'outil ; les champs des actualités web (lien, identifiants libres) restent à réexaminer au regard de MCP-05 ; une seule sémantique de filtre pour l'API et le MCP
- **Compromis** : rupture du contrat MCP pour les appels existants (ancienne combinaison par OU, tri par date de modification) ; les clients MCP qui combinaient plusieurs critères obtiennent moins de résultats

### D7 — 100 vues par utilisateur

- **Statut** : [PROPOSITION]
- **Choix** : borne de stockage paramétrable (`argos.recherche.vues-max`)

### D8 — Filtre projet de l'écran sans liste des projets

- **Statut** : [PROPOSITION]
- **Choix** : l'écran propose en cases à cocher les projets cités par les résultats déjà affichés et ceux d'une vue rappelée, et accepte la saisie de l'identifiant (UUID) d'un projet
- **Justification** : aucun point d'accès de `/api/v1` ne liste les projets visibles de l'utilisateur (`EspacesResource` liste les espaces, sans leurs projets) ; l'écran n'invente pas d'API
- **Alternatives écartées** : ajouter `GET /api/v1/projets` dans ce changement (contrat serveur hors du périmètre de l'écran) ; filtre projet absent (parcours « Oracle, projet, 30 jours » impossible)
- **Compromis** : un projet jamais cité par un résultat ne se choisit que par son identifiant ; écart consigné ci-dessous

### D9 — Point d'appel commun du client REST

- **Statut** : [PROPOSITION]
- **Choix** : `appelerApi` (`argos-ui/src/api/client.ts`) porte l'unique `fetch` de l'IHM (méthode, corps JSON, délai, réponse 204, corps `Erreur`) ; les fonctions existantes et celles de `recherche.ts` l'appellent
- **Justification** : l'en-tête d'authentification OIDC ne s'ajoute qu'à un endroit ; le test `recherche.test.mjs` vérifie qu'il n'existe qu'un `fetch` dans `client.ts` et aucun dans `recherche.ts`
- **Compromis** : un refus d'une lecture existante affiche désormais le message du corps `Erreur` quand le serveur en renvoie un
- **Authentification** : `appelerApi` ajoute `Authorization: Bearer` sur tout `/api/v1` sauf `/api/v1/system/info` et transmet les 401 et 403 à la session (`connecter-ihm-oidc`, ACC-09)

### D10 — Périodes proposées

- **Statut** : [PROPOSITION]
- **Choix** : toute période, 7, 30, 90 et 365 jours (`horizon` de l'API) ; une vue enregistrée avec un autre horizon l'affiche tel quel
- **Justification** : le parcours de référence filtre sur 30 jours ; l'API admet de 1 à 3 650 jours

### D11 — Délai, chargements concurrents et suppression

- **Statut** : [PROPOSITION]
- **Choix** : la recherche et les résultats d'une vue disposent d'un délai de 20 s (`DELAI_RECHERCHE_MS`), au-delà des 5 s des autres appels ; un délai dépassé affiche « le serveur n'a pas répondu à temps », distinct de « serveur injoignable ». Chaque chargement (recherche, page, vue rappelée) annule le précédent par un `AbortSignal`, dont la réponse tardive est ignorée. La suppression d'une vue se confirme dans la page ; supprimer la vue affichée vide les résultats et leur source.
- **Justification** : un mot très fréquent demande plusieurs secondes au serveur (R-16, QS-13) ; une réponse ancienne ne doit pas écraser la vue courante ; une pagination sur une vue supprimée mènerait à « Vue introuvable »
- **Alternatives écartées** : délai global allongé pour tous les appels (détection de coupure du desktop ralentie) ; `window.confirm` (dialogue bloquant, hors du style de l'IHM)

## Risques

- Limites de PostgreSQL à grande échelle (R-16) → mesure QS-13 avant tout autre moteur ; un mot très fréquent dépasse déjà la cible.
- Divergence entre les politiques RLS et la fonction de sélection (D2) → relecture sous RLS et test de cloisonnement.

## Écarts code / tests / specs / documentation

- Parcours depuis le client desktop (tâche 4.2) non consigné : l'écran de recherche existe, mais `argos-ui` n'envoie pas encore de jeton OIDC, et l'API refuse tout appel non authentifié (401).
- Aucun point d'accès ne liste les projets visibles de l'utilisateur : le filtre projet de l'écran repose sur les projets cités par les résultats ou sur la saisie d'un identifiant (D8).

## Stratégie de vérification

| Exigence / scénario | Niveau de test | Moyen de preuve | État |
|---|---|---|---|
| RCH-01-S1, RCH-01-S2 | intégration API et MCP | `RechercheVeilleTest`, `RechercheMcpTest` | passe |
| RCH-02-S1 | intégration API | `RechercheVeilleTest.rch02S1FiltresCombines` | passe |
| RCH-03-S1 | intégration, sous `argos_app` | `RechercheVeilleTest.rch03*`, `RechercheMcpTest.rch03*` | passe |
| RCH-04-S1, RCH-04-S2 | intégration API | `RechercheVeilleTest.rch04*` | passe |
| RCH-05-S1 | charge | `MesurePerformanceRechercheTest`, QS-13 | passe (p95 = 250 ms) |
| RCH-01, RCH-02, RCH-04 (écran) | unitaire et statique IHM | `argos-ui/tests/recherche.test.mjs` (paramètres, période, vues, sécurité de l'écran), `npm --prefix argos-ui run build` (contrôle de types) | passe |
| RCH-04-S1 depuis le desktop | E2E | tâche 4.2 | non réalisé |

## Documentation durable impactée

- `/docs/specifications/recherche-veille.md`, registre `/docs/specifications/README.md`
- arc42 §5.6, §8.10, §12 ; `/docs/architecture/quality/scenarios.md` (QS-13) et `/docs/architecture/quality/mesure-qs13-recherche.md`

## Questions ouvertes

Aucune autre que celles de la proposition.
