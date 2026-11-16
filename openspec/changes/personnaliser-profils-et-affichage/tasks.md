# Tâches : personnaliser les profils de veille et la mise en page

<!-- Aucune tâche n'est réalisée. Les tâches d'implémentation supposent le socle argos-server / argos-ui existant et le module workspace du changement formaliser-chaine-securite-poc. -->

## 1. Arbitrages

- [x] 1.1 Faire trancher la portée d'Argos : outil interne ou produit ouvert à d'autres publics (Q-1) — couvre ADR-0024 point 8 — vérification : décision consignée dans la fiche `profils-veille` (produit ouvert, ADR-0025, 2026-11-16)
- [x] 1.2 Faire décider si un profil proposé par l'équipe peut être désactivé (Q-2) — couvre PRF-01 — vérification : décision inscrite dans la fiche `profils-veille` et, si elle crée une règle, exigence ajoutée au delta (décidé le 2026-11-16)
- [x] 1.3 Faire décider l'articulation entre abonnements `PERSONAL` et profils actifs (Q-3) — couvre PRF-02, ABO-04 — vérification : décision inscrite dans les fiches `profils-veille` et `abonnements-veille` (décidé le 2026-11-16)
- [x] 1.4 Faire fixer le référentiel d'accessibilité (Q-4) — couvre AFF-05 — vérification : référentiel inscrit dans la fiche `affichage-personnalise` (décidé le 2026-11-16)

## 2. Préférences et mise en page

- [ ] 2.1 Écrire l'OpenAPI des préférences et du catalogue des mises en page, puis la migration des préférences dans `workspace` — couvre AFF-01, AFF-03, AFF-04 — vérification : test de contrat OpenAPI passe
- [ ] 2.2 Implémenter le choix, le refus d'une mise en page inconnue et le défaut `classique` — couvre AFF-01-S1, AFF-01-S2, AFF-04-S1, AFF-04-S2 — vérification : tests d'intégration passent
- [ ] 2.3 Découper `argos-ui` en couche contenu et couche mise en page, avec un test d'architecture interdisant à une mise en page d'importer le client API — couvre AFF-02 — vérification : test d'architecture front-end passe
- [ ] 2.4 Implémenter les quatre mises en page à partir des maquettes, polices embarquées et licences enregistrées — couvre AFF-01, AFF-02 — vérification : E2E « même jeu de données dans les quatre mises en page » passe (AFF-02-S1, AFF-02-S2)
- [ ] 2.5 Lire la préférence dans le client desktop — couvre AFF-03-S1 — vérification : E2E web puis desktop passe
- [ ] 2.6 Auditer l'accessibilité des quatre mises en page — couvre AFF-05-S1 — vérification : rapport d'audit versionné
- [ ] 2.7 Mettre à jour la fiche `affichage-personnalise` — vérification : `node scripts/verifier-docs.mjs` sans erreur

## 3. Profils de veille

- [ ] 3.1 Définir le format du manifeste de profil et le catalogue dans `catalog` ; publier `developpeur` et `expert-metier` — couvre PRF-03-S1 — vérification : test d'intégration du catalogue passe
- [ ] 3.2 Enregistrer les profils actifs dans `workspace`, avec au moins un profil actif — couvre PRF-01-S1 à PRF-01-S3 — vérification : tests d'intégration passent
- [ ] 3.3 Brancher les stratégies de rapprochement des profils dans `impact-engine` — couvre PRF-03-S2 — vérification : tests unitaires des stratégies passent
- [ ] 3.4 Restituer l'union des profils actifs, sans doublon, avec rubriques — couvre PRF-02-S1, PRF-02-S2 — vérification : tests d'intégration passent
- [ ] 3.5 Publier un profil de démonstration et vérifier l'absence d'effet sur les autres utilisateurs — couvre PRF-04-S1 — vérification : test d'intégration passe
- [ ] 3.6 Vérifier qu'aucun profil n'élargit les données visibles — couvre PRF-05-S1 — vérification : tests inter-espaces passent ; règle ArchUnit : `workspace` ne lit pas les préférences pour décider d'une autorisation
- [ ] 3.7 Appliquer les garde-fous côté serveur, dans l'API et le serveur MCP — couvre PRF-06-S1, PRF-06-S2 — vérification : tests d'intégration et de contrat MCP passent
- [ ] 3.8 Mettre à jour la fiche `profils-veille` — vérification : `node scripts/verifier-docs.mjs` sans erreur

## 4. Documentation durable et décisions

- [ ] 4.1 Mettre à jour arc42 §5 (responsabilités de `catalog`, `workspace`, IHM), arc42 §8 (taxonomie extensible des signaux) et le glossaire (« profil de veille », « mise en page »), puis l'inventaire `/docs/architecture/diagrams/README.md` — vérification : `node scripts/verifier-docs.mjs` sans erreur
- [ ] 4.2 Faire revoir ADR-0023 et ADR-0024 et mettre à jour leur statut dans l'index et arc42 §9 — vérification : index ADR et arc42 §9 synchronisés

## 5. Vérifications d'intégration

- [ ] 5.1 Valider le changement — vérification : `openspec validate personnaliser-profils-et-affichage --strict` réussit
- [ ] 5.2 Parcours complet : un utilisateur `developpeur` + `expert-metier` choisit la mise en page `gazette` sur le web, la retrouve sur le desktop et voit l'union de ses veilles — vérification : scénario E2E consigné avec ses résultats

## Matrice de traçabilité

État au 2026-11-16 : aucune tâche réalisée, aucun test écrit. Toutes les preuves sont manquantes.

| Exigence | Scénario | Conception / diagramme | Tâche | Test ou preuve | État de la preuve |
|---|---|---|---|---|---|
| AFF-01 | AFF-01-S1 | DC-1 | 2.1, 2.2 | test d'intégration des préférences | à écrire |
| AFF-01 | AFF-01-S2 | DC-1 | 2.2 | test d'intégration des préférences | à écrire |
| AFF-02 | AFF-02-S1 | DC-2, D1 | 2.3, 2.4 | E2E quatre mises en page | à écrire |
| AFF-02 | AFF-02-S2 | DC-2, D1 | 2.4 | E2E quatre mises en page | à écrire |
| AFF-03 | AFF-03-S1 | DC-1 | 2.5 | E2E web puis desktop | à écrire |
| AFF-04 | AFF-04-S1 | DC-1, INV-2 | 2.2 | test d'intégration | à écrire |
| AFF-04 | AFF-04-S2 | DC-1, INV-2 | 2.2 | test d'intégration | à écrire |
| AFF-05 | AFF-05-S1 | stratégie de vérification | 1.4, 2.6 | rapport d'audit d'accessibilité | à écrire ; référentiel non fixé |
| PRF-01 | PRF-01-S1 | DC-1, INV-1 | 3.2 | test d'intégration | à écrire |
| PRF-01 | PRF-01-S2 | DC-1, INV-1, D3 | 1.2, 3.2 | test d'intégration | à écrire |
| PRF-01 | PRF-01-S3 | DC-1, INV-2 | 3.2 | test d'intégration | à écrire |
| PRF-02 | PRF-02-S1 | DC-2 | 1.3, 3.4 | test d'intégration | à écrire |
| PRF-02 | PRF-02-S2 | DC-2 | 3.4 | test d'intégration | à écrire |
| PRF-03 | PRF-03-S1 | DC-1, D2 | 3.1 | test du catalogue | à écrire |
| PRF-03 | PRF-03-S2 | DC-2, D2 | 3.3 | tests unitaires des stratégies | à écrire |
| PRF-04 | PRF-04-S1 | D2 | 3.5 | test d'intégration | à écrire |
| PRF-05 | PRF-05-S1 | DC-1, INV-3 | 3.6 | tests inter-espaces, ArchUnit | à écrire |
| PRF-06 | PRF-06-S1 | DC-2 | 3.7 | tests d'intégration et de contrat MCP | à écrire |
| PRF-06 | PRF-06-S2 | DC-2 | 3.7 | tests d'intégration | à écrire |

## Workflow follow-up

- Basculer dans les fiches durables les statuts [PROPOSITION] de ce changement en [EXIGENCE] ou [EXISTANT] selon les preuves obtenues.
- Archiver le changement uniquement sur demande explicite, après revue (`openspec archive personnaliser-profils-et-affichage`).
- Après archivage, vérifier que la section Purpose des nouvelles specs principales contient le lien vers la fiche durable.
- Après archivage, remplacer dans les fiches les liens vers `/openspec/changes/personnaliser-profils-et-affichage/` par la spec principale ou le dossier d'archive, puis exécuter `node scripts/verifier-docs.mjs`.
