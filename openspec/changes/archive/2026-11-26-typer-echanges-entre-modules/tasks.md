# Tâches : typer les échanges entre modules

<!--
Refactoring du sprint 5 (AUD-QUA-08), skip_specs: true. À appliquer après extraire-noyau-commun (mêmes fichiers).
Règles : JSON REST et MCP inchangé ; aucune migration ; aucune branche default sur les énumérations introduites ;
comportement du bilan de l'import planifié conservé (Q-1 de proposal.md).
-->

## 1. Niveau d'impact de bout en bout

- [x] 1.1 Exposer l'énumération du niveau (design.md, D1) dans la vue d'impact et la convertir à la lecture de `project_impact.level` ; une valeur inconnue lève une erreur explicite — couvre AUD-QUA-08 (niveau), non-régression COR-02 — vérification : `grep -rn "String level" argos-server/src/main/java/io/argos/impact` vide ; tests de corrélation existants verts
- [x] 1.2 Propager le type dans les vues de `api` (veille, suivi), de `mcp` (outils de sécurité) et dans le contenu des alertes ; moteur d'alertes : comparaison et rang sur l'énumération, `switch` sans `default` (design.md, D2) — couvre AUD-QUA-08, non-régression ALR-02 — vérification : `grep -n '"A_VERIFIER"\|case "EXACT"' argos-server/src/main/java/io/argos/alerting/MoteurAlertes.java` vide ; tests d'alertes existants verts
- [x] 1.3 Tests du groupe : sérialisation JSON de `level` dans une réponse REST et une sortie MCP (TE-1) ; relecture d'un contenu d'alerte JSON écrit avant le changement (TE-2) ; conversion d'une valeur inconnue (TE-4) — vérification : `mvn -B -f argos-server/pom.xml verify` passe

## 2. Clé de résumé

- [x] 2.1 Remplacer la clé « fréquence:canal » par un record (design.md, D4) — couvre AUD-QUA-08 (clé composite), non-régression ALR-04 — vérification : `grep -n 'split(":")' argos-server/src/main/java/io/argos/alerting/MoteurAlertes.java` vide ; tests des résumés périodiques verts

## 3. Statuts d'import et d'analyse

- [x] 3.1 Énumération du statut d'import d'inventaire ; `switch` exhaustif de l'import planifié (`KEPT` et `NONE` comptés « conservés » comme aujourd'hui) — couvre AUD-QUA-08 (statuts d'import), non-régression INV-07 — vérification : aucune branche `default` dans `inventory/ImportPlanifie.java` ; test du bilan par statut
- [x] 3.2 Énumération du statut d'analyse complémentaire de la corrélation — couvre AUD-QUA-08 (statuts de corrélation) — vérification : `grep -n '"NONE"\|"DONE"\|"UNAVAILABLE"\|"BUDGET_EXHAUSTED"' argos-server/src/main/java/io/argos/impact/CorrelationEngine.java` vide ; tests existants de la corrélation et de l'analyse complémentaire verts
- [x] 3.3 Documentation du groupe : arc42 §8.16 « Conventions de code », règle de typage des valeurs fermées échangées entre modules (TE-3) — vérification : `node scripts/verifier-docs.mjs` sans erreur

## 4. Vérifications d'intégration

- [x] 4.1 Valider le changement et la documentation — vérification : `openspec validate typer-echanges-entre-modules --strict` réussit ; `node scripts/verifier-docs.mjs` sans erreur
- [x] 4.2 Contrat REST et MCP inchangé — vérification : diff du document `/q/openapi` avant / après limité à l'ajout de `enum` sur `level` et `niveau` ; si une autre différence apparaît (valeur, champ), la consigner comme **BREAKING** et la soumettre au porteur avant de poursuivre ; suite `mvn -B -f argos-server/pom.xml verify` complète verte

## Matrice de traçabilité

| Constat | Exigence / scénario | Conception / diagramme | Tâche | Test ou preuve | État de la preuve |
|---|---|---|---|---|---|
| AUD-QUA-08 (niveau en chaîne) | aucune (`skip_specs`) ; non-régression COR-02, ALR-02 | D1, D2, DC-1, TE-1, TE-2, TE-4 | 1.1, 1.2, 1.3 | tests TE-1, TE-2, TE-4 ; tests de corrélation et d'alertes existants | non exécuté |
| AUD-QUA-08 (clé « fréquence:canal ») | aucune ; non-régression ALR-04 | D4, DC-1 | 2.1 | tests des résumés périodiques | non exécuté |
| AUD-QUA-08 (statuts d'import et de corrélation) | aucune ; non-régression INV-07 | D3, DC-1, TE-3 | 3.1, 3.2, 3.3 | test du bilan par statut ; compilation exhaustive | non exécuté |
| Contrat inchangé | aucune | « Contrats d'interface » | 4.2 | diff `/q/openapi` | non exécuté |

## Workflow follow-up

- Mettre à jour le statut d'AUD-QUA-08 dans `/docs/audit/2026-11-21/etat.md`.
- Archiver le changement uniquement sur demande explicite, après revue (`openspec archive typer-echanges-entre-modules`).
