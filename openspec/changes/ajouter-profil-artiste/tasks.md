# Tâches : ajouter le profil de veille « artiste »

<!-- Aucune tâche n'est réalisée. Les tâches supposent `personnaliser-profils-et-affichage` (format du manifeste et catalogue). -->

## 1. Arbitrages

- [x] 1.1 Faire choisir les sources prioritaires, le périmètre géographique et les disciplines (Q-1 à Q-3) — couvre ART-02, ART-05 — vérification : choix inscrits dans la fiche `profil-artiste` (décidé le 2026-11-16)

## 2. Pack et rapprochement

- [ ] 2.1 Écrire le manifeste du pack `artiste` (rubriques, listes, garde-fous) — couvre ART-01-S1 — vérification : test du catalogue passe
- [ ] 2.2 Enregistrer disciplines et territoires suivis — couvre ART-02-S1, ART-02-S2 — vérification : tests d'intégration passent
- [ ] 2.3 Implémenter la stratégie discipline + territoire + date limite — couvre ART-03-S1, ART-03-S2 — vérification : tests unitaires passent
- [ ] 2.4 Planifier les rappels de date limite — couvre ART-04-S1 — vérification : test avec horloge simulée passe

## 3. Sources et garde-fous

- [ ] 3.1 Bloquer la collecte des sources non vérifiées — couvre ART-05-S1 — vérification : test d'intégration passe
- [ ] 3.2 Appliquer le gabarit de résumé sans avis d'éligibilité — couvre ART-06-S1 — vérification : test d'intégration et revue passent

## 4. Documentation durable et décisions

- [ ] 4.1 Mettre à jour la fiche `profil-artiste`, les sections arc42 §5.4 (personnalisation), le glossaire et l'inventaire `/docs/architecture/diagrams/README.md` — vérification : `node scripts/verifier-docs.mjs` sans erreur

## 5. Vérifications d'intégration

- [ ] 5.1 Valider le changement — vérification : `openspec validate ajouter-profil-artiste --strict` réussit
- [ ] 5.2 Parcours : utilisateur développeur active le profil artiste, choisit arts visuels et Occitanie, reçoit un appel correspondant puis son rappel ; sa veille développeur est inchangée — vérification : scénario E2E consigné avec ses résultats

## Matrice de traçabilité

État au 2026-11-16 : aucune tâche réalisée, aucun test écrit. Toutes les preuves sont manquantes.

| Exigence | Scénario | Conception / diagramme | Tâche | Test ou preuve | État de la preuve |
|---|---|---|---|---|---|
| ART-01 | ART-01-S1 | Composants | 2.1 | test à écrire | à écrire |
| ART-02 | ART-02-S1 | Composants | 1.1, 2.2 | test à écrire | à écrire |
| ART-02 | ART-02-S2 | Composants | 1.1, 2.2 | test à écrire | à écrire |
| ART-03 | ART-03-S1 | DC-1 | 2.3 | test à écrire | à écrire |
| ART-03 | ART-03-S2 | DC-1 | 2.3 | test à écrire | à écrire |
| ART-04 | ART-04-S1 | DC-1 | 2.4 | test à écrire | à écrire |
| ART-05 | ART-05-S1 | Composants | 1.1, 3.1 | test à écrire | à écrire |
| ART-06 | ART-06-S1 | Composants | 3.2 | test à écrire | à écrire |

## Workflow follow-up

- Basculer dans la fiche durable les statuts [PROPOSITION] de ce changement en [EXIGENCE] ou [EXISTANT] selon les preuves obtenues.
- Archiver le changement uniquement sur demande explicite, après revue (`openspec archive ajouter-profil-artiste`).
- Après archivage, vérifier que la section Purpose de la spec principale contient le lien vers la fiche durable.
- Après archivage, remplacer dans la fiche les liens vers `/openspec/changes/ajouter-profil-artiste/` par la spec principale ou le dossier d'archive, puis exécuter `node scripts/verifier-docs.mjs`.
