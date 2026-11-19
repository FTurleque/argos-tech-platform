# Proposition : optimiser la restitution de « Ma veille »

<!-- Statuts : [EXIGENCE] [EXISTANT] [DÉCISION] [PROPOSITION] [QUESTION] — légende : /docs/specifications/README.md#statuts-des-informations. -->

## Why — Pourquoi

La campagne de performance du changement `automatiser-chaine-veille` mesure `GET /api/v1/me/veille` au-dessus de la cible p95 < 500 ms : p50 = 509,3 ms, p95 = 533,8 ms sur la branche intégrée, avec le jeu de référence (20 projets × 100 composants, 1 000 avis, 20 000 impacts, 4 clients simultanés) ; les quinze autres opérations mesurées ont un p95 inférieur à 130 ms [EXISTANT — `/docs/architecture/quality/mesure-performance-api-mcp.md`, campagne du 2026-11-19, révision `7c5b9d7`]. Le porteur du projet a décidé que ce dépassement fait l'objet d'un changement dédié [DÉCISION K2, 2026-11-19 — `/openspec/changes/automatiser-chaine-veille/proposal.md`, décisions K1 à K6]. Le jeu de référence est celui de la décision K3 du même jour.

## What Changes — Changements

- Lecture groupée des signaux des avis restitués par « Ma veille » : une requête pour tous les avis, jamais une requête (avec sa connexion et sa transaction) par avis.
- Lecture groupée des signaux retenus par la recherche de veille des abonnements (`SignalQueries.search`) et par un critère d'abonnement `PROJECT`.
- Index `project_impact_recents (updated_at DESC, id)` (migration V21) : la lecture des 200 impacts actifs les plus récents d'un espace suit l'ordre de l'index et s'arrête aux 200 premières lignes visibles, sans lire, filtrer sous la RLS ni trier tous les impacts de l'espace.
- Contenu restitué inchangé : mêmes éléments, mêmes champs, même ordre, mêmes règles RLS et de visibilité. Aucune rupture de contrat (**BREAKING** : aucun).

## Capabilities — Capacités

### New Capabilities — Nouvelles capacités

Aucune.

### Modified Capabilities — Capacités modifiées

- `profils-veille` : exigence ajoutée PRF-07 (latence de la restitution de « Ma veille » dans la cible, contenu inchangé). PRF-01 à PRF-06 sont inchangées.

## Périmètre

### Inclus

- La restitution de « Ma veille » (`GET /api/v1/me/veille`, `io.argos.api.veille.VeilleProfilsResource`) et les lectures qu'elle appelle : `io.argos.signal.SignalQueries`, `io.argos.impact.ImpactQueries.forWorkspaces`, `io.argos.api.SignauxAbonnements`.
- Un index de la table `project_impact` (migration V21), justifié par `EXPLAIN ANALYZE` (design.md, D2).
- La campagne de performance relancée et son rapport versionné ; la fiche `performance-api-mcp` (écarts constatés).

### Exclu

- Les quinze autres opérations de la campagne, déjà dans la cible : elles ne sont pas optimisées ici ; l'outil MCP `get_security_alerts`, qui lit aussi `ImpactQueries.forWorkspaces`, profite de l'index sans autre modification.
- Les politiques RLS et leurs fonctions (`argos_espace_lisible`, `argos_projet_lisible`, ADR-0016) : inchangées, même si leur évaluation ligne à ligne est la cause du coût de la lecture des impacts.
- Tout cache applicatif : la mesure ne le justifie pas (design.md, D3).
- Le jeu de référence et la méthode de la campagne (décision K3, PERF-02) : inchangés.
- L'archivage de ce changement et de `automatiser-chaine-veille` : sur demande explicite seulement.

## Acteurs et parties prenantes

| Acteur | Rôle dans ce changement | Statut |
|---|---|---|
| Utilisateur | consulte « Ma veille » ; reçoit le même contenu, plus vite | [EXIGENCE] — PRF-02 (spec `profils-veille`) |
| Porteur du projet | a décidé le changement dédié (K2) et le jeu de référence (K3) | [DÉCISION] — 2026-11-19 |
| Développeur, exploitant | lance la campagne et lit le rapport | [PROPOSITION] — PERF-02 (changement `automatiser-chaine-veille`) |

## Sources et preuves

| Élément | Statut | Source |
|---|---|---|
| Cible p95 < 500 ms des consultations API et MCP | [PROPOSITION] (cible initiale), vérification [DÉCISION] J8 | arc42 §8, §10.2 Q-05 ; PERF-01 (`/openspec/changes/automatiser-chaine-veille/specs/performance-api-mcp/spec.md`) |
| Dépassement de `GET /api/v1/me/veille` (p95 = 533,8 ms) | [EXISTANT] | `/docs/architecture/quality/mesure-performance-api-mcp.md` (campagne du 2026-11-19) |
| Changement dédié à l'optimisation de « Ma veille » | [DÉCISION] K2, 2026-11-19 | `/openspec/changes/automatiser-chaine-veille/proposal.md` |
| Jeu de référence 20 × 100, 1 000 avis, 4 clients | [DÉCISION] K3, 2026-11-19 | idem ; `e2e/performance/donnees-performance.sql` |
| Union des profils actifs et des abonnements `PERSONAL`, dans la limite des droits | [EXIGENCE] | PRF-02, `/openspec/specs/profils-veille/spec.md` |
| Migrations Flyway non réversibles automatiquement, inventaire dans arc42 §8.7 | [DÉCISION] | ADR-0032 (Accepté) ; `/docs/architecture/arc42/08-concepts-transverses.md` |
| Licences permissives, aucune dépendance nouvelle | [DÉCISION] | ADR-0021 |

## Écarts constatés

- La cause mesurée ne se limite pas à la lecture des signaux un par un (hypothèse 1, environ 200 ms par requête) : la lecture des impacts récents sous la RLS coûte environ 240 ms (hypothèse 2), et un index `(workspace_id, state, updated_at)` n'y remédie pas, le planificateur ne s'en servant pas pour ce tri (design.md, « Contexte technique »).
- La spec `performance-api-mcp` n'existe que dans le changement actif `automatiser-chaine-veille` : l'exigence de latence de « Ma veille » est ajoutée à `profils-veille`, capacité de référence (design.md, D4).

## Impact

- **Composants** (arc42 §5) : `API & Security` (`VeilleProfilsResource`, `SignauxAbonnements`), `signal` (`SignalQueries`), `impact-engine` (lecture des impacts), PostgreSQL (index V21).
- **Contrats d'interface** : aucun changement ; OpenAPI et MCP identiques.
- **Données et persistance** : migration V21, un index sur `project_impact` ; aucune donnée modifiée.
- **Sécurité, confidentialité, droits** : aucun changement ; les lectures d'impacts restent sous la RLS du rôle `argos_app` avec le compte de l'appelant ; les signaux sont des données publiques (ADR-0016).
- **Exploitation et observabilité** : aucune instrumentation ajoutée ; preuve par la campagne `npm --prefix e2e run test:performance`.
- **Documentation durable à mettre à jour** : fiches `profils-veille` et `performance-api-mcp`, registre `/docs/specifications/README.md`, arc42 §8.7 (V21, prochaine migration, retour arrière), D-13 (liste des migrations), rapport de la campagne.
- **ADR** : aucun ADR modifié ; aucune décision candidate à un nouvel ADR (index et lecture groupée : décisions locales).

## Conception requise

allégée — un seul flux (lecture), aucun nouveau composant ni contrat ; la conception consigne le diagnostic mesuré, les choix et les alternatives écartées.

## Questions ouvertes

| ID | Question | Bloquante ? | Destinataire |
|---|---|---|---|
| — | Aucune | — | — |
