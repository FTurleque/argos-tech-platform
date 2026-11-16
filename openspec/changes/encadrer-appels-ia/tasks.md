# Tâches : encadrer tous les appels aux modèles d'IA par une passerelle unique

<!-- Aucune tâche n'est réalisée. Les tâches supposent le socle argos-server / argos-ui et les changes dont dépend celui-ci (voir la proposition). -->

## 1. Arbitrages

- [x] 1.1 Faire fixer le plafond mensuel, le quota par espace et les classes transmissibles (Q-1, Q-2, Q-4) — couvre IAG-02, IAG-03 — vérification : valeurs inscrites dans la fiche `passerelle-ia` (décidé le 2026-11-16)

## 2. Passerelle

- [ ] 2.1 Créer le contrat interne, le registre de prompts versionnés et les tables de traçabilité — couvre IAG-01-S1 — vérification : test d'intégration avec fournisseur simulé passe
- [ ] 2.2 Appliquer la classification des entrées — couvre IAG-02-S1, IAG-02-S2 — vérification : tests d'intégration passent ; QS-09 exécuté
- [ ] 2.3 Appliquer plafond et quotas avec poursuite sans IA — couvre IAG-03-S1 — vérification : test d'intégration passe ; QS-08 exécuté
- [ ] 2.4 Valider les sorties par schéma — couvre IAG-04-S1 — vérification : test d'intégration passe ; QS-15 exécuté
- [ ] 2.5 Fournir la mention IA et les sources aux couches de restitution — couvre IAG-05-S1 — vérification : test E2E passe

## 3. Documentation durable et décisions

- [ ] 3.1 Mettre à jour la fiche `passerelle-ia`, les sections arc42 §8.8, le glossaire et l'inventaire `/docs/architecture/diagrams/README.md` — vérification : `node scripts/verifier-docs.mjs` sans erreur

## 4. Vérifications d'intégration

- [ ] 4.1 Valider le changement — vérification : `openspec validate encadrer-appels-ia --strict` réussit
- [ ] 4.2 Parcours : résumé d'un avis public (tracé), refus d'une donnée confidentielle, plafond atteint puis alerte diffusée sans résumé — vérification : scénario E2E consigné avec ses résultats

## Matrice de traçabilité

État au 2026-11-16 : aucune tâche réalisée, aucun test écrit. Toutes les preuves sont manquantes.

| Exigence | Scénario | Conception / diagramme | Tâche | Test ou preuve | État de la preuve |
|---|---|---|---|---|---|
| IAG-01 | IAG-01-S1 | DC-1 | 2.1 | test à écrire | à écrire |
| IAG-02 | IAG-02-S1 | DC-1 | 1.1, 2.2 | test à écrire | à écrire |
| IAG-02 | IAG-02-S2 | DC-1 | 1.1, 2.2 | test à écrire | à écrire |
| IAG-03 | IAG-03-S1 | DC-1 | 1.1, 2.3 | test à écrire | à écrire |
| IAG-04 | IAG-04-S1 | DC-1 | 2.4 | test à écrire | à écrire |
| IAG-05 | IAG-05-S1 | DC-1 | 2.5 | test à écrire | à écrire |

## Workflow follow-up

- Basculer dans la fiche durable les statuts [PROPOSITION] de ce changement en [EXIGENCE] ou [EXISTANT] selon les preuves obtenues.
- Archiver le changement uniquement sur demande explicite, après revue (`openspec archive encadrer-appels-ia`).
- Après archivage, vérifier que la section Purpose de la spec principale contient le lien vers la fiche durable.
- Après archivage, remplacer dans la fiche les liens vers `/openspec/changes/encadrer-appels-ia/` par la spec principale ou le dossier d'archive, puis exécuter `node scripts/verifier-docs.mjs`.
