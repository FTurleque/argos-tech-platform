# État des constats du 17/11 au 2026-11-20

Vérification de chaque constat de l'audit du 17/11 ([../2026-11-17/](../2026-11-17/README.md)) dans le code à `origin/main` à 54add78 (PR #21, lot 18). Aucun suivi des identifiants AUD-* n'existait dans le dépôt : chaque verdict repose sur une relecture du code, pas sur un message de commit.

| Statut | Nombre |
|---|---|
| Fermé | 0 |
| Fermé avec réserve | 0 |
| Partiel | 10 |
| Ouvert | 48 |
| Décision documentée | 1 |
| Info | 34 |

Plus 39 constats nouveaux (delta 9d96406..54add78 et axe Documentation, ajouté à cette date).

| ID | Sévérité 17/11 → 20/11 | Statut | Verdict |
|---|---|---|---|
| AUD-ARC-02 | Élevée | Partiel | La partie client est corrigée et testée ; le serveur exige toujours une session sur /* y compris sur les ressources statiques de l'IHM embarquée dans l'image Docker, qui ne peut donc pas se charger pour lancer la connexion. |
| AUD-QUA-01 | Élevée | Partiel | Partiel, mais sans effet en production : la tâche ImportPlanifie (77181e9, testée) importe chaque nuit les dépôts déjà rattachés, or aucun point d'entrée ne crée de projet ni ne rattache de dépôt — elle n'a donc rien à parcourir. Désaccord écrit avec inventaire-sbom.md:33, qui affirme que « l'import manuel existant reste possible ». |
| AUD-PER-01 | Élevée → Moyenne | Partiel | La collecte régulière existe et ne passe plus par une requête HTTP ; en revanche le lancement manuel reste synchrone, OSV est toujours lu avis par avis et les reprises dorment toujours sur le fil qui synchronise. |
| AUD-ARC-12 | Faible | Partiel | La divergence de gestion d'erreur est corrigée ; le contrat reste écrit à la main, sans génération ni test de contrat côté IHM, et couvre 11 racines @Path sur 23. |
| AUD-ARC-14 | Faible | Partiel | Les ADR récents sont actés, mais les ADR fondateurs que le code applique depuis le début restent « Proposé ». |
| AUD-TST-04 | Moyenne → Faible | Partiel | Partiel : scm est corrigé (4 classes unitaires et 2 de contrat) et sa couverture passe de 26,9 % à 51,8 % des lignes (41,3 % des branches) d'après la mesure JaCoCo du 20/11 ; cinq modules restent sans classe de test dédiée (workspace, inventory, vulnintel, releaseradar, audit) mais sont couverts à 81–89 % des lignes par les parcours. |
| AUD-TST-09 | Moyenne → Faible | Partiel | Logique et rendu statique largement couverts ; client REST effectif et vues toujours hors tests unitaires. |
| AUD-TST-10 | Faible | Partiel | Surface preload/IPC protégée ; webPreferences de main.js toujours sans test. |
| AUD-DEP-19 | Faible | Partiel | Le serveur est conforme et contrôlé ; l'IHM livrée (vue, vue-router, MIT) part dans l'image et l'installeur sans avis, et aucun contrôle npm n'est automatisé. |
| AUD-DEP-20 | Moyenne → Faible | Partiel | Règle documentée et respectée dans le delta, mais rien ne la fait respecter mécaniquement (pas de CI ni de hook). |
| AUD-DEP-06 | Faible | Décision documentée | Non corrigé mais arbitré explicitement dans la doc de déploiement et le Dockerfile. |
| AUD-QUA-02 | Élevée | Ouvert | Pipeline sans pipefail, rotation sans contrôle de validité et message de succès inconditionnel toujours présents. |
| AUD-TST-01 | Moyenne → Élevée | Ouvert | Ouvert : toujours aucun workflow CI (.github absent) ni hook ; le Dockerfile contrôle désormais les licences mais construit toujours avec -DskipTests. 158 commits et 14 lots ont été intégrés sans gate automatique ; sept constats en dépendent. Sévérité relevée à Élevée (dette qui bloque la vérification des chantiers en cours). |
| AUD-PER-02 | Élevée | Ouvert | L'enrichissement reste synchrone (observateur AFTER_SUCCESS, une requête HTTP par CVE et par source) et retryMissing n'a toujours aucun appelant en production. |
| AUD-DEP-01 | Élevée | Ouvert | Aggravé : 28 variables lues en production absentes du service argos (au lieu de 23), 27 absentes de .env.example (au lieu de 22) ; doc toujours en retard. |
| AUD-ARC-04 | Moyenne | Ouvert | Le protocole RLS est toujours écrit quatre fois ; les deux variantes partielles de workspace ne réinitialisent toujours ni sujet_courant ni email_courant. |
| AUD-ARC-05 | Moyenne | Ouvert | Le noyau commun implicite s'étend : chaque nouveau module (regintel) a obtenu sa propre exception au lieu d'un paquet transverse. |
| AUD-ARC-06 | Faible → Moyenne | Ouvert | Le couplage par le schéma s'étend et crée désormais des dépendances absentes de la table ALLOWED ou plus larges que la règle d'exception. |
| AUD-QUA-04 | Moyenne | Ouvert | Les deux modèles transactionnels coexistent toujours et le modèle manuel s'est étendu au nouveau module regintel. |
| AUD-QUA-06 | Moyenne | Ouvert | Le squelette JDBC continue d'être recopié ; aucune factorisation. |
| AUD-SEC-01 | Moyenne | Ouvert | La résolution vérifiée et la résolution de connexion restent distinctes ; aucun épinglage d'adresse ni test de rebinding. |
| AUD-SEC-03 | Moyenne | Ouvert | Aucune purge ni bornage des journaux ; la surface d'écriture sans compte Argos s'élargit. |
| AUD-TST-03 | Moyenne | Ouvert | Pyramide toujours inversée, à 71 %. |
| AUD-TST-05 | Moyenne | Ouvert | Contrôle de rôle impératif toujours sans matrice, sur une surface en hausse de 37 %. |
| AUD-PER-03 | Moyenne | Ouvert | Le N+1 tient toujours et s'est aggravé : une transaction et une relecture du signal par signal, puis au moins 3 instructions préparées dans la boucle pour chaque composant correspondant, au lieu d'une seule. |
| AUD-PER-04 | Moyenne | Ouvert | Toujours 3 transactions et au moins 3 requêtes par membre, plus une requête par canal, puis l'envoi SMTP, sur le fil de corrélation. |
| AUD-PER-06 | Moyenne | Ouvert | Même requête sur expressions non indexées et même relecture signal par signal, maintenant exécutées par deux routes, dont « Ma veille », mesurée hors cible. |
| AUD-PER-18 | Moyenne | Ouvert | Les opportunités sans date limite sont toujours relues et renvoyées sans LIMIT, sans pagination et sans purge. |
| AUD-DEP-03 | Moyenne | Ouvert | La procédure de restauration reste un psql sans ON_ERROR_STOP sur un dump texte sans --clean, non testée. |
| AUD-DEP-07 | Moyenne | Ouvert | Toujours ni journaux structurés, ni métriques, ni traçage, ni contrôle de santé métier. |
| AUD-ARC-07 | Faible | Ouvert | La table reste plus large que le code et ne contraint toujours pas un paquet io.argos non déclaré. |
| AUD-ARC-09 | Faible | Ouvert | Les deux styles coexistent toujours sans règle ; la chaîne StandardModifie -> RadarProjets -> AnnonceStandard -> RappelsRadar reste synchrone dans le PUT /standards, exceptions avalées. |
| AUD-ARC-10 | Faible | Ouvert | La dispersion s'accroît ; aucune fabrique commune. |
| AUD-ARC-11 | Faible | Ouvert | Toujours aucun objet-dieu au sens strict ; la responsabilité recherche reste mal attribuée et impact concentre corrélation, traitement des impacts (V20), recherche, radar, opportunités et IA. |
| AUD-ARC-15 | Faible | Ouvert | Toujours aucun client pour les points d'entrée du profil artiste ; le lien de rappel vers une page inexistante subsiste et n'est pas consigné. |
| AUD-QUA-08 | Faible | Ouvert | Niveau d'impact, statuts d'import et résumé IA restent typés par chaînes et tableaux ; nouveau switch sur chaînes avec défaut silencieux. |
| AUD-QUA-09 | Faible | Ouvert | Mélange français/anglais inchangé et étendu aux nouvelles tables et classes. |
| AUD-QUA-10 | Faible | Ouvert | Horloge toujours non injectée ; dispersion en hausse. |
| AUD-QUA-11 | Faible | Ouvert | Les trois exécuteurs statiques hors CDI existent toujours, sans arrêt. |
| AUD-SEC-04 | Faible | Ouvert | L'adresse brute du jeton reste enregistrée et sert désormais aussi aux rappels réglementaires. |
| AUD-SEC-05 | Faible | Ouvert | Toujours aucune CSP, nosniff, Referrer-Policy ni frame-ancestors servis par Quarkus. |
| AUD-SEC-06 | Faible | Ouvert | Le repli « argos » subsiste pour la base et l'application. |
| AUD-SEC-07 | Faible | Ouvert | Pas d'umask, pas de chiffrement. |
| AUD-SEC-09 | Faible | Ouvert | Toujours aucune limite sur la recherche plein texte ; périmètre élargi à la veille personnelle. |
| AUD-TST-02 | Faible | Ouvert | Aucun seuil ni rapport de couverture dans le build. |
| AUD-TST-06 | Faible | Ouvert | Délais réels et horloge réelle toujours utilisés dans les assertions. |
| AUD-TST-08 | Faible | Ouvert | Isolation toujours fondée sur deux listes codées en dur, sans contrôle d'exhaustivité. |
| AUD-TST-12 | Faible | Ouvert | Aucun test sur l'artefact empaqueté. |
| AUD-TST-19 | Faible | Ouvert | Pas de garde de version de JDK pour les tests. |
| AUD-PER-05 | Faible | Ouvert | Le radar charge toujours les composants de tous les espaces puis filtre en Java, et lit le calendrier composant par composant. |
| AUD-PER-07 | Faible | Ouvert | La sélection reste matérialisée, classée et comptée en entier à chaque page. Les seules mesures à grand volume restent celles de QS-13 (poste de développement). |
| AUD-PER-09 | Faible | Ouvert | Les index manquants le sont toujours ; SET NULL demande la même recherche par component_id que CASCADE. |
| AUD-PER-10 | Faible | Ouvert | Les listes d'exploitation restent sans LIMIT. Leur croissance est désormais automatique : synchronisation horaire et import quotidien. |
| AUD-PER-11 | Faible | Ouvert | Les réponses OSV et KEV sont toujours lues en entier en String, sans plafond, et l'index OSV est maintenant relu à chaque passage horaire. |
| AUD-PER-12 | Faible | Ouvert | Les réglages JVM, le pool et Postgres sont toujours aux valeurs par défaut. |
| AUD-PER-13 | Faible | Ouvert | La collecte d'activité lit toujours l'échéance projet par projet et appelle les forges en séquence. |
| AUD-DEP-05 | Faible | Ouvert | Toujours aucune veille automatisée ; le retard d'Electron s'est creusé (45.3.2 alors que 45.3.3, 45.3.4 et 45.4.1 sont publiées). |
| AUD-DEP-08 | Faible | Ouvert | Toujours aucun healthcheck pour argos/ollama ni rotation des journaux. |
| AUD-DEP-15 | Faible | Ouvert | Toujours ni signature de code ni mise à jour automatique. |
| AUD-ARC-01 | Info | Info | La protection tient : graphe toujours acyclique et intégralement conforme à ALLOWED ; les 16 package-info concordent avec la table. |
| AUD-ARC-08 | Info | Info | La protection tient : api ne contient toujours aucun accès JDBC/SQL ; les nouvelles ressources délèguent aux modules. |
| AUD-ARC-13 | Info | Info | La protection tient : la couche mise en page n'importe que vue, les polices et ses propres fichiers ; le processus principal desktop n'appelle pas l'API. |
| AUD-QUA-14 | Info | Info | Les règles de gestion d'erreurs et de journalisation tiennent, y compris dans regintel, inventaire et l'IHM. |
| AUD-QUA-15 | Info | Info | Fermeture systématique des ressources JDBC confirmée à 54add78. |
| AUD-QUA-16 | Info | Info | Le typage strict tient ; l'affirmation « taille maîtrisée, pas de fichier dieu » ne tient plus qu'en partie. |
| AUD-SEC-10 | Info | Info | Tient : aucun secret réel versionné ni dans l'historique du delta. |
| AUD-SEC-11 | Info | Info | Tient. |
| AUD-SEC-12 | Info | Info | Tient : règle /* authenticated, seules /api/v1/system/info et /q/health* publiques, aucun résolveur sans authentification. |
| AUD-SEC-13 | Info | Info | Tient : pas d'IDOR inter-espaces trouvé sur les routes du delta. |
| AUD-SEC-14 | Info | Info | Tient hors rebinding (AUD-SEC-01) ; les nouveaux flux ne visent que des origines de configuration. |
| AUD-SEC-15 | Info | Info | Tient et s'étend au nouveau parseur réglementaire. |
| AUD-SEC-16 | Info | Info | Tient : authentification, limitation et balisage en place sur les 8 outils. |
| AUD-SEC-17 | Info | Info | Tient : l'IA ne déclenche aucune action sans validation humaine. |
| AUD-SEC-18 | Info | Info | Tient ; le défaut résiduel de l'écoute locale fait l'objet de AUD-SEC-23. |
| AUD-SEC-19 | Info | Info | Tient : aucun v-html/innerHTML/eval. |
| AUD-SEC-20 | Info | Info | Tient. |
| AUD-SEC-21 | Info | Info | Tient. |
| AUD-TST-13 | Info | Info | npm test passe (206/206) et vue-tsc passe. |
| AUD-TST-14 | Info | Info | npm test passe (54/54) et check:syntax passe sur les 8 fichiers source. |
| AUD-TST-15 | Info | Info | Aucun test désactivé ni focalisé. |
| AUD-TST-16 | Info | Info | Ports aléatoires, sources simulées, courrier simulé et planificateur désactivé tiennent. |
| AUD-TST-17 | Info | Info | Couverture ciblée de la sécurité, du MCP et de l'alerting maintenue et étendue. |
| AUD-TST-18 | Info | Info | Frontières des 16 modules vérifiées par ArchUnit ; exécution non constatée ici. |
| AUD-PER-14 | Info | Info | La protection tient : les 13 @Scheduled déclarent SKIP, et l'exclusion repose sur la base (index uniques, verrous consultatifs). |
| AUD-PER-15 | Info | Info | La protection tient : files bornées, délais, taille de réponse plafonnée, quotas. |
| AUD-PER-16 | Info | Info | La protection tient : les 7 clients HttpClient déclarent un délai de connexion et refusent les redirections (sauf la passerelle IA, locale), et les caches KEV et CERT-FR gardent leur durée de vie. |
| AUD-PER-17 | Info | Info | Bundle toujours léger (201,61 kB, 68,41 kB gzippé, sous le seuil de 300 kB) ; en revanche la seconde moitié de la protection, l'import par lot, a régressé (nouveau constat AUD-PER-20). |
| AUD-DEP-11 | Info | Info | Tient : 6 vulnérabilités modérées, toutes via minimatch dans la chaîne electron-builder (outil de build). |
| AUD-DEP-12 | Info | Info | Tient : 0 vulnérabilité, toutes les dépendances directes à la dernière version sauf TypeScript (tilde délibéré). |
| AUD-DEP-13 | Info | Info | Tient : Quarkus 3.40.1.2 sur la LTS 3.40, Java 17 compatible. |
| AUD-DEP-16 | Info | Info | Tient et s'améliore : multi-étapes, JRE seul, UID 1001, mem_limit, healthcheck PostgreSQL, ports locaux. |
| AUD-DEP-17 | Info | Info | Tient : prod sans défaut pour base et OIDC, mailer simulé en dev/test seulement, STARTTLS exigé, aucun secret réel versionné. |
| AUD-DEP-18 | Info | Info | Tient : aucune dépendance copyleft forte ni non commerciale livrée ; le seul copyleft faible (EPL-2.0) est nommé et décidé. |
