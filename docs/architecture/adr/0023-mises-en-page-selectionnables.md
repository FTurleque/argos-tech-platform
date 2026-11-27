---
id: ADR-0023
title: "Séparer le contenu de la mise en page et laisser chaque utilisateur choisir la sienne"
domain: architecture
rules: false
---

# ADR-0023 — Séparer le contenu de la mise en page et laisser chaque utilisateur choisir la sienne

- **Statut** : Accepté
- **Date** : 2026-11-18 (acceptation ; proposition du 2026-11-15)
- **Décideurs** : porteur du projet (acceptation du 2026-11-18)
- **Remplace** : —
- **Remplacé par** : —

## Contexte

Le dossier v2.4 prévoit une IHM unique (portail Vue.js 3, ADR-0019), reprise par le client desktop (ADR-0022). Le porteur du projet souhaite une IHM « plus jolie et qui ne fasse pas penser au travail », et demande que **chaque utilisateur puisse choisir sa mise en page**. Quatre mises en page ont été maquettées le 2026-11-15 et sont retenues pour démarrer :

| Mise en page | Esprit |
|---|---|
| `classique` | application de travail : menu latéral, filtres, listes |
| `journal` | quotidien contemporain : une, rubriques, colonnes |
| `fantaisie` | magazine décalé et coloré |
| `gazette` | presse imprimée du XIXᵉ siècle : papier vieilli, gothique, avis de recherche |

Les quatre affichent **les mêmes informations** ; seules la présentation et la structure de la page changent. D'autres mises en page viendront plus tard.

Contraintes : le contenu et les droits ne dépendent jamais de l'affichage (ADR-0013, ADR-0016) ; les mentions « résumé IA » restent visibles partout (dossier v2.4 §A.3) ; le desktop interdit les ressources distantes par sa politique de sécurité du contenu (ADR-0022).

## Critères de décision

1. Ajouter une mise en page sans toucher au serveur, à l'API ni au code des autres mises en page.
2. Contenu, droits et mentions identiques quelle que soit la mise en page.
3. Choix enregistré par utilisateur et partagé entre le portail web et le desktop.
4. Accessibilité tenue dans chaque mise en page.
5. Fonctionnement sans ressource externe (intranet, desktop).

## Options considérées

### Option A — Couche de contenu commune et mises en page enregistrées dans un catalogue

- l'IHM est découpée en deux couches : une couche **contenu** (appels API, droits, tri, construction de modèles de vue : une, rubriques, articles, agenda) et une couche **mise en page** (composants Vue propres à chaque style) ;
- chaque mise en page est un module déclaré dans un catalogue (identifiant, nom, description, aperçu, polices, jetons de couleur) ;
- avantages : ajout d'une mise en page = un nouveau module ; aucune duplication de logique ; contenu testable une seule fois ;
- inconvénients : discipline de découpage à tenir ; chaque mise en page doit couvrir tous les modèles de vue ;
- coût : moyen au départ, faible ensuite.

### Option B — Thèmes purement graphiques (feuilles de style et jetons)

- avantages : très simple ;
- inconvénients : ne permet pas les structures différentes maquettées (une de journal, avis de recherche, colonnes) ;
- risques : ne répond pas à la demande.

### Option C — Une IHM complète par style

- avantages : liberté totale ;
- inconvénients : logique dupliquée quatre fois ; divergences de contenu et de droits ;
- risques : viole le critère 2.

## Décision

**Option A retenue.**

1. `argos-ui` sépare une couche contenu et une couche mise en page. Une mise en page ne fait aucun appel réseau et n'applique aucune règle de droits : elle reçoit des modèles de vue déjà filtrés.
2. Les quatre mises en page initiales sont `classique` (par défaut), `journal`, `fantaisie` et `gazette`.
3. Le choix est une **préférence utilisateur** enregistrée par le serveur (module `workspace`), donc identique sur le web et le desktop. Sans choix, ou si la mise en page choisie est retirée, `classique` s'applique.
4. Chaque mise en page affiche les mentions obligatoires (résumé IA, sources, niveau de confiance), fournies par la couche contenu sous forme de composants communs qu'elle ne peut pas omettre.
5. Les polices sont embarquées dans l'application (aucun appel à un service de polices externe). Elles doivent être sous licence compatible ADR-0021 ; les polices maquettées sont sous SIL Open Font License, à vérifier une par une à l'introduction.
6. Chaque mise en page est soumise au même contrôle d'accessibilité avant d'entrer au catalogue.

## Conséquences positives

- l'IHM peut changer de ton sans risque pour le contenu ni pour les droits ;
- les nouvelles mises en page deviennent un travail purement front-end ;
- un même utilisateur retrouve sa mise en page sur tous ses postes.

## Conséquences négatives

- chaque nouvel écran fonctionnel doit être décliné dans toutes les mises en page du catalogue ;
- le poids de l'IHM augmente avec les polices embarquées.

## Conséquences neutres / compromis

- le contenu peut être plus ou moins condensé selon la mise en page, mais jamais tronqué de ses mentions obligatoires.

## Méthode de validation

- POC : bascule entre les quatre mises en page sur un même jeu de données, contenu comparé automatiquement ;
- test d'architecture front-end : aucune mise en page n'importe le client API ;
- audit d'accessibilité de chaque mise en page ;
- préférence modifiée sur le web puis constatée dans le desktop.

## Traçabilité

- **Exigences** : `AFF-01` à `AFF-05` (changement OpenSpec `personnaliser-profils-et-affichage`)
- **Scénarios qualité** : —
- **Diagrammes** : fiche `/docs/specifications/affichage-personnalise.md` (DF-AFF-1)
- **Tickets/PR** : Non déterminé
- **Fichiers source** : `argos-ui/` ; maquettes : artefact « ARGOS desktop — maquettes des écrans »

## Notes

Les noms de mise en page sont des identifiants techniques ; les libellés affichés pourront évoluer.
