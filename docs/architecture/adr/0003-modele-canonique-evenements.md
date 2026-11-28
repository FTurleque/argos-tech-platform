---
id: ADR-0003
title: "Introduire un modèle canonique d’événements"
domain: architecture
rules: false
---

# ADR-0003 — Introduire un modèle canonique d’événements

- **Statut** : Accepté
- **Date** : 2026-11-24 (acceptation ; proposition du 2026-09-24)
- **Décideurs** : porteur du projet (acceptation du 2026-11-24)
- **Remplace** : —

## Contexte

ARGOS doit intégrer RSS, Web, GitHub, GitLab, CVE et potentiellement d’autres sources. Faire dépendre les règles métier de chaque payload externe créerait un couplage durable et des divergences de comportement.

## Critères

- ajout simple de nouvelles sources ;
- stabilité du domaine ;
- traçabilité de la provenance ;
- idempotence ;
- évolutivité des APIs externes.

## Options

### A — Modèle canonique + adapters

+ domaine stable ;
+ tests de contrats isolés ;
+ provenance uniforme ;
- coût initial de mapping et conception.

### B — Payload fournisseur directement dans le domaine

+ démarrage plus rapide ;
- couplage fort ;
- duplication des règles ;
- migration difficile.

### C — Schéma générique JSON sans types métier

+ flexible ;
- faible expressivité et validations métier difficiles.

## Décision

**Option A retenue.** Les adapters transforment les formats externes vers des modèles normalisés propres au domaine ; le modèle réel de l'ingestion est `NormalizedAdvisory` (`io.argos.ingestion`), produit par l'adaptateur `OsvNormalizer`. Le payload brut peut être conservé séparément pour audit/retraitement.

## Conséquences positives

- GitHub/GitLab deviennent interchangeables au niveau métier ;
- meilleure testabilité ;
- possibilité de replay.

## Conséquences négatives

- gouvernance du schéma canonique nécessaire ;
- certains champs spécifiques peuvent nécessiter des extensions.

## Conséquences neutres

Le modèle canonique n’interdit pas de conserver un `raw_payload` JSONB.

## Validation

- mapper au moins un événement équivalent GitHub et GitLab ;
- prouver l’idempotence ;
- ajouter une source RSS sans modifier le cœur métier ;
- contract tests sur payloads réels anonymisés.

## Traçabilité

- `arc42/04-strategie-solution.md`
- `arc42/05-vue-blocs.md`
- `arc42/06-vue-execution.md`
- Q-03, Q-04
