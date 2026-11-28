---
id: ADR-0020
title: "Spécifier les exigences avec OpenSpec et le schéma `detailed-uml`"
domain: architecture
rules: false
---

# ADR-0020 — Spécifier les exigences avec OpenSpec et le schéma `detailed-uml`

- **Statut** : Accepté
- **Date** : 2026-11-24 (acceptation ; proposition du 2026-11-15)
- **Décideurs** : porteur du projet (acceptation du 2026-11-24)
- **Remplace** : —
- **Remplacé par** : —

## Contexte

Les comportements attendus d'ARGOS sont décrits dans `docs/architecture/` comme une architecture cible : sections arc42, baselines et ADR. Ces documents ne contiennent ni exigences atomiques, ni scénarios testables, ni identifiants stables ; les ADR citent des exigences `F01`–`F16` dont les définitions ne figurent pas dans le dépôt. OpenSpec 1.14.3 est initialisé dans `openspec/` avec le schéma par défaut `spec-driven`, dont les modèles sont en anglais et ne prévoient ni diagrammes, ni statut de preuve, ni traçabilité vers les tests.

Contraintes observées sur OpenSpec 1.14.3 (copie isolée) : l'archivage ne conserve dans une spec principale que `Purpose` et les exigences ; les liens relatifs d'un delta cassent à l'archivage ; le validateur exige `MUST`/`SHALL` et des scénarios `GIVEN`/`WHEN` + `THEN`.

## Critères de décision

1. Chaque exigence est identifiable, testable et traçable jusqu'à une preuve.
2. Aucune exigence normative n'est écrite à deux endroits.
3. Les diagrammes et le contexte fonctionnel survivent à l'archivage OpenSpec.
4. Documentation en français et diagrammes Mermaid (contraintes arc42 §2.1, ADR-0005).
5. Aucune modification des fichiers internes d'OpenSpec.

## Options considérées

### Option A — OpenSpec avec un schéma de projet `detailed-uml` et des fiches capacité durables

- exigences et scénarios dans OpenSpec, contexte et diagrammes métier dans `docs/specifications/`, architecture dans arc42, décisions dans les ADR ;
- avantages : validation structurelle outillée, deltas revus avant intégration, identifiants stables, survie des diagrammes à l'archivage ;
- inconvénients : trois supports à tenir cohérents ; le fork ne suit pas automatiquement le schéma officiel ;
- coût : maintenance du schéma à chaque montée de version d'OpenSpec.

### Option B — OpenSpec avec le schéma par défaut `spec-driven`

- avantages : aucune maintenance de schéma ;
- inconvénients : modèles en anglais, pas de statut de preuve, pas de traçabilité ni d'emplacement pour les diagrammes ;
- risques : diagrammes ou contexte placés dans des sections perdues à l'archivage.

### Option C — Exigences rédigées dans arc42, sans OpenSpec

- avantages : un seul support ;
- inconvénients : pas de deltas ni de validation structurelle ; arc42 mélange alors exigences proposées et architecture consolidée.

## Décision

Option A retenue. Le schéma `openspec/schemas/detailed-uml/` est sélectionné par `openspec/config.yaml`. Les exigences normatives vivent dans OpenSpec (identifiants `<PRÉFIXE>-<NN>`), le contexte fonctionnel et les diagrammes métier dans `docs/specifications/<capability-path>.md`, la vue consolidée dans arc42, les décisions structurantes dans les ADR. Les liens écrits depuis `openspec/` partent de la racine du dépôt.

## Conséquences positives

- exigences testables et traçables (exigence → scénario → conception → tâche → preuve) ;
- distinction explicite entre proposé, décidé et existant vérifié ;
- diagrammes et contexte conservés après archivage.

## Conséquences négatives

- les fiches et arc42 ne sont pas mis à jour par OpenSpec : chaque changement doit prévoir ces mises à jour dans ses tâches ;
- le schéma doit être comparé manuellement au schéma officiel à chaque montée de version.

## Conséquences neutres / compromis

- les en-têtes imposés par OpenSpec (`## Why`, `### Requirement:`, `MUST`) restent en anglais dans des documents en français.

## Méthode de validation

- `openspec schema validate detailed-uml` ;
- `openspec validate --all --strict` ;
- `node scripts/verifier-docs.mjs` (liens, blocs Mermaid, identifiants) ;
- parcours création → validation stricte → archivage rejoué dans une copie isolée ;
- revue d'un premier changement réel (`formaliser-chaine-securite-poc`).

## Traçabilité

- **Exigences** : toutes les capacités de `docs/specifications/README.md`
- **Scénarios qualité** : —
- **Diagrammes** : organigramme du workflow dans `docs/specifications/guide-openspec-detailed-uml.md`
- **Tickets/PR** : Non déterminé
- **Fichiers source** : `openspec/config.yaml`, `openspec/schemas/detailed-uml/`, `docs/specifications/`, `scripts/verifier-docs.mjs`

## Notes

La procédure de comparaison avec le schéma officiel est décrite dans `docs/specifications/guide-openspec-detailed-uml.md`.
