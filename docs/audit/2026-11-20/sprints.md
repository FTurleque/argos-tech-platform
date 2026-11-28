# Ordre de correction — mise à jour du 2026-11-20

Le plan d'action ([plan-action.md](plan-action.md)) classe par urgence ; ce document classe par **ordre d'exécution**, qui prime en cas de divergence. Seuls les constats qui restent à traiter sont affectés à un sprint ; les constats Documentation forment le dernier sprint.

## Vue d'ensemble

| Sprint | Titre | À traiter | dont partiels | Élevée | Moyenne | Faible | Charge (j) |
|---|---|---|---|---|---|---|---|
| 1 | Remettre les gates en marche | 9 | 1 | 1 | 0 | 8 | 3 à 11 |
| 2 | Exploitation sûre : sauvegarde, déploiement, SSRF | 9 | 0 | 2 | 2 | 5 | 3 à 11 |
| 3 | Observabilité et socle d'exécution | 5 | 0 | 0 | 1 | 4 | 2 à 7 |
| 4 | Décisions écrites et noyau commun | 7 | 1 | 0 | 5 | 2 | 4,75 à 15 |
| 5 | Portail déployable et autorisations | 9 | 3 | 2 | 2 | 5 | 4,5 à 15 |
| 6 | Alimenter l'inventaire et couvrir les modules | 8 | 3 | 1 | 1 | 6 | 5 à 16 |
| 7 | Ingestion asynchrone | 7 | 1 | 1 | 1 | 5 | 4 à 13 |
| 8 | Requêtes et volumétrie | 9 | 0 | 0 | 5 | 4 | 6,75 à 21 |
| 9 | Structure et distribution | 9 | 1 | 0 | 1 | 8 | 8,75 à 26 |
| 10 | Documentation alignée sur le code livré | 10 | 0 | 0 | 4 | 6 | 4 à 14 |

Charge totale : **45,75 à 149 jours** — enveloppe (S 0,25 à 1 j, M 1 à 3 j, L 3 à 8 j) hors revue, allers-retours de CI et arbitrages écrits. Un constat partiel garde l'effort estimé au 17/11 : le reste à faire est souvent plus court.

## Chemin critique

- **AUD-TST-01** (M, sprint 1) — Débloque le seuil de couverture, le build sur JDK 17, la garde des migrations, les tests du script de licences, la campagne E2E automatisée et le contrôle documentaire en CI — et conditionne tous les critères « vérifié en CI » des sprints 5 à 10.
- **AUD-ARC-14** (S, sprint 4) — Effort S : écrire l'ADR du noyau commun et passer en « Accepté » les ADR appliqués débloque l'extraction du noyau (ARC-05), puis l'unification RLS et JDBC, la refonte d'arc42 (DOC-02) et la réécriture des ADR (DOC-01, DOC-08).
- **AUD-ARC-05** (M, sprint 4) — Le module transverse unique est le prérequis de l'unification du contexte RLS (ARC-04), de l'utilitaire JDBC (QUA-06) et de la description d'architecture.
- **AUD-DEP-21** (S, sprint 5) — Avec ARC-02, il ferme le bloqueur de déploiement du portail et conditionne la procédure d'installation du guide (DOC-03) et celle du desktop (DOC-04).

## Sprint 1 — Remettre les gates en marche

**Pourquoi ici.** Aucun test n'est exécuté automatiquement, l'image est construite avec -DskipTests et 158 commits ont été intégrés depuis le 17/11 sans gate : aucune correction des sprints suivants n'est vérifiable. Ce sprint ne touche presque pas au code applicatif.

**Critère de sortie.** Chaque push lance, sur JDK 17, mvn -B clean verify (649 tests à 54add78, environ 16 min), npm test et vue-tsc pour l'IHM, npm test et check:syntax pour le desktop, la vérification de types de e2e/, le contrôle documentaire et npm audit ; le profil de licences et les tests du script de licences tournent quand pom.xml change ; JaCoCo est publié avec un seuil qui fait échouer le build (référence : 85,5 % des lignes, 71,7 % des branches) ; modifier une migration déjà fusionnée fait échouer la CI (règle d'ADR-0032) ; un paquet io.argos non déclaré fait échouer ModuleBoundariesTest ; Renovate ou Dependabot ouvre les mises à jour des dépendances et des tags d'images (épinglage par tag, décision H2).

**Charge.** 3 à 11 jours. Déjà engagés (partiels) : AUD-DEP-20.

**Ordre interne.** AUD-TST-01 → AUD-TST-19 → AUD-DEP-20 → AUD-DEP-22 → AUD-TST-02 → AUD-ARC-07 → AUD-DEP-05 → AUD-QUA-19 → AUD-SEC-24.
Dépendances internes : AUD-TST-19 après AUD-TST-01 ; AUD-DEP-20 après AUD-TST-01 ; AUD-DEP-22 après AUD-TST-01 ; AUD-TST-02 après AUD-TST-01.

**Prérequis des sprints antérieurs.** aucun.

| ID | Statut | Sévérité | Axes | Effort | Titre |
|---|---|---|---|---|---|
| AUD-TST-01 | Ouvert | Élevée | Tests, Dépendances | M | Aucune intégration continue : aucun test (mvn verify, npm test, ModuleBoundariesTest) n'est exécuté automatiquement |
| AUD-TST-19 | Ouvert | Faible | Tests, Dépendances | S | Build incrémental non fiable et tests exécutés sur un JDK différent de la production (JDK 24 local, JRE 17 en image) |
| AUD-DEP-20 | Partiel | Faible | Dépendances, Qualité | S | Migration V13 modifiée sur place avant fusion, sans garde contre la réécriture d'une migration déjà fusionnée |
| AUD-DEP-22 | Ouvert | Faible | Dépendances | S | Le script de contrôle des licences (404 lignes) n'a aucun test automatisé et ne s'exécute qu'au build de l'image, hors de « mvn verify » |
| AUD-TST-02 | Ouvert | Faible | Tests | S | Pas de seuil de couverture : JaCoCo absent du pom, la couverture (85,5 % des lignes, 71,7 % des branches à 54add78) n'est mesurable qu'à la main |
| AUD-ARC-07 | Ouvert | Faible | Architecture, Tests | S | La table ALLOWED autorise 7 dépendances sur 57 que le code n'utilise pas et ne contraint pas un paquet io.argos non déclaré |
| AUD-DEP-05 | Ouvert | Faible | Dépendances | S | Aucune veille automatisée des dépendances (Renovate/Dependabot, CVE Maven) ; Electron 45.3.2 hors de la mineure supportée (45.4.1 publiée), Playwright 1.61.1 (1.65.1 publiée) |
| AUD-QUA-19 | Ouvert | Faible | Qualité | S | Les 24 fichiers TypeScript de e2e/ (3 485 lignes) ne sont soumis à aucune vérification de types : pas de tsconfig ni de dépendance typescript ; un tsc --strict révèle 6 erreurs |
| AUD-SEC-24 | Ouvert | Faible | Sécurité | S | Campagne de performance e2e : serveur lancé par « mvn.cmd » via le PATH avec shell: true sous Windows, contrairement à la règle des chemins absolus |

## Sprint 2 — Exploitation sûre : sauvegarde, déploiement, SSRF

**Pourquoi ici.** Le NAS est la seule cible réelle : une sauvegarde qui échoue en silence et un compose qui ne transmet pas 28 des 37 variables lues en production sont une perte de données et des fonctions mortes. Les fichiers d'exploitation (compose.yaml, Dockerfile, script de sauvegarde) sont traités en un seul lot. Le rebinding DNS, à fenêtre étroite, est corrigé avant l'ouverture au public.

**Critère de sortie.** Un pg_dump en échec fait sortir le script en erreur sans rotation et les sauvegardes sont écrites en 0600 ; une restauration a été rejouée de bout en bout sur une base vierge ; un test compare les 37 ${ARGOS_*} lus par application.properties à compose.yaml et .env.example et échoue sur tout écart ; les conteneurs argos et ollama ont un healthcheck, la JVM s'arrête sur OutOfMemoryError ; quarkus-app appartient à root et l'utilisateur 1001 ne peut que le lire, le service tourne en read_only avec cap_drop: [ALL] et no-new-privileges ; un test avec un résolveur DNS qui change de réponse prouve que la veille web se connecte à l'adresse validée.

**Charge.** 3 à 11 jours.

**Ordre interne.** AUD-QUA-02 → AUD-SEC-07 → AUD-DEP-03 → AUD-DEP-01 → AUD-SEC-06 → AUD-DEP-08 → AUD-PER-12 → AUD-SEC-25 → AUD-SEC-01.
Dépendances internes : AUD-SEC-07 après AUD-QUA-02 ; AUD-DEP-03 après AUD-QUA-02.

**Prérequis des sprints antérieurs.** aucun.

| ID | Statut | Sévérité | Axes | Effort | Titre |
|---|---|---|---|---|---|
| AUD-QUA-02 | Ouvert | Élevée | Qualité, Dépendances | S | Le script de sauvegarde ne détecte pas l'échec de pg_dump (pas de pipefail) et sa rotation peut supprimer les bonnes sauvegardes |
| AUD-SEC-07 | Ouvert | Faible | Sécurité | S | Sauvegardes complètes de la base écrites en clair avec les permissions par défaut |
| AUD-DEP-03 | Ouvert | Moyenne | Dépendances | S | Procédure de restauration documentée non testée et non idempotente : dump texte sans --clean rejoué par psql sans ON_ERROR_STOP sur une base déjà migrée |
| AUD-DEP-01 | Ouvert | Élevée | Dépendances | S | compose.yaml ne transmet pas au conteneur 28 des 37 variables lues en production (SMTP, ARGOS_PUBLIC_URL, jetons de forge, audience OIDC, quotas IA, planifications) et 27 manquent dans .env.example : e-mails vers localhost:587, liens vers localhost:5173 |
| AUD-SEC-06 | Ouvert | Faible | Sécurité, Dépendances | S | Mot de passe de base de données par défaut « argos » si .env absent |
| AUD-DEP-08 | Ouvert | Faible | Dépendances | S | Conteneurs argos et ollama sans healthcheck, pas de HEALTHCHECK dans le Dockerfile ni de rotation des journaux Docker |
| AUD-PER-12 | Ouvert | Faible | Performance | S | Réglages JVM et pool : heap à 75 % d'une limite de 1 Gio, sans ExitOnOutOfMemoryError, et pool de connexions laissé aux valeurs par défaut |
| AUD-SEC-25 | Ouvert | Faible | Sécurité | S | Image : code de l'application modifiable par l'utilisateur d'exécution et conteneurs sans durcissement (read_only, cap_drop, no-new-privileges) |
| AUD-SEC-01 | Ouvert | Moyenne | Sécurité | M | Veille web : contrôle SSRF contournable par rebinding DNS (résolution vérifiée distincte de la résolution de connexion) |

## Sprint 3 — Observabilité et socle d'exécution

**Pourquoi ici.** Les sprints 7 et 8 déplacent la chaîne d'ingestion et réécrivent les requêtes chaudes : sans métriques ni journaux structurés, une régression de débit ou une tâche bloquée ne se verrait pas. L'observabilité passe donc avant les corrections de fond, même sans dépendance formelle.

**Critère de sortie.** Le serveur expose des métriques (/q/metrics) et des journaux JSON en production, avec un contrôle de santé par tâche planifiée (13 tâches) ; une recherche de Instant.now() et LocalDate.now() dans src/main ne trouve plus que le producteur du bean Clock ; les exécuteurs sont gérés par CDI ; les en-têtes de sécurité HTTP sont posés et vérifiés par un test, la CSP autorisant l'origine de l'émetteur OIDC dans connect-src.

**Charge.** 2 à 7 jours.

**Ordre interne.** AUD-DEP-07 → AUD-QUA-10 → AUD-TST-06 → AUD-QUA-11 → AUD-SEC-05.
Dépendances internes : AUD-TST-06 après AUD-QUA-10.

**Prérequis des sprints antérieurs.** aucun.

| ID | Statut | Sévérité | Axes | Effort | Titre |
|---|---|---|---|---|---|
| AUD-DEP-07 | Ouvert | Moyenne | Dépendances | M | Observabilité minimale : ni journaux structurés, ni métriques, ni traçage, ni contrôle de santé métier |
| AUD-QUA-10 | Ouvert | Faible | Qualité | S | Horloge injectable seulement dans CollecteWeb et LimiteurDebit : 63 appels à now() côté Java (37 fichiers) sans Clock commun, mêlés à 47 now() SQL, et deux LocalDate.now() sans fuseau |
| AUD-TST-06 | Ouvert | Faible | Tests | S | Assertions dépendantes de l'horloge réelle et de délais fixes (sleep 5 s simulé, sleep 1 s, attente active 5 s, marge de 900 ms sur le délai IA) |
| AUD-QUA-11 | Ouvert | Faible | Qualité | S | Des exécuteurs de threads statiques, créés hors du cycle de vie CDI, ne sont jamais arrêtés |
| AUD-SEC-05 | Ouvert | Faible | Sécurité | S | Aucun en-tête de sécurité HTTP sur l'IHM et l'API servies par Quarkus |

## Sprint 4 — Décisions écrites et noyau commun

**Pourquoi ici.** Plusieurs mécanismes partagés (contexte RLS en 4 copies, transactions manuelles en 6 copies, 273 prepareStatement sans utilitaire, propriété des tables) restent recopiés ou implicites, et 14 ADR fondateurs sont encore « Proposé ». Les décisions doivent être écrites avant que les modules consommateurs soient migrés vers un mécanisme unique.

**Critère de sortie.** Les ADR en vigueur ont un statut à jour ; un module transverse unique porte le contexte RLS, le modèle transactionnel et l'utilitaire JDBC, et aucune autre classe ne pose SET LOCAL ROLE ni setAutoCommit ; la propriété de chaque table est documentée et les lectures croisées sont vérifiées par un test.

**Charge.** 4,75 à 15 jours. Déjà engagés (partiels) : AUD-ARC-14.

**Ordre interne.** AUD-ARC-14 → AUD-ARC-05 → AUD-ARC-04 → AUD-QUA-06 → AUD-ARC-06 → AUD-QUA-04 → AUD-QUA-08.
Dépendances internes : AUD-ARC-05 après AUD-ARC-14 ; AUD-ARC-04 après AUD-ARC-05 ; AUD-QUA-06 après AUD-ARC-05.

**Prérequis des sprints antérieurs.** aucun.

| ID | Statut | Sévérité | Axes | Effort | Titre |
|---|---|---|---|---|---|
| AUD-ARC-14 | Partiel | Faible | Architecture, Documentation | S | Le socle d'architecture repose sur des ADR fondateurs restés « Proposé » (14 sur 33, dont 0001, 0013, 0014, 0016) alors que le code les applique |
| AUD-ARC-05 | Ouvert | Moyenne | Architecture | M | Un noyau partagé implicite (RLS, erreurs du domaine, appelant) est logé dans workspace et api.security, d'où cinq règles ArchUnit d'exception au cas par cas |
| AUD-ARC-04 | Ouvert | Moyenne | Architecture, Qualité | S | Le protocole RLS « mécanisme unique » est réimplémenté quatre fois, avec des variantes divergentes |
| AUD-QUA-06 | Ouvert | Moyenne | Qualité | M | Le code JDBC brut est répété sans utilitaire commun : 273 prepareStatement, 130 catch SQLException, 11 copies du même convertisseur de date et 7 de la troncature |
| AUD-ARC-06 | Ouvert | Moyenne | Architecture, Qualité | M | Couplage par le schéma SQL : 13 tables lues par plusieurs modules, dont trois lectures qu'aucune dépendance autorisée ne couvre (impact → compte, catalog → rôles d'appartenance, alerting → préférences) |
| AUD-QUA-04 | Ouvert | Moyenne | Qualité | M | Deux modèles transactionnels coexistent (89 @Transactional/23 QuarkusTransaction et setAutoCommit/commit manuels recopiés six fois) |
| AUD-QUA-08 | Ouvert | Faible | Qualité | S | Le typage entre modules repose sur des chaînes et des tableaux : niveau d'impact en String alors que l'énumération existe, String[] comme couple de retour, clé composite découpée par split(":") |

## Sprint 5 — Portail déployable et autorisations

**Pourquoi ici.** L'IHM et le desktop envoient désormais le jeton OIDC, mais le portail servi par l'image Docker reste inutilisable : la règle /* authenticated couvre index.html et l'émetteur OIDC est figé sur localhost:8180 au build. Les autorisations, plus nombreuses (96 opérations), n'ont toujours pas de test matriciel, et une route réglementaire interroge la forge avant de vérifier l'appelant.

**Critère de sortie.** L'image construite par docker compose répond 200 sur / et sur les ressources statiques sans jeton, et 401 sur /api/v1/* sans jeton ; l'émetteur et le client OIDC de l'IHM de l'image viennent de la configuration de déploiement (aucune occurrence de localhost:8180 dans le bundle de l'image, contrôlé par un test) ; un E2E connecte un utilisateur au portail servi par l'image ; un test parcourt chaque point d'entrée REST pour chaque rôle, dont PUT …/demande-evolution où un non-expert est refusé sans aucune requête vers la forge ; les webPreferences de main.js sont testées ; l'écoute de bouclage du desktop ne se ferme que sur le state attendu ; recherche et /me/veille ont une limite de débit ; les alertes ne partent que vers une adresse vérifiée.

**Charge.** 4,5 à 15 jours. Déjà engagés (partiels) : AUD-ARC-02, AUD-TST-10, AUD-ARC-12.

**Ordre interne.** AUD-ARC-02 → AUD-DEP-21 → AUD-TST-10 → AUD-ARC-12 → AUD-TST-05 → AUD-SEC-22 → AUD-SEC-04 → AUD-SEC-09 → AUD-SEC-23.

**Prérequis des sprints antérieurs.** aucun.

| ID | Statut | Sévérité | Axes | Effort | Titre |
|---|---|---|---|---|---|
| AUD-ARC-02 | Partiel | Élevée | Architecture, Sécurité | M | L'IHM et le desktop joignent désormais le jeton OIDC, mais l'IHM embarquée dans l'image Docker reste derrière « /* authenticated » et ne peut pas se charger |
| AUD-DEP-21 | Ouvert | Élevée | Dépendances, Architecture | S | L'image Docker fige l'émetteur OIDC de l'IHM sur http://localhost:8180/realms/argos (aucun ARG au build) et le desktop installé borne sa CSP et son émetteur à localhost faute de ARGOS_API_URL/ARGOS_OIDC_URL : connexion impossible hors du poste de développement |
| AUD-TST-10 | Partiel | Faible | Tests | S | Desktop : main.js (248 l.), qui porte la configuration de sécurité Electron, n'est pas testé ; preload.js l'est désormais |
| AUD-ARC-12 | Partiel | Faible | Architecture, Qualité | M | Le contrat client de l'IHM est écrit à la main et partiel (11 racines REST sur 23), sans génération ni test de contrat ; le point d'appel HTTP est désormais unique |
| AUD-TST-05 | Ouvert | Moyenne | Tests | M | Autorisations par appels impératifs (80 appels access.*() pour 96 opérations) sans test matriciel point d'entrée × rôle ; POST /ops/sources/{id}/synchronizations jamais testé |
| AUD-SEC-22 | Ouvert | Moyenne | Sécurité | S | Demande d'évolution : tout compte peut faire interroger GitHub/GitLab avec les jetons du serveur avant tout contrôle d'expertise (oracle d'existence sur dépôts privés, 10 appels/min/compte) |
| AUD-SEC-04 | Ouvert | Faible | Sécurité | S | Alertes e-mail envoyées à l'adresse du jeton même non vérifiée |
| AUD-SEC-09 | Ouvert | Faible | Sécurité | S | Recherche plein texte et veille personnelle (/me/veille) sans limite de débit |
| AUD-SEC-23 | Ouvert | Faible | Sécurité | S | Desktop : l'écoute de bouclage 127.0.0.1 accepte le premier GET /argos/retour sans comparer le state et se ferme (interruption de connexion par un processus local) |

## Sprint 6 — Alimenter l'inventaire et couvrir les modules

**Pourquoi ici.** L'import planifié existe, mais aucun point d'entrée ne crée de projet ni ne rattache de dépôt : corrélation, radar de dette et alertes d'impact n'ont toujours aucune donnée réelle. Plusieurs fonctions ne sont atteintes que par les tests, dont les suggestions IA du radar, et le profil artiste n'a pas d'IHM.

**Critère de sortie.** Un utilisateur crée un projet, lui rattache un dépôt et importe une SBOM ou des manifestes par l'API et l'IHM ; les vues artiste existent ; les suggestions du radar ont un producteur, ou l'endpoint de décision est retiré ; aucune méthode publique de src/main n'est atteinte seulement par les tests ; scm atteint le seuil de couverture (51,8 % des lignes à 54add78) ; appelerApi et les vues ont des tests de composant ; les listes d'exploitation et les impacts d'un projet sont paginés.

**Charge.** 5 à 16 jours. Déjà engagés (partiels) : AUD-QUA-01, AUD-TST-04, AUD-TST-09.

**Ordre interne.** AUD-QUA-01 → AUD-QUA-17 → AUD-ARC-15 → AUD-TST-04 → AUD-TST-08 → AUD-TST-09 → AUD-PER-09 → AUD-PER-10.

**Prérequis des sprints antérieurs.** AUD-TST-02 (sprint 1)

| ID | Statut | Sévérité | Axes | Effort | Titre |
|---|---|---|---|---|---|
| AUD-QUA-01 | Partiel | Élevée | Qualité | M | La création de projet, l'ajout de dépôt et l'import manuel (SBOM, manifestes) n'ont toujours aucun point d'entrée en production ; seul l'import planifié des dépôts déjà configurés est branché |
| AUD-QUA-17 | Ouvert | Moyenne | Qualité | S | Fonctionnalités atteintes seulement par les tests : suggestions IA du radar jamais produites alors que leur décision est exposée en REST, collecte immédiate d'activité, trois méthodes sans appelant |
| AUD-ARC-15 | Ouvert | Faible | Architecture, Qualité | M | Profil artiste livré sans IHM : six points d'entrée et la rubrique des préférences ne sont appelés par aucun client |
| AUD-TST-04 | Partiel | Faible | Tests | M | Couverture inégale : cinq modules sans classe de test dédiée, dont workspace (26 fichiers, 1 759 lignes) et inventory (11 fichiers, 1 863 lignes) ; scm désormais testé |
| AUD-TST-08 | Ouvert | Faible | Tests | S | Isolation entre tests par liste TRUNCATE codée en dur ; six tables jamais vidées |
| AUD-TST-09 | Partiel | Faible | Tests | M | IHM : la logique et 9 composants sur 21 (rendu SSR) sont testés ; ni le client API effectif (appelerApi) ni les 10 vues |
| AUD-PER-09 | Ouvert | Faible | Performance | S | Index manquants : clés étrangères component_id (suppressions en cascade) et colonnes de filtre des listes d'exploitation |
| AUD-PER-10 | Ouvert | Faible | Performance | S | Listes renvoyées en entier sans pagination : listes d'exploitation (sources rejetées, événements, échecs d'import) et impacts d'un projet (GET /projects/{id}/impacts) |

## Sprint 7 — Ingestion asynchrone

**Pourquoi ici.** La collecte est planifiée, mais le lancement manuel reste synchrone, l'enrichissement NVD/EPSS et la corrélation tournent dans le fil d'ingestion transaction ouverte, et un enrichissement refusé n'est jamais rattrapé — un avis inchangé ne déclenche même plus d'événement.

**Critère de sortie.** POST /ops/sources/{id}/synchronizations répond 202 et réutilise la tâche de fond existante ; l'amorçage OSV passe par l'archive de l'écosystème au lieu d'un GET par avis ; retryMissing est planifiée et le javadoc d'Enricher devient vrai ; aucun appel à EnrichmentSource.lookup ni à AdvisoryFeed.fetch dans une transaction (test ArchUnit) ; tous les clients HTTP lisent les réponses avec un plafond de taille ; AlerteEmise est observée ou supprimée.

**Charge.** 4 à 13 jours. Déjà engagés (partiels) : AUD-PER-01.

**Ordre interne.** AUD-PER-02 → AUD-PER-01 → AUD-ARC-09 → AUD-ARC-19 → AUD-ARC-10 → AUD-PER-11 → AUD-PER-13.

**Prérequis des sprints antérieurs.** AUD-SEC-01 (sprint 2)

| ID | Statut | Sévérité | Axes | Effort | Titre |
|---|---|---|---|---|---|
| AUD-PER-02 | Ouvert | Élevée | Performance, Qualité | M | L'enrichissement NVD/EPSS et la corrélation sont exécutés de façon synchrone pour chaque signal créé ou modifié, dans le fil de l'ingestion |
| AUD-PER-01 | Partiel | Moyenne | Performance | M | La synchronisation est planifiée, mais le lancement manuel reste synchrone dans la requête HTTP et OSV est toujours lu avis par avis, reprises comprises sur le fil qui synchronise |
| AUD-ARC-09 | Ouvert | Faible | Architecture | S | Deux styles de propagation d'événements CDI coexistent ; la chaîne radar s'exécute dans la requête HTTP |
| AUD-ARC-19 | Ouvert | Faible | Architecture | S | L'événement AlerteEmise est publié en cinq points sans aucun observateur |
| AUD-ARC-10 | Ouvert | Faible | Architecture, Qualité | M | La collecte HTTP est dispersée dans sept clients répartis sur cinq modules |
| AUD-PER-11 | Ouvert | Faible | Performance | S | Réponses des sources d'avis et d'enrichissement lues en mémoire sans plafond de taille |
| AUD-PER-13 | Ouvert | Faible | Performance | S | Collecte d'activité des dépôts : lecture de l'échéance projet par projet et appels de forge séquentiels |

## Sprint 8 — Requêtes et volumétrie

**Pourquoi ici.** Corrélation, alertes, radar, « Ma veille » et opportunités font des requêtes en N+1 ou relisent des tables entières ; l'import nocturne réécrit et recorrèle tout ; plusieurs tables grossissent sans fin, désormais toutes les heures. Le mécanisme de rétention vient en premier.

**Critère de sortie.** Corrélation, alertes, radar et fil de veille s'exécutent en un nombre de requêtes indépendant du nombre de signaux ou de membres ; l'import nocturne ne réécrit ni ne recorrèle un inventaire inchangé (empreinte du document) et insère les composants par lot ; GET /api/v1/me/veille passe sous la cible (p95 < 500 ms) dans la campagne de performance ; chaque table de journal, d'échec ou d'historique a une purge planifiée avec une rétention configurée.

**Charge.** 6,75 à 21 jours.

**Ordre interne.** AUD-SEC-03 → AUD-PER-18 → AUD-PER-04 → AUD-PER-03 → AUD-PER-19 → AUD-PER-20 → AUD-PER-06 → AUD-PER-05 → AUD-PER-07.
Dépendances internes : AUD-PER-18 après AUD-SEC-03 ; AUD-PER-20 après AUD-PER-19.

**Prérequis des sprints antérieurs.** aucun.

| ID | Statut | Sévérité | Axes | Effort | Titre |
|---|---|---|---|---|---|
| AUD-SEC-03 | Ouvert | Moyenne | Sécurité, Performance | S | Journaux d'audit sans rétention ni bornage : un compte du fournisseur d'identité non invité écrit une ligne par requête |
| AUD-PER-18 | Ouvert | Moyenne | Performance, Qualité | S | Opportunités sans date limite (toutes celles des flux RSS) restituées indéfiniment : ni LIMIT, ni pagination, ni purge |
| AUD-PER-04 | Ouvert | Moyenne | Performance | M | Évaluation des alertes en N+1 par destinataire : 3 transactions et au moins 3 requêtes par membre, plus une requête par canal, synchrones dans le fil de corrélation |
| AUD-PER-03 | Ouvert | Moyenne | Performance | M | Corrélation par projet en N+1 aggravé : une transaction et une relecture du signal par signal, puis 3 à 5 instructions SQL par composant correspondant (rattachement, état précédent, upsert, transitions) |
| AUD-PER-19 | Ouvert | Faible | Performance | M | L'import quotidien réécrit et recorrèle chaque nuit tout l'inventaire de chaque projet à dépôt, même si la SBOM ou les manifestes n'ont pas changé |
| AUD-PER-20 | Ouvert | Faible | Performance | S | Régression de l'import d'inventaire : les composants sont insérés un par un (un aller-retour SQL par composant), alors qu'ils l'étaient par lot à l'audit initial |
| AUD-PER-06 | Ouvert | Moyenne | Performance | M | « Ma veille » hors cible (p95 533,8 ms mesuré, cible 500 ms) : réponse non paginée, signaux relus un par un et signal_package parcouru par des expressions non indexées |
| AUD-PER-05 | Ouvert | Faible | Performance | M | Le radar charge, pour chaque espace, les composants de tous les espaces puis filtre en Java, et lit le calendrier composant par composant |
| AUD-PER-07 | Ouvert | Faible | Performance | M | Recherche plein texte : bonne conception (tsvector généré + GIN), mais toute la sélection est matérialisée, classée et comptée à chaque page |

## Sprint 9 — Structure et distribution

**Pourquoi ici.** Les chantiers de fond (pyramide de tests, campagne E2E automatisée contre l'image, taille des méthodes, distribution du desktop) ne valent qu'une fois la structure stabilisée par les sprints 4 à 8.

**Critère de sortie.** La majorité des tests serveur s'exécutent sans Docker ; la campagne E2E et la campagne d'accessibilité (étendue aux écrans Inventaire, abonnements et traitement des impacts) tournent en CI contre l'image construite ; un seuil de longueur et de complexité des méthodes est vérifié en CI ; le desktop est signé et se met à jour ; les avis MIT de vue et vue-router sont livrés dans l'image et l'installeur, ou la décision H1 motive leur absence.

**Charge.** 8,75 à 26 jours. Déjà engagés (partiels) : AUD-DEP-19.

**Ordre interne.** AUD-TST-03 → AUD-TST-12 → AUD-TST-20 → AUD-TST-23 → AUD-ARC-11 → AUD-QUA-09 → AUD-QUA-18 → AUD-DEP-15 → AUD-DEP-19.
Dépendances internes : AUD-TST-23 après AUD-TST-20.

**Prérequis des sprints antérieurs.** AUD-TST-01 (sprint 1)

| ID | Statut | Sévérité | Axes | Effort | Titre |
|---|---|---|---|---|---|
| AUD-TST-03 | Ouvert | Moyenne | Tests | L | Pyramide inversée : 71 % des méthodes de test serveur (425 sur 597) sont des @QuarkusTest exigeant Docker (PostgreSQL + Keycloak Dev Services) |
| AUD-TST-12 | Ouvert | Faible | Tests | M | Pas de tests d'intégration sur l'artefact empaqueté (skipITs=true, aucun *IT ni @QuarkusIntegrationTest) |
| AUD-TST-20 | Ouvert | Faible | Tests | M | Campagne E2E principale (34 tests, 14 fichiers) rejouable seulement sur un poste complet et jamais automatisée ; reuseExistingServer: true réutilise sans contrôle un serveur ou un Vite déjà lancés |
| AUD-TST-23 | Ouvert | Faible | Tests, Documentation | M | L'audit d'accessibilité n'a pas suivi les écrans livrés après le 17/11 : vue d'inventaire, actions sur les abonnements et traitement des impacts sans mesure de contraste ni revue |
| AUD-ARC-11 | Ouvert | Faible | Architecture | S | Aucun objet-dieu (max. 556 lignes, InventoryQueries) ; impact (2 905 l.) porte aussi la recherche plein texte que le package-info attribue à signal |
| AUD-QUA-09 | Ouvert | Faible | Qualité | S | Le nommage mélange français et anglais sans convention, jusque dans un même concept et un même identifiant |
| AUD-QUA-18 | Ouvert | Faible | Qualité | M | La taille et la complexité du serveur progressent : 19 fichiers Java de plus de 300 lignes (9 à 9d96406), 10 méthodes de complexité cyclomatique ≥ 15 (8), et deux méthodes longues encore allongées malgré les commits de découpage |
| AUD-DEP-15 | Ouvert | Faible | Dépendances | M | Client desktop distribué sans signature de code ni mise à jour automatique |
| AUD-DEP-19 | Partiel | Faible | Dépendances | S | Licences : contrôle automatisé et avis livrés pour le serveur ; IHM livrée (vue, vue-router) sans avis MIT dans l'image ni l'installeur, aucun contrôle npm |

## Sprint 10 — Documentation alignée sur le code livré

**Pourquoi ici.** Les points d'entrée décrivent encore le cadrage de septembre et le guide de déploiement mène à une installation qui ne démarre pas. La documentation passe en dernier pour décrire l'état produit par les sprints 1 à 9 (émetteur OIDC de l'image, variables de compose, noyau commun, CI) au lieu d'être réécrite deux fois — avec un correctif immédiat de quelques lignes du guide, recommandé hors sprint (AUD-DOC-03).

**Critère de sortie.** En suivant uniquement docs/deploiement/synology-et-pc.md, on installe Argos et Keycloak sur une machine vierge puis on se connecte au portail et au desktop : procédure rejouée et datée dans le guide ; chaque variable ARGOS_* lue en production figure dans le guide, vérifié par script ; README racine, docs/architecture/README.md, openspec/config.yaml et arc42 §1-3, §5, §7, §10 et §11 décrivent le code au HEAD (plus de Claude API, FreshRSS, IdP d'entreprise ni « socle sans fonctionnalité métier ») ; un seul registre des risques ; les ADR acceptés sont rédigés en « Décision » ; verifier-docs --rendu passe, contrôle les README des sous-projets et tourne en CI ; .archgate/ est supprimé ou documenté ; le registre des spécifications concorde avec les tasks.md.

**Charge.** 4 à 14 jours.

**Ordre interne.** AUD-DOC-03 → AUD-DOC-04 → AUD-DOC-01 → AUD-DOC-02 → AUD-DOC-05 → AUD-DOC-06 → AUD-DOC-07 → AUD-DOC-08 → AUD-DOC-10 → AUD-DOC-11.

**Prérequis des sprints antérieurs.** AUD-TST-01 (sprint 1), AUD-DEP-01 (sprint 2), AUD-ARC-05 (sprint 4), AUD-ARC-06 (sprint 4), AUD-ARC-14 (sprint 4), AUD-ARC-02 (sprint 5), AUD-DEP-21 (sprint 5)

| ID | Statut | Sévérité | Axes | Effort | Titre |
|---|---|---|---|---|---|
| AUD-DOC-03 | Ouvert | Moyenne | Documentation, Dépendances, Sécurité | M | Le guide de déploiement mène à une installation qui ne démarre pas : ARGOS_OIDC_URL et ARGOS_ADMIN_EMAIL, obligatoires, absents des étapes et du tableau des réglages, aucune procédure Keycloak, et le guide affirme que l'authentification « n'est pas en place » |
| AUD-DOC-04 | Ouvert | Moyenne | Documentation | S | Client desktop : la procédure « viser le NAS » ne règle que VITE_ARGOS_API_URL, alors que l'application installée limite sa CSP à localhost:8080 et son émetteur OIDC à localhost:8180 sans ARGOS_API_URL et ARGOS_OIDC_URL, variables documentées nulle part pour l'utilisateur |
| AUD-DOC-01 | Ouvert | Moyenne | Documentation | S | Les points d'entrée (README racine, docs/architecture/README.md, openspec/config.yaml) décrivent encore le cadrage de septembre : socle « sans fonctionnalité métier », Claude derrière un AI Gateway, questions ouvertes déjà tranchées par des ADR acceptés |
| AUD-DOC-02 | Ouvert | Moyenne | Documentation, Architecture | M | arc42 (§1, §2, §3 D-01, §5.2, §7, §10, §11) et les deux registres de risques divergents ne suivent pas les ADR 0025 à 0033 ; §3 et §7 citent encore Claude API et l'IdP d'entreprise |
| AUD-DOC-05 | Ouvert | Faible | Documentation | S | README racine, démarrage local : Node.js 22.12 annoncé contre ^22.18.0 exigé, Docker présenté comme requis pour les seuls tests alors que quarkus:dev démarre Keycloak, aucune mention de la connexion (royaume de test, comptes), e2e réduit au « portail web » |
| AUD-DOC-06 | Ouvert | Faible | Documentation | S | Le diagramme D-19 (arc42 §6.6, veille réglementaire) ne se rend pas : un « ; » dans un message de séquence casse l'analyse Mermaid, et le contrôle documentaire passe parce que le rendu est facultatif |
| AUD-DOC-07 | Ouvert | Faible | Documentation | S | Documents de suivi incohérents entre eux : statuts de tâches et de décisions, inventaire des diagrammes (D-01 à D-13 contre 19), migrations de D-13 et statuts de capacités ne concordent pas |
| AUD-DOC-08 | Ouvert | Faible | Documentation | S | Neuf ADR acceptés (0023, 0024, 0027 à 0033) gardent une décision rédigée comme une proposition, et ADR-0029 se dit encore subordonné à sa propre acceptation |
| AUD-DOC-10 | Ouvert | Faible | Documentation | S | Le contrôle documentaire laisse passer des chemins périmés : références en code en ligne vers trois changements archivés (ADR-0027, ADR-0028), README d'argos-server et d'e2e hors périmètre, aucune exécution automatique |
| AUD-DOC-11 | Ouvert | Faible | Documentation | S | Reliquat d'outillage Archgate : un second dossier d'ADR avec l'exemple non rempli GEN-001 et une documentation de règles de lint renvoyant à un fichier absent |

## Hors sprint

Constats Info (ce qui tient), décisions documentées et constats fermés. Ceux marqués **borne un risque** expliquent pourquoi un risque est déjà limité : ne pas les « corriger » par erreur.

- AUD-DEP-06 — Décision documentée — Images Docker en tags flottants sans digest : node:22-slim, maven:3.9-eclipse-temurin-17, eclipse-temurin:17-jre, postgres:18
- AUD-ARC-01 — Info — Graphe inter-modules acyclique (51 arêtes) et intégralement conforme à la table ModuleBoundariesTest
- AUD-ARC-08 — Info — Le module api reste une couche d'exposition mince, sans accès SQL
- AUD-ARC-13 — Info — Les couches de l'IHM et la séparation processus principal/IHM du desktop sont respectées
- AUD-ARC-18 — Info — Les quatre nouveaux sous-paquets et les nouvelles arêtes du lot réglementaire sont couverts par ModuleBoundariesTest, déclarés « table d'abord » — **borne un risque**
- AUD-QUA-14 — Info — La gestion d'erreurs côté serveur est saine : aucun catch vide, aucun printStackTrace, interruptions rétablies, messages exposés assainis
- AUD-QUA-15 — Info — Les ressources JDBC sont systématiquement fermées (try-with-resources), y compris le cas particulier du verrou consultatif
- AUD-QUA-16 — Info — Typage TypeScript strict tenu dans argos-ui (vue-tsc sans erreur, un seul contournement)
- AUD-SEC-10 — Info — Aucun secret réel versionné ni dans l'historique git
- AUD-SEC-11 — Info — Requêtes SQL paramétrées ; les seules concaténations portent sur des constantes
- AUD-SEC-12 — Info — Authentification : jeton OIDC obligatoire partout, audience vérifiée, aucun mode sans authentification — **borne un risque**
- AUD-SEC-13 — Info — Autorisation par espace vérifiée dans chaque ressource REST, doublée par la RLS PostgreSQL (pas d'IDOR trouvé) — **borne un risque**
- AUD-SEC-14 — Info — Garde SSRF de la veille web complète hors rebinding (plages, IPv6 embarquées, ports, redirections, tailles) — **borne un risque**
- AUD-SEC-15 — Info — XXE neutralisé dans la lecture RSS/Atom — **borne un risque**
- AUD-SEC-16 — Info — Serveur MCP : authentification, limitation et balisage du contenu externe en place (8 outils) — **borne un risque**
- AUD-SEC-17 — Info — Passerelle IA : données filtrées, sortie validée, fournisseur non pilotable par l'utilisateur — **borne un risque**
- AUD-SEC-18 — Info — Client Electron durci — **borne un risque**
- AUD-SEC-19 — Info — IHM Vue sans rendu HTML brut des contenus de veille
- AUD-SEC-20 — Info — Conteneurs et exposition réseau raisonnables ; CSRF non applicable — **borne un risque**
- AUD-SEC-21 — Info — Delta du lot 4 conforme : RLS sur les suivis, sources réservées à l'administrateur et collectées après vérification des conditions, hôtes data.culture filtrés, liens externes assainis — **borne un risque**
- AUD-SEC-26 — Info — Flux OIDC Authorization Code + PKCE de l'IHM et du desktop conforme (ADR-0033) : PKCE S256, state vérifié en temps constant, iss contrôlé, jetons en mémoire, redirections internes filtrées — **borne un risque**
- AUD-SEC-27 — Info — Veille réglementaire et inventaire : archives JORF bornées sans XXE ni écriture disque, routes réglementaires et impacts doublés par la RLS, aucune route d'import de SBOM — **borne un risque**
- AUD-SEC-28 — Info — Outillage e2e confiné à la boucle locale : secrets de test seulement, sources simulées sur 127.0.0.1/::1 sans traversée, docker/git/taskkill par chemin absolu — **borne un risque**
- AUD-TST-13 — Info — argos-ui : npm test passe (206/206) et la vérification de types vue-tsc passe
- AUD-TST-14 — Info — argos-desktop : npm test passe (54/54) et check:syntax passe
- AUD-TST-15 — Info — Aucun test désactivé ni focalisé ; un seul test conditionnel, volontaire et documenté
- AUD-TST-16 — Info — Hygiène des tests serveur solide : ports aléatoires, sources externes simulées, attente et délais simulés, courrier simulé
- AUD-TST-17 — Info — Sécurité, MCP, alerting et ingestion disposent de tests ciblés nombreux (MCP 42, comptes 38, alerting 35)
- AUD-TST-18 — Info — ModuleBoundariesTest couvre les 16 modules et 6 règles fines ; il est éligible à surefire (nom *Test, JUnit 5)
- AUD-TST-21 — Info — Campagne d'accessibilité des mises en page rejouée hors du poste du mainteneur : 29/29 en 35 s, sans serveur ni base
- AUD-TST-22 — Info — Nouveaux tests structurants dans le delta : couverture RLS générique, contrats SCM enregistrés, migrations, pont preload et fonctions pures de la campagne de performance
- AUD-PER-14 — Info — Les tâches planifiées sont protégées contre le chevauchement et idempotentes en base — **borne un risque**
- AUD-PER-15 — Info — Les appels IA sont bornés : exécuteurs dédiés à file bornée, délais maximaux, taille de réponse plafonnée, Ollama configuré à un modèle et une requête à la fois — **borne un risque**
- AUD-PER-16 — Info — Clients HTTP sortants : délais de connexion et de requête configurés partout, sans redirection ; caches KEV et CERT-FR avec durée de vie — **borne un risque**
- AUD-PER-17 — Info — Bundle de l'IHM encore léger : 201,6 kB, dont 68,4 kB gzippé (41,9 kB le 17/11)
- AUD-PER-23 — Info — L'empreinte du contenu normalisé évite les versions et les événements superflus : une resynchronisation horaire d'un avis inchangé ne relance ni l'enrichissement, ni la corrélation, ni les alertes — **borne un risque**
- AUD-PER-24 — Info — La collecte JORF est bornée : archive de 50 Mio au plus, décompression en flux plafonnée à 500 Mio et 50 000 entrées, fichier XML de 5 Mio au plus, débit limité à 10 requêtes par minute — **borne un risque**
- AUD-DEP-11 — Info — npm audit argos-desktop : 6 vulnérabilités modérées (minimatch GHSA-g3hv-75wg-m249, déni de service) via electron-builder, outil de build uniquement — **borne un risque**
- AUD-DEP-12 — Info — argos-ui : aucune vulnérabilité connue et dépendances à jour (Vue 3.5.45, Vite 8.3.8, vue-router 5.5.1), lockfiles v3 présents et utilisés par npm ci
- AUD-DEP-13 — Info — Quarkus 3.40.1.2 sur le flux LTS courant (3.40) ; Java 17 compatible jusqu'à la fin des 3.x
- AUD-DEP-16 — Info — Conteneurisation et assemblage Compose solides : multi-étapes, JRE seul, utilisateur non root, mem_limit, restart, healthcheck PostgreSQL, ports locaux par défaut
- AUD-DEP-17 — Info — Configuration par profils correcte : prod sans valeurs par défaut pour la base et l'OIDC, mailer simulé seulement en dev/test, STARTTLS exigé, aucun secret réel versionné — **borne un risque**
- AUD-DEP-18 — Info — Licences des dépendances livrées compatibles avec ADR-0021 (MIT/Apache-2.0/BSD) ; licences restrictives uniquement dans l'outillage de build
- AUD-DEP-23 — Info — Le script de contrôle des licences rejoué sur des inventaires factices donne les bons codes de sortie (1 GPL ou licence absente, 2 BOM manquant, 0 conforme)
- AUD-DOC-13 — Info — Liens et ancres sains dans tout le dépôt ; le contrôle documentaire passe à 54add78 sans l'option --rendu (avec : 1 erreur, AUD-DOC-06)
- AUD-DOC-14 — Info — Statuts des 33 ADR identiques dans les en-têtes, l'index adr/README.md et arc42 §9.1 ; §9.3 reporte les questions tranchées par ADR-0031 et ADR-0032
- AUD-DOC-15 — Info — Schéma et tâches planifiées documentés à jour : migrations V1 à V20 listées par arc42 §8.7 et D-13, treize tâches @Scheduled décrites
- AUD-DOC-16 — Info — e2e/README.md et argos-server/README.md exacts par rapport au code
- AUD-DOC-17 — Info — Contrats cités cohérents : routes REST, outils MCP, chiffres de performance et inventaire OpenSpec
- AUD-DOC-18 — Info — Traçabilité OpenSpec transparente : les tâches non cochées des changements actifs portent leur motif et une décision datée
