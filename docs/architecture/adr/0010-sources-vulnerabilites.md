# ADR-0010 — Utiliser OSV comme source principale de vulnérabilités

- **Statut** : Proposé
- **Date** : 2026-09-27
- **Décideurs** : revue d’architecture ARGOS
- **Remplace** : —
- **Remplacé par** : —

## Contexte

ARGOS doit détecter rapidement les vulnérabilités affectant les dépendances réelles des projets. Le dossier v2.3 prévoit de croiser plusieurs sources sans reconstruire une base de vulnérabilités propriétaire.

## Critères de décision

1. Couverture Maven, npm, PyPI et autres écosystèmes utilisés.
2. Identifiants et plages de versions exploitables automatiquement.
3. Données publiques, traçables et intégrables au POC.
4. Résilience aux quotas et indisponibilités.

## Options considérées

### Option A — OSV principal, enrichi par NVD, KEV, EPSS et CERT-FR

- OSV fournit un format directement exploitable pour les dépendances open source ;
- NVD enrichit les CVE ;
- KEV apporte l’exploitation avérée ;
- EPSS apporte une probabilité d’exploitation ;
- CERT-FR apporte le contexte national.

### Option B — NVD seule

- source de référence CVE ;
- mapping vers les packages et versions moins direct ;
- dépendance plus forte aux quotas et au modèle NVD.

## Décision

Utiliser **OSV comme source principale** pour le rapprochement package/version, puis enrichir les signaux avec NVD, CISA KEV, FIRST EPSS et CERT-FR lorsque les identifiants le permettent.

## Conséquences positives

- rapprochement plus direct avec les PURL/SBOM ;
- enrichissement multi-source ;
- meilleure tolérance à l’indisponibilité d’une source secondaire.

## Conséquences négatives

- déduplication multi-source à implémenter ;
- règles de priorité et de fraîcheur à documenter.

## Conséquences neutres / compromis

La source primaire peut évoluer ultérieurement si les mesures du POC montrent une couverture insuffisante.

## Méthode de validation

- POC sur les deux projets pilotes ;
- jeu de vulnérabilités connues ;
- mesure de couverture et de délai source → signal ;
- tests d’idempotence et de déduplication.

## Traçabilité

- **Exigences** : F01, F02, F04
- **Scénarios qualité** : alerte sécurité < 1 h ; robustesse source indisponible
- **Diagrammes** : contexte, chaîne de traitement d’un signal
- **Tickets/PR** : PR d’alignement v2.3
- **Fichiers source** : `arc42/03-contexte-perimetre.md`, `arc42/06-vue-execution.md`
