# ADR-0009 — Utiliser PolyForm Internal Use License 1.0.0

- **Statut** : Accepté
- **Date** : 2026-09-24
- **Remplace** : —

## Contexte

ARGOS doit pouvoir être utilisé par son auteur à titre personnel et par une entreprise pour ses opérations internes, y compris par plusieurs collaborateurs. Le projet ne doit pas, sous sa licence publique actuelle, pouvoir être redistribué ou revendu à des tiers.

Aucune stratégie de licence commerciale distincte n’est définie à ce stade.

## Critères

- autoriser l’utilisation interne en entreprise ;
- autoriser les adaptations nécessaires à cet usage interne ;
- interdire la redistribution du logiciel sous la licence publique ;
- conserver la possibilité pour le titulaire des droits de faire évoluer ultérieurement sa stratégie de licence ;
- rendre les conditions compréhensibles dans le dépôt.

## Options étudiées

1. **MIT** : très permissive, autorise redistribution et usages commerciaux ; ne répond pas au besoin de restriction de redistribution.
2. **GNU AGPLv3** : copyleft réseau, mais autorise toujours la redistribution et les usages commerciaux sous ses conditions ; ne répond pas complètement au besoin exprimé.
3. **PolyForm Internal Use License 1.0.0** : permet l’usage interne et les modifications pour cet usage, tout en interdisant la distribution.

## Décision

ARGOS est distribué sous la **PolyForm Internal Use License 1.0.0**.

Le fichier `LICENSE` à la racine contient le texte applicable de la licence.

Aucune licence commerciale séparée n’est définie dans cette décision.

## Conséquences positives

- ARGOS peut être utilisé dans les opérations internes d’une entreprise ;
- les utilisateurs peuvent adapter le logiciel pour les usages permis par la licence ;
- la redistribution du logiciel n’est pas autorisée sous cette licence ;
- le dépôt peut rester publiquement consultable tout en limitant les droits accordés.

## Conséquences négatives

- ARGOS n’est pas « Open Source » au sens de l’OSI ; il doit être qualifié de **source-available** ;
- certaines entreprises ou communautés peuvent refuser les licences source-available ;
- les dépendances futures devront être vérifiées pour éviter toute incompatibilité de licences ;
- les contributions externes nécessiteront une politique claire avant leur acceptation à grande échelle.

## Conséquences neutres

La présente décision ne définit ni tarification, ni édition commerciale, ni politique de support.

## Méthode de validation

- présence du fichier `LICENSE` à la racine ;
- mention cohérente dans `README.md` ;
- contrôle des licences des dépendances lors de leur introduction ;
- revue juridique recommandée avant un déploiement ou une diffusion dans un contexte contractuel sensible.

## Liens

- `LICENSE`
- `README.md`
- `docs/architecture/arc42/02-contraintes.md`
- `docs/architecture/arc42/09-decisions.md`
