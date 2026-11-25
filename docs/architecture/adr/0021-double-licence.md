# ADR-0021 — Préserver la possibilité d'une double licence

- **Statut** : Accepté
- **Date** : 2026-11-24 (acceptation ; proposition du 2026-11-15)
- **Décideurs** : porteur du projet (acceptation du 2026-11-24)
- **Remplace** : —
- **Remplacé par** : —
- **Complète** : [ADR-0009](0009-licence-polyform-internal-use.md) (Accepté), sans en modifier la décision

## Contexte

- **Fait établi** : ARGOS est publié sous la PolyForm Internal Use License 1.0.0 (`LICENSE`, ADR-0009). Cette licence autorise l'usage interne et interdit la distribution ; elle n'empêche pas le titulaire d'accorder d'autres licences (« These terms do not … prevent the licensor from granting licenses to anyone else », `LICENSE`).
- **Fait établi** : ADR-0009 ne définit aucune licence commerciale et conserve la possibilité pour le titulaire de faire évoluer sa stratégie de licence.
- **Fait déclaré par le titulaire (2026-11-15)** : ARGOS lui appartient et il en est le seul contributeur. Aucune vente n'est prévue à court terme, mais la possibilité de vendre ARGOS à des clients doit être préservée.
- **Fait établi (2026-11-15)** : plusieurs composants envisagés par l'architecture ont des licences restrictives pour une offre commerciale. FreshRSS est sous AGPL-3.0 (dépôt GitHub `FreshRSS/FreshRSS`) ; n8n est sous Sustainable Use License, qui n'autorise la distribution que gratuitement et à des fins non commerciales (`n8n-io/n8n`, `LICENSE.md`) ; changedetection.io est sous Apache-2.0 (`dgtlmoon/changedetection.io`).
- **Question ouverte** : la compatibilité des licences des autres dépendances reste à vérifier ; les dépendances sont déclarées dans les `pom.xml`, `package.json` et `Dockerfile`, et leurs licences sont contrôlées au build par le profil Maven `licences` (`argos-server/pom.xml`) et par l'étape de licences du `Dockerfile`.

La possibilité de vendre ARGOS sous une licence propre au titulaire repose sur deux conditions qui se perdent facilement : rester seul titulaire des droits sur tout le code, et n'intégrer au produit distribué aucun composant dont la licence interdit ou contraint cette distribution. Une fois perdues, ces conditions sont coûteuses ou impossibles à rétablir (réécriture de code, accord de chaque contributeur, remplacement d'une dépendance).

## Critères de décision

1. Conserver la licence publique d'ADR-0009 sans changement pour les utilisateurs actuels.
2. Permettre au titulaire de proposer une licence commerciale à tout moment, sans renégociation avec des tiers.
3. Rendre chaque introduction de dépendance ou de contribution vérifiable au regard de ce critère.
4. Ne pas engager dès maintenant de coûts juridiques ou commerciaux.

## Options considérées

Analyse du 2026-11-15, présentée comme telle.

### Option A — Double licence préparée : licence publique inchangée, licence commerciale réservée au titulaire

- avantages : aucun changement pour les utilisateurs actuels ; option de vente préservée ; coût limité à une discipline sur les contributions et les dépendances ;
- inconvénients : contributions externes soumises à une cession de droits ; certains composants exclus du produit distribué ;
- risques : discipline non tenue lors de l'ajout d'une dépendance ;
- coûts : revue de licence à chaque dépendance ; rédaction juridique repoussée à la décision de vendre.

### Option B — Statu quo : licence publique seule, sans règle de préservation

- avantages : aucune contrainte immédiate ;
- inconvénients : l'option commerciale peut être perdue sans que personne ne s'en aperçoive (contribution sans cession, dépendance incompatible) ;
- risques : constat tardif, au moment d'une vente.

### Option C — Rédiger et publier dès maintenant une licence commerciale

- avantages : offre prête ;
- inconvénients : coût juridique sans besoin immédiat ; conditions (prix, support, garanties, responsabilité) fixées avant de connaître les clients ;
- risques : conditions inadaptées à la première vente réelle.

## Décision

Option A retenue.

1. La licence publique d'ARGOS reste la PolyForm Internal Use License 1.0.0 (ADR-0009, inchangé).
2. Le titulaire se réserve le droit de concéder ARGOS sous une **licence commerciale distincte**. Son texte (étendue, prix, support, garanties, responsabilité) n'est rédigé qu'à la décision de vendre, avec une revue juridique.
3. **Titularité** : aucune contribution d'un tiers n'est intégrée sans un accord écrit (cession de droits ou accord de contribution) permettant au titulaire de la concéder sous licence commerciale. Tant qu'aucun accord type n'existe, les contributions externes ne sont pas acceptées.
4. **Dépendances du produit distribué** : toute dépendance livrée avec ARGOS ou intégrée à son code DOIT avoir une licence compatible avec une distribution commerciale sous la licence du titulaire. Les licences permissives (par exemple MIT, Apache-2.0, BSD, PostgreSQL License) sont acceptées après vérification ; les licences copyleft (GPL, AGPL, LGPL selon le mode de liaison) et les licences qui interdisent l'usage ou la distribution commerciale (par exemple Sustainable Use License) exigent une décision explicite avant introduction.
5. **Systèmes externes** : un logiciel tiers seulement consommé comme système séparé (ADR-0006, ADR-0007), non distribué avec ARGOS, n'entre pas dans le produit. Sa livraison à un client dans une offre commerciale exige une revue de sa licence : n8n ne peut pas être distribué dans une offre payante ; FreshRSS impose les obligations de l'AGPL-3.0 au composant livré.
6. La licence de chaque dépendance est enregistrée au moment de son introduction.

Hors périmètre : tarification, édition commerciale, politique de support, choix de la forme juridique de vente.

## Conséquences positives

- la vente sous licence commerciale reste possible sans accord de tiers ;
- les utilisateurs de la licence publique ne voient aucun changement ;
- les choix de dépendances deviennent explicites et traçables.

## Conséquences négatives

- les contributions externes sont bloquées tant qu'aucun accord de contribution n'existe ;
- certains composants pressentis (n8n, FreshRSS) ne peuvent pas faire partie d'une offre commerciale sans décision dédiée ;
- une revue de licence s'ajoute à chaque nouvelle dépendance.

## Conséquences neutres / compromis

- la licence commerciale reste à rédiger ; cet ADR ne fixe que les conditions qui la rendent possible ;
- la protection par le droit d'auteur du code produit avec l'aide d'outils d'IA est une **question ouverte** à soumettre à la revue juridique préalable à une vente.

## Méthode de validation

- **Contrôle automatisé** : aucun à ce jour.
- **Revue humaine** : à chaque ajout de dépendance (manifest Maven ou npm, image de conteneur livrée), vérifier et consigner sa licence ; à chaque contribution externe, vérifier l'existence d'un accord écrit.
- **Contrôle candidat** : extraire les licences de la SBOM CycloneDX d'ARGOS (ADR-0011) dans la CI et bloquer les licences non autorisées ; à décider lorsque le build existera.
- **Avant toute vente** : revue juridique de la titularité, des dépendances livrées et du texte de la licence commerciale.

## Traçabilité

- **Exigences** : —
- **Scénarios qualité** : —
- **Risques** : R-07 (licence/compliance composant mal comprise), `/docs/architecture/risks/register.md`
- **Diagrammes** : —
- **Tickets/PR** : Non déterminé
- **Fichiers source** : `LICENSE`, `README.md`, ADR-0006, ADR-0007, ADR-0009, ADR-0011

## Notes

Cet ADR ne modifie ni `LICENSE` ni ADR-0009. Toute décision de vendre, et le texte de la licence commerciale, feront l'objet d'une validation par le titulaire après revue juridique.
