# État des constats au 2026-11-21

Vérification de chaque constat des audits précédents ([17/11](../2026-11-17/README.md), [20/11](../2026-11-20/README.md)) dans le code à `origin/main` à b142695 (PR #30). Aucun suivi des identifiants AUD-* n'existait dans le dépôt : chaque verdict repose sur une relecture du code, pas sur un message de commit.

| Statut | au 20/11 | au 21/11 |
|---|---|---|
| Fermé | 0 | 0 |
| Fermé avec réserve | 0 | 1 |
| Partiel | 10 | 16 |
| Ouvert | 72 | 65 |
| Décision documentée | 1 | 1 |
| Info | 49 | 49 |

Plus 22 constats nouveaux au 21/11 (delta 54add78..b142695).

| ID | Sévérité | Statut 20/11 → 21/11 | Verdict du 21/11 |
|---|---|---|---|
| AUD-ARC-02 | Élevée | Partiel → **Fermé avec réserve** | La cause du constat disparaît : avec la règle « longest path wins » de Quarkus, la page, ses ressources Vite et l'avis de licence des polices sont servis sans jeton, l'API reste protégée ; l'IHM embarquée peut donc se charger et ouvrir sa session. Accord avec le message de commit, mais aucune exécution ni aucun test ne le prouve, et le parcours de connexion par défaut de l'image reste cassé pour d'autres raisons (AUD-DEP-25, AUD-DEP-21). |
| AUD-QUA-01 | Élevée | Partiel | Inchangé depuis le 20/11 : l'import planifié (ImportPlanifie) parcourt les projets ayant un dépôt, mais rien en production ne crée de projet ni ne rattache de dépôt ; l'import manuel SBOM/manifestes n'est appelé que par les tests. |
| AUD-DEP-21 | Élevée | Ouvert → **Partiel** | Le desktop devient configurable à l'emballage ; l'IHM de l'image Docker reste figée sur l'émetteur http://localhost:8180/realms/argos : l'installation locale fonctionne parce que tout est sur localhost, la connexion reste impossible hors du poste (NAS, réseau local). |
| AUD-PER-01 | Moyenne | Partiel | La collecte planifiée tient ; le lancement manuel reste synchrone dans la requête HTTP, OSV est toujours lu avis par avis et les attentes de reprise dorment toujours sur le fil qui synchronise. |
| AUD-PER-06 | Moyenne | Ouvert → **Partiel** | Les deux causes mesurées sont corrigées et testées : relecture groupée des signaux et index V21. « Ma veille » passe sous la cible sur le jeu K3. En revanche, la recherche des abonnements porte toujours sur des expressions non indexées, la réponse n'est toujours pas paginée, et la campagne n'exerce pas ce chemin. |
| AUD-ARC-12 | Faible | Partiel | Le point d'appel unique tient ; le contrat reste manuel, sans génération ni test de contrat côté IHM, et 10 classes @Path sur 23 ne sont appelées par aucun client. |
| AUD-ARC-14 | Faible | Partiel | Inchangé depuis le 20/11 : les ADR récents sont actés, les ADR fondateurs que le code applique restent « Proposé ». |
| AUD-QUA-08 | Faible | Ouvert → **Partiel** | Une des quatre preuves est corrigée (String[] -> record) ; niveau, clé composite et statuts restent typés par chaînes, dont le switch à défaut silencieux de l'import planifié. |
| AUD-QUA-09 | Faible | Ouvert → **Partiel** | Une convention est désormais écrite et outillée, mais elle entérine le mélange par fichier au lieu de fixer une langue par concept ; tables et classes mêlent toujours espace/workspace. |
| AUD-QUA-18 | Faible | Ouvert → **Partiel** | Les méthodes les plus complexes ont réellement été découpées, sauf SignalRecorder.record ; la taille des fichiers continue de croître et aucun seuil n'est vérifié. Correction de mesure : SbomDocument.reconnaitre (« CC≈28 / 88 l. » au 20/11) était un artefact du script, qui comptait l'accolade du littéral '{' (SbomDocument.java:69) ; la méthode fait 15 lignes, CC 6, inchangée depuis 9d96406. |
| AUD-TST-04 | Faible | Partiel | Partiel : scm corrigé ; cinq modules toujours sans classe de test dédiée, désormais modifiés sur leurs chemins d'échec sans test. |
| AUD-TST-09 | Faible | Partiel | Logique et rendu statique couverts ; client REST effectif et vues toujours hors tests unitaires. |
| AUD-TST-10 | Faible | Partiel | Surface preload/IPC protégée ; main.js, dont la configuration de sécurité vient de gagner une nouvelle source de réglages, toujours sans test. |
| AUD-DEP-19 | Faible | Partiel | Serveur contrôlé et avis livrés ; IHM (vue, vue-router, MIT) livrée dans l'image et l'installeur sans avis ; aucun contrôle npm automatisé ; aucun NOTICE dans argos-desktop. |
| AUD-DEP-20 | Faible | Partiel | Règle ADR-0032 respectée dans le delta, mais toujours ni CI ni hook ni test d'empreinte pour la faire respecter. |
| AUD-DOC-03 | Moyenne | Ouvert → **Partiel** | Partiel : un parcours Keycloak existe désormais pour le PC, mais le parcours principal du PC (:18-23), tout le parcours Synology (:58, aucune étape d'identité), le tableau des réglages (:111-118, 6 lignes, ni ARGOS_OIDC_URL ni ARGOS_ADMIN_EMAIL) et la phrase « Tant que l'authentification (OIDC, ADR-0008) n'est pas en place » (:80) sont restés tels quels ; README.md:85-92 donne toujours `cp .env.example .env` puis `docker compose up -d --build` sans OIDC. |
| AUD-DOC-04 | Moyenne | Ouvert → **Partiel** | Partiel : la cohérence API/CSP est documentée pour le cas « autre port », mais la section « Client desktop » (:165) ne règle toujours que VITE_ARGOS_API_URL pour viser le NAS, et ni VITE_ARGOS_OIDC_ISSUER, ni `argos.oidcUrl`, ni ARGOS_OIDC_URL ne sont mentionnés : un client visant le Keycloak du NAS garde l'émetteur et la CSP sur http://localhost:8180/realms/argos. |
| AUD-DEP-06 | Faible | Décision documentée | Toujours arbitré par la décision H2 pour les images de base ; la nouvelle image Keycloak n'est pas couverte par le tableau de la décision. |
| AUD-QUA-02 | Élevée | Ouvert | Pipeline sans pipefail, rotation sans contrôle de validité et message de succès inconditionnel toujours présents. |
| AUD-TST-01 | Élevée | Ouvert | Ouvert : toujours aucun workflow ni hook ; le volume non protégé continue de croître (602 méthodes serveur, 206 tests IHM, 54 desktop, 31 tests de scripts, 34 E2E, 29 d'accessibilité). |
| AUD-PER-02 | Élevée | Ouvert | Désaccord avec le message de f6b93dd : l'enrichissement n'est « différé » que dans le texte du journal. Rien ne le reprend : retryMissing n'a toujours aucun appelant en production, et l'enrichissement reste synchrone, une requête HTTP par CVE et par source. |
| AUD-DEP-01 | Élevée | Ouvert | Inchangé : 37 variables lues en production ou sans profil, 10 clés transmises, 28 absentes du service argos, 27 absentes de .env.example (mêmes chiffres qu'à 54add78). |
| AUD-ARC-04 | Moyenne | Ouvert | Le protocole RLS reste écrit quatre fois ; les deux variantes de workspace ne réinitialisent toujours ni sujet_courant ni email_courant, et PreferencesRepository peut toujours lever une NullPointerException. |
| AUD-ARC-05 | Moyenne | Ouvert | Le noyau commun implicite reste logé dans workspace et api.security, avec cinq règles d'exception au cas par cas. |
| AUD-ARC-06 | Moyenne | Ouvert | Le couplage par le schéma est inchangé, y compris les trois lectures que la table ALLOWED ou les règles d'exception ne couvrent pas. |
| AUD-QUA-04 | Moyenne | Ouvert | Deux modèles transactionnels coexistent toujours ; les six copies ne sont même pas identiques (PreparationFiches ne restaure pas l'autocommit). |
| AUD-QUA-06 | Moyenne | Ouvert | Le squelette JDBC reste recopié ; le lot superviseur n'a introduit aucune factorisation. |
| AUD-QUA-17 | Moyenne | Ouvert | Les cinq éléments restent atteints seulement par les tests ou sans appelant. |
| AUD-SEC-01 | Moyenne | Ouvert | La résolution vérifiée et la résolution de connexion restent distinctes ; pas d'épinglage d'adresse ni de test de rebinding. |
| AUD-SEC-03 | Moyenne | Ouvert | Toujours aucune rétention ni bornage cumulé des journaux de refus, d'appels MCP et d'appels IA. |
| AUD-SEC-05 | Moyenne (20/11 : Faible) | Ouvert | Toujours aucune CSP, nosniff, Referrer-Policy ni frame-ancestors servis par Quarkus, alors que le portail web est désormais livré. |
| AUD-SEC-22 | Moyenne | Ouvert | La forge est toujours interrogée avec le jeton du serveur avant le contrôle de visibilité et d'expertise ; réponse différenciée DEMANDE_INTROUVABLE. |
| AUD-TST-03 | Moyenne | Ouvert | Pyramide toujours inversée, à 71 % ; les découpages du lot 22 n'ont pas été mis à profit pour des tests unitaires. |
| AUD-TST-05 | Moyenne | Ouvert | Contrôle de rôle impératif toujours sans matrice ; 5 des 11 opérations d'exploitation sans test de refus. |
| AUD-PER-03 | Moyenne | Ouvert | Le N+1 tient : une transaction, un verrou et une relecture du signal pour chaque signal, puis 3 instructions préparées (jusqu'à 5) pour chaque composant correspondant. L'import nocturne le relance pour chaque projet à dépôt. |
| AUD-PER-04 | Moyenne | Ouvert | Toujours 3 transactions et au moins 3 requêtes par membre, plus une requête par canal, l'attente IA et l'envoi SMTP, sur le fil de corrélation. |
| AUD-PER-18 | Moyenne | Ouvert | Les opportunités sans date limite sont toujours relues et renvoyées sans LIMIT, sans pagination ni purge. |
| AUD-DEP-03 | Moyenne | Ouvert | La restauration reste un psql sans ON_ERROR_STOP sur un dump texte sans --clean, non testée ; elle ignore en plus la base keycloak introduite par #28 (voir AUD-DEP-24). |
| AUD-DEP-07 | Moyenne | Ouvert | Toujours ni journaux structurés, ni métriques, ni traçage, ni contrôle de santé métier. |
| AUD-ARC-07 | Faible | Ouvert | La table autorise toujours 7 dépendances inutilisées sur 57 et ne contraint toujours pas un paquet io.argos de premier niveau absent de la table. |
| AUD-ARC-09 | Faible | Ouvert | La chaîne StandardModifie → RadarProjets → AnnonceStandard → RappelsRadar reste synchrone dans la requête HTTP ; elle n'avale plus que les pannes d'accès aux données. |
| AUD-ARC-10 | Faible | Ouvert | La collecte HTTP reste dispersée, sans fabrique commune ; la garde d'adresses reste propre à la veille web. |
| AUD-ARC-11 | Faible | Ouvert | Toujours aucun objet-dieu au sens strict ; la recherche reste portée par impact alors que le package-info l'attribue à signal. |
| AUD-ARC-15 | Faible | Ouvert | Toujours aucun client ; le lien de rappel vise un chemin qui n'existe ni comme route de l'IHM ni comme ressource publique du serveur. |
| AUD-ARC-19 | Faible | Ouvert | AlerteEmise est toujours publié en cinq points sans aucun observateur. |
| AUD-QUA-10 | Faible | Ouvert | Horloge toujours non injectée ; chiffres du titre confirmés. |
| AUD-QUA-11 | Faible | Ouvert | Les trois exécuteurs statiques hors CDI existent toujours, sans arrêt. |
| AUD-QUA-19 | Faible | Ouvert | Les six erreurs de types relevées le 20/11 sont toujours présentes. |
| AUD-SEC-04 | Faible | Ouvert | L'adresse brute du jeton est toujours enregistrée et sert aux alertes, résumés et rappels réglementaires. |
| AUD-SEC-06 | Faible | Ouvert | Le repli « argos » subsiste pour la base, l'application et désormais Keycloak. |
| AUD-SEC-07 | Faible | Ouvert | Pas d'umask, pas de chiffrement. |
| AUD-SEC-09 | Faible | Ouvert | Toujours aucune limite sur la recherche plein texte ni sur la veille personnelle. |
| AUD-SEC-23 | Faible | Ouvert | L'écoute accepte toujours le premier GET /argos/retour sans comparer le state, puis se ferme. |
| AUD-SEC-24 | Faible | Ouvert | mvn toujours résolu par le PATH avec shell: true sous Windows. |
| AUD-SEC-25 | Faible | Ouvert | Code de l'application toujours propriété de l'utilisateur d'exécution ; conteneurs sans read_only, cap_drop ni no-new-privileges. |
| AUD-TST-02 | Faible | Ouvert | Aucun seuil ni rapport de couverture dans le build. |
| AUD-TST-06 | Faible | Ouvert | Délais réels et horloge réelle toujours utilisés dans les assertions. |
| AUD-TST-08 | Faible | Ouvert | Isolation toujours fondée sur deux listes codées en dur, sans contrôle d'exhaustivité. |
| AUD-TST-12 | Faible | Ouvert | Aucun test sur l'artefact empaqueté ; le parcours « IHM servie par le serveur » introduit par la PR #28 n'est exercé nulle part. |
| AUD-TST-19 | Faible | Ouvert | Pas de garde de version de JDK pour les tests. |
| AUD-TST-20 | Faible | Ouvert | Campagne principale toujours manuelle, et non rejouée (ou non consignée) après les lots 20 à 23 et la PR #28. |
| AUD-TST-23 | Faible | Ouvert | Écrans livrés après le 17/11 toujours sans mesure de contraste. |
| AUD-PER-05 | Faible | Ouvert | Le radar charge toujours, pour chaque espace, les composants de tous les espaces avant de filtrer en Java, et il lit le calendrier composant par composant. |
| AUD-PER-07 | Faible | Ouvert | La sélection est toujours matérialisée, classée et comptée en entier à chaque page. |
| AUD-PER-09 | Faible | Ouvert | Les index manquants le sont toujours. |
| AUD-PER-10 | Faible | Ouvert | Les listes d'exploitation, les échecs d'import et les impacts d'un projet sont toujours renvoyés sans LIMIT. |
| AUD-PER-11 | Faible | Ouvert | Les réponses OSV et KEV sont toujours lues en entier dans une String, sans plafond, et l'index OSV complet est relu chaque heure. |
| AUD-PER-12 | Faible | Ouvert | Les réglages JVM, le pool et Postgres sont toujours aux valeurs par défaut. Depuis #28, l'absence de compression HTTP touche aussi l'IHM, désormais servie par Quarkus. |
| AUD-PER-13 | Faible | Ouvert | La collecte d'activité lit toujours l'échéance projet par projet et appelle les forges en séquence. |
| AUD-PER-19 | Faible | Ouvert | Chaque nuit, l'import réécrit et recorrèle encore tout l'inventaire de chaque projet à dépôt, même sans changement de la SBOM ou des manifestes. |
| AUD-PER-20 | Faible | Ouvert | Les composants sont toujours insérés un par un, avec un aller-retour SQL par composant ; seule l'occurrence passe par lot. |
| AUD-DEP-05 | Faible | Ouvert | Toujours aucune veille automatisée ; Electron 45.3.2 sur une mineure non supportée (45.4.1 publiée le 19/11), Playwright 1.61.1 (1.65.1 publiée), Keycloak 26.7.4 alors que 26.7.5 et 26.8.1 existent. |
| AUD-DEP-08 | Faible | Ouvert | Inchangé pour argos et ollama ; seul le nouveau service Keycloak est surveillé. |
| AUD-DEP-15 | Faible | Ouvert | Toujours ni signature de code ni mise à jour automatique ; l'absence de signature est désormais écrite dans la doc, sans décision ni restriction d'usage. |
| AUD-DEP-22 | Faible | Ouvert | Toujours aucun test automatisé du script de licences, toujours hors de « mvn verify » et sans CI. |
| AUD-DOC-01 | Moyenne | Ouvert | Ouvert, légèrement aggravé : les cadrages de septembre subsistent mot pour mot et une affirmation de plus est devenue fausse après #29. |
| AUD-DOC-02 | Moyenne | Ouvert | Ouvert : arc42 §1-§3, §5.2, §7, §10 Q-06, §11 et le registre des risques restent au cadrage de septembre. |
| AUD-DOC-05 | Faible | Ouvert | Ouvert : Node.js 22.12 annoncé, Docker toujours présenté comme requis pour les seuls tests, pas de paragraphe de connexion, e2e réduit au portail web. |
| AUD-DOC-06 | Faible | Ouvert | Ouvert : le rendu Mermaid de D-19 échoue toujours. |
| AUD-DOC-07 | Faible | Ouvert | Ouvert, aggravé sur l'inventaire des diagrammes. |
| AUD-DOC-08 | Faible | Ouvert | Ouvert : neuf ADR acceptés gardent « Proposition » ; ADR-0029 se dit toujours subordonné à sa propre acceptation. |
| AUD-DOC-10 | Faible | Ouvert | Ouvert : mêmes trous de contrôle ; le périmètre réellement contrôlé a diminué avec l'archivage. |
| AUD-DOC-11 | Faible | Ouvert | Ouvert. |
| AUD-ARC-01 | Info | Info | La protection tient : graphe acyclique, intégralement conforme à ALLOWED, et les package-info concordent toujours avec la table. |
| AUD-ARC-08 | Info | Info | La protection tient : api ne contient toujours aucun accès JDBC/SQL. |
| AUD-ARC-13 | Info | Info | La protection tient : la couche mise en page n'importe que vue, les polices et ses propres fichiers (plus MentionsObligatoires) ; le processus principal desktop n'appelle toujours pas l'API. |
| AUD-ARC-18 | Info | Info | La protection tient : tous les sous-paquets restent sous un module de premier niveau présent dans ALLOWED et les package-info concordent avec la table. |
| AUD-QUA-14 | Info | Info | Régression partielle de la protection : aucun catch vide ni printStackTrace, interruptions rétablies, causes mieux journalisées, aucune relance sans cause nouvelle ; mais l'isolation aux frontières ne couvre plus que IllegalStateException — toute autre RuntimeException traverse désormais les boucles par source, par projet, par espace et la garde MCP (voir AUD-QUA-20 et AUD-QUA-20). |
| AUD-QUA-15 | Info | Info | Fermeture systématique des ressources JDBC confirmée à b142695. |
| AUD-QUA-16 | Info | Info | Typage strict tenu : vue-tsc sans erreur, toujours un seul contournement. |
| AUD-SEC-10 | Info | Info | Tient : aucun secret réel versionné dans le delta. |
| AUD-SEC-11 | Info | Info | Tient. |
| AUD-SEC-12 | Info | Info | Tient : chemins publics exacts ou limités aux ressources statiques ; /api, /mcp et /q/openapi restent sous `/*` authenticated. D'après la documentation Quarkus (Context7), un chemin sans `/*` final est une correspondance exacte et le chemin le plus long l'emporte. |
| AUD-SEC-13 | Info | Info | Tient : pas d'IDOR ; les contrôles d'accès sont inchangés en nombre dans chaque fichier refactorisé. |
| AUD-SEC-14 | Info | Info | Tient hors rebinding (AUD-SEC-01). |
| AUD-SEC-15 | Info | Info | Tient. |
| AUD-SEC-16 | Info | Info | Tient : les 8 outils passent par garde.appeler. |
| AUD-SEC-17 | Info | Info | Tient : refus des classes non transmissibles et des secrets avant toute transmission, chevrons neutralisés, réservation de quota atomique, sortie validée par schéma et journalisée sans le texte du modèle. |
| AUD-SEC-18 | Info | Info | Tient. |
| AUD-SEC-19 | Info | Info | Tient : aucun v-html/innerHTML/eval. |
| AUD-SEC-20 | Info | Info | Tient. |
| AUD-SEC-21 | Info | Info | Tient. |
| AUD-SEC-26 | Info | Info | Tient côté clients (IHM et desktop) : PKCE S256, state en temps constant, iss contrôlé, jetons en mémoire. |
| AUD-SEC-27 | Info | Info | Tient. |
| AUD-SEC-28 | Info | Info | Tient. |
| AUD-TST-13 | Info | Info | npm test passe (206/206), vue-tsc passe, vite build passe. |
| AUD-TST-14 | Info | Info | npm test passe (54/54) et check:syntax passe sur les 8 fichiers source. |
| AUD-TST-15 | Info | Info | Aucun test désactivé ni focalisé. |
| AUD-TST-16 | Info | Info | Ports aléatoires, sources simulées, courrier simulé et planificateur désactivé tiennent. |
| AUD-TST-17 | Info | Info | Couverture ciblée de la sécurité, du MCP et de l'alerting maintenue. |
| AUD-TST-18 | Info | Info | Frontières des 16 modules vérifiées par ArchUnit ; exécution non constatée ici. |
| AUD-TST-21 | Info | Info | Campagne d'accessibilité des mises en page rejouée hors du poste du mainteneur : 29/29 en 30,8 s, sans serveur ni base. |
| AUD-TST-22 | Info | Info | Tests structurants du 20/11 toujours en place. |
| AUD-PER-14 | Info | Info | La protection tient : les 13 @Scheduled (12 classes) déclarent SKIP, et l'exclusion repose sur la base (index uniques, verrous consultatifs). |
| AUD-PER-15 | Info | Info | La protection tient : files bornées, délais, taille de réponse plafonnée, quotas. |
| AUD-PER-16 | Info | Info | La protection tient : les 7 clients HttpClient déclarent un délai de connexion et refusent les redirections, et les caches KEV et CERT-FR gardent leur durée de vie. |
| AUD-PER-17 | Info | Info | Le bundle reste léger : 201,74 kB, 68,43 kB gzippé, sous le seuil de 300 kB. |
| AUD-PER-23 | Info | Info | La protection tient : un avis inchangé (empreinte normalisée identique) renvoie UNCHANGED sans publier SignalChanged. |
| AUD-PER-24 | Info | Info | La protection tient : archive de 50 Mio au plus, décompression plafonnée à 500 Mio et 50 000 entrées, fichier de 5 Mio au plus, 10 requêtes par minute. |
| AUD-DEP-11 | Info | Info | Tient : 6 vulnérabilités modérées, toutes via minimatch dans la chaîne electron-builder (outil de build). |
| AUD-DEP-12 | Info | Info | Tient : 0 vulnérabilité ; dépendances directes à jour à une exception de un jour près (vue 3.5.46 publiée le 20/11) et TypeScript (tilde délibéré). |
| AUD-DEP-13 | Info | Info | Tient : Quarkus 3.40.1.2 sur la LTS 3.40, Java 17 compatible. |
| AUD-DEP-16 | Info | Info | Tient : multi-étapes, JRE seul, UID 1001, mem_limit, healthcheck PostgreSQL, ports locaux ; le nouveau service Keycloak respecte le même modèle. |
| AUD-DEP-17 | Info | Info | Tient : prod sans défaut pour base et OIDC, mailer simulé en dev/test seulement, STARTTLS exigé, aucun secret réel versionné. |
| AUD-DEP-18 | Info | Info | Tient : aucune dépendance copyleft forte ni non commerciale livrée ; EPL-2.0 nommée et décidée. |
| AUD-DEP-23 | Info | Info | Tient après refactorisation : codes de sortie 1 (GPL, licence absente), 2 (BOM absent), 0 (conforme, textes copiés). |
| AUD-DOC-13 | Info | Info | Tient : liens et ancres sains dans tout le dépôt ; verifier-docs sans --rendu sans erreur. |
| AUD-DOC-14 | Info | Info | Tient : 33/33 statuts concordants entre en-têtes, index et arc42 §9.1. |
| AUD-DOC-15 | Info | Info | Tient : migrations V1 à V21 et 13 tâches planifiées documentées à jour. |
| AUD-DOC-16 | Info | Info | Tient : versions, projets, tableaux du script et textes de licence concordent. |
| AUD-DOC-17 | Info | Info | Ne tient plus qu'à moitié : outils MCP (8 @Tool = « huit outils ») toujours cohérents ; les chiffres de performance et l'inventaire OpenSpec ne concordent plus (nouveaux constats AUD-DOC-19 et AUD-DOC-20). |
| AUD-DOC-18 | Info | Info | Tient : aucune tâche ouverte n'est archivée sans motif ni renvoi ; la correspondance tâche → recette est exacte. |
