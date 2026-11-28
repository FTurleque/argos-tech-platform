# Ordre de correction — 2026-11-17

Le plan d'action ([plan-action.md](plan-action.md)) classe par urgence ; ce document classe par **ordre d'exécution**. Quand les deux divergent, l'ordre des sprints s'applique : une correction dont le prérequis n'est pas livré ne peut pas être vérifiée.

## Vue d'ensemble

| Sprint | Titre | Constats | Élevée | Moyenne | Faible | Charge (j) |
|---|---|---|---|---|---|---|
| 1 | Remettre les gates en marche | 7 | 0 | 2 | 5 | 2,5 à 9 |
| 2 | Exploitation sûre : sauvegarde, déploiement, SSRF | 8 | 2 | 2 | 4 | 2,75 à 10 |
| 3 | Observabilité et socle d'exécution | 5 | 0 | 1 | 4 | 2 à 7 |
| 4 | Décisions écrites et noyau commun | 7 | 0 | 4 | 3 | 4,75 à 15 |
| 5 | Authentification de bout en bout | 6 | 1 | 1 | 4 | 3,75 à 12 |
| 6 | Alimenter l'inventaire et couvrir les modules | 7 | 1 | 2 | 4 | 4,75 à 15 |
| 7 | Ingestion asynchrone | 6 | 2 | 0 | 4 | 3,75 à 12 |
| 8 | Requêtes et volumétrie | 7 | 0 | 5 | 2 | 5,5 à 17 |
| 9 | Structure, documentation et distribution | 7 | 0 | 2 | 5 | 6 à 18 |

Charge totale : **35,75 à 115 jours**. C'est une enveloppe (S = 0,25 à 1 j, M = 1 à 3 j, L = 3 à 8 j) : elle ne couvre ni la revue, ni les allers-retours de CI, ni les arbitrages écrits que demandent les chantiers de fond.

## Chemin critique

- **AUD-TST-01** (M, sprint 1) — Débloque directement le seuil de couverture, la garde des migrations et le build propre sur JDK 17, puis les tests ciblés du sprint 6 ; surtout, c'est ce qui rend vérifiable chacune des 59 autres corrections.
- **AUD-ARC-12** (M, sprint 5) — Un client HTTP unique dans client.ts est le prérequis de l'authentification (ARC-02), des tests de l'IHM et des vues artiste.
- **AUD-ARC-02** (M, sprint 5) — Le flux PKCE rend enfin l'IHM utilisable et débloque les vues du profil artiste ; c'est la seule voie vers une preuve de bout en bout.
- **AUD-ARC-14** (S, sprint 4) — Effort S : écrire l'ADR du noyau commun et mettre les statuts à jour débloque l'extraction du noyau (ARC-05), puis l'unification RLS, l'utilitaire JDBC et la refonte d'arc42.

## Sprint 1 — Remettre les gates en marche

**Pourquoi ici.** Aucun test n'est exécuté automatiquement, l'image est construite avec -DskipTests et un build incrémental a lancé 0 test sans le signaler : sans gate fiable, aucune correction des sprints suivants n'est vérifiable. Ce sprint ne touche presque pas au code applicatif.

**Critère de sortie.** Chaque push lance, sur JDK 17, mvn -B clean verify, npm test et vue-tsc (IHM et desktop), le contrôle documentaire et npm audit ; la couverture JaCoCo est publiée avec un seuil qui fait échouer le build ; la modification d'une migration déjà fusionnée fait échouer la CI ; un paquet io.argos non déclaré fait échouer ModuleBoundariesTest ; toutes les images (production et tests) sont épinglées et Renovate ouvre les mises à jour.

**Charge.** 2,5 à 9 jours.

**Ordre interne.** AUD-TST-01 → AUD-TST-19 → AUD-DEP-20 → AUD-TST-02 → AUD-ARC-07 → AUD-DEP-06 → AUD-DEP-05.
Dépendances internes : AUD-TST-19 après AUD-TST-01 ; AUD-DEP-20 après AUD-TST-01 ; AUD-TST-02 après AUD-TST-01.

**Prérequis des sprints antérieurs.** aucun.

| ID | Sévérité | Axes | Effort | Titre |
|---|---|---|---|---|
| AUD-TST-01 | Moyenne | Tests, Dépendances | M | Aucune intégration continue : aucun test (mvn verify, npm test, ModuleBoundariesTest) n'est exécuté automatiquement |
| AUD-TST-19 | Faible | Tests, Dépendances | S | Build incrémental non fiable et tests exécutés sur un JDK différent de la production (JDK 24 local, JRE 17 en image) |
| AUD-DEP-20 | Moyenne | Dépendances, Qualité | S | Migration V13 modifiée sur place avant fusion, sans garde contre la réécriture d'une migration déjà fusionnée |
| AUD-TST-02 | Faible | Tests | S | Pas de seuil de couverture : JaCoCo absent du pom, la couverture (83,8 % des lignes) n'est mesurable qu'à la main |
| AUD-ARC-07 | Faible | Architecture, Tests | S | La table ALLOWED est plus large que le code et ne contraint pas un paquet io.argos non déclaré |
| AUD-DEP-06 | Faible | Dépendances, Tests | S | Images Docker en tags flottants sans digest : node:22-slim, maven:3.9-eclipse-temurin-17, eclipse-temurin:17-jre, postgres:18 |
| AUD-DEP-05 | Faible | Dépendances | S | Aucune veille automatisée des dépendances (Renovate/Dependabot, CVE Maven, SBOM du produit) ; Electron déjà en retard d'un correctif (45.3.2, 45.3.3 publié) |

## Sprint 2 — Exploitation sûre : sauvegarde, déploiement, SSRF

**Pourquoi ici.** Le NAS est la seule cible réelle : une sauvegarde qui échoue en silence et un compose qui ne transmet pas la configuration SMTP sont une perte de données et des fonctions mortes en production. Les fichiers d'exploitation (compose.yaml, Dockerfile, script de sauvegarde) sont traités ici en un seul lot. Le contournement SSRF par rebinding, à fenêtre étroite, est corrigé avant l'ouverture au public.

**Critère de sortie.** Un pg_dump en échec fait sortir le script en erreur sans rotation ; une restauration a été rejouée de bout en bout sur une base vierge ; un test compare les ${ARGOS_*} lus par application.properties à compose.yaml et .env.example et échoue sur tout écart ; les conteneurs argos et ollama ont un healthcheck et la JVM s'arrête sur OutOfMemoryError ; un test avec un résolveur DNS qui change de réponse prouve que la veille web se connecte à l'adresse validée.

**Charge.** 2,75 à 10 jours.

**Ordre interne.** AUD-QUA-02 → AUD-SEC-07 → AUD-DEP-03 → AUD-DEP-01 → AUD-SEC-06 → AUD-DEP-08 → AUD-PER-12 → AUD-SEC-01.
Dépendances internes : AUD-SEC-07 après AUD-QUA-02 ; AUD-DEP-03 après AUD-QUA-02.

**Prérequis des sprints antérieurs.** aucun.

| ID | Sévérité | Axes | Effort | Titre |
|---|---|---|---|---|
| AUD-QUA-02 | Élevée | Qualité, Dépendances | S | Le script de sauvegarde ne détecte pas l'échec de pg_dump (pas de pipefail) et sa rotation peut supprimer les bonnes sauvegardes |
| AUD-SEC-07 | Faible | Sécurité | S | Sauvegardes complètes de la base écrites en clair avec les permissions par défaut |
| AUD-DEP-03 | Moyenne | Dépendances | S | Procédure de restauration documentée non testée et non idempotente : dump texte sans --clean rejoué par psql sans ON_ERROR_STOP sur une base déjà migrée |
| AUD-DEP-01 | Élevée | Dépendances, Sécurité | S | compose.yaml ne transmet pas au conteneur 23 variables lues en production (SMTP, ARGOS_PUBLIC_URL, jetons de forge, quotas IA) et la doc de déploiement est en retard : e-mails vers localhost:587, liens vers localhost:5173 |
| AUD-SEC-06 | Faible | Sécurité, Dépendances | S | Mot de passe de base de données par défaut « argos » si .env absent |
| AUD-DEP-08 | Faible | Dépendances | S | Conteneurs argos et ollama sans healthcheck, pas de HEALTHCHECK dans le Dockerfile ni de rotation des journaux Docker |
| AUD-PER-12 | Faible | Performance | S | Réglages JVM et pool : heap à 75 % d'une limite de 1 Gio, sans ExitOnOutOfMemoryError, et pool de connexions laissé aux valeurs par défaut |
| AUD-SEC-01 | Moyenne | Sécurité | M | Veille web : contrôle SSRF contournable par rebinding DNS (résolution vérifiée distincte de la résolution de connexion) |

## Sprint 3 — Observabilité et socle d'exécution

**Pourquoi ici.** Les sprints 7 et 8 déplacent l'ingestion hors des requêtes et réécrivent les requêtes chaudes : sans métriques ni journaux structurés, une régression de débit ou une tâche bloquée ne se verrait pas. L'observabilité passe donc avant les corrections de fond, même sans dépendance formelle.

**Critère de sortie.** Le serveur expose des métriques (/q/metrics) et des journaux JSON en production, avec un contrôle de santé par tâche planifiée ; une recherche de Instant.now() et LocalDate.now() dans src/main ne trouve plus que le producteur du bean Clock ; les exécuteurs sont gérés par CDI ; les en-têtes de sécurité HTTP sont posés et vérifiés par un test.

**Charge.** 2 à 7 jours.

**Ordre interne.** AUD-DEP-07 → AUD-QUA-10 → AUD-TST-06 → AUD-QUA-11 → AUD-SEC-05.

**Prérequis des sprints antérieurs.** aucun.

| ID | Sévérité | Axes | Effort | Titre |
|---|---|---|---|---|
| AUD-DEP-07 | Moyenne | Dépendances | M | Observabilité minimale : ni journaux structurés, ni métriques, ni traçage, ni contrôle de santé métier |
| AUD-QUA-10 | Faible | Qualité | S | Horloge injectable seulement dans CollecteWeb et LimiteurDebit : 37 appels à now() côté Java (23 fichiers) sans Clock commun, mêlés à 45 now() SQL, et deux LocalDate.now() sans fuseau |
| AUD-TST-06 | Faible | Tests | S | Assertions dépendantes de l'horloge réelle et de délais fixes (sleep 5 s simulé, sleep 1 s, attente active 5 s) |
| AUD-QUA-11 | Faible | Qualité | S | Des exécuteurs de threads statiques, créés hors du cycle de vie CDI, ne sont jamais arrêtés |
| AUD-SEC-05 | Faible | Sécurité | S | Aucun en-tête de sécurité HTTP sur l'IHM et l'API servies par Quarkus |

## Sprint 4 — Décisions écrites et noyau commun

**Pourquoi ici.** Plusieurs mécanismes partagés (contexte RLS, transactions, accès JDBC, propriété des tables) sont recopiés ou implicites. Les décisions doivent être écrites en ADR avant que les modules consommateurs soient migrés vers un mécanisme unique.

**Critère de sortie.** Les ADR en vigueur ont un statut à jour ; un module transverse unique porte le contexte RLS, le modèle transactionnel et l'utilitaire JDBC, et aucune autre classe ne pose SET LOCAL ROLE ni setAutoCommit ; la propriété de chaque table est documentée et les lectures croisées sont vérifiées par un test.

**Charge.** 4,75 à 15 jours.

**Ordre interne.** AUD-ARC-14 → AUD-ARC-05 → AUD-ARC-04 → AUD-QUA-06 → AUD-ARC-06 → AUD-QUA-04 → AUD-QUA-08.
Dépendances internes : AUD-ARC-05 après AUD-ARC-14 ; AUD-ARC-04 après AUD-ARC-05 ; AUD-QUA-06 après AUD-ARC-05.

**Prérequis des sprints antérieurs.** aucun.

| ID | Sévérité | Axes | Effort | Titre |
|---|---|---|---|---|
| AUD-ARC-14 | Faible | Architecture | S | Le socle d'architecture repose sur des ADR restés « Proposé », y compris ceux que le code a déjà remplacés |
| AUD-ARC-05 | Moyenne | Architecture | M | Un noyau partagé implicite (RLS, erreurs du domaine, appelant) est logé dans workspace et api.security, d'où des exceptions ArchUnit au cas par cas |
| AUD-ARC-04 | Moyenne | Architecture, Qualité | S | Le protocole RLS « mécanisme unique » est réimplémenté quatre fois, avec des variantes divergentes |
| AUD-QUA-06 | Moyenne | Qualité | M | Le code JDBC brut est répété sans utilitaire commun : 198 prepareStatement, 101 catch SQLException et 10 copies du même convertisseur de date |
| AUD-ARC-06 | Faible | Architecture, Qualité | M | Couplage par le schéma SQL : des modules lisent directement les tables d'autres modules, hors du contrôle d'ArchUnit |
| AUD-QUA-04 | Moyenne | Qualité | M | Deux modèles transactionnels coexistent (JTA @Transactional/QuarkusTransaction et setAutoCommit/commit manuels recopiés cinq fois) |
| AUD-QUA-08 | Faible | Qualité | S | Le typage entre modules repose sur des chaînes et des tableaux : niveau d'impact en String alors que l'énumération existe, String[] comme couple de retour, clé composite découpée par split(":") |

## Sprint 5 — Authentification de bout en bout

**Pourquoi ici.** L'IHM et le desktop n'envoient aucun jeton alors que toutes les routes, IHM statique comprise, exigent OIDC : en image Docker, même index.html reçoit 401. Les tests de main.js et preload.js sont écrits d'abord, pour protéger la configuration Electron pendant que le flux PKCE la modifie.

**Critère de sortie.** Un utilisateur se connecte depuis le portail web et le desktop par Keycloak (code + PKCE) et toutes les vues fonctionnent avec son jeton ; client.ts n'a plus qu'une fonction d'appel ; un test parcourt chaque point d'entrée REST pour chaque rôle ; main.js et preload.js sont couverts par des tests.

**Charge.** 3,75 à 12 jours.

**Ordre interne.** AUD-TST-10 → AUD-ARC-12 → AUD-ARC-02 → AUD-TST-05 → AUD-SEC-04 → AUD-SEC-09.
Dépendances internes : AUD-ARC-02 après AUD-ARC-12 ; AUD-ARC-02 après AUD-TST-10.

**Prérequis des sprints antérieurs.** aucun.

| ID | Sévérité | Axes | Effort | Titre |
|---|---|---|---|---|
| AUD-TST-10 | Faible | Tests | S | Desktop : main.js (260 l.) et preload.js (42 l.), qui portent la configuration de sécurité Electron et l'IPC, ne sont pas testés |
| AUD-ARC-12 | Faible | Architecture, Qualité | M | Le contrat client.ts est écrit à la main et partiel (8 appels pour 20 ressources REST) ; ses trois fonctions HTTP divergent sur la gestion d'erreur |
| AUD-ARC-02 | Élevée | Architecture, Sécurité | M | L'IHM et le client desktop n'envoient aucun jeton alors que l'API exige OIDC sur toutes les routes sauf /system/info |
| AUD-TST-05 | Moyenne | Tests | M | Autorisations par appels impératifs (access.operator()…) sans test matriciel point d'entrée × rôle ; POST /ops/sources/{id}/synchronizations jamais testé |
| AUD-SEC-04 | Faible | Sécurité | S | Alertes e-mail envoyées à l'adresse du jeton même non vérifiée |
| AUD-SEC-09 | Faible | Sécurité | S | Recherche plein texte sans limite de débit |

## Sprint 6 — Alimenter l'inventaire et couvrir les modules

**Pourquoi ici.** La création de projet et l'import d'inventaire n'existent que dans les tests : corrélation, radar et alertes d'impact n'ont jamais de données en production. Le profil artiste, lui, n'a pas d'IHM. Les vues ont besoin de l'authentification du sprint 5 ; les tests ciblés, du seuil de couverture du sprint 1.

**Critère de sortie.** Un utilisateur crée un projet et importe un SBOM ou des manifestes par l'API et l'IHM ; les vues artiste (suivis, opportunités) existent ; scm atteint le seuil de couverture ; les listes d'exploitation sont paginées.

**Charge.** 4,75 à 15 jours.

**Ordre interne.** AUD-QUA-01 → AUD-ARC-15 → AUD-TST-04 → AUD-TST-08 → AUD-TST-09 → AUD-PER-09 → AUD-PER-10.

**Prérequis des sprints antérieurs.** AUD-TST-02 (sprint 1), AUD-ARC-02 (sprint 5), AUD-ARC-12 (sprint 5)

| ID | Sévérité | Axes | Effort | Titre |
|---|---|---|---|---|
| AUD-QUA-01 | Élevée | Qualité | M | La création de projet et l'import d'inventaire (SBOM, manifestes) n'ont aucun point d'entrée en production : code atteint seulement par les tests |
| AUD-ARC-15 | Faible | Architecture, Qualité | M | Profil artiste livré sans IHM : six points d'entrée et la rubrique des préférences ne sont appelés par aucun client |
| AUD-TST-04 | Moyenne | Tests | M | Couverture inégale : scm à 27 % des lignes et 9 % des branches, six modules sans classe de test dédiée dont workspace (26 fichiers, 1 693 lignes) |
| AUD-TST-08 | Faible | Tests | S | Isolation entre tests par liste TRUNCATE codée en dur ; six tables jamais vidées |
| AUD-TST-09 | Moyenne | Tests | M | IHM : seuls la logique pure et des contrôles textuels de sources sont testés ; aucun test de composant Vue ni du client API |
| AUD-PER-09 | Faible | Performance | S | Index manquants : clés étrangères component_id (suppressions en cascade) et colonnes de filtre des listes d'exploitation |
| AUD-PER-10 | Faible | Performance | S | Listes d'exploitation renvoyées en entier, sans pagination (sources rejetées, événements, échecs d'import) |

## Sprint 7 — Ingestion asynchrone

**Pourquoi ici.** La synchronisation des avis, l'enrichissement NVD/EPSS, la corrélation et les alertes s'exécutent dans le fil de la requête ou de l'ingestion, avec une transaction ouverte pendant des appels HTTP. Le rattrapage des enrichissements perdus (retryMissing, quelques lignes) ouvre le sprint.

**Critère de sortie.** POST /ops/sources/{id}/synchronizations répond 202 et la synchronisation tourne en tâche de fond ; retryMissing est planifiée ; EnrichmentSource.lookup et AdvisoryFeed.fetch ne sont appelés depuis aucune méthode @Transactional ni bloc QuarkusTransaction (vérifié par un test ArchUnit) ; tous les clients HTTP lisent les réponses avec un plafond de taille.

**Charge.** 3,75 à 12 jours.

**Ordre interne.** AUD-PER-02 → AUD-PER-01 → AUD-ARC-09 → AUD-ARC-10 → AUD-PER-11 → AUD-PER-13.

**Prérequis des sprints antérieurs.** AUD-SEC-01 (sprint 2)

| ID | Sévérité | Axes | Effort | Titre |
|---|---|---|---|---|
| AUD-PER-02 | Élevée | Performance, Qualité | M | L'enrichissement NVD/EPSS et la corrélation sont exécutés de façon synchrone pour chaque signal créé ou modifié, dans le fil de l'ingestion |
| AUD-PER-01 | Élevée | Performance | M | La synchronisation d'une source d'avis s'exécute en entier dans la requête HTTP d'exploitation, avec un appel distant par avis et des attentes de reprise sur le fil de requête |
| AUD-ARC-09 | Faible | Architecture | S | Deux styles de propagation d'événements CDI coexistent ; la chaîne radar s'exécute dans la requête HTTP |
| AUD-ARC-10 | Faible | Architecture, Qualité | M | La collecte HTTP est dispersée dans six clients répartis sur cinq modules |
| AUD-PER-11 | Faible | Performance | S | Réponses des sources d'avis et d'enrichissement lues en mémoire sans plafond de taille |
| AUD-PER-13 | Faible | Performance | S | Collecte d'activité des dépôts : lecture de l'échéance projet par projet et appels de forge séquentiels |

## Sprint 8 — Requêtes et volumétrie

**Pourquoi ici.** Corrélation, alertes, radar, fil de veille et opportunités font des requêtes en N+1 ou relisent des tables entières, et plusieurs tables grossissent sans fin. Le mécanisme de rétention vient en premier : la purge des opportunités s'y branche.

**Critère de sortie.** Corrélation, alertes, radar et fil de veille s'exécutent en un nombre de requêtes indépendant du nombre de signaux ou de membres ; la recherche et GET /opportunites ne matérialisent que la page demandée ; chaque table de journal ou d'historique, opportunités comprises, a une purge planifiée avec une durée de rétention configurée.

**Charge.** 5,5 à 17 jours.

**Ordre interne.** AUD-SEC-03 → AUD-PER-18 → AUD-PER-04 → AUD-PER-03 → AUD-PER-06 → AUD-PER-05 → AUD-PER-07.
Dépendances internes : AUD-PER-18 après AUD-SEC-03.

**Prérequis des sprints antérieurs.** aucun.

| ID | Sévérité | Axes | Effort | Titre |
|---|---|---|---|---|
| AUD-SEC-03 | Moyenne | Sécurité, Performance | S | Journaux d'audit sans rétention ni bornage : un compte du fournisseur d'identité non invité écrit une ligne par requête |
| AUD-PER-18 | Moyenne | Performance, Qualité | S | Opportunités sans date limite (toutes celles des flux RSS) restituées indéfiniment : ni LIMIT, ni pagination, ni purge |
| AUD-PER-04 | Moyenne | Performance | M | Évaluation des alertes en N+1 par destinataire : 3 transactions et au moins 3 requêtes par membre, plus une requête par canal, synchrones dans le fil de corrélation |
| AUD-PER-03 | Moyenne | Performance | M | Corrélation par projet en N+1 : une transaction, une relecture du signal et une requête de composants par signal, puis un INSERT préparé à chaque ligne |
| AUD-PER-06 | Moyenne | Performance | M | Le fil de veille (/me/watch) relit chaque signal un par un et parcourt signal_package par des expressions non indexées |
| AUD-PER-05 | Faible | Performance | M | Le radar charge, pour chaque espace, les composants de tous les espaces puis filtre en Java, et lit le calendrier composant par composant |
| AUD-PER-07 | Faible | Performance | M | Recherche plein texte : bonne conception (tsvector généré + GIN), mais toute la sélection est matérialisée, classée et comptée à chaque page |

## Sprint 9 — Structure, documentation et distribution

**Pourquoi ici.** Les chantiers de fond (pyramide de tests, documentation d'architecture, distribution du desktop) ne valent qu'une fois la structure stabilisée par les sprints 4 à 8 : les documenter avant serait à refaire.

**Critère de sortie.** La majorité des tests serveur s'exécutent sans Docker ; l'artefact empaqueté a ses tests d'intégration ; arc42 §5 correspond au graphe réel ; le desktop est signé et se met à jour ; un inventaire des licences est produit à chaque build.

**Charge.** 6 à 18 jours.

**Ordre interne.** AUD-TST-03 → AUD-TST-12 → AUD-ARC-03 → AUD-ARC-11 → AUD-QUA-09 → AUD-DEP-15 → AUD-DEP-19.

**Prérequis des sprints antérieurs.** AUD-ARC-05 (sprint 4), AUD-ARC-06 (sprint 4)

| ID | Sévérité | Axes | Effort | Titre |
|---|---|---|---|---|
| AUD-TST-03 | Moyenne | Tests | L | Pyramide inversée : 71 % des méthodes de test serveur sont des @QuarkusTest exigeant Docker (PostgreSQL + Keycloak Dev Services) |
| AUD-TST-12 | Faible | Tests | M | Pas de tests d'intégration sur l'artefact empaqueté (skipITs=true, aucun *IT ni @QuarkusIntegrationTest) |
| AUD-ARC-03 | Moyenne | Architecture | S | Le diagramme arc42 §5.2 et le contexte §5.1 ne décrivent plus l'architecture réelle (sens inversés, arêtes manquantes, fournisseurs obsolètes) |
| AUD-ARC-11 | Faible | Architecture | S | Aucun objet-dieu ; impact porte aussi la recherche plein texte que le package-info attribue à signal |
| AUD-QUA-09 | Faible | Qualité | S | Le nommage mélange français et anglais sans convention, jusque dans un même concept et un même identifiant |
| AUD-DEP-15 | Faible | Dépendances | M | Client desktop distribué sans signature de code ni mise à jour automatique |
| AUD-DEP-19 | Faible | Dépendances | S | Inventaire des licences non automatisé et incomplet pour npm ; pas d'avis tiers (NOTICE) joint à l'image |

## Constats Info, hors sprint

Ils documentent ce qui tient et ne demandent aucune action. Ceux marqués **borne un risque** expliquent pourquoi un risque est déjà limité : ne pas les « corriger » par erreur.

- AUD-ARC-01 — Graphe inter-modules acyclique et intégralement conforme à la table ModuleBoundariesTest
- AUD-ARC-08 — Le module api reste une couche d'exposition mince, sans accès SQL
- AUD-ARC-13 — Les couches de l'IHM et la séparation processus principal/IHM du desktop sont respectées
- AUD-QUA-14 — La gestion d'erreurs côté serveur est saine : aucun catch vide, aucun printStackTrace, interruptions rétablies, messages exposés assainis
- AUD-QUA-15 — Les ressources JDBC sont systématiquement fermées (try-with-resources), y compris le cas particulier du verrou consultatif
- AUD-QUA-16 — Taille et complexité du code maîtrisées ; TypeScript en mode strict sans any ni contournement
- AUD-SEC-10 — Aucun secret réel versionné ni dans l'historique git
- AUD-SEC-11 — Requêtes SQL paramétrées ; les seules concaténations portent sur des constantes
- AUD-SEC-12 — Authentification : jeton OIDC obligatoire partout, audience vérifiée, aucun mode sans authentification — **borne un risque**
- AUD-SEC-13 — Autorisation par espace vérifiée dans chaque ressource REST, doublée par la RLS PostgreSQL (pas d'IDOR trouvé) — **borne un risque**
- AUD-SEC-14 — Garde SSRF de la veille web complète hors rebinding (plages, IPv6 embarquées, ports, redirections, tailles) — **borne un risque**
- AUD-SEC-15 — XXE neutralisé dans la lecture RSS/Atom — **borne un risque**
- AUD-SEC-16 — Serveur MCP : authentification, limitation et balisage du contenu externe en place — **borne un risque**
- AUD-SEC-17 — Passerelle IA : données filtrées, sortie validée, fournisseur non pilotable par l'utilisateur — **borne un risque**
- AUD-SEC-18 — Client Electron durci — **borne un risque**
- AUD-SEC-19 — IHM Vue sans rendu HTML brut des contenus de veille
- AUD-SEC-20 — Conteneurs et exposition réseau raisonnables ; CSRF non applicable — **borne un risque**
- AUD-SEC-21 — Delta du lot 4 conforme : RLS sur les suivis, sources réservées à l'administrateur et collectées après vérification des conditions, hôtes data.culture filtrés, liens externes assainis — **borne un risque**
- AUD-TST-13 — argos-ui : npm test passe (25/25) et la vérification de types vue-tsc passe
- AUD-TST-14 — argos-desktop : npm test passe (22/22) et check:syntax passe
- AUD-TST-15 — Aucun test désactivé ni focalisé ; un seul test conditionnel, volontaire et documenté
- AUD-TST-16 — Hygiène des tests serveur solide : ports aléatoires, sources externes simulées, attente et délais simulés, courrier simulé
- AUD-TST-17 — Sécurité, MCP, alerting et ingestion disposent de tests ciblés nombreux
- AUD-TST-18 — ModuleBoundariesTest couvre les 16 modules et 5 règles fines ; il est éligible à surefire (nom *Test, JUnit 5)
- AUD-PER-14 — Les tâches planifiées sont protégées contre le chevauchement et idempotentes en base — **borne un risque**
- AUD-PER-15 — Les appels IA sont bornés : exécuteurs dédiés à file bornée, délais maximaux, taille de réponse plafonnée, Ollama configuré à un modèle et une requête à la fois — **borne un risque**
- AUD-PER-16 — Clients HTTP sortants : délais de connexion et de requête configurés partout, sans redirection ; caches KEV et CERT-FR avec durée de vie — **borne un risque**
- AUD-PER-17 — Bundle de l'IHM léger (42 kB gzippé) ; l'import d'inventaire par lot est prêt mais pas encore joignable en production (AUD-QUA-01)
- AUD-DEP-11 — npm audit argos-desktop : 6 vulnérabilités modérées (minimatch GHSA-g3hv-75wg-m249, déni de service) via electron-builder, outil de build uniquement — **borne un risque**
- AUD-DEP-12 — argos-ui : aucune vulnérabilité connue et dépendances à jour (Vue 3.5.45, Vite 8.3.8, vue-router 5.5.1), lockfiles v3 présents et utilisés par npm ci
- AUD-DEP-13 — Quarkus 3.40.1.2 sur le flux LTS courant (3.40) ; Java 17 compatible jusqu'à la fin des 3.x
- AUD-DEP-16 — Conteneurisation et assemblage Compose solides : multi-étapes, JRE seul, utilisateur non root, mem_limit, restart, healthcheck PostgreSQL, ports locaux par défaut
- AUD-DEP-17 — Configuration par profils correcte : prod sans valeurs par défaut pour la base et l'OIDC, mailer simulé seulement en dev/test, STARTTLS exigé, aucun secret réel versionné — **borne un risque**
- AUD-DEP-18 — Licences des dépendances livrées compatibles avec ADR-0021 (MIT/Apache-2.0/BSD) ; licences restrictives uniquement dans l'outillage de build
