# Tâches : diffuser les alertes ciblées et les résumés périodiques

<!-- Aucune tâche n'est réalisée. Les tâches supposent le socle argos-server / argos-ui et les changes dont dépend celui-ci (voir la proposition). -->

## 1. Arbitrages

- [x] 1.1 Faire choisir le serveur d'envoi d'e-mails (Q-1) — couvre ALR-06 — vérification : choix inscrit dans la fiche `alertes-digests` (décidé le 2026-11-16)

## 2. Alertes immédiates

- [ ] 2.1 Créer les migrations des alertes et de leur état par canal, avec la clé d'idempotence — couvre ALR-03 — vérification : test d'unicité passe
- [ ] 2.2 Évaluer les règles sur les impacts publiés par `impact-engine` — couvre ALR-01-S1, ALR-01-S2, ALR-02-S1 — vérification : tests d'intégration passent
- [ ] 2.3 Regrouper par signal, utilisateur et canal — couvre ALR-03-S1 — vérification : test d'intégration passe
- [ ] 2.4 Demander le résumé IA sans bloquer l'envoi — couvre ALR-05-S1 — vérification : test avec ai-gateway au plafond passe ; QS-10 exécuté

## 3. Résumés et canaux

- [ ] 3.1 Planifier et composer les résumés quotidiens et hebdomadaires — couvre ALR-04-S1, ALR-04-S2 — vérification : tests avec horloge simulée passent
- [ ] 3.2 Envoyer par e-mail avec nouvelles tentatives et état d'échec — couvre ALR-06-S1, ALR-06-S2 — vérification : tests avec SMTP de test passent
- [ ] 3.3 Exposer les alertes au client desktop (contrat utilisé par `notifier-sur-le-poste`) — couvre ALR-06 — vérification : test de contrat passe

## 4. Documentation durable et décisions

- [ ] 4.1 Mettre à jour la fiche `alertes-digests`, les sections arc42 §6.1 et §8.2, le glossaire et l'inventaire `/docs/architecture/diagrams/README.md` — vérification : `node scripts/verifier-docs.mjs` sans erreur

## 5. Vérifications d'intégration

- [ ] 5.1 Valider le changement — vérification : `openspec validate diffuser-alertes-et-digests --strict` réussit
- [ ] 5.2 Parcours : faille critique de test → impact exact → alerte e-mail et desktop en moins du délai cible (Q-4), puis résumé du lendemain sans doublon — vérification : scénario E2E consigné avec ses résultats

## Matrice de traçabilité

État au 2026-11-16 : aucune tâche réalisée, aucun test écrit. Toutes les preuves sont manquantes.

| Exigence | Scénario | Conception / diagramme | Tâche | Test ou preuve | État de la preuve |
|---|---|---|---|---|---|
| ALR-01 | ALR-01-S1 | DC-1 | 2.2 | test à écrire | à écrire |
| ALR-01 | ALR-01-S2 | DC-1 | 2.2 | test à écrire | à écrire |
| ALR-02 | ALR-02-S1 | DC-1 | 2.2 | test à écrire | à écrire |
| ALR-03 | ALR-03-S1 | DC-1 | 2.1, 2.3 | test à écrire | à écrire |
| ALR-04 | ALR-04-S1 | DC-1 | 3.1 | test à écrire | à écrire |
| ALR-04 | ALR-04-S2 | DC-1 | 3.1 | test à écrire | à écrire |
| ALR-05 | ALR-05-S1 | DC-1 | 2.4 | test à écrire | à écrire |
| ALR-06 | ALR-06-S1 | DC-1 | 1.1, 3.2, 3.3 | test à écrire | à écrire |
| ALR-06 | ALR-06-S2 | DC-1 | 1.1, 3.2, 3.3 | test à écrire | à écrire |

## Workflow follow-up

- Basculer dans la fiche durable les statuts [PROPOSITION] de ce changement en [EXIGENCE] ou [EXISTANT] selon les preuves obtenues.
- Archiver le changement uniquement sur demande explicite, après revue (`openspec archive diffuser-alertes-et-digests`).
- Après archivage, vérifier que la section Purpose de la spec principale contient le lien vers la fiche durable.
- Après archivage, remplacer dans la fiche les liens vers `/openspec/changes/diffuser-alertes-et-digests/` par la spec principale ou le dossier d'archive, puis exécuter `node scripts/verifier-docs.mjs`.
