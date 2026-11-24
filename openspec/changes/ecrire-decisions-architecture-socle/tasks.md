# Tâches : écrire les décisions d'architecture du socle

<!--
Changement documentaire du sprint 5 (constat AUD-ARC-14, AUD-DOC-09 fusionné), skip_specs: true.
Statuts cibles des 14 ADR « Proposé » : [DÉCISION] décision du porteur, 2026-11-24 (design.md, D1).
Règle : l'ADR du noyau commun ne change de statut que sur décision écrite du porteur.
Constats : /docs/audit/2026-11-21/findings.md#aud-arc-14 (dossier non versionné, cité par chemin).
-->

## 1. Vérification et décision

- [ ] 1.1 Relire contre la révision courante la preuve de chacun des 14 ADR « Proposé » (design.md, D1) au moment d'appliquer les statuts ; signaler au porteur toute preuve disparue avant d'appliquer le statut concerné — couvre AUD-ARC-14 — vérification : tableau D1 daté de la révision relue, chaque ligne avec un chemin `fichier:ligne` existant
- [x] 1.2 Soumettre au porteur du projet les 14 recommandations et les questions sur ADR-0008, ADR-0007 et ADR-0012 — couvre AUD-ARC-14 — vérification : décision du porteur du 2026-11-24, consignée sous « Décisions du porteur » et dans design.md, D1

## 2. Application des statuts décidés

- [ ] 2.1 Passer en « Accepté » ADR-0001, 0010, 0011, 0013, 0014, 0015, 0016, 0020 et 0022 : ligne Statut, champ « Décideurs » (porteur du projet), date 2026-11-24 — couvre AUD-ARC-14 — vérification : `grep -m1 Statut` de ces neuf fichiers renvoie « Accepté »
- [ ] 2.2 ADR-0021 : corriger le contexte (ligne 16, dépendances désormais déclarées et contrôlées par le profil `licences`) sans changer la décision, puis passer en « Accepté » (décideur, 2026-11-24) — couvre AUD-DOC-09 — vérification : relecture ; `argos-server/pom.xml` (profil `licences`) cité ; Statut « Accepté »
- [ ] 2.3 ADR-0008 : passer en « Accepté » pour le principe (OIDC, autorisation fine dans ARGOS), avec renvoi à ADR-0027 pour le choix de l'IdP ; remplacer « Complété par ADR-0027 (Proposé) » par le statut accepté d'ADR-0027 — couvre AUD-ARC-14 — vérification : liens croisés ADR-0008 ↔ ADR-0027 cohérents (règle 2 du README des ADR) ; Statut « Accepté » avec la portée écrite
- [ ] 2.4 ADR-0003 : amendement de clarification (noms `IntelligenceItem` / `ProjectActivity` remplacés par les modèles normalisés réels, dont `NormalizedAdvisory`), puis « Accepté » (décideur, 2026-11-24) ; ADR-0007 et ADR-0012 : statut « Proposé » inchangé — couvre AUD-ARC-14 — vérification : relecture ; `grep -m1 Statut` d'ADR-0007 et d'ADR-0012 renvoie toujours « Proposé »
- [ ] 2.5 ADR-0028 et ADR-0030 : remplacer « à l'acceptation du présent ADR » et le statut « (Proposé) » du prédécesseur par la date de remplacement — couvre AUD-ARC-14 — vérification : relecture ; ADR-0004 et ADR-0006 inchangés (déjà « Remplacé »)
- [ ] 2.6 Mettre à jour `/docs/architecture/adr/README.md` (statut de chaque ADR dans l'index, phrase finale sur ADR-0010 à ADR-0016) et `/docs/architecture/arc42/09-decisions.md` (colonne Statut) — couvre AUD-ARC-14 — vérification : statut identique dans les trois emplacements pour chaque ADR (design.md, RD-2) ; `node scripts/verifier-docs.mjs` sans erreur

## 3. ADR du noyau commun

- [ ] 3.1 Rédiger l'ADR du noyau commun au statut **Proposé** depuis `docs/architecture/adr/template.md`, prochain numéro libre (ADR-0034 au 2026-11-24) : contexte (AUD-ARC-05, AUD-ARC-04), options A, B, C de design.md D2, périmètre (ce qui entre et n'entre pas dans le paquet commun), validation par le test des frontières — couvre AUD-ARC-14 (prérequis d'AUD-ARC-05) — vérification : fichier présent, statut Proposé, référencé dans `/docs/architecture/adr/README.md` et `/docs/architecture/arc42/09-decisions.md`
- [ ] 3.2 Soumettre l'ADR du noyau commun au porteur (proposal.md, Q-5), puis appliquer sa décision ; s'il n'est pas accepté, signaler le blocage de `extraire-noyau-commun` — vérification : décision du porteur consignée sous « Décisions du porteur » ; statut de l'ADR conforme

## 4. Vérifications d'intégration

- [ ] 4.1 Valider le changement et la documentation — vérification : `openspec validate ecrire-decisions-architecture-socle --strict` réussit ; `node scripts/verifier-docs.mjs` sans erreur
- [ ] 4.2 Recompter les statuts — vérification : `grep -m1 Statut docs/architecture/adr/0*.md` ; seuls ADR-0007, ADR-0012 et l'ADR du noyau commun (si non encore décidé) restent « Proposé » parmi ceux traités ici

## Matrice de traçabilité

| Constat | Exigence / scénario | Conception / diagramme | Tâche | Test ou preuve | État de la preuve |
|---|---|---|---|---|---|
| AUD-ARC-14 (statuts des ADR appliqués) | aucune (`skip_specs`) | design.md D1, DC-1, RD-1, RD-2 | 1.1, 1.2, 2.1, 2.3, 2.4, 2.5, 2.6, 4.2 | relevé des lignes Statut ; décision du porteur du 2026-11-24 | décision obtenue (1.2) ; application non exécutée |
| AUD-ARC-14 (ADR du noyau commun) | aucune | design.md D2 | 3.1, 3.2 | ADR Proposé versionné, décision du porteur | non exécuté |
| AUD-DOC-09 (contexte d'ADR-0021) | aucune | design.md D1 (ligne 0021) | 2.2 | ADR-0021 relu | non exécuté |
| AUD-ARC-14 (synchronisation des index) | aucune | design.md RD-2, RD-3 | 2.6, 4.1 | `node scripts/verifier-docs.mjs` | non exécuté |

## Décisions du porteur

- 2026-11-24 — [DÉCISION] statuts des 14 ADR « Proposé » : « Accepté » pour ADR-0001, 0010, 0011, 0013, 0014, 0015, 0016, 0020, 0022 ; « Accepté » après clarification pour ADR-0003 et après correction de la ligne 16 pour ADR-0021 ; « Accepté » pour le principe avec renvoi à ADR-0027 pour ADR-0008 ; « Proposé » maintenu pour ADR-0007 et ADR-0012. La révision d'ADR-0009 et d'ADR-0021 demandée par ADR-0025 reste hors périmètre.
- ADR du noyau commun : décision attendue (tâche 3.2).

## Workflow follow-up

- Mettre à jour le statut d'AUD-ARC-14 et d'AUD-DOC-09 dans `/docs/audit/2026-11-21/etat.md` selon les statuts appliqués.
- Archiver le changement uniquement sur demande explicite, après revue (`openspec archive ecrire-decisions-architecture-socle`).
- Prévenir `extraire-noyau-commun` de la décision du porteur sur l'ADR du noyau commun.
