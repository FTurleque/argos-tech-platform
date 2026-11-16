# Proposition : collecter la veille RSS/Atom et les pages web surveillées

<!-- Statuts : [EXIGENCE] [EXISTANT] [DÉCISION] [PROPOSITION] [QUESTION] — légende : /docs/specifications/README.md#statuts-des-informations. -->

## Why — Pourquoi

Une large part de la veille (blogs, notes de version, annonces) n'a pas d'API structurée [PROPOSITION — ADR-0006 (Proposé) ; `/docs/specifications/README.md`, lot 4]. Ces contenus exposent à l'injection d'instructions (R-11) et à la falsification de requête côté serveur (R-12). Dans un produit ouvert (ADR-0025), n'importe quel utilisateur peut proposer une adresse : la protection devient indispensable.

## What Changes — Changements

- Flux RSS/Atom et pages surveillées, au niveau de l'espace ou personnels.
- Refus des adresses vers des réseaux privés ou locaux, délais bornés.
- Contenu externe traité comme donnée non fiable.
- Déduplication d'un même article ; rapprochement par nom de technologie au niveau « à vérifier ».
- Aucune rupture de contrat (**BREAKING** : aucun) : aucune spec de référence n'existe.

## Capabilities — Capacités

### New Capabilities — Nouvelles capacités

- `veille-rss-web` (préfixe `WEB`) : flux RSS/Atom, pages surveillées, protection SSRF, contenu non fiable, déduplication.

### Modified Capabilities — Capacités modifiées

Aucune : aucune spec de référence n'existe.

## Périmètre

### Inclus

- Ajout, collecte, déduplication et restitution des éléments de type actualité.

### Exclu

- Le choix entre services séparés et collecteur intégré : question Q-1.

## Acteurs et parties prenantes

| Acteur | Rôle dans ce changement | Statut |
|---|---|---|
| Utilisateur | ajoute des sources personnelles | [PROPOSITION] — ABO-04 |
| Administrateur d'espace | ajoute des sources pour l'espace | [PROPOSITION] — ABO-03 |
| Sites et flux externes | publient le contenu | [PROPOSITION] — ADR-0006 (Proposé) |

## Sources et preuves

| Élément | Statut | Source |
|---|---|---|
| Collecteurs séparés (FreshRSS, changedetection.io) | [PROPOSITION] | `/docs/architecture/adr/0006-collecteurs-separes.md` (Proposé) |
| Injection depuis contenu Web/RSS | [PROPOSITION] | risque R-11 |
| SSRF via surveillance d'URL | [PROPOSITION] | risque R-12 |
| Niveau « à vérifier » sans alerte | [PROPOSITION] | ADR-0013 (Proposé) ; ALR-02 |
| Socle applicatif sans code métier | [EXISTANT] | `README.md` §Statut ; `argos-server/src/main/java/io/argos/` |

## Écarts constatés

- ADR-0021 signale l'AGPL de FreshRSS : un service distribué avec Argos en hériterait les obligations (question Q-1).

## Impact

- **Composants** (arc42 §5) : `source-ingestion`, `signal`, `impact-engine`
- **Contrats d'interface** : OpenAPI des sources ; contrat avec le ou les collecteurs
- **Données et persistance** : sources, articles, empreintes de déduplication
- **Sécurité, confidentialité, droits** : SSRF, injection d'instructions, abus d'un produit ouvert
- **Exploitation et observabilité** : état de chaque source
- **Documentation durable à mettre à jour** : `/docs/specifications/veille-rss-web.md`, registre `/docs/specifications/README.md`, arc42 §8.3
- **ADR** : ADR-0006 (Proposé) ; décision Q-1 candidate à ADR

## Conception requise

Complète : entrées non fiables fournies par des tiers et exposition aux risques R-11 et R-12.

## Questions ouvertes

| ID | Question | Bloquante ? | Destinataire |
|---|---|---|---|
| Q-1 | Utiliser FreshRSS et changedetection.io en services séparés (AGPL pour FreshRSS) ou un collecteur intégré ? | **Répondue le 2026-11-16** : collecteur intégré au serveur, à bibliothèques sous licence permissive ; ni FreshRSS (AGPL) ni changedetection.io [DÉCISION] | porteur du projet |
| Q-2 | À quelle fréquence collecter ? | **Répondue le 2026-11-16** : toutes les heures, paramétrable [DÉCISION] | Product Owner |
| Q-3 | Combien de sources personnelles par utilisateur au maximum ? | **Répondue le 2026-11-16** : 50 sources personnelles par utilisateur, paramétrable [DÉCISION] | porteur du projet |
