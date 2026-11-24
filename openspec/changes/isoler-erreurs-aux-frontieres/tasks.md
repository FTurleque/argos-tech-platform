# Tâches : isoler les erreurs imprévues aux frontières de traitement

<!--
Constats traités : AUD-QUA-20 (Élevée) et AUD-QUA-23 (Faible), sprint 4 (/docs/audit/2026-11-21/findings.md, /docs/audit/2026-11-21/sprints.md).
Règles : captures précises conservées à l'intérieur des unités ; filet journalisé (exception en premier argument) aux seules frontières (design.md D1) ; aucune requête vers Internet dans les tests (sources et forges simulées) ; aucune dépendance nouvelle.
Ordre du sprint 4 : après `instrumenter-observabilite`, avant `injecter-horloge-et-executeurs` (classes communes).
-->

## 1. Cause de données : décodage des clés de paquet

- [ ] 1.1 Faire renvoyer « illisible » au décodage d'une clé de paquet sur un encodage invalide, sans erreur d'exécution (I-5), et vérifier le traitement de chaque appelant (avis, SBOM, saisie de standard, paramètres MCP, recherche) — couvre COL-11-S1, INV-11-S1, RAD-07-S1, AUD-QUA-20 — vérification : test unitaire du décodage (`%zz`, `%`, `%e9%`) passe
- [ ] 1.2 Tests du groupe : saisie d'un standard de clé `pkg:maven/%zz/x` refusée en 400 avec l'attribut `cle`, référentiel inchangé ; paramètre MCP mal encodé refusé `PARAMETRES_INVALIDES` — couvre RAD-07-S1 — vérification : `mvn -B -f argos-server/pom.xml verify` passe

## 2. Collecte des avis et import des inventaires

- [ ] 2.1 Terminer une synchronisation de source en `FAILED`/`PERMANENT` sur toute erreur d'exécution imprévue (design.md D3) et poser le filet par source dans le passage planifié, sources en erreur au bilan — couvre COL-11, COL-11-S2 — vérification : tests de 2.3
- [ ] 2.2 Poser le filet par projet dans l'import planifié (relance de recorrélation et import), projet compté `enEchec`, cause journalisée — couvre INV-11, INV-11-S2 — vérification : tests de 2.3
- [ ] 2.3 Tests du groupe : deux sources dont la première reçoit un avis `pkg:npm/%zz@1.0.0` ; erreur imprévue simulée à l'enregistrement d'un avis (`FAILED`, curseur inchangé, source suivante synchronisée) ; seuil `DEGRADED` atteint par erreurs imprévues ; SBOM avec PURL `%zz` (composant écarté, projet suivant importé) ; erreur imprévue pour un projet (`Bilan.enEchec` vérifié) ; compléter `chain/ResilienceTest` par une erreur autre que `IllegalStateException` — couvre COL-11-S1, COL-11-S2, COL-11-S3, INV-11-S1, INV-11-S2 — vérification : `mvn -B -f argos-server/pom.xml verify` passe
- [ ] 2.4 Fiches `/docs/specifications/collecte-avis-securite.md` (COL-11, état `FAILED` sur erreur imprévue, traçabilité) et `/docs/specifications/inventaire-sbom.md` (INV-11, PURL mal encodé écarté) — couvre COL-11, INV-11 — vérification : `node scripts/verifier-docs.mjs` sans erreur

## 3. Radar et alertes

- [ ] 3.1 Filet journalisé dans les observateurs synchrones `StandardModifie` et `AnnonceStandard` et dans les boucles par espace du radar (design.md D4) — couvre RAD-07, RAD-07-S2 — vérification : tests de 3.3
- [ ] 3.2 Filet journalisé dans l'observateur `ImpactsRecorded` et l'envoi des alertes ; journaliser la cause avec sa pile pour le résumé IA indisponible (`MoteurAlertes`, design.md D6) — couvre AUD-QUA-20, AUD-QUA-23 — vérification : tests de 3.3 ; relecture du diff (exception en premier argument)
- [ ] 3.3 Tests du groupe : annonce d'un standard qui échoue sur une erreur imprévue (200, entrée et historique persistés, échec journalisé) ; évaluation d'alertes qui échoue sans remonter à la corrélation ; espace en erreur dans la tâche quotidienne du radar sans interrompre les autres — couvre RAD-07-S2 — vérification : `mvn -B -f argos-server/pom.xml verify` passe
- [ ] 3.4 Fiche `/docs/specifications/radar-standards.md` (RAD-07, préconditions alignées, traçabilité) — couvre RAD-07 — vérification : `node scripts/verifier-docs.mjs` sans erreur

## 4. Serveur MCP

- [ ] 4.1 Garde MCP : `INDISPONIBLE` et `Issue.ERREUR` pour toute erreur d'exécution interne, cause journalisée, Javadoc réalignée (design.md D5) ; vérifier que l'OpenAPI déclare la réponse 400 de `PUT /standards` — couvre MCP-08, RAD-07-S1 — vérification : tests de 4.2
- [ ] 4.2 Tests du groupe : panne d'accès aux données et erreur imprévue simulées dans un outil ; résultat d'erreur d'outil `INDISPONIBLE`, aucune erreur JSON-RPC, `Issue.ERREUR` au journal des appels — couvre MCP-08-S1, MCP-08-S2 — vérification : `mvn -B -f argos-server/pom.xml verify` passe ; `grep -rn INDISPONIBLE argos-server/src/test/java/io/argos/mcp` non vide
- [ ] 4.3 Fiche `/docs/specifications/serveur-mcp.md` (MCP-08, code `INDISPONIBLE`, traçabilité) — couvre MCP-08 — vérification : `node scripts/verifier-docs.mjs` sans erreur

## 5. Autres frontières

- [ ] 5.1 Filet journalisé dans les boucles de `release-radar` (cycles de vie), `reg-intel` (préparation des fiches), `project-intel` (collecte d'activité) et autour du fournisseur de modèle dans `ai-gateway` (toute erreur = modèle indisponible) — couvre AUD-QUA-20 (I-1, I-3) — vérification : un test par frontière simulant une erreur imprévue, `mvn -B -f argos-server/pom.xml verify` passe

## 6. Documentation durable et décisions

- [ ] 6.1 Écrire la règle des frontières d'erreur dans arc42 §8 (`/docs/architecture/arc42/08-concepts-transverses.md`, §8.5 Résilience) : captures précises à l'intérieur, filet aux frontières, journalisation avec l'exception en premier argument, analyseurs sans erreur d'exécution non typée, inventaire des frontières, test par frontière (design.md, « Gestion des erreurs et reprise ») — couvre AUD-QUA-20, AUD-QUA-23 — vérification : `node scripts/verifier-docs.mjs` sans erreur ; `grep -n "frontière" docs/architecture/arc42/08-concepts-transverses.md` non vide
- [ ] 6.2 Proposer un test d'architecture qui vérifie le filet des méthodes `@Scheduled` et des observateurs synchrones, ou consigner pourquoi il n'est pas retenu — couvre AUD-QUA-20 (I-1, I-3) — vérification : test présent et vert, ou décision consignée dans design.md
- [ ] 6.3 Registre `/docs/specifications/README.md` (comptes d'exigences des quatre capacités, changement actif) ; D-08 et D-09 d'arc42 §6 et inventaire `/docs/architecture/diagrams/README.md` si la reprise d'une synchronisation y est décrite ; soumettre Q-1 (type de panne dédié) au porteur du projet et, s'il est retenu, rédiger un ADR (Proposé) et l'index arc42 §9 — vérification : `node scripts/verifier-docs.mjs` sans erreur ; décision consignée

## 7. Vérifications d'intégration

- [ ] 7.1 Valider le changement et la documentation — vérification : `openspec validate isoler-erreurs-aux-frontieres --strict` réussit et `node scripts/verifier-docs.mjs` sans erreur
- [ ] 7.2 Parcours E2E de la chaîne automatisée et MCP toujours verts (sources et forges simulées) — vérification : `npm --prefix e2e test` (dont `e2e/tests/chaine-automatisee.spec.ts`, `e2e/mcp/parcours-mcp.spec.ts`)

## Matrice de traçabilité

| Exigence | Scénario | Conception / diagramme | Tâche | Test ou preuve | État de la preuve |
|---|---|---|---|---|---|
| COL-11 | COL-11-S1 | design.md D2, D3 ; DC-1, DC-2 | 1.1, 2.1, 2.3 | test d'intégration : avis `pkg:npm/%zz@1.0.0`, source suivante synchronisée | à écrire ; non exécuté |
| COL-11 | COL-11-S2 | design.md D3 ; DC-2 | 2.1, 2.3 | `chain/ResilienceTest` complété (erreur autre que `IllegalStateException`) | à écrire ; non exécuté |
| COL-11 | COL-11-S3 | design.md D3 ; DC-2 | 2.1, 2.3 | test d'intégration : seuil `DEGRADED` par erreurs imprévues | à écrire ; non exécuté |
| INV-11 | INV-11-S1 | design.md D2 ; DC-1 | 1.1, 2.3 | test d'intégration : SBOM au PURL `%zz`, composant écarté, projet suivant importé | à écrire ; non exécuté |
| INV-11 | INV-11-S2 | design.md D1 ; DC-1 | 2.2, 2.3 | test d'intégration : `Bilan.enEchec` = 1 | à écrire ; non exécuté |
| RAD-07 | RAD-07-S1 | design.md D2 ; DC-3 ; « Contrats d'interface » | 1.1, 1.2, 4.1 | test API : `PUT /standards` clé `pkg:maven/%zz/x` → 400 | à écrire ; non exécuté |
| RAD-07 | RAD-07-S2 | design.md D4 ; DC-3 | 3.1, 3.3 | test d'intégration : annonce en échec, 200 et historique persistés | à écrire ; non exécuté |
| MCP-08 | MCP-08-S1 | design.md D5 | 4.1, 4.2 | test MCP : panne d'accès aux données → `INDISPONIBLE`, `Issue.ERREUR` | à écrire ; non exécuté |
| MCP-08 | MCP-08-S2 | design.md D5 | 4.1, 4.2 | test MCP : erreur imprévue → `INDISPONIBLE`, pas d'erreur JSON-RPC | à écrire ; non exécuté |

Constat → exigence → tâche → preuve attendue :

| Constat | Exigence / scénarios | Tâches | Preuve attendue | État |
|---|---|---|---|---|
| AUD-QUA-20 (PURL mal encodé, synchronisation des sources) | COL-11-S1, COL-11-S2, COL-11-S3 | 1.1, 2.1, 2.3 | tests d'intégration verts | non exécuté |
| AUD-QUA-20 (import nocturne) | INV-11-S1, INV-11-S2 | 1.1, 2.2, 2.3 | tests d'intégration verts ; `grep -rn enEchec argos-server/src/test/java` non vide | non exécuté |
| AUD-QUA-20 (PUT /standards après commit ; AUD-ARC-23) | RAD-07-S1, RAD-07-S2 | 1.2, 3.1, 3.3 | tests API et d'intégration verts | non exécuté |
| AUD-QUA-20 (garde MCP ; AUD-QUA-22) | MCP-08-S1, MCP-08-S2 | 4.1, 4.2 | tests MCP verts | non exécuté |
| AUD-QUA-20 (autres frontières : alertes, cycles de vie, fiches, activité, passerelle IA) | — (règle d'arc42 §8.5) | 3.2, 5.1, 6.2 | un test par frontière | non exécuté |
| AUD-QUA-20 (règle absente d'arc42 §8) | — | 6.1 | arc42 §8.5 à jour ; `node scripts/verifier-docs.mjs` sans erreur | non exécuté |
| AUD-QUA-23 (cause sans pile) | — (tâche seulement) | 3.2, 6.1 | diff relu, règle I-4 écrite | non exécuté |

## Workflow follow-up

- Basculer dans les fiches `collecte-avis-securite`, `inventaire-sbom`, `radar-standards` et `serveur-mcp` le statut [PROPOSITION] de COL-11, INV-11, RAD-07 et MCP-08 selon les preuves obtenues, et les statuts d'AUD-QUA-20 et AUD-QUA-23 dans le suivi d'audit.
- Archiver le changement uniquement sur demande explicite, après revue (`openspec archive isoler-erreurs-aux-frontieres`).
- Après archivage, remplacer dans les fiches les liens vers `/openspec/changes/isoler-erreurs-aux-frontieres/` par la spec principale ou le dossier d'archive, puis exécuter `node scripts/verifier-docs.mjs`.
