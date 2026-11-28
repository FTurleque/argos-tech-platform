# Ordre de correction — mise à jour du 2026-11-21

Le plan d'action ([plan-action.md](plan-action.md)) classe par urgence ; ce document classe par **ordre d'exécution**, qui prime en cas de divergence. Seuls les constats qui restent à traiter sont affectés à un sprint ; les constats Documentation forment le dernier sprint.

## Vue d'ensemble

| Sprint | Titre | À traiter | dont partiels | Élevée | Moyenne | Faible | Charge (j) |
|---|---|---|---|---|---|---|---|
| 1 | Remettre les gates en marche | 10 | 1 | 1 | 0 | 9 | 3,25 à 12 |
| 2 | Sauvegarde, restauration et base | 6 | 0 | 2 | 2 | 2 | 2,25 à 8 |
| 3 | Déploiement et conteneurs | 7 | 0 | 1 | 1 | 5 | 2,5 à 9 |
| 4 | Observabilité et frontières d'erreur | 6 | 0 | 1 | 1 | 4 | 3 à 10 |
| 5 | Décisions écrites et noyau commun | 7 | 2 | 0 | 5 | 2 | 4,75 à 15 |
| 6 | Portail déployable et autorisations | 10 | 1 | 1 | 5 | 4 | 3,25 à 12 |
| 7 | Alimenter l'inventaire et compléter l'IHM | 9 | 5 | 1 | 2 | 6 | 6 à 19 |
| 8 | Ingestion asynchrone | 8 | 1 | 1 | 1 | 6 | 4,25 à 14 |
| 9 | Rétention, import et pagination | 7 | 0 | 0 | 3 | 4 | 2,5 à 9 |
| 10 | Requêtes chaudes | 7 | 1 | 0 | 3 | 4 | 6,25 à 19 |
| 11 | Structure et distribution | 9 | 3 | 0 | 1 | 8 | 8,75 à 26 |
| 12 | Documentation alignée sur le code livré | 13 | 2 | 0 | 5 | 8 | 4,75 à 17 |

Charge totale : **51,5 à 170 jours** — enveloppe (S 0,25 à 1 j, M 1 à 3 j, L 3 à 8 j) hors revue, allers-retours de CI et arbitrages écrits. Un constat partiel garde l'effort estimé au 17/11 : le reste à faire est souvent plus court.

## Chemin critique

- **AUD-TST-01** (M, sprint 1) — Débloque le seuil de couverture, le build sur JDK 17, la garde des migrations, les tests du script de licences, les campagnes E2E et le contrôle documentaire en CI — et aurait arrêté la régression d'AUD-QUA-20.
- **AUD-QUA-02** (S, sprint 2) — Effort S : une ligne de pipefail, prérequis logique de la sauvegarde de la base Keycloak (AUD-DEP-24) et de la restauration rejouée (AUD-DEP-03).
- **AUD-ARC-14** (S, sprint 5) — Effort S : l'ADR du noyau commun et les statuts d'ADR débloquent l'extraction du noyau (ARC-05), l'unification RLS et JDBC, arc42 (DOC-02) et la réécriture des ADR (DOC-01, DOC-08).
- **AUD-DEP-21** (S, sprint 6) — Débloque la CSP de l'IHM servie (SEC-05) et les procédures d'installation du portail et du desktop (DOC-03, DOC-04).

## Sprint 1 — Remettre les gates en marche

**Pourquoi ici.** Aucun test n'est exécuté automatiquement, l'image est construite avec -DskipTests et 313 commits (253 hors fusions) ont été intégrés depuis le 17/11 sans gate : aucune correction des sprints suivants n'est vérifiable. Ce sprint ne touche presque pas au code applicatif.

**Critère de sortie.** Chaque push lance, sur JDK 17, mvn -B clean verify (654 tests à b142695, environ 11 min), npm test et vue-tsc pour l'IHM, npm test, check:syntax et une vérification de types pour le desktop, la vérification de types de e2e/, le contrôle documentaire et npm audit ; les tests du script de licences tournent quand pom.xml change ; JaCoCo est publié avec un seuil qui fait échouer le build (référence : 85,8 % des lignes, 72,0 % des branches) ; modifier une migration fusionnée fait échouer la CI ; un paquet io.argos non déclaré fait échouer ModuleBoundariesTest et les 7 dépendances autorisées inutilisées sont retirées de la table ou motivées ; mesurer-p95.mjs lance Maven par chemin absolu, sans shell: true ; Renovate ou Dependabot ouvre les mises à jour des dépendances et des tags d'images.

**Charge.** 3,25 à 12 jours. Déjà engagés (partiels) : AUD-DEP-20.

**Ordre interne.** AUD-TST-01 → AUD-TST-19 → AUD-DEP-20 → AUD-DEP-22 → AUD-TST-02 → AUD-ARC-07 → AUD-DEP-05 → AUD-QUA-19 → AUD-TST-26 → AUD-SEC-24.
Dépendances internes : AUD-TST-19 après AUD-TST-01 ; AUD-DEP-20 après AUD-TST-01 ; AUD-DEP-22 après AUD-TST-01 ; AUD-TST-02 après AUD-TST-01.

**Prérequis des sprints antérieurs.** aucun.

| ID | Statut | Sévérité | Axes | Effort | Titre |
|---|---|---|---|---|---|
| AUD-TST-01 | Ouvert | Élevée | Tests, Dépendances | M | Aucune intégration continue : aucun test (mvn verify, npm test, ModuleBoundariesTest) n'est exécuté automatiquement |
| AUD-TST-19 | Ouvert | Faible | Tests, Dépendances | S | Build incrémental non fiable et tests exécutés sur un JDK différent de la production (JDK 24 local, JRE 17 en image) |
| AUD-DEP-20 | Partiel | Faible | Dépendances, Qualité | S | Migration V13 modifiée sur place avant fusion, sans garde contre la réécriture d'une migration déjà fusionnée |
| AUD-DEP-22 | Ouvert | Faible | Dépendances | S | Le script de contrôle des licences (440 lignes, refactorisé deux fois dans le delta) n'a aucun test automatisé et ne s'exécute qu'au build de l'image, hors de « mvn verify » |
| AUD-TST-02 | Ouvert | Faible | Tests | S | Pas de seuil de couverture : JaCoCo absent du pom ; la couverture (85,8 % des lignes, 72,0 % des branches à b142695, scm à 51,8 % / 41,3 %) n'est mesurée qu'à la main |
| AUD-ARC-07 | Ouvert | Faible | Architecture, Tests | S | La table ALLOWED autorise 7 dépendances sur 57 que le code n'utilise pas et ne contraint pas un paquet io.argos non déclaré |
| AUD-DEP-05 | Ouvert | Faible | Dépendances | S | Aucune veille automatisée des dépendances (Renovate/Dependabot, CVE Maven) ; Electron 45.3.2 hors de la mineure supportée (45.4.1 publiée), Playwright 1.61.1 (1.65.1 publiée) |
| AUD-QUA-19 | Ouvert | Faible | Qualité | S | Les 24 fichiers TypeScript de e2e/ (3 500 lignes) ne sont soumis à aucune vérification de types : pas de tsconfig ni de dépendance typescript ; un tsc --strict révèle 6 erreurs |
| AUD-TST-26 | Ouvert | Faible | Tests | S | argos-desktop sans vérification de types : check:syntax ne contrôle que la syntaxe ; un tsc --checkJs non strict relève 8 erreurs, dont 2 annotations JSDoc invalides sur les dépendances des canaux IPC |
| AUD-SEC-24 | Ouvert | Faible | Sécurité | S | Campagne de performance e2e : serveur lancé par « mvn.cmd » via le PATH avec shell: true sous Windows, contrairement à la règle des chemins absolus |

## Sprint 2 — Sauvegarde, restauration et base

**Pourquoi ici.** Le NAS est la seule cible réelle : la sauvegarde échoue en silence, n'emporte pas la base keycloak dont dépendent les comptes, et Keycloak partage le compte superutilisateur d'Argos. Ce lot touche le script de sauvegarde et les rôles PostgreSQL, rien d'autre.

**Critère de sortie.** Un pg_dump en échec fait sortir le script en erreur sans rotation ; les bases argos et keycloak sont sauvegardées en 0600 ; une restauration des deux bases a été rejouée sur une instance vierge et un utilisateur s'y reconnecte avec ses espaces ; Argos et Keycloak se connectent avec des rôles distincts, non superutilisateurs, propriétaires de leur seule base ; aucun mot de passe par défaut ne subsiste dans compose.yaml.

**Charge.** 2,25 à 8 jours.

**Ordre interne.** AUD-QUA-02 → AUD-SEC-07 → AUD-DEP-24 → AUD-SEC-30 → AUD-DEP-03 → AUD-SEC-06.
Dépendances internes : AUD-DEP-03 après AUD-QUA-02 ; AUD-DEP-03 après AUD-DEP-24 ; AUD-DEP-03 après AUD-SEC-30.

**Prérequis des sprints antérieurs.** aucun.

| ID | Statut | Sévérité | Axes | Effort | Titre |
|---|---|---|---|---|---|
| AUD-QUA-02 | Ouvert | Élevée | Qualité, Dépendances | S | Le script de sauvegarde ne détecte pas l'échec de pg_dump (pas de pipefail) et sa rotation peut supprimer les bonnes sauvegardes |
| AUD-SEC-07 | Ouvert | Faible | Sécurité | S | Sauvegardes complètes de la base écrites en clair avec les permissions par défaut |
| AUD-DEP-24 | Ouvert | Élevée | Dépendances, Sécurité, Qualité | S | La base keycloak ajoutée dans le même PostgreSQL n'est ni sauvegardée ni restaurée : après une reprise, le royaume réimporté émet de nouveaux sujets OIDC et les comptes Argos (sujet_oidc UNIQUE) deviennent orphelins |
| AUD-SEC-30 | Ouvert | Moyenne | Sécurité, Dépendances | M | Keycloak et Argos partagent le même compte PostgreSQL, superutilisateur de l'instance : la compromission de l'un donne la base de l'autre, clés de signature du royaume comprises, et la RLS d'Argos ne tient qu'au SET LOCAL ROLE |
| AUD-DEP-03 | Ouvert | Moyenne | Dépendances | S | Procédure de restauration documentée non testée et non idempotente : dump texte sans --clean rejoué par psql sans ON_ERROR_STOP sur une base déjà migrée |
| AUD-SEC-06 | Ouvert | Faible | Sécurité, Dépendances | S | Mot de passe de base de données par défaut « argos » si .env absent |

## Sprint 3 — Déploiement et conteneurs

**Pourquoi ici.** compose.yaml ne transmet pas 28 des 37 variables lues en production, le profil idp-local n'a pas de garde sur ses variables obligatoires, et les conteneurs n'ont ni healthcheck ni durcissement. Le rebinding DNS, à fenêtre étroite, est corrigé ici, avant l'ouverture au public.

**Critère de sortie.** Un test compare les ${ARGOS_*} lus par application.properties à compose.yaml et .env.example et échoue sur tout écart ; le profil idp-local refuse de démarrer si une variable obligatoire manque ; argos, ollama et keycloak ont un healthcheck et une rotation des journaux, la JVM s'arrête sur OutOfMemoryError ; un profil NAS documenté laisse au moins 3 Go au DSM avec les profils ia et idp-local actifs, et les pools (quarkus.datasource.jdbc.max-size, KC_DB_POOL_MAX_SIZE) restent sous le max_connections réglé de PostgreSQL ; quarkus-app appartient à root, le service tourne en read_only avec cap_drop: [ALL] et no-new-privileges ; un test avec un résolveur DNS qui change de réponse prouve que la veille web se connecte à l'adresse validée.

**Charge.** 2,5 à 9 jours.

**Ordre interne.** AUD-DEP-01 → AUD-DEP-27 → AUD-DEP-08 → AUD-PER-12 → AUD-PER-28 → AUD-SEC-25 → AUD-SEC-01.
Dépendances internes : AUD-DEP-27 après AUD-DEP-01.

**Prérequis des sprints antérieurs.** aucun.

| ID | Statut | Sévérité | Axes | Effort | Titre |
|---|---|---|---|---|---|
| AUD-DEP-01 | Ouvert | Élevée | Dépendances | S | compose.yaml ne transmet pas au conteneur 28 des 37 variables lues en production (SMTP, ARGOS_PUBLIC_URL, jetons de forge, audience OIDC, quotas IA, planifications) et 27 manquent dans .env.example : e-mails vers localhost:587, liens vers localhost:5173 |
| AUD-DEP-27 | Ouvert | Faible | Dépendances | S | Profil idp-local sans garde sur ses variables obligatoires : mots de passe initial et d'administration Keycloak vides par défaut et seulement commentés dans .env.example, 3 variables Keycloak de compose.yaml absentes de .env.example, import du royaume exécuté une seule fois |
| AUD-DEP-08 | Ouvert | Faible | Dépendances | S | Conteneurs argos et ollama sans healthcheck, pas de HEALTHCHECK dans le Dockerfile ni de rotation des journaux Docker |
| AUD-PER-12 | Ouvert | Faible | Performance | S | Réglages JVM et pool : heap à 75 % d'une limite de 1 Gio, sans ExitOnOutOfMemoryError, et pool de connexions laissé aux valeurs par défaut |
| AUD-PER-28 | Ouvert | Faible | Performance | S | Profil idp-local : Keycloak ajoute un conteneur de 1 Gio et partage le PostgreSQL plafonné à 512 Mio, non réglé (max_connections par défaut). La somme des plafonds atteint 5,5 Gio avec le profil ia (2,5 Gio sans) sur un DS1522+ livré avec 8 Go |
| AUD-SEC-25 | Ouvert | Faible | Sécurité | S | Image : code de l'application modifiable par l'utilisateur d'exécution et conteneurs sans durcissement (read_only, cap_drop, no-new-privileges) |
| AUD-SEC-01 | Ouvert | Moyenne | Sécurité | M | Veille web : contrôle SSRF contournable par rebinding DNS (résolution vérifiée distincte de la résolution de connexion) |

## Sprint 4 — Observabilité et frontières d'erreur

**Pourquoi ici.** Le lot 23 a resserré les captures d'exception sans tests : une erreur imprévue bloque désormais des collectes entières et le MCP renvoie des erreurs de protocole. Les sprints 8 à 10 déplacent la chaîne d'ingestion : sans métriques ni journaux structurés, une régression ne se verrait pas.

**Critère de sortie.** Chaque boucle par source, par projet et par espace isole les erreurs imprévues : PUT /standards avec la clé pkg:maven/%zz/x répond 400, un avis et une SBOM portant ce PURL n'arrêtent que la source ou le projet fautif, vérifié par un test par frontière ; la garde MCP journalise et répond INDISPONIBLE pour toute erreur ; les journaux d'erreur passent la cause, pile comprise ; le serveur expose /q/metrics et des journaux JSON avec un contrôle de santé par tâche planifiée ; une recherche de (Instant|LocalDate|LocalDateTime|OffsetDateTime|ZonedDateTime).now( dans src/main ne trouve que le producteur du Clock ; aucun Thread.sleep ni attente sur l'horloge réelle dans src/test ; les exécuteurs sont gérés par CDI.

**Charge.** 3 à 10 jours.

**Ordre interne.** AUD-DEP-07 → AUD-QUA-20 → AUD-QUA-23 → AUD-QUA-10 → AUD-TST-06 → AUD-QUA-11.
Dépendances internes : AUD-TST-06 après AUD-QUA-10.

**Prérequis des sprints antérieurs.** aucun.

| ID | Statut | Sévérité | Axes | Effort | Titre |
|---|---|---|---|---|---|
| AUD-DEP-07 | Ouvert | Moyenne | Dépendances | M | Observabilité minimale : ni journaux structurés, ni métriques, ni traçage, ni contrôle de santé métier |
| AUD-QUA-20 | Ouvert | Élevée | Qualité, Tests, Architecture | M | Le resserrement des captures d'exception du lot 23 laisse remonter les erreurs imprévues : un PURL mal encodé bloque la synchronisation des sources suivantes et l'import nocturne, et la chaîne radar fait échouer PUT /standards après commit |
| AUD-QUA-23 | Ouvert | Faible | Qualité | S | Le résumé IA indisponible est journalisé sans sa pile : MoteurAlertes passe la cause en argument de format, à l'inverse de la règle suivie par le reste du lot superviseur |
| AUD-QUA-10 | Ouvert | Faible | Qualité | S | Horloge injectable seulement dans CollecteWeb et LimiteurDebit : 63 appels à now() côté Java (37 fichiers) sans Clock commun, mêlés à 47 now() SQL, et deux LocalDate.now() sans fuseau |
| AUD-TST-06 | Ouvert | Faible | Tests | S | Assertions dépendantes de l'horloge réelle et de délais fixes (sleep 5 s simulé, sleep 1 s, attente active 5 s, marge de 900 ms sur le délai IA) |
| AUD-QUA-11 | Ouvert | Faible | Qualité | S | Des exécuteurs de threads statiques, créés hors du cycle de vie CDI, ne sont jamais arrêtés |

## Sprint 5 — Décisions écrites et noyau commun

**Pourquoi ici.** Les mécanismes partagés restent recopiés (4 contextes RLS, 6 transactions manuelles dont une qui ne restaure pas l'autocommit, 272 prepareStatement) et 14 ADR appliqués sont encore « Proposé ». Les décisions doivent être écrites avant de migrer les consommateurs vers un mécanisme unique.

**Critère de sortie.** Les ADR appliqués ont le statut Accepté ; un module transverse unique porte le contexte RLS, le modèle transactionnel et l'utilitaire JDBC, et aucune autre classe ne pose SET LOCAL ROLE ni setAutoCommit ; la propriété de chaque table est documentée et les lectures croisées sont vérifiées par un test. Le niveau d'impact circule sous l'énumération MatchRules.Level de bout en bout, les statuts d'import et de corrélation sont des énumérations sans branche default silencieuse, et la clé « fréquence:canal » n'est plus découpée par split.

**Charge.** 4,75 à 15 jours. Déjà engagés (partiels) : AUD-ARC-14, AUD-QUA-08.

**Ordre interne.** AUD-ARC-14 → AUD-ARC-05 → AUD-ARC-04 → AUD-QUA-06 → AUD-ARC-06 → AUD-QUA-04 → AUD-QUA-08.
Dépendances internes : AUD-ARC-05 après AUD-ARC-14 ; AUD-ARC-04 après AUD-ARC-05 ; AUD-QUA-06 après AUD-ARC-05.

**Prérequis des sprints antérieurs.** aucun.

| ID | Statut | Sévérité | Axes | Effort | Titre |
|---|---|---|---|---|---|
| AUD-ARC-14 | Partiel | Faible | Architecture, Documentation | S | Le socle d'architecture repose sur des ADR fondateurs restés « Proposé » (14 sur 33, dont 0001, 0013, 0014, 0016) alors que le code les applique |
| AUD-ARC-05 | Ouvert | Moyenne | Architecture | M | Un noyau partagé implicite (RLS, erreurs du domaine, appelant) est logé dans workspace et api.security, d'où cinq règles ArchUnit d'exception au cas par cas |
| AUD-ARC-04 | Ouvert | Moyenne | Architecture, Qualité | S | Le protocole RLS « mécanisme unique » est réimplémenté quatre fois, avec des variantes divergentes |
| AUD-QUA-06 | Ouvert | Moyenne | Qualité | M | Le code JDBC brut est répété sans utilitaire commun : 272 prepareStatement (54 fichiers), 130 catch SQLException, 11 copies du même convertisseur de date et 7 de la troncature |
| AUD-ARC-06 | Ouvert | Moyenne | Architecture, Qualité | M | Couplage par le schéma SQL : 13 tables lues par plusieurs modules, dont trois lectures qu'aucune dépendance autorisée ne couvre (impact → compte, catalog → rôles d'appartenance, alerting → préférences) |
| AUD-QUA-04 | Ouvert | Moyenne | Qualité | M | Deux modèles transactionnels coexistent (90 @Transactional et 23 QuarkusTransaction, contre six transactions manuelles setAutoCommit/commit recopiées) |
| AUD-QUA-08 | Partiel | Faible | Qualité | S | Le typage entre modules repose encore sur des chaînes : niveau d'impact en String alors que l'énumération existe, clé composite découpée par split(":"), statuts d'import et de corrélation en chaînes avec défaut silencieux |

## Sprint 6 — Portail déployable et autorisations

**Pourquoi ici.** L'IHM servie par l'image se charge désormais, mais la connexion n'aboutit que sur le poste hôte, au port 8088 : émetteur figé au build, redirections du royaume limitées à localhost:8088, royaume sans protection contre la force brute, IHM qui détient des jetons sans CSP. Les autorisations n'ont toujours pas de test matriciel.

**Critère de sortie.** Avec les valeurs par défaut, un utilisateur se connecte au portail servi par l'image depuis l'hôte ; en réglant seulement ARGOS_BIND, ARGOS_PUBLIC_URL et l'émetteur selon le guide, il se connecte depuis un autre poste (aucune occurrence de localhost:8180 dans le bundle, contrôlé par un test) ; un test vérifie 200 sur / et /assets/* sans jeton et 401 sur /api/v1/* ; CSP (nommant l'émetteur configuré), frame-ancestors et nosniff sont posés sur l'IHM servie et testés ; le royaume livré a une politique de mot de passe, la protection contre la force brute et sslRequired external ou all avant toute exposition hors 127.0.0.1 ; un test parcourt chaque point d'entrée REST pour chaque rôle, dont PUT …/demande-evolution refusé sans requête vers la forge ; l'écoute de bouclage du desktop ne se ferme que sur le state attendu ; recherche et /me/veille ont une limite de débit ; les alertes ne partent que vers une adresse vérifiée.

**Charge.** 3,25 à 12 jours. Déjà engagés (partiels) : AUD-DEP-21.

**Ordre interne.** AUD-DEP-21 → AUD-DEP-25 → AUD-SEC-05 → AUD-SEC-29 → AUD-TST-25 → AUD-TST-05 → AUD-SEC-22 → AUD-SEC-04 → AUD-SEC-09 → AUD-SEC-23.
Dépendances internes : AUD-SEC-05 après AUD-DEP-21 ; AUD-SEC-29 après AUD-DEP-25.

**Prérequis des sprints antérieurs.** aucun.

| ID | Statut | Sévérité | Axes | Effort | Titre |
|---|---|---|---|---|---|
| AUD-DEP-21 | Partiel | Élevée | Dépendances, Architecture, Documentation | S | L'image Docker fige l'émetteur OIDC de l'IHM sur http://localhost:8180/realms/argos (aucun ARG au build) : connexion web impossible hors du poste ; le desktop est désormais configurable à l'emballage (apiUrl, oidcUrl) mais sans documentation de l'émetteur ni test |
| AUD-DEP-25 | Ouvert | Moyenne | Dépendances, Architecture, Documentation | S | Le royaume Keycloak importé n'autorise que http://localhost:8088 en redirection et en origine, alors qu'Argos est publié par défaut sur le port 8080 : connexion refusée avec la configuration par défaut |
| AUD-SEC-05 | Ouvert | Moyenne | Sécurité | S | Aucun en-tête de sécurité HTTP sur l'IHM et l'API servies par Quarkus |
| AUD-SEC-29 | Ouvert | Moyenne | Sécurité | S | Royaume Keycloak livré pour l'installation (PR #28) sans protection contre la force brute ni politique de mot de passe, avec sslRequired « none » et des redirections à joker en http |
| AUD-TST-25 | Ouvert | Faible | Tests | S | PR #28 : quatre chemins publics ajoutés à la politique d'accès HTTP (/, /index.html, /assets/*, /licences-polices.txt) sans aucun test, ni de l'ouverture ni du maintien du 401 sur l'API |
| AUD-TST-05 | Ouvert | Moyenne | Tests | M | Autorisations par appels impératifs (80 appels access.*() pour 96 opérations) sans test matriciel point d'entrée × rôle ; POST /ops/sources/{id}/synchronizations jamais testé |
| AUD-SEC-22 | Ouvert | Moyenne | Sécurité | S | Demande d'évolution : tout compte peut faire interroger GitHub/GitLab avec les jetons du serveur avant tout contrôle d'expertise (oracle d'existence sur dépôts privés, 10 appels/min/compte) |
| AUD-SEC-04 | Ouvert | Faible | Sécurité | S | Alertes e-mail envoyées à l'adresse du jeton même non vérifiée |
| AUD-SEC-09 | Ouvert | Faible | Sécurité | S | Recherche plein texte et veille personnelle (/me/veille) sans limite de débit |
| AUD-SEC-23 | Ouvert | Faible | Sécurité | S | Desktop : l'écoute de bouclage 127.0.0.1 accepte le premier GET /argos/retour sans comparer le state et se ferme (interruption de connexion par un processus local) |

## Sprint 7 — Alimenter l'inventaire et compléter l'IHM

**Pourquoi ici.** Aucun point d'entrée ne crée de projet ni ne rattache de dépôt, des fonctions ne sont atteintes que par les tests, le profil artiste n'a pas d'IHM et les liens des alertes visent des routes que l'IHM n'a pas.

**Critère de sortie.** Un utilisateur crée un projet, lui rattache un dépôt et importe une SBOM par l'API et l'IHM ; les vues artiste existent ; chaque lien produit par les alertes ouvre une vue existante de l'IHM (test) ; aucune méthode publique de src/main n'est atteinte seulement par les tests ; appelerApi, les vues et main.js ont des tests ; le client de l'IHM est généré depuis /q/openapi ou vérifié par un test de contrat ; workspace, inventory, vulnintel, releaseradar et audit ont chacun une classe de test dédiée et chaque module, scm compris (51,8 % à b142695), atteint le seuil JaCoCo ; le nettoyage entre tests est dérivé du schéma, sans liste TRUNCATE codée en dur.

**Charge.** 6 à 19 jours. Déjà engagés (partiels) : AUD-QUA-01, AUD-TST-10, AUD-ARC-12, AUD-TST-09, AUD-TST-04.

**Ordre interne.** AUD-QUA-01 → AUD-QUA-17 → AUD-ARC-15 → AUD-ARC-22 → AUD-TST-10 → AUD-ARC-12 → AUD-TST-09 → AUD-TST-04 → AUD-TST-08.

**Prérequis des sprints antérieurs.** AUD-TST-02 (sprint 1), AUD-DEP-01 (sprint 3)

| ID | Statut | Sévérité | Axes | Effort | Titre |
|---|---|---|---|---|---|
| AUD-QUA-01 | Partiel | Élevée | Qualité | M | La création de projet, l'ajout de dépôt et l'import manuel (SBOM, manifestes) n'ont toujours aucun point d'entrée en production ; seul l'import planifié des dépôts déjà configurés est branché |
| AUD-QUA-17 | Ouvert | Moyenne | Qualité | S | Fonctionnalités atteintes seulement par les tests : suggestions IA du radar jamais produites alors que leur décision est exposée en REST, collecte immédiate d'activité, trois méthodes sans appelant |
| AUD-ARC-15 | Ouvert | Faible | Architecture, Qualité | M | Profil artiste livré sans IHM : six points d'entrée et la rubrique des préférences ne sont appelés par aucun client |
| AUD-ARC-22 | Ouvert | Moyenne | Architecture | S | Les liens des alertes (signaux, radar, opportunités) visent /signaux/…, /espaces/…/radar et /profils/artiste/… : routes absentes de l'IHM (historique hash) et chemins non publics (401) ; aucune vue n'est atteignable depuis un courriel |
| AUD-TST-10 | Partiel | Faible | Tests | S | Desktop : main.js (256 l.), qui porte la configuration de sécurité Electron et désormais les réglages d'emballage (PR #28), n'est pas testé ; preload.js l'est |
| AUD-ARC-12 | Partiel | Faible | Architecture, Qualité | M | Le contrat client de l'IHM est écrit à la main et partiel (10 classes REST sur 23 sans appel), sans génération ni test de contrat ; le point d'appel HTTP est unique |
| AUD-TST-09 | Partiel | Faible | Tests | M | IHM : la logique et 9 composants sur 21 (rendu SSR) sont testés ; ni le client API effectif (appelerApi) ni les 10 vues |
| AUD-TST-04 | Partiel | Faible | Tests | M | Couverture inégale : cinq modules sans classe de test dédiée, dont workspace (26 fichiers, 1 761 lignes) et inventory (11 fichiers, 1 865 lignes) ; scm doté de tests mais module le moins couvert (51,8 % des lignes) |
| AUD-TST-08 | Ouvert | Faible | Tests | S | Isolation entre tests par liste TRUNCATE codée en dur ; six tables jamais vidées |

## Sprint 8 — Ingestion asynchrone

**Pourquoi ici.** La collecte est planifiée, mais le lancement manuel reste synchrone, deux fils peuvent synchroniser la même source pendant une attente de reprise, et l'enrichissement tourne dans le fil d'ingestion sans rattrapage.

**Critère de sortie.** POST /ops/sources/{id}/synchronizations répond 202 et réutilise la tâche de fond ; un run en attente de reprise reste exclusif (test de concurrence) ; l'amorçage OSV passe par l'archive de l'écosystème ; retryMissing est planifiée ; aucun appel à EnrichmentSource.lookup ni AdvisoryFeed.fetch dans une transaction (test ArchUnit) ; tous les clients HTTP partagent une configuration et un plafond de taille ; AlerteEmise est observée ou supprimée. La chaîne StandardModifie → RadarProjets → AnnonceStandard → RappelsRadar ne s'exécute plus dans la requête PUT /standards (observateur transactionnel ou asynchrone, testé) ; la collecte d'activité lit les échéances en une requête et borne le parallélisme des appels de forge.

**Charge.** 4,25 à 14 jours. Déjà engagés (partiels) : AUD-PER-01.

**Ordre interne.** AUD-PER-02 → AUD-PER-01 → AUD-PER-27 → AUD-ARC-09 → AUD-ARC-19 → AUD-ARC-10 → AUD-PER-11 → AUD-PER-13.

**Prérequis des sprints antérieurs.** aucun.

| ID | Statut | Sévérité | Axes | Effort | Titre |
|---|---|---|---|---|---|
| AUD-PER-02 | Ouvert | Élevée | Performance, Qualité | M | L'enrichissement NVD/EPSS et la corrélation sont exécutés de façon synchrone pour chaque signal créé ou modifié, dans le fil de l'ingestion |
| AUD-PER-01 | Partiel | Moyenne | Performance | M | La synchronisation est planifiée, mais le lancement manuel reste synchrone dans la requête HTTP et OSV est toujours lu avis par avis, reprises comprises sur le fil qui synchronise |
| AUD-PER-27 | Ouvert | Faible | Performance | S | Pendant l'attente de reprise (jusqu'à PT5M, 5 tentatives), le run de synchronisation passe RETRYABLE et sort de l'exclusion sync_run_one_running : un lancement manuel ou l'échéance horaire le reprend pendant que le premier fil dort, puis les deux fils synchronisent la même source sur le même run |
| AUD-ARC-09 | Ouvert | Faible | Architecture | S | Deux styles de propagation d'événements CDI coexistent ; la chaîne radar s'exécute dans la requête HTTP |
| AUD-ARC-19 | Ouvert | Faible | Architecture | S | L'événement AlerteEmise est publié en cinq points sans aucun observateur |
| AUD-ARC-10 | Ouvert | Faible | Architecture, Qualité | M | La collecte HTTP est dispersée dans sept clients répartis sur cinq modules |
| AUD-PER-11 | Ouvert | Faible | Performance | S | Réponses des sources d'avis et d'enrichissement lues en mémoire sans plafond de taille |
| AUD-PER-13 | Ouvert | Faible | Performance | S | Collecte d'activité des dépôts : lecture de l'échéance projet par projet et appels de forge séquentiels |

## Sprint 9 — Rétention, import et pagination

**Pourquoi ici.** Les tables de journal et d'échec grossissent sans fin, l'import nocturne réécrit et recorrèle tout et la corrélation réécrit chaque impact chaque nuit, ce qui fausse aussi l'ordre « récents ». Le mécanisme de rétention vient en premier.

**Critère de sortie.** Chaque table de journal, d'échec ou d'historique, opportunités comprises, a une purge planifiée avec une rétention configurée ; l'import nocturne ne réécrit ni ne recorrèle un inventaire inchangé et insère par lot ; la corrélation ne modifie updated_at que si l'impact change ; les index manquants sont créés ; toutes les listes renvoyées par l'API sont paginées.

**Charge.** 2,5 à 9 jours.

**Ordre interne.** AUD-SEC-03 → AUD-PER-18 → AUD-PER-19 → AUD-PER-20 → AUD-PER-25 → AUD-PER-09 → AUD-PER-10.
Dépendances internes : AUD-PER-18 après AUD-SEC-03 ; AUD-PER-20 après AUD-PER-19.

**Prérequis des sprints antérieurs.** aucun.

| ID | Statut | Sévérité | Axes | Effort | Titre |
|---|---|---|---|---|---|
| AUD-SEC-03 | Ouvert | Moyenne | Sécurité, Performance | S | Journaux d'audit sans rétention ni bornage : un compte du fournisseur d'identité non invité écrit une ligne par requête |
| AUD-PER-18 | Ouvert | Moyenne | Performance, Qualité | S | Opportunités sans date limite (toutes celles des flux RSS) restituées indéfiniment : ni LIMIT, ni pagination, ni purge |
| AUD-PER-19 | Ouvert | Faible | Performance | M | L'import quotidien réécrit et recorrèle chaque nuit tout l'inventaire de chaque projet à dépôt, même si la SBOM ou les manifestes n'ont pas changé |
| AUD-PER-20 | Ouvert | Faible | Performance | S | Régression de l'import d'inventaire : les composants sont insérés un par un (un aller-retour SQL par composant), alors qu'ils l'étaient par lot à l'audit initial |
| AUD-PER-25 | Ouvert | Moyenne | Performance | S | La corrélation réécrit chaque impact à chaque passage (updated_at = now() sans condition) : chaque nuit, mises à jour non HOT dans les 8 index de project_impact, et ordre « récents » de Ma veille faussé |
| AUD-PER-09 | Ouvert | Faible | Performance | S | Index manquants : clés étrangères component_id (suppressions en cascade) et colonnes de filtre des listes d'exploitation |
| AUD-PER-10 | Ouvert | Faible | Performance | S | Listes renvoyées en entier sans pagination : listes d'exploitation (sources rejetées, événements, échecs d'import) et impacts d'un projet (GET /projects/{id}/impacts) |

## Sprint 10 — Requêtes chaudes

**Pourquoi ici.** Corrélation, alertes et radar font des requêtes en N+1 ; « Ma veille » est dans la cible mesurée mais sans abonnements dans le jeu de mesure, et l'index V21 ne porte pas l'espace.

**Critère de sortie.** Corrélation, alertes et radar s'exécutent en un nombre de requêtes indépendant du nombre de signaux ou de membres ; la campagne de performance inclut des abonnements et plusieurs espaces de tailles différentes, et GET /me/veille reste sous 500 ms au p95, y compris pour un appelant de moins de 200 impacts actifs dans une table project_impact d'au moins 500 000 lignes majoritairement résolues ; la recherche ne matérialise que la page demandée ; aucun index redondant sur project_impact.

**Charge.** 6,25 à 19 jours. Déjà engagés (partiels) : AUD-PER-06.

**Ordre interne.** AUD-PER-04 → AUD-PER-03 → AUD-PER-06 → AUD-PER-26 → AUD-PER-29 → AUD-PER-05 → AUD-PER-07.

**Prérequis des sprints antérieurs.** AUD-PER-09 (sprint 9)

| ID | Statut | Sévérité | Axes | Effort | Titre |
|---|---|---|---|---|---|
| AUD-PER-04 | Ouvert | Moyenne | Performance | M | Évaluation des alertes en N+1 par destinataire : 3 transactions et au moins 3 requêtes par membre, plus une requête par canal, synchrones dans le fil de corrélation |
| AUD-PER-03 | Ouvert | Moyenne | Performance | M | Corrélation par projet en N+1 aggravé : une transaction et une relecture du signal par signal, puis 3 à 5 instructions SQL par composant correspondant (rattachement, état précédent, upsert, transitions) |
| AUD-PER-06 | Partiel | Moyenne | Performance | M | « Ma veille » dans la cible sur le jeu K3 (p95 85 ms mesuré, contre 533,8 ms), mais la recherche des abonnements porte encore sur des expressions non indexées, n'est pas mesurée, et la réponse n'est pas paginée |
| AUD-PER-26 | Ouvert | Faible | Performance | M | L'index V21 (updated_at DESC, id) ne porte ni l'espace ni l'état : pour un appelant qui a peu d'impacts actifs, « Ma veille » parcourt les impacts de tous les espaces, résolus compris (22,9 ms mesurés pour 40 000 lignes écartées, coût croissant avec la table) |
| AUD-PER-29 | Ouvert | Faible | Performance | S | project_impact porte 8 index, dont 2 redondants : project_impact_signal (signal_id) et project_impact_project (project_id) sont couverts par project_impact_signal_etat et project_impact_projet_etat, ajoutés par V20 sans retirer les anciens |
| AUD-PER-05 | Ouvert | Faible | Performance | M | Le radar charge, pour chaque espace, les composants de tous les espaces puis filtre en Java, et lit le calendrier composant par composant |
| AUD-PER-07 | Ouvert | Faible | Performance | M | Recherche plein texte : bonne conception (tsvector généré + GIN), mais toute la sélection est matérialisée, classée et comptée à chaque page |

## Sprint 11 — Structure et distribution

**Pourquoi ici.** Les chantiers de fond (pyramide de tests, campagnes E2E contre l'image, taille des méthodes, distribution du desktop) ne valent qu'une fois la structure stabilisée par les sprints 5 à 10.

**Critère de sortie.** La majorité des tests serveur s'exécutent sans Docker ; les campagnes E2E et d'accessibilité (étendue aux écrans livrés depuis le 17/11) tournent en CI contre l'image construite ; un seuil de longueur et de complexité des méthodes est vérifié en CI ; le desktop est signé et se met à jour ; les avis MIT de vue et vue-router sont livrés ou motivés. La recherche plein texte réside dans le module que désigne le package-info ; une langue par concept (espace ou workspace) est fixée et vérifiée par le contrôle de nommage ; au moins un @QuarkusIntegrationTest tourne contre l'artefact empaqueté.

**Charge.** 8,75 à 26 jours. Déjà engagés (partiels) : AUD-QUA-09, AUD-QUA-18, AUD-DEP-19.

**Ordre interne.** AUD-TST-03 → AUD-TST-12 → AUD-TST-20 → AUD-TST-23 → AUD-ARC-11 → AUD-QUA-09 → AUD-QUA-18 → AUD-DEP-15 → AUD-DEP-19.
Dépendances internes : AUD-TST-23 après AUD-TST-20.

**Prérequis des sprints antérieurs.** AUD-TST-01 (sprint 1)

| ID | Statut | Sévérité | Axes | Effort | Titre |
|---|---|---|---|---|---|
| AUD-TST-03 | Ouvert | Moyenne | Tests | L | Pyramide inversée : 71 % des méthodes de test serveur (427 sur 602) sont des @QuarkusTest exigeant Docker (PostgreSQL + Keycloak Dev Services) |
| AUD-TST-12 | Ouvert | Faible | Tests | M | Pas de tests d'intégration sur l'artefact empaqueté (skipITs=true, aucun *IT ni @QuarkusIntegrationTest) |
| AUD-TST-20 | Ouvert | Faible | Tests | M | Campagne E2E principale (34 tests, 14 fichiers) rejouable seulement sur un poste complet, jamais automatisée et sans preuve de passage depuis le 19/11 ; reuseExistingServer: true réutilise sans contrôle un serveur ou un Vite déjà lancés |
| AUD-TST-23 | Ouvert | Faible | Tests, Documentation | M | L'audit d'accessibilité n'a pas suivi les écrans livrés après le 17/11 : vue d'inventaire, actions sur les abonnements et traitement des impacts sans mesure de contraste ni revue |
| AUD-ARC-11 | Ouvert | Faible | Architecture | S | Aucun objet-dieu (max. 557 lignes, InventoryQueries) ; impact (2 960 l.) porte aussi la recherche plein texte que le package-info attribue à signal |
| AUD-QUA-09 | Partiel | Faible | Qualité | S | Le nommage mélange français et anglais sans convention, jusque dans un même concept et un même identifiant |
| AUD-QUA-18 | Partiel | Faible | Qualité | M | La taille du serveur progresse encore (21 fichiers Java de plus de 300 lignes, 19 à 54add78, 9 à 9d96406) ; les méthodes de complexité ≥ 15 reculent à 6 (9) après les découpages du lot23, mais SignalRecorder.record (86 lignes) et PreparationFiches.preparer (74) restent d'un bloc |
| AUD-DEP-15 | Ouvert | Faible | Dépendances | M | Client desktop distribué sans signature de code ni mise à jour automatique |
| AUD-DEP-19 | Partiel | Faible | Dépendances | S | Licences : contrôle automatisé et avis livrés pour le serveur ; IHM livrée (vue, vue-router) sans avis MIT dans l'image ni l'installeur, aucun contrôle npm |

## Sprint 12 — Documentation alignée sur le code livré

**Pourquoi ici.** Les points d'entrée décrivent encore le cadrage de septembre, le registre des spécifications ignore l'archivage du 20/11 et le guide de déploiement ne mène pas à une installation qui fonctionne hors du poste. La documentation passe en dernier pour décrire l'état produit par les sprints 1 à 11 — avec un correctif immédiat du guide recommandé hors sprint (AUD-DOC-03).

**Critère de sortie.** En suivant uniquement le guide de déploiement, on installe Argos et Keycloak sur une machine vierge puis on se connecte au portail et au desktop depuis un autre poste : procédure rejouée et datée ; chaque variable ARGOS_* lue en production figure dans le guide, vérifié par script ; README, docs/architecture/README.md, openspec/config.yaml, arc42 et le registre des spécifications décrivent le code et l'état OpenSpec au HEAD ; un seul registre des risques ; les ADR acceptés sont rédigés en « Décision » ; verifier-docs --rendu passe, couvre les README des sous-projets et tourne en CI ; .archgate/ est supprimé ou documenté.

**Charge.** 4,75 à 17 jours. Déjà engagés (partiels) : AUD-DOC-03, AUD-DOC-04.

**Ordre interne.** AUD-DOC-03 → AUD-DOC-04 → AUD-DOC-01 → AUD-DOC-02 → AUD-DOC-19 → AUD-DOC-05 → AUD-DOC-06 → AUD-DOC-07 → AUD-DOC-08 → AUD-DOC-10 → AUD-DOC-11 → AUD-DOC-20 → AUD-DOC-23.

**Prérequis des sprints antérieurs.** AUD-TST-01 (sprint 1), AUD-DEP-01 (sprint 3), AUD-ARC-05 (sprint 5), AUD-ARC-06 (sprint 5), AUD-ARC-14 (sprint 5), AUD-DEP-21 (sprint 6), AUD-DEP-25 (sprint 6)

| ID | Statut | Sévérité | Axes | Effort | Titre |
|---|---|---|---|---|---|
| AUD-DOC-03 | Partiel | Moyenne | Documentation, Dépendances, Sécurité | M | Le guide de déploiement mène encore à une installation qui ne démarre pas hors du profil idp-local : parcours Synology et parcours PC de base sans étape d'identité, tableau des réglages sans ARGOS_OIDC_URL ni ARGOS_ADMIN_EMAIL, phrase « l'authentification n'est pas en place » maintenue |
| AUD-DOC-04 | Partiel | Moyenne | Documentation | S | Client desktop : le guide documente désormais l'URL du serveur fixée à l'emballage (argos.apiUrl), mais ni l'émetteur OIDC (VITE_ARGOS_OIDC_ISSUER au build, argos.oidcUrl ou ARGOS_OIDC_URL à l'exécution) ni leur obligation de cohérence ; la section « viser le NAS » est inchangée |
| AUD-DOC-01 | Ouvert | Moyenne | Documentation | S | Les points d'entrée (README racine, docs/architecture/README.md, openspec/config.yaml) décrivent encore le cadrage de septembre : socle « sans fonctionnalité métier », Claude derrière un AI Gateway, questions ouvertes déjà tranchées par des ADR acceptés |
| AUD-DOC-02 | Ouvert | Moyenne | Documentation, Architecture | M | arc42 (§1, §2, §3 D-01, §5.2, §7, §10, §11) et les deux registres de risques divergents ne suivent pas les ADR 0025 à 0033 ; §3 et §7 citent encore Claude API et l'IdP d'entreprise |
| AUD-DOC-19 | Ouvert | Moyenne | Documentation | S | Après l'archivage des cinq derniers changements (#29), le registre des spécifications et sept fiches décrivent encore ces capacités comme « proposées », « non archivées », sans spec de référence ; l'inventaire OpenSpec annonce 11 specs, 5 changements actifs et 11 archivés contre 18, 0 et 16 |
| AUD-DOC-05 | Ouvert | Faible | Documentation | S | README racine, démarrage local : Node.js 22.12 annoncé contre ^22.18.0 exigé, Docker présenté comme requis pour les seuls tests alors que quarkus:dev démarre Keycloak, aucune mention de la connexion (royaume de test, comptes), e2e réduit au « portail web » |
| AUD-DOC-06 | Ouvert | Faible | Documentation | S | Le diagramme D-19 (arc42 §6.6, veille réglementaire) ne se rend pas : un « ; » dans un message de séquence casse l'analyse Mermaid, et le contrôle documentaire passe parce que le rendu est facultatif |
| AUD-DOC-07 | Ouvert | Faible | Documentation | S | Documents de suivi incohérents entre eux : statuts de tâches et de décisions, inventaire des diagrammes (D-01 à D-13 contre 19), migrations de D-13 et statuts de capacités ne concordent pas |
| AUD-DOC-08 | Ouvert | Faible | Documentation | S | Neuf ADR acceptés (0023, 0024, 0027 à 0033) gardent une décision rédigée comme une proposition, et ADR-0029 se dit encore subordonné à sa propre acceptation |
| AUD-DOC-10 | Ouvert | Faible | Documentation | S | Le contrôle documentaire laisse passer des chemins périmés : références en code en ligne vers trois changements archivés (ADR-0027, ADR-0028), README d'argos-server et d'e2e hors périmètre, aucune exécution automatique |
| AUD-DOC-11 | Ouvert | Faible | Documentation | S | Reliquat d'outillage Archgate : un second dossier d'ADR avec l'exemple non rempli GEN-001 et une documentation de règles de lint renvoyant à un fichier absent |
| AUD-DOC-20 | Ouvert | Faible | Documentation | S | arc42 §10 (Q-05) et le scénario QS-06 donnent encore GET /api/v1/me/veille hors cible (p95 = 533,8 ms, 15 opérations sur 16) alors que le rapport de campagne du 2026-11-20 mesure 85 ms et 16 opérations sur 16 dans la cible |
| AUD-DOC-23 | Ouvert | Faible | Documentation | S | La PR #28 n'est pas reportée dans l'architecture : D-11 dit Keycloak hors de compose.yaml, D-11 et le guide (« Deux conteneurs démarrent ») ignorent keycloak, le README ne cite ni deploiement/ ni le profil idp-local |

## Hors sprint

Constats Info (ce qui tient), décisions documentées et constats fermés. Ceux marqués **borne un risque** expliquent pourquoi un risque est déjà limité : ne pas les « corriger » par erreur.

- AUD-ARC-02 — Fermé avec réserve — L'IHM et le desktop joignent le jeton OIDC et l'IHM embarquée est servie sans session (chemins statiques publics), sans test qui le prouve
- AUD-DEP-06 — Décision documentée — Images Docker en tags flottants sans digest : node:22-slim, maven:3.9-eclipse-temurin-17, eclipse-temurin:17-jre, postgres:18
- AUD-ARC-01 — Info — Graphe inter-modules acyclique (50 arêtes, plus une référence de javadoc) et intégralement conforme à la table ModuleBoundariesTest
- AUD-ARC-08 — Info — Le module api reste une couche d'exposition mince, sans accès SQL
- AUD-ARC-13 — Info — Les couches de l'IHM et la séparation processus principal/IHM du desktop sont respectées
- AUD-ARC-18 — Info — Les quatre nouveaux sous-paquets et les nouvelles arêtes du lot réglementaire sont couverts par ModuleBoundariesTest, déclarés « table d'abord » — **borne un risque**
- AUD-ARC-24 — Info — L'ouverture de l'IHM servie se limite à quatre chemins statiques ; l'API et /q/openapi restent protégés — **borne un risque**
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
- AUD-SEC-32 — Info — Installation locale Keycloak (PR #28, #30) : aucun mot de passe par défaut pour l'administration Keycloak ni pour le compte initial, inscription libre désactivée, ports publiés sur 127.0.0.1, interface de gestion non publiée, règle publique Quarkus limitée aux fichiers statiques — **borne un risque**
- AUD-TST-13 — Info — argos-ui : npm test passe (206/206) et la vérification de types vue-tsc passe
- AUD-TST-14 — Info — argos-desktop : npm test passe (54/54) et check:syntax passe
- AUD-TST-15 — Info — Aucun test désactivé ni focalisé ; un seul test conditionnel, volontaire et documenté
- AUD-TST-16 — Info — Hygiène des tests serveur solide : ports aléatoires, sources externes simulées, attente et délais simulés, courrier simulé
- AUD-TST-17 — Info — Sécurité, MCP, alerting et ingestion disposent de tests ciblés nombreux (MCP 42, comptes 38, alerting 35)
- AUD-TST-18 — Info — ModuleBoundariesTest couvre les 16 modules et 6 règles fines ; il est éligible à surefire (nom *Test, JUnit 5)
- AUD-TST-21 — Info — Campagne d'accessibilité des mises en page rejouée hors du poste du mainteneur : 29/29 en 35 s, sans serveur ni base
- AUD-TST-22 — Info — Nouveaux tests structurants dans le delta : couverture RLS générique, contrats SCM enregistrés, migrations, pont preload et fonctions pures de la campagne de performance
- AUD-TST-27 — Info — Nouveaux tests du delta : règle ArchUnit de lecture groupée des signaux avec exemple négatif auto-vérifié, comparaison de « Ma veille » à un calcul de référence, contrôle de nommage testé (31/31)
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
- AUD-DEP-28 — Info — Profil Keycloak local bien assemblé : image épinglée par version exacte, healthcheck conforme à la recette officielle (bash /dev/tcp sur le port de gestion 9000), port lié à 127.0.0.1, mémoire bornée, dépendance facultative d'Argos, scripts montés forcés en LF — **borne un risque**
- AUD-DOC-13 — Info — Liens et ancres sains dans tout le dépôt (213 fichiers, 512 liens) ; le contrôle documentaire passe à b142695 sans l'option --rendu (avec : 1 erreur, AUD-DOC-06)
- AUD-DOC-14 — Info — Statuts des 33 ADR identiques dans les en-têtes, l'index adr/README.md et arc42 §9.1 ; §9.3 reporte les questions tranchées par ADR-0031 et ADR-0032
- AUD-DOC-15 — Info — Schéma et tâches planifiées documentés à jour : migrations V1 à V21 listées par arc42 §8.7 et D-13, treize tâches @Scheduled décrites
- AUD-DOC-16 — Info — e2e/README.md et argos-server/README.md exacts par rapport au code
- AUD-DOC-17 — Info — Contrats cités : outils MCP cohérents ; chiffres de performance et inventaire OpenSpec devenus incohérents (AUD-DOC-19, AUD-DOC-20)
- AUD-DOC-18 — Info — Traçabilité OpenSpec transparente : les cinq tâches ouvertes des changements archivés le 2026-11-20 portent une décision datée et une recette R-1 à R-5
