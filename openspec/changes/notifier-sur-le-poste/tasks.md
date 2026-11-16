# Tâches : notifier les alertes sur le poste de travail

<!-- Aucune tâche n'est réalisée. Les tâches supposent `diffuser-alertes-et-digests` (flux d'alertes) et le client desktop Electron existant. -->

## 1. Flux et notifications

- [ ] 1.1 Consommer le flux d'alertes avec curseur dans la couche contenu de `argos-ui` — couvre NOT-01, NOT-06-S1 — vérification : test E2E de reprise passe
- [ ] 1.2 Étendre le pont `window.argosDesktop` (identifiant d'élément, compteur) et le processus principal — couvre NOT-01-S1, NOT-01-S2, NOT-02-S1, NOT-03-S1 — vérification : tests E2E Electron passent ; `npm run check` passe
- [ ] 1.3 Limiter le texte des notifications — couvre NOT-05-S1 — vérification : test unitaire passe
- [ ] 1.4 Ajouter la préférence « notifications du poste » dans les réglages — couvre NOT-04-S1 — vérification : test d'intégration passe

## 2. Documentation durable et décisions

- [ ] 2.1 Mettre à jour la fiche `notifications-poste`, les sections arc42 §5.1 (client desktop), le glossaire et l'inventaire `/docs/architecture/diagrams/README.md` — vérification : `node scripts/verifier-docs.mjs` sans erreur

## 3. Vérifications d'intégration

- [ ] 3.1 Valider le changement — vérification : `openspec validate notifier-sur-le-poste --strict` réussit
- [ ] 3.2 Parcours : client desktop masqué, alerte critique de test, notification, clic, alerte affichée ; puis coupure et reprise sans perte — vérification : scénario E2E consigné avec ses résultats

## Matrice de traçabilité

État au 2026-11-16 : aucune tâche réalisée, aucun test écrit. Toutes les preuves sont manquantes.

| Exigence | Scénario | Conception / diagramme | Tâche | Test ou preuve | État de la preuve |
|---|---|---|---|---|---|
| NOT-01 | NOT-01-S1 | DC-1 | 1.1, 1.2 | test à écrire | à écrire |
| NOT-01 | NOT-01-S2 | DC-1 | 1.1, 1.2 | test à écrire | à écrire |
| NOT-02 | NOT-02-S1 | DC-1 | 1.2 | test à écrire | à écrire |
| NOT-03 | NOT-03-S1 | DC-1 | 1.2 | test à écrire | à écrire |
| NOT-04 | NOT-04-S1 | DC-1 | 1.4 | test à écrire | à écrire |
| NOT-05 | NOT-05-S1 | DC-1 | 1.3 | test à écrire | à écrire |
| NOT-06 | NOT-06-S1 | DC-1 | 1.1 | test à écrire | à écrire |

## Workflow follow-up

- Basculer dans la fiche durable les statuts [PROPOSITION] de ce changement en [EXIGENCE] ou [EXISTANT] selon les preuves obtenues.
- Archiver le changement uniquement sur demande explicite, après revue (`openspec archive notifier-sur-le-poste`).
- Après archivage, vérifier que la section Purpose de la spec principale contient le lien vers la fiche durable.
- Après archivage, remplacer dans la fiche les liens vers `/openspec/changes/notifier-sur-le-poste/` par la spec principale ou le dossier d'archive, puis exécuter `node scripts/verifier-docs.mjs`.
