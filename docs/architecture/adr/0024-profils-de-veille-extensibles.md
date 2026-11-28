---
id: ADR-0024
title: "Décrire chaque profil de veille comme un pack déclaratif, cumulable par utilisateur"
domain: architecture
rules: false
---

# ADR-0024 — Décrire chaque profil de veille comme un pack déclaratif, cumulable par utilisateur

- **Statut** : Accepté
- **Date** : 2026-11-18 (acceptation ; proposition du 2026-11-15)
- **Décideurs** : porteur du projet (acceptation du 2026-11-18)
- **Remplace** : —
- **Remplacé par** : —

## Contexte

Le dossier v2.4 couvre deux publics : les **développeurs** (failles, versions, fins de support, rapprochés des projets par SBOM — ADR-0010, ADR-0011, ADR-0013) et les **experts métier** (MKP : textes réglementaires rattachés aux domaines et modules produits, avec validation — ADR-0018). Ces deux veilles sont aujourd'hui décrites comme des fonctions distinctes du produit.

Le porteur du projet demande que chaque utilisateur puisse choisir **un ou plusieurs profils d'utilisation**, et que d'autres publics puissent être servis plus tard : par exemple des **artistes**, des **comédiens** ou des acteurs de la **vie politique**. Chacun a ses propres sources, son vocabulaire, ses « objets suivis » et sa façon de juger qu'une information le concerne.

| Profil | Ce qu'il suit | Ce qui le concerne |
|---|---|---|
| Développeur | failles, versions, fins de support, écosystème IA | composants de ses projets (SBOM, PURL) |
| Expert métier | textes officiels, normes déclaratives | domaines et modules produits (dictionnaire métier) |
| Artiste (exemple) | appels à projets, aides publiques, droit d'auteur, statut social | discipline, territoire, échéances de candidature |
| Comédien (exemple) | castings, convention collective, intermittence, formations | spécialités, région, agenda |
| Politique (exemple) | agenda des assemblées, textes déposés, Journal officiel, rapports publics | thèmes, commissions, territoires |

Argos est désormais un produit ouvert à tous les publics, porté à titre personnel (ADR-0025, accepté) : ces profils futurs sont donc dans la cible du produit.

## Critères de décision

1. Ajouter un profil sans modifier le cœur ni les profils existants.
2. Un utilisateur peut cumuler plusieurs profils ; sa veille est l'union, sans doublon.
3. Le rapprochement reste déterministe et explicable pour chaque profil (ADR-0013) ; l'IA résume, elle ne décide pas.
4. Un profil ne donne jamais de droit d'accès (ADR-0016, exigence `ABO-05`).
5. Les garde-fous propres à un domaine (validation par un expert, neutralité, absence d'avis juridique) sont portés par le profil et appliqués partout.

## Options considérées

### Option A — Pack de profil déclaratif, avec stratégies de rapprochement enfichables

- un profil est un **pack** versionné, décrit par un manifeste : identifiant, libellés, catégories de sources et connecteurs utilisés, rubriques et types de signaux, types d'objets suivis, stratégies de rapprochement, garde-fous, libellés par mise en page ;
- les stratégies de rapprochement sont des composants Java du module `impact-engine` derrière un contrat commun (par exemple « PURL + plage de versions », « domaine métier + NOR », plus tard « discipline + territoire + échéance ») ;
- avantages : nouveau public = nouveau pack et, si besoin, une nouvelle stratégie ; cœur inchangé ; chaque rapprochement reste testable ;
- inconvénients : le modèle de signal et la taxonomie doivent devenir extensibles ; gouvernance des packs à prévoir ;
- coût : moyen, surtout au premier pack non technique.

### Option B — Profils codés en dur dans le produit

- avantages : simple pour deux profils ;
- inconvénients : chaque nouveau public modifie le cœur ; conditions dispersées dans le code ;
- risques : viole le critère 1.

### Option C — Une instance d'Argos par public

- avantages : isolation forte ;
- inconvénients : impossible de cumuler deux profils ; exploitation multipliée ;
- risques : viole le critère 2.

### Option D — Profils décrits en langage naturel, rapprochement confié à l'IA

- avantages : création de profil très rapide ;
- inconvénients : rapprochement non déterministe, coûteux et difficile à auditer ;
- risques : viole le critère 3 et ADR-0013.

## Décision

**Option A retenue.**

1. Un **profil de veille** est un pack déclaratif enregistré dans le module `catalog`. Le catalogue liste les profils disponibles.
2. Un utilisateur active **au moins un** profil, et peut en cumuler plusieurs. Ses profils actifs sont une préférence gérée par le module `workspace`. Une équipe peut proposer des profils par défaut à ses membres.
3. Les packs initiaux sont `developpeur` et `expert-metier`. Ils reprennent sans changement les comportements déjà proposés (ADR-0010 à ADR-0013, ADR-0018).
4. Les signaux gardent un noyau commun (identifiant, source, dates, provenance, gravité, empreinte — ADR-0003) ; un pack ajoute ses rubriques et ses attributs propres sans modifier le noyau.
5. `impact-engine` exécute les stratégies de rapprochement déclarées par les profils actifs. Chaque stratégie est déterministe et produit un niveau de confiance explicable.
6. Un profil ne modifie jamais les autorisations : la visibilité des données internes reste décidée par RBAC + RLS.
7. Les garde-fous d'un profil s'appliquent avant toute diffusion, dans toutes les mises en page et via MCP. Exemples : validation obligatoire par un expert (`expert-metier`, ADR-0018) ; pour un futur profil politique, sources institutionnelles, pluralité des sources et aucune recommandation ni classement partisan produit par l'IA.
8. Les profils `artiste`, `comedien` et `politique` sont des **exemples de packs futurs**, non engagés. Chacun demandera son propre changement OpenSpec, une revue de ses sources (licences, conditions d'usage) et, s'il concerne des personnes, une analyse avec le DPO.

## Conséquences positives

- Argos devient une plateforme de veille générique, dont la veille technique et la veille réglementaire sont les deux premiers usages ;
- un même utilisateur peut être à la fois développeur et expert métier ;
- les garde-fous propres à chaque domaine sont explicites et testables.

## Conséquences négatives

- la taxonomie des signaux (dossier v2.4 §8.2) doit être rendue extensible ;
- les profils futurs exigent de nouveaux connecteurs et parfois de nouvelles stratégies ;
- un public hors entreprise changerait l'hébergement, la sécurité et le cadre juridique du produit.

## Conséquences neutres / compromis

- les profils et les mises en page (ADR-0023) sont indépendants : tout profil s'affiche dans toute mise en page.

## Méthode de validation

- POC : un utilisateur cumulant `developpeur` et `expert-metier` reçoit l'union des deux veilles, sans doublon ;
- test d'architecture : ajouter un pack de démonstration sans modifier les packs existants ;
- tests : aucun profil n'élargit les données visibles (scénarios `PRF-05`) ;
- revue des garde-fous par domaine avec les parties prenantes (MKP, DPO).

## Traçabilité

- **Exigences** : `PRF-01` à `PRF-06` (changement OpenSpec `personnaliser-profils-et-affichage`)
- **Scénarios qualité** : —
- **Diagrammes** : fiche `/docs/specifications/profils-veille.md` (DF-PRF-1, DF-PRF-2)
- **Tickets/PR** : Non déterminé
- **Fichiers source** : Non déterminé (aucune implémentation)

## Notes

Le mot « profil » désigne ici un type de veille, et non un rôle d'autorisation : le rôle (lecteur, contributeur, administrateur) reste défini par ADR-0016.
