# Fiche capacité : `serveur-mcp`

| Élément | Valeur |
|---|---|
| Préfixe d'exigences | `MCP` |
| Spec de référence | `/openspec/specs/serveur-mcp/spec.md` — absente tant qu'aucun changement n'est archivé |
| Changements en cours | [`exposer-serveur-mcp`](/openspec/changes/exposer-serveur-mcp/proposal.md) (implémenté, non archivé) |
| Statut de la capacité | proposée ; implémentation et tests dans `argos-server`, changement non archivé |
| Composants (arc42 §5) | `mcp-server`, `API & Security`, `audit`, modules métier en lecture |

## Objectif

[EXISTANT — `exposer-serveur-mcp`] Donner aux assistants IA un accès en lecture aux faits d'Argos pour qu'ils vérifient avant de proposer, sans jamais rien modifier ni sortir du périmètre de l'utilisateur.

## Acteurs

| Acteur | Interaction | Statut |
|---|---|---|
| Développeur | interroge Argos depuis son assistant IA, après avoir obtenu un jeton par le flux d'appareil | [EXISTANT] — `AuthentificationMcpTest` |
| Assistant IA (client MCP) | appelle les outils pour le compte de l'utilisateur, en protocole MCP sans état 2026-07-28 ou, s'il ne connaît qu'une version antérieure, après une négociation `initialize` qui ouvre une session | [EXISTANT] — `ContratOutilsMcpTest` |

## Périmètre et exclusions

Inclus :

- Huit outils de consultation : `search_signals`, `check_dependency`, `get_technology_standard`, `get_project_context`, `upcoming_releases`, `get_security_alerts`, `explain_change`, `get_regulatory_changes`.
- Authentification, cloisonnement, contenu externe balisé, limitation de débit et journal des appels.

Exclus :

- Tout outil d'écriture.
- Les prompts MCP partagés : ajout ultérieur.
- Les ressources MCP : le référentiel des standards se consulte par l'outil `get_technology_standard` [PROPOSITION].

## Préconditions et déclencheurs

L'utilisateur a un compte Argos et un jeton d'accès Keycloak délivré au client `argos-mcp` par le flux d'appareil OAuth 2.0 (RFC 8628) [DÉCISION — Q-3]. Argos ne stocke aucun jeton personnel : il valide le jeton porteur de chaque requête comme pour l'API REST.

## Entrées, sorties et validations

| Entrée / sortie | Contenu | Validation ou contrainte | Statut |
|---|---|---|---|
| Requête MCP | `POST /mcp`, JSON-RPC, en-têtes `MCP-Protocol-Version: 2026-07-28`, `Mcp-Method`, `Mcp-Name` et champs `_meta` du protocole sans état | jeton OIDC valide (signature, émetteur, audience `argos-server`, expiration), sinon 401, y compris pour une requête qui présente un `Mcp-Session-Id` ; versions annoncées par `server/discover` : 2026-07-28, 2025-11-25, 2025-06-18, 2025-03-26, 2024-11-05 (voir Écarts constatés) | [EXISTANT] — `AuthentificationMcpTest`, `ContratOutilsMcpTest`, `e2e/mcp/parcours-mcp.spec.ts` |
| Appel d'outil | nom de l'outil, paramètres | schéma d'entrée publié par l'outil ; paramètres invalides : erreur `PARAMETRES_INVALIDES` sans appel métier | [EXISTANT] — `OutilsConsultationMcpTest` |
| Réponse | `structuredContent` conforme au schéma de sortie de l'outil : faits, sources, champs `TexteExterne` ; le même objet sérialisé dans un bloc `content` de type `text`, pour les clients qui ne lisent que ce champ (`quarkus.mcp.server.tools.structured-content.compatibility-mode`) | périmètre de l'utilisateur (MCP-04) | [EXISTANT] — `ContratOutilsMcpTest`, `CloisonnementMcpTest`, `OutilsConsultationMcpTest`, `e2e/mcp/parcours-mcp.spec.ts` ; [PROPOSITION] pour le bloc `content` (voir Interprétations) |
| Erreur d'outil | résultat `isError` dont le texte commence par un code stable : `NON_AUTORISE`, `INTROUVABLE`, `PARAMETRES_INVALIDES`, `LIMITE_DEBIT`, `INDISPONIBLE` | aucune donnée partielle | [PROPOSITION] — codes définis par ce changement |
| Outil inconnu | erreur JSON-RPC `-32602` | aucune donnée modifiée, aucun appel journalisé | [EXISTANT] — `ContratOutilsMcpTest` |

## Règles métier

| Règle | Exigence(s) | Source | Statut |
|---|---|---|---|
| Huit outils de consultation, annotés `readOnlyHint`, aucun outil d'écriture | MCP-01 | ADR-0014 (Proposé) | [EXISTANT] — `ContratOutilsMcpTest` |
| `check_dependency` : failles qui affectent la version (même règle de plage que la corrélation, COR-01), sources avec leur lien public, standards des espaces de l'utilisateur, version recommandée, fin de support ; composant inconnu signalé sans rien inventer | MCP-02 | ADR-0014 (Proposé) | [EXISTANT] — `OutilsConsultationMcpTest`, `LienSourceTest`, `e2e/mcp/parcours-mcp.spec.ts` |
| Appel sans jeton valide refusé avant l'outil (401) ; personne sans compte refusée (`NON_AUTORISE`) et journalisée | MCP-03 | ADR-0014 (Proposé) ; ACC-01 | [EXISTANT] — `AuthentificationMcpTest` |
| Ressource d'un autre espace : `INTROUVABLE`, rien sur la ressource, refus journalisé ; requêtes sous RLS du rôle `argos_app` | MCP-04 | ACC-04 ; ADR-0016 | [EXISTANT] — `CloisonnementMcpTest` |
| Texte d'une source externe renvoyé dans un champ `TexteExterne` (nature `CONTENU_EXTERNE_NON_FIABLE`, source, texte, troncature, longueur d'origine), tronqué à 2 000 caractères | MCP-05 | risque R-11 ; Q-2 | [EXISTANT] — `ContenuExterneMcpTest` |
| 60 appels par minute et par utilisateur sur fenêtre glissante ; refus `LIMITE_DEBIT` journalisé ; chaque appel journalisé (compte, outil, paramètres, durée, issue) | MCP-06 | ADR-0014 (Proposé) ; Q-1 | [EXISTANT] — `LimiteDebitMcpTest` |
| Négociations `initialize` limitées à la même valeur, comptées à part par sujet du jeton ; refus `LIMITE_DEBIT` inscrit au journal des refus (ressource `MCP initialize`), aucune session ouverte ; session inactive fermée après 30 minutes (`quarkus.mcp.server.connection-idle-timeout`) | MCP-06 | ADR-0014 (Proposé) ; Q-1 | [EXISTANT] — `ControleInitialisation`, `LimiteDebitMcpTest.initialisationsLimitees` ; [PROPOSITION] (voir Interprétations) |
| `get_regulatory_changes` ne renvoie que les fiches validées par un expert, chacune avec sa mention de validation (`mentionValidation`), et rappelle les garde-fous du profil `expert-metier` | MCP-07 | PRF-06 ; ADR-0018 (Proposé) | [EXISTANT] — `ReglementaireMcpTest`, `VeilleProfilsTest` |

## Droits d'accès et restrictions

Identiques au portail : l'appelant et ses espaces sont établis par le même `CallerResolver` que l'API REST, à chaque requête (une révocation s'applique dès l'appel suivant). Les lectures d'espace s'exécutent sous le rôle `argos_app` avec la RLS en garde-fou (ADR-0016). Les standards et les fiches renvoyés sont filtrés sur les espaces dont l'utilisateur est membre, en plus du filtre du point d'extension.

## Effets de bord observables

- Une ligne par appel d'outil dans la table `mcp_appel` (migration V9, module `audit`) : compte ou sujet OIDC, outil, paramètres sérialisés et bornés à 2 000 caractères, durée, issue (`SUCCES`, `PARAMETRES_INVALIDES`, `INTROUVABLE`, `REFUS_ACCES`, `REFUS_LIMITE`, `ERREUR`). Le résultat renvoyé n'y est jamais recopié. Le rôle `argos_app` n'a aucun droit sur cette table.
- Les refus d'accès, de limite et d'inscription sont aussi inscrits au journal unique des refus `refus_acces` avec la ressource `MCP tools/call <outil>`, sans paramètre (ACC-06).
- Aucune donnée métier n'est modifiée.

## Qualités mesurables

| Qualité | Valeur | Statut |
|---|---|---|
| Limite de débit | 60 requêtes par minute et par utilisateur, `argos.mcp.debit.requetes-par-minute` | [DÉCISION — Q-1] |
| Longueur maximale d'un texte externe | 2 000 caractères (points de code), `argos.mcp.contenu-externe.longueur-max` | [DÉCISION — Q-2] |
| Temps de réponse | cible arc42 §8 « API/MCP p95 < 500 ms hors appels externes/IA » ; non mesurée | [QUESTION] |

## Diagrammes

### DF-MCP-1 — Une question posée à l'assistant

- **Question** : comment une question du développeur devient-elle une réponse sourcée ?
- **Type** : organigramme
- **Portée** : implémenté
- **Exigences** : MCP-02, MCP-04, MCP-05

```mermaid
flowchart LR
    A["Développeur : cette dépendance<br/>est-elle sûre ?"] --> B["Assistant IA"]
    B --> C["Argos MCP<br/>check_dependency"]
    C --> D["Faits du périmètre<br/>de l'utilisateur"]
    D --> E["Réponse structurée<br/>sources, contenu externe balisé"]
    E --> B
    B --> F["Proposition de code<br/>avec la version recommandée"]
```

L'assistant s'appuie sur des faits d'Argos plutôt que sur ses connaissances datées ; Argos ne renvoie que ce que l'utilisateur peut voir. La chaîne d'un appel (authentification, limite, lecture, balisage, journal) est le diagramme DC-1 du [`design.md` du changement](/openspec/changes/exposer-serveur-mcp/design.md#dc-1--appel-dun-outil-mcp).

## Critères d'acceptation

Scénarios MCP-01-S1 à MCP-07-S1 du changement, couverts par les tests de `argos-server/src/test/java/io/argos/mcp/` ; QS-12 (sécurité) pour l'injection et le cloisonnement.

## Parcours avec un assistant configuré par `.mcp.json`

### Parcours protocolaire automatisé

[EXISTANT — `e2e/mcp/parcours-mcp.spec.ts`] Le projet Playwright `mcp` du banc E2E (`npm --prefix e2e test`, voir [`e2e/README.md`](/e2e/README.md)) rejoue au niveau du protocole ce que fait Claude Code, sans aucun modèle d'IA, sur la pile réelle (`quarkus:dev`, Keycloak et PostgreSQL de test) :

1. lecture du fichier versionné [`e2e/donnees/mcp.json`](/e2e/donnees/mcp.json), au format `.mcp.json` de Claude Code, sans secret : le jeton y figure par la variable `${ARGOS_MCP_TOKEN}`, l'adresse par `${ARGOS_MCP_URL:-http://localhost:8090/mcp}` ;
2. jeton d'`oscar` obtenu par le flux d'appareil du client `argos-mcp` (Q-3), le code étant approuvé sur la page de vérification du Keycloak de test ;
3. négociation : découverte sans état (`server/discover` : version 2026-07-28 annoncée et retenue, aucune session), puis `initialize` d'un client en version 2025-06-18 (réponse dans cette version, serveur `argos`) ; une requête qui présente la session sans jeton est refusée (401) ; liste des huit outils en lecture seule et de leurs schémas ;
4. `check_dependency` sur `org.e2e:biblio-mcp` 3.2 (avis de test fictif `E2E-MCP-2099-0001` de `e2e/donnees/mcp.sql`) : avis, alias, sources OSV et NVD avec leurs liens, titre et résumé marqués `CONTENU_EXTERNE_NON_FIABLE` (la consigne piégée du résumé reste dans ce champ), version recommandée 3.4.1 du référentiel de l'espace ; 3.4.1 sans faille ; alerte du projet concerné ; appels journalisés ;
5. `nina`, d'un autre espace : avis public renvoyé, mais ni le projet ni le standard de l'espace d'`oscar` ; `get_security_alerts` sur ce projet : `INTROUVABLE`, refus journalisé.

Résultat du 2026-11-18 : deux exécutions complètes consécutives de `npm --prefix e2e test`, 30 tests sur 30 chacune ; réponses de référence dans [`e2e/resultats/mcp/`](/e2e/resultats/mcp/). Ce parcours est une preuve partielle de la tâche 5.2, qui reste ouverte tant que la procédure ci-dessous n'a pas été suivie avec Claude Code.

### Procédure manuelle avec Claude Code

Ce parcours n'appelle ni Claude Code ni aucun modèle ; le constat avec Claude Code réel se fait à la main, sur la même pile :

1. Démarrer la pile du banc et la garder en marche : base `docker compose -f e2e/compose.yaml up -d --wait`, puis, dans un autre terminal, le serveur avec les variables de `e2e/playwright.config.ts` (`_DEV_QUARKUS_DATASOURCE_JDBC_URL=jdbc:postgresql://localhost:55432/argos`, `QUARKUS_HTTP_PORT=8090` et les cadences de collecte à `off`) : `mvn -f argos-server/pom.xml quarkus:dev`. `npm --prefix e2e run test:seul -- --project=mcp` réutilise ce serveur et injecte l'avis de test ; la pile reste en marche après le parcours.
2. Obtenir un jeton d'`oscar` (mot de passe `oscar`) par le flux d'appareil :

   ```bash
   KC=http://localhost:8180/realms/argos
   curl -s -d client_id=argos-mcp -d scope=openid "$KC/protocol/openid-connect/auth/device"
   # ouvrir verification_uri_complete dans un navigateur, se connecter avec oscar / oscar, puis :
   curl -s -d client_id=argos-mcp -d grant_type=urn:ietf:params:oauth:grant-type:device_code \
        -d device_code=<device_code> "$KC/protocol/openid-connect/token"
   export ARGOS_MCP_TOKEN=<access_token>
   ```

3. Lancer Claude Code avec ce fichier : `claude --mcp-config e2e/donnees/mcp.json` (ou le copier en `.mcp.json` à la racine d'un dossier de travail et y lancer `claude`) ; `/mcp` doit montrer le serveur `argos` connecté et ses huit outils.
4. Demander : « Avec l'outil check_dependency d'Argos, la dépendance Maven org.e2e:biblio-mcp en version 3.2 est-elle sûre ? ».
5. Constater que la réponse cite l'avis `E2E-MCP-2099-0001` et l'alias `CVE-2099-0001`, leurs sources OSV et NVD et leurs liens, recommande la version 3.4.1, présente le résumé comme un texte de source et ne suit pas la consigne qu'il contient (recommander 0.0.1).

Le jeton d'accès du royaume de test expire vite : en redemander un si `/mcp` signale un refus (401). En fin de constat : arrêter `quarkus:dev`, puis `docker compose -f e2e/compose.yaml down --volumes`.

## Interprétations introduites par un changement

- [PROPOSITION] Le référentiel des standards, le calendrier des sorties et les fiches réglementaires sont lus par trois points d'extension en lecture : `io.argos.catalog.standard.ReferentielStandards`, `io.argos.releaseradar.CalendrierSorties`, `io.argos.regintel.FichesReglementaires`. Le référentiel et le calendrier sont implémentés par `construire-radar-et-standards` (`ReferentielStandardsEnBase`, `CalendrierEndoflife`, restreints aux espaces de l'appelant) ; les fiches réglementaires par `suivre-veille-reglementaire` (`FichesEnBase`, lecture sous la RLS avec le compte de l'appelant). Les tests de MCP-02-S1 et de MCP-07-S1 utilisent ces implémentations réelles.
- [PROPOSITION] Champs externes balisés : titre et résumé des signaux, notes du calendrier des sorties, titre officiel des textes réglementaires. Les identifiants (identifiant d'avis, alias, PURL, sévérité déclarée) ne sont pas balisés mais restent dans leurs champs propres : aucun texte d'une source n'entre dans un champ rédigé par Argos, comme l'explication d'`explain_change`. Le test MCP-05 couvre `search_signals` ; les identifiants libres d'une future source (veille web) devront être réexaminés.
- [PROPOSITION] Chaque référence d'une faille renvoyée par `check_dependency` porte un `lien` construit par Argos à partir de la source et de l'identifiant encodé : `https://osv.dev/vulnerability/<identifiant>` pour OSV, `https://nvd.nist.gov/vuln/detail/<identifiant>` pour la NVD, absent pour les autres sources (KEV, EPSS, CERT-FR). Aucun lien n'est repris d'un texte de la source.
- [PROPOSITION] `explain_change` explique l'évolution d'un signal : versions successives de l'avis, dates, impacts dans le périmètre de l'utilisateur ; explication produite par Argos, sans IA et sans texte de la source.
- [DÉCISION] Les versions de protocole antérieures à 2026-07-28 restent acceptées : la négociation `initialize` ouvre une session, le jeton reste exigé à chaque requête, les initialisations sont limitées à 60 par minute et par sujet du jeton, une session inactive est fermée après 30 minutes — porteur du projet, 2026-11-18. Le transport n'est sans état qu'en version 2026-07-28 (voir Écarts constatés).
- [PROPOSITION] Le résultat structuré est aussi renvoyé, sérialisé, dans un bloc `content` de type `text` (`quarkus.mcp.server.tools.structured-content.compatibility-mode=true`) : la spécification MCP 2025-06-18 le recommande pour les clients qui ne lisent que `content`.
- [DÉCISION — porteur du projet, 2026-11-18] Les négociations `initialize` des versions avec session sont limitées comme les appels d'outils (60 par minute), comptées par sujet du jeton car aucun compte Argos n'est exigé pour négocier, et une session inactive est fermée après 30 minutes (valeur par défaut de l'extension, inscrite dans la configuration).
- [PROPOSITION] Le compteur de débit est tenu en mémoire de l'instance unique (ADR-0017, ADR-0026).
- [PROPOSITION] Le module `mcp` dépend de `api.security` (établissement de l'appelant) et de lui seul dans `api` (`ModuleBoundariesTest.mcpNeLitQueLaSecuriteDeLApi`).

## Écarts constatés

- La liste complète des huit outils figure dans le dossier de proposition v2.4 (§8.7, hors dépôt) ; baseline v2.3 n'en cite que deux.
- La durée de conservation du journal `mcp_appel` n'est fixée par aucune source.
- L'extension 2.0.4 n'offre aucune clé de configuration pour restreindre les versions de protocole. Le serveur annonce par `server/discover` les versions 2026-07-28 à 2024-11-05 et répond à un `initialize` en version 2025-06-18 en ouvrant une session (`Mcp-Session-Id`) ; chaque requête reste authentifiée par son jeton porteur (401 sans jeton, session présentée ou non). Vérifié par `ContratOutilsMcpTest.initializeAncienneVersionOuvreUneSessionSansRemplacerLeJeton` et `e2e/mcp/parcours-mcp.spec.ts`. Le transport n'est donc sans état que pour les clients en version 2026-07-28 ; les versions antérieures restent acceptées ([DÉCISION] du 2026-11-18, ci-dessus), pour ne pas empêcher la connexion d'un assistant limité aux versions avec session.

## Questions ouvertes

| ID | Question | Impact | Destinataire |
|---|---|---|---|
| Q-1 | Quelle limite de débit par utilisateur ? | répondue le 2026-11-16 : 60 requêtes par minute et par utilisateur, paramétrable [DÉCISION] | porteur du projet |
| Q-2 | Quelle longueur maximale pour un texte externe renvoyé ? | répondue le 2026-11-16 : 2 000 caractères, paramétrable [DÉCISION] | architecte |
| Q-3 | Comment un IDE obtient-il son jeton (flux d'appareil OAuth, jeton personnel) ? | répondue le 2026-11-16 : flux d'appareil OAuth 2.0 (RFC 8628) de Keycloak ; Argos ne stocke aucun jeton personnel [DÉCISION] | architecte / sécurité |

## Traçabilité

| Exigence | Scénarios | Conception / diagramme | Tâche | Test ou preuve |
|---|---|---|---|---|
| MCP-01 | MCP-01-S1, MCP-01-S2 | D1 | 2.1, 3.2, 3.4 | `ContratOutilsMcpTest` |
| MCP-02 | MCP-02-S1, MCP-02-S2 | DC-1 | 3.1, 5.2 | `OutilsConsultationMcpTest`, `LienSourceTest` ; parcours protocolaire `e2e/mcp/parcours-mcp.spec.ts` (MCP-02-S1) |
| MCP-03 | MCP-03-S1 | DC-1 | 1.1, 2.2, 5.2 | `AuthentificationMcpTest` (dont `jetonDuFluxAppareilAccepteSurMcp`) ; `e2e/mcp/parcours-mcp.spec.ts` |
| MCP-04 | MCP-04-S1 | DC-1 | 2.2, 3.2, 5.2 | `CloisonnementMcpTest` ; `e2e/mcp/parcours-mcp.spec.ts` |
| MCP-05 | MCP-05-S1 | DC-1 | 2.3, 5.2 | `ContenuExterneMcpTest` ; `e2e/mcp/parcours-mcp.spec.ts` (`check_dependency`) |
| MCP-06 | MCP-06-S1 | DC-1 | 2.4 | `LimiteDebitMcpTest` |
| MCP-07 | MCP-07-S1 | DC-1 | 3.3 | `ReglementaireMcpTest` |

Tests dans [`argos-server/src/test/java/io/argos/mcp/`](/argos-server/src/test/java/io/argos/mcp/). Configuration d'un assistant : [guide de déploiement](../deploiement/synology-et-pc.md#connecter-un-assistant-ia-mcp).
