# Proposition : rechercher et filtrer la veille, et enregistrer des vues

<!-- Statuts : [EXIGENCE] [EXISTANT] [DÉCISION] [PROPOSITION] [QUESTION] — légende : /docs/specifications/README.md#statuts-des-informations. -->

## Why — Pourquoi

Le fil de veille doit pouvoir passer de « tout ce qui est nouveau » à « ce qui arrive sur Oracle dans les 30 jours pour le projet Paie » [PROPOSITION — maquette « Fil de veille » ; `/docs/specifications/README.md`, lot 4]. La recherche plein texte de PostgreSQL est retenue avant tout moteur dédié ou recherche sémantique [PROPOSITION — ADR-0015 (Proposé)]. Les risques R-16 (limites de PostgreSQL) et R-17 (fuite entre espaces) s'appliquent.

## What Changes — Changements

- Recherche plein texte sur les éléments autorisés.
- Filtres combinables : projet, technologie, type, gravité, horizon, profil.
- Résultats toujours limités aux droits de l'utilisateur.
- Vues enregistrées personnelles.
- Respect de la cible de temps de réponse de consultation.
- Aucune rupture de contrat (**BREAKING** : aucun) : aucune spec de référence n'existe.

## Capabilities — Capacités

### New Capabilities — Nouvelles capacités

- `recherche-veille` (préfixe `RCH`) : recherche plein texte, filtres combinables, vues enregistrées, respect des droits.

### Modified Capabilities — Capacités modifiées

Aucune : aucune spec de référence n'existe.

## Périmètre

### Inclus

- Recherche dans le portail, le desktop et l'outil MCP `search_signals`.

### Exclu

- La recherche sémantique et les embeddings (ADR-0015 : différés).

## Acteurs et parties prenantes

| Acteur | Rôle dans ce changement | Statut |
|---|---|---|
| Utilisateur | recherche, filtre, enregistre des vues | [PROPOSITION] — ADR-0015 (Proposé) |

## Sources et preuves

| Élément | Statut | Source |
|---|---|---|
| PostgreSQL FTS, pgvector différé | [PROPOSITION] | `/docs/architecture/adr/0015-postgresql-fts-pgvector.md` (Proposé) |
| Performance de consultation | [PROPOSITION] | arc42 §10.2 Q-05 ; QS-13 |
| Fuite inter-espaces | [PROPOSITION] | risque R-17 ; exigence ACC-04 |
| Socle applicatif sans code métier | [EXISTANT] | `README.md` §Statut ; `argos-server/src/main/java/io/argos/` |

## Écarts constatés

Aucun écart constaté (comparaison avec les ADR et sections arc42 citées).

## Impact

- **Composants** (arc42 §5) : `signal`, `API & Security`, `workspace`, `mcp-server`, IHM — couche contenu (écran de recherche de `argos-ui`)
- **Contrats d'interface** : OpenAPI de recherche ; outil MCP `search_signals`
- **Données et persistance** : index plein texte, vues enregistrées
- **Sécurité, confidentialité, droits** : filtrage par droits avant restitution
- **Exploitation et observabilité** : temps de réponse mesurés
- **Documentation durable à mettre à jour** : `/docs/specifications/recherche-veille.md`, registre `/docs/specifications/README.md`, arc42 §8.10
- **ADR** : ADR-0015 (Proposé) reste la référence

## Conception requise

Allégée : recherche sur la base existante, sans nouvelle dépendance.

## Questions ouvertes

| ID | Question | Bloquante ? | Destinataire |
|---|---|---|---|
| Q-1 | Faut-il une recherche bilingue français et anglais (racines des mots) ? | **Répondue le 2026-11-16** : oui : recherche plein texte PostgreSQL avec les configurations `french` et `english`, sans moteur externe [DÉCISION] | Product Owner |
| Q-2 | Quel volume d'éléments viser par espace pour mesurer la performance ? | **Répondue le 2026-11-16** : 100 000 éléments par espace pour la mesure de performance [DÉCISION] | porteur du projet |
