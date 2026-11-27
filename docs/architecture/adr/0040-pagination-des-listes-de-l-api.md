---
id: ADR-0040
title: "Paginer les listes de l'API au format unique `page`, `size`, `{items, page, size, total}`"
domain: architecture
rules: false
---

# ADR-0040 — Paginer les listes de l'API au format unique `page`, `size`, `{items, page, size, total}`

- **Statut** : Proposé
- **Date** : 2026-11-26
- **Décideurs** : porteur du projet (à valider)
- **Remplace** : —
- **Remplacé par** : —
- **Précise** : arc42 [§8.10](../arc42/08-concepts-transverses.md#810-performance) (pagination des collections)

## Contexte

- Trois formats de liste coexistent dans l'API REST :
  - `page`/`size` → `{items, page, size, total}` : projets, composants d'un projet, impacts d'un signal (50 par défaut, 200 au plus) ;
  - `page`/`taille` → `{elements, page, taille, total, message}` : recherche (100 au plus) ;
  - `limite` → tableau : alertes, fiches réglementaires, actualités web.
- D'autres listes sont renvoyées en entier. Leur taille croît avec les données collectées, importées ou journalisées : avis mis à l'écart, événements d'exploitation, échecs d'import, impacts d'un projet, inventaire d'un projet, opportunités (audit du 2026-11-21, AUD-PER-10, AUD-PER-18).
- L'IHM lit déjà le premier format (`PageImpactsApi`, pages de projets et de composants).
- La lecture des paramètres (`api.ParametresPagination`) refuse une valeur non entière par une réponse 400 `DONNEE_INVALIDE` plutôt que par le 404 d'une conversion JAX-RS.

## Critères de décision

1. Un seul format pour toute liste paginée, que l'IHM, le desktop et un client tiers lisent de la même façon.
2. Aucune liste non bornée renvoyée par l'API.
3. Coût de migration des listes paginées existantes et de l'IHM.
4. Mémoire bornée sur le NAS (ADR-0026).

## Options considérées

### Option A — Décalage : `page`, `size`, réponse `{items, page, size, total}`

- avantages : déjà employé par trois listes et lu par l'IHM ; accès direct à une page ; total disponible ;
- inconvénients : une insertion pendant la lecture décale les pages ; `count(*)` à chaque page ;
- coûts : faibles.

### Option B — Curseur sur la clé de tri

- avantages : pages stables sous insertion, coût constant en profondeur ;
- inconvénients : romprait les trois listes paginées et l'IHM ; pas de total ni d'accès direct ;
- coûts : moyens.

### Option C — Statu quo, un format par liste

- inconvénients : chaque client apprend chaque format ; les listes non bornées restent.

## Décision

Proposition : **option A**.

- Paramètres de requête : `page` (entier, à partir de 0, 0 par défaut ; une valeur négative vaut 0) et `size` (50 par défaut ; ramenée dans [1, 200]). Une valeur non entière donne 400 `DONNEE_INVALIDE`, l'attribut en cause étant le nom du paramètre.
- Réponse : un objet portant `items` (éléments de la page), `page` et `size` appliqués, `total` (nombre d'éléments de la liste). Une liste peut ajouter des champs qui la décrivent ; elle ne renomme ni ne retire les quatre champs communs.
- Tri : propre à chaque liste, stable, terminé par l'identifiant. Un journal se lit du plus récent au plus ancien.
- Périmètre : toute liste dont la taille croît avec les données collectées, importées ou journalisées est paginée. Une liste bornée par la configuration (catalogue, espaces de l'appelant, membres, abonnements, vues, sources) ne l'est pas.
- Code partagé : `io.argos.commun.Pagination` (bornes, décalage) et `io.argos.commun.Page<T>` (ADR-0037) ; lecture REST par `io.argos.api.ParametresPagination.pagination`.

## Conséquences positives

- Un client lit toutes les listes paginées de la même façon.
- Aucune réponse ne grossit avec l'historique.

## Conséquences négatives

- Rupture de contrat pour chaque liste qui rendait un tableau.
- Un `count(*)` par page.

## Conséquences neutres / compromis

- La recherche garde son format (`page`, `taille`, `elements`), et les listes bornées par `limite` (alertes, fiches, actualités) gardent ce paramètre (décisions du porteur du projet, 2026-11-26). `GET /api/v1/me/veille` est une liste au sens de cette décision.
- La pagination par décalage convient à des tables bornées par la conservation des données (ADR-0041).

## Méthode de validation

- `ParametresPaginationTest` (bornes, valeur non entière) ; tests d'intégration de chaque liste paginée (page par défaut, pages disjointes) ;
- revue de `/q/openapi` : aucune opération `GET` de collection sans `page` et `size`, hors listes bornées par la configuration.

## Traçabilité

- **Exigences** : PERF-03
- **Scénarios qualité** : arc42 §10
- **Diagrammes** : —
- **Changement** : `openspec/changes/archive/2026-11-27-retenir-importer-et-paginer/design.md` (D6)
- **Fichiers source** : `argos-server/src/main/java/io/argos/commun/Pagination.java`, `argos-server/src/main/java/io/argos/commun/Page.java`, `argos-server/src/main/java/io/argos/api/ParametresPagination.java`
