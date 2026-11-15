# Proposition : ajouter le profil de veille « artiste »

<!-- Statuts : [EXIGENCE] [EXISTANT] [DÉCISION] [PROPOSITION] [QUESTION] — légende dans le modèle du schéma detailed-uml. -->

## Why — Pourquoi

Argos est un produit ouvert à tous les publics [DÉCISION — ADR-0025 (Accepté)] et ses profils sont des packs déclaratifs cumulables [PROPOSITION — ADR-0024 (Proposé) ; PRF-04]. Le profil « artiste » est le premier exemple cité hors du monde technique [PROPOSITION — `/docs/specifications/profils-veille.md`]. Il vérifie que le modèle de pack tient pour un public non technique.

## What Changes — Changements

- Pack de profil `artiste` publié au catalogue.
- Rubriques : appels à projets et résidences, aides à la création, droits et rémunérations, statut social et fiscal, événements.
- Objets suivis : disciplines, territoires, échéances.
- Rapprochement déterministe par discipline, territoire et date limite ; rappels avant date limite.
- Sources officielles ou des organismes eux-mêmes, conditions d'usage vérifiées ; aucun avis d'éligibilité.
- Aucune rupture de contrat (**BREAKING** : aucun) : aucune spec de référence n'existe.

## Capabilities — Capacités

### New Capabilities — Nouvelles capacités

- `profil-artiste` (préfixe `ART`) : pack de profil pour les artistes : rubriques, objets suivis, rapprochement, rappels, sources et garde-fous.

### Modified Capabilities — Capacités modifiées

Aucune : aucune spec de référence n'existe.

## Périmètre

### Inclus

- Le pack, sa stratégie de rapprochement et ses garde-fous.

### Exclu

- Les profils `comedien` et `politique` : changes dédiés.
- La candidature en ligne aux appels.

## Acteurs et parties prenantes

| Acteur | Rôle dans ce changement | Statut |
|---|---|---|
| Artiste | active le profil, choisit ses disciplines et territoires | [PROPOSITION] — ADR-0024 (Proposé) |
| Administrateur plateforme | publie le pack et valide ses sources | [PROPOSITION] — PRF-04 |
| Organismes publics et sociétés de gestion | publient appels, aides et informations | [QUESTION] — Q-1 |

## Sources et preuves

| Élément | Statut | Source |
|---|---|---|
| Profils cumulables, packs déclaratifs | [PROPOSITION] | `/docs/architecture/adr/0024-profils-de-veille-extensibles.md` (Proposé) |
| Produit ouvert à tous les publics | [DÉCISION] | `/docs/architecture/adr/0025-produit-ouvert-projet-personnel.md` (Accepté) |
| Exemple de profil artiste | [PROPOSITION] | `/docs/specifications/profils-veille.md` |
| Ajout d'un profil sans effet sur les autres | [PROPOSITION] | exigence PRF-04 |
| Socle applicatif sans code métier | [EXISTANT] | `README.md` §Statut ; `argos-server/src/main/java/io/argos/` |

## Écarts constatés

- Aucune source « artiste » n'est encore identifiée ni vérifiée : les rubriques sont posées, leurs sources restent une question (Q-1).

## Impact

- **Composants** (arc42 §5) : `catalog` (pack), `impact-engine` (stratégie), `source-ingestion`, `alerting`
- **Contrats d'interface** : manifeste du pack
- **Données et persistance** : disciplines et territoires suivis par l'utilisateur, appels avec date limite
- **Sécurité, confidentialité, droits** : sources publiques seulement ; aucune donnée de tiers
- **Exploitation et observabilité** : état des sources du pack
- **Documentation durable à mettre à jour** : `/docs/specifications/profil-artiste.md`, registre `/docs/specifications/README.md`, fiche `profils-veille`
- **ADR** : ADR-0024 (Proposé) ; revue des conditions d'usage (ADR-0025)

## Conception requise

Allégée : premier pack sur le modèle d'ADR-0024 ; seule la stratégie de rapprochement est nouvelle.

## Questions ouvertes

| ID | Question | Bloquante ? | Destinataire |
|---|---|---|---|
| Q-1 | Quelles sources prioritaires (ministère de la Culture, centres nationaux, régions, sociétés de gestion) et leurs conditions d'usage ? | oui pour ART-05 | porteur du projet |
| Q-2 | Quel périmètre géographique au démarrage (France, Europe) ? | oui pour ART-02 | porteur du projet |
| Q-3 | Quelle liste de disciplines ? | oui pour ART-02 | porteur du projet |
| Q-4 | À quelles échéances rappeler une date limite de candidature ? | non : paramétrable | Product Owner |
