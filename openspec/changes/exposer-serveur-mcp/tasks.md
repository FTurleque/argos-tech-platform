# Tâches : exposer Argos aux assistants IA par un serveur MCP en lecture seule

<!-- Tâches 1 à 5.1 réalisées et vérifiées ; 5.2 (parcours avec Claude Code réel) reste à faire : seule sa partie protocole est vérifiée. -->

## 1. Arbitrages

- [x] 1.1 Faire choisir le mode d'obtention du jeton par les IDE (Q-3) — couvre MCP-03 — vérification : choix inscrit dans la fiche `serveur-mcp` (décidé le 2026-11-16)

## 2. Serveur et sécurité

- [x] 2.1 Ajouter `quarkus-mcp-server-http` (licence enregistrée, ADR-0021) et le transport sans état — couvre MCP-01 — vérification : test de contrat « liste des outils » passe
- [x] 2.2 Brancher l'authentification et l'espace courant — couvre MCP-03-S1, MCP-04-S1 — vérification : tests d'intégration passent
- [x] 2.3 Baliser et tronquer le contenu externe — couvre MCP-05-S1 — vérification : test du texte piégé passe
- [x] 2.4 Limiter le débit et journaliser les appels — couvre MCP-06-S1 — vérification : test d'intégration passe

## 3. Outils

- [x] 3.1 Implémenter `check_dependency` — couvre MCP-02-S1, MCP-02-S2 — vérification : tests d'intégration passent
- [x] 3.2 Implémenter `search_signals`, `get_technology_standard`, `get_project_context`, `upcoming_releases`, `get_security_alerts`, `explain_change` — couvre MCP-01-S1, MCP-04 — vérification : tests de contrat par outil passent
- [x] 3.3 Implémenter `get_regulatory_changes` avec les garde-fous — couvre MCP-07-S1 — vérification : test d'intégration passe
- [x] 3.4 Rejeter tout outil d'écriture — couvre MCP-01-S2 — vérification : test de contrat passe

## 4. Documentation durable et décisions

- [x] 4.1 Mettre à jour la fiche `serveur-mcp`, les sections arc42 §5.3, le glossaire et l'inventaire `/docs/architecture/diagrams/README.md` — vérification : `node scripts/verifier-docs.mjs` sans erreur

## 5. Vérifications d'intégration

- [x] 5.1 Valider le changement — vérification : `openspec validate exposer-serveur-mcp --strict` réussit
- [ ] 5.2 Parcours : Claude Code configuré avec `.mcp.json`, appel de `check_dependency` sur une version vulnérable de test, réponse citée et sourcée — vérification : scénario E2E consigné avec ses résultats

## Preuves des tâches

| Tâche | Preuve | Résultat |
|---|---|---|
| 2.1 | `argos-server/pom.xml` : `io.quarkiverse.mcp:quarkus-mcp-server-http` 2.0.4, licence Apache-2.0 consignée (dépendances transitives `quarkus-mcp-server-core`, `quarkus-mcp-server-sse-client`, `org.mcpjava:mcp-server-api` 1.0.0, `com.github.victools:jsonschema-generator` 4.38.0, `jsonschema-module-jackson`, `jsonschema-module-jakarta-validation`, `jsonschema-module-swagger-2` : Apache-2.0) ; `application.properties` : `quarkus.mcp.server.supported-protocol-versions=2026-07-28` (clé inconnue de l'extension, sans effet : voir `design.md`, Écarts) ; `ContratOutilsMcpTest` (liste, schémas, transport sans état, découverte) | passe |
| 2.2 | `GardeAppels` (appelant par `CallerResolver`, refus journalisés) ; `AuthentificationMcpTest`, `CloisonnementMcpTest` (dont RLS sous `argos_app`) ; règle `ModuleBoundariesTest.mcpNeLitQueLaSecuriteDeLApi` | passe |
| 2.3 | `BalisageExterne`, `TexteExterne` ; `ContenuExterneMcpTest` | passe |
| 2.4 | `api.security.LimiteurDebit` (commun avec la veille web), `audit.JournalAppelsMcp`, migration `V9__journal_appels_mcp.sql` ; `LimiteDebitMcpTest` (profil de test à 3 appels par minute), `LimiteurDebitTest` | passe |
| 3.1 | `OutilsSecurite.checkDependency` ; `OutilsConsultationMcpTest` (MCP-02-S1 avec référentiel et calendrier simulés, MCP-02-S2, paramètres invalides) | passe |
| 3.2 | `OutilsSecurite`, `OutilsProjets`, `OutilsReferentiel` ; `OutilsConsultationMcpTest`, `CloisonnementMcpTest`, `ContratOutilsMcpTest.chaqueOutilPublieSesSchemas` | passe |
| 3.3 | `OutilsReglementaires` ; `ReglementaireMcpTest` (fiches réelles de `suivre-veille-reglementaire`) | passe |
| 3.4 | `ContratOutilsMcpTest.mcp01S2OutilInconnu` | passe |
| 4.1 | fiche `docs/specifications/serveur-mcp.md`, arc42 §5.3 et §5.6, glossaire (MCP, contenu externe, flux d'appareil), guide de déploiement ; aucun nouveau diagramme d'architecture : l'inventaire `/docs/architecture/diagrams/README.md` reste exact (DC-1 demeure dans `design.md`) ; `node scripts/verifier-docs.mjs` | « Résultat : aucune erreur » |
| 5.1 | `openspec validate exposer-serveur-mcp --strict` | « Change 'exposer-serveur-mcp' is valid » |
| 5.2 | preuve partielle, partie protocole : `e2e/mcp/parcours-mcp.spec.ts` (projet Playwright `mcp`), client MCP écrit avec `fetch`, sans modèle d'IA, configuré par `e2e/donnees/mcp.json` (format `.mcp.json` de Claude Code, sans secret) ; jeton d'`oscar` par le flux d'appareil du client `argos-mcp` (Q-3) ; `server/discover` (2026-07-28 annoncée, aucune session) et `initialize` 2025-06-18 ; huit outils ; `check_dependency` sur l'avis fictif `E2E-MCP-2099-0001` (`e2e/donnees/mcp.sql`) : avis, alias, sources OSV et NVD avec leurs liens, titre et résumé `CONTENU_EXTERNE_NON_FIABLE`, version recommandée 3.4.1 ; `nina` d'un autre espace sans le projet ni le standard ; réponses gardées dans `e2e/resultats/mcp/`. Le 2026-11-18, deux exécutions complètes consécutives de `npm --prefix e2e test` : « 30 passed (7.3m) » puis « 30 passed (6.3m) ». Reste le parcours avec Claude Code réel : procédure manuelle de la fiche `serveur-mcp` (section « Procédure manuelle avec Claude Code »), non exécutée | partielle ; à faire avec Claude Code |

Suite complète : `mvn -B -f argos-server/pom.xml verify` — « Tests run: 193, Failures: 0, Errors: 0, Skipped: 0 », BUILD SUCCESS (2026-11-16), dont 28 tests dans `io.argos.mcp` ; le 2026-11-18, avec le lien des sources et le bloc `content` : « Tests run: 504, Failures: 0, Errors: 0, Skipped: 1 », BUILD SUCCESS.

## Matrice de traçabilité

Au 2026-11-18, MCP-02-S1, MCP-03-S1, MCP-04-S1 et MCP-05 sont aussi parcourus de bout en bout au niveau du protocole par `e2e/mcp/parcours-mcp.spec.ts` (tâche 5.2, partie protocole). État au 2026-11-16 : tous les scénarios sont couverts par un test qui passe dans `argos-server/src/test/java/io/argos/mcp/`. MCP-02-S1 et MCP-07-S1 s'appuient sur des implémentations simulées des points d'extension `ReferentielStandards`, `CalendrierSorties` et `FichesReglementaires` (`testsupport/ReferentielsSimules`), car le référentiel des standards et la veille réglementaire ne sont pas livrés.

| Exigence | Scénario | Conception / diagramme | Tâche | Test ou preuve | État de la preuve |
|---|---|---|---|---|---|
| MCP-01 | MCP-01-S1 | D1 | 2.1, 3.2 | `ContratOutilsMcpTest.mcp01S1SeulsDesOutilsDeConsultation` | passe |
| MCP-01 | MCP-01-S2 | D1 | 2.1, 3.4 | `ContratOutilsMcpTest.mcp01S2OutilInconnu` | passe |
| MCP-02 | MCP-02-S1 | DC-1 | 3.1, 5.2 | `OutilsConsultationMcpTest.mcp02S1VersionVulnerable`, `LienSourceTest` ; parcours protocolaire `e2e/mcp/parcours-mcp.spec.ts` | passe |
| MCP-02 | MCP-02-S2 | DC-1 | 3.1 | `OutilsConsultationMcpTest.mcp02S2DependanceInconnue` | passe |
| MCP-03 | MCP-03-S1 | DC-1 | 1.1, 2.2, 5.2 | `AuthentificationMcpTest.mcp03S1AppelSansJeton`, `jetonInvalideOuAutreAudience`, `fluxAppareilDuClientMcp`, `jetonDuFluxAppareilAccepteSurMcp` (flux d'appareil complet du client `argos-mcp` : connexion sur la page de vérification du Keycloak de test, jeton accepté sur `POST /mcp`) ; `e2e/mcp/parcours-mcp.spec.ts` (sans jeton, session sans jeton, jeton du flux d'appareil) | passe |
| MCP-04 | MCP-04-S1 | DC-1 | 2.2, 3.2, 5.2 | `CloisonnementMcpTest.mcp04S1ProjetDUnAutreEspace`, `rlsSousLeRoleApplicatif` ; `e2e/mcp/parcours-mcp.spec.ts` (compte d'un autre espace) | passe |
| MCP-05 | MCP-05-S1 | DC-1 | 2.3, 5.2 | `ContenuExterneMcpTest.mcp05S1TexteContenantUneConsigne` ; `e2e/mcp/parcours-mcp.spec.ts` (consigne piégée dans le résumé renvoyé par `check_dependency`) | passe |
| MCP-06 | MCP-06-S1 | DC-1 | 2.4 | `LimiteDebitMcpTest.mcp06S1LimiteDepassee` | passe |
| MCP-07 | MCP-07-S1 | DC-1 | 3.3 | `ReglementaireMcpTest.mcp07S1FicheNonValidee` (fiches réelles de `suivre-veille-reglementaire`) | passe |

## Workflow follow-up

- Basculer dans la fiche durable les statuts [PROPOSITION] de ce changement en [EXIGENCE] ou [EXISTANT] selon les preuves obtenues.
- Réaliser le parcours 5.2 avec Claude Code réel selon la procédure manuelle de la fiche `serveur-mcp` et consigner son résultat.
- Décider du sort des versions de protocole antérieures à 2026-07-28, que le serveur accepte (`design.md`, Écarts).
- Brancher les implémentations réelles des points d'extension lors de `construire-radar-et-standards` et `suivre-veille-reglementaire`.
- Archiver le changement uniquement sur demande explicite, après revue (`openspec archive exposer-serveur-mcp`).
- Après archivage, vérifier que la section Purpose de la spec principale contient le lien vers la fiche durable.
- Après archivage, remplacer dans la fiche les liens vers `/openspec/changes/exposer-serveur-mcp/` par la spec principale ou le dossier d'archive, puis exécuter `node scripts/verifier-docs.mjs`.
