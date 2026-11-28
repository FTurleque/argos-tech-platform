# État des constats au 2026-11-28

Vérification de chaque constat des audits précédents ([17/11](../2026-11-17/README.md), [20/11](../2026-11-20/README.md), [21/11](../2026-11-21/README.md)) dans le code à `origin/main` à 01fea6d (PR #79), complété à ae4a2da (PR #82) pour AUD-ARC-11, AUD-QUA-09 et AUD-QUA-18. Le suivi tenu par le mainteneur dans OpenSpec (tâches citant les identifiants AUD-*) a été confronté au code : un constat n'est fermé que si la preuve d'origine a disparu, que la correction est complète et, autant que possible, protégée par un test.

| Statut | au 21/11 | au 28/11 |
|---|---|---|
| Fermé | 0 | 21 |
| Fermé avec réserve | 1 | 66 |
| Partiel | 16 | 13 |
| Ouvert | 83 | 0 |
| Décision documentée | 1 | 1 |
| Info | 53 | 53 |

Plus 38 constats nouveaux au 28/11 (delta b142695..01fea6d).

| ID | Sévérité | Statut 21/11 → 28/11 | Verdict du 28/11 |
|---|---|---|---|
| AUD-ARC-02 | Élevée | Fermé avec réserve → **Fermé** | L'IHM embarquée se charge sans jeton et ouvre sa session ; le comportement est maintenant protégé par un test serveur et par l'E2E sur l'image. |
| AUD-ARC-04 | Moyenne | Ouvert → **Fermé** | Accord avec le suivi (tâche 3.1 cochée) : le protocole RLS n'est plus écrit qu'à un endroit, les variantes partielles et la NullPointerException ont disparu, et un test de source empêche une nouvelle copie. |
| AUD-ARC-22 | Moyenne | Ouvert → **Fermé** | Les liens visent des routes existantes, servies sous / (chemin public) : la page se charge puis ouvre la session. Concordance serveur/IHM testée des deux côtés. |
| AUD-PER-03 | Moyenne | Ouvert → **Fermé** | Le N+1 a disparu : verrou, rattachement, lecture des existants, upsert, réouverture et résolution sont chacun une instruction par lot. Un test d'intégration compare le nombre d'instructions pour 2 et 20 signaux. |
| AUD-PER-25 | Moyenne | Ouvert → **Fermé** | Un impact inchangé n'est plus réécrit et garde son updated_at ; testé. |
| AUD-ARC-11 | Faible | Ouvert → **Fermé** | Fermé : la recherche plein texte réside dans le module signal, comme l'annonce son package-info. |
| AUD-QUA-19 | Faible | Ouvert → **Fermé** | Les six erreurs ont disparu et la vérification de types est un gate atteignable de la CI (job « outils », déclenché sur chaque pull request et push sur main, sans filtre de chemin). |
| AUD-SEC-04 | Faible | Ouvert → **Fermé** | Plus aucune adresse non vérifiée enregistrée ni utilisée ; le canal e-mail refuse un destinataire sans adresse. |
| AUD-SEC-09 | Faible | Ouvert → **Fermé** | Limites de débit en place avant tout travail, testées. |
| AUD-SEC-23 | Faible | Ouvert → **Fermé** | Corrigé et testé. |
| AUD-SEC-24 | Faible | Ouvert → **Fermé** | Plus de résolution par le PATH ni de shell: true dans la campagne. |
| AUD-TST-10 | Faible | Partiel → **Fermé** | Corrigé : main.js et sa configuration de sécurité sont testés. |
| AUD-TST-25 | Faible | Ouvert → **Fermé** | Corrigé et testé. |
| AUD-PER-05 | Faible | Ouvert → **Fermé** | Le radar travaille en un nombre fixe de requêtes par espace ; test dédié. |
| AUD-PER-13 | Faible | Ouvert → **Fermé** | Plus de lecture d'échéance par projet ; les forges sont collectées en parallèle ; test dédié. |
| AUD-PER-19 | Faible | Ouvert → **Fermé** | Un import nocturne sans changement n'écrit plus et ne recorrèle plus ; tests dédiés. |
| AUD-PER-26 | Faible | Ouvert → **Fermé** | Un petit espace ne parcourt plus les impacts des autres espaces ; mesuré. |
| AUD-PER-29 | Faible | Ouvert → **Fermé** | Index redondants supprimés ; testé. |
| AUD-DEP-19 | Faible | Partiel → **Fermé** | Avis MIT (vue, vue-router) et OFL livrés dans l'image et l'installeur ; contrôle npm automatisé et testé. |
| AUD-DEP-22 | Faible | Ouvert → **Fermé** | Script testé et exécuté en CI. |
| AUD-DOC-23 | Faible | Ouvert → **Fermé** | Corrigé. |
| AUD-QUA-01 | Élevée | Partiel → **Fermé avec réserve** | La preuve d'origine a disparu : le projet naît par l'API avec contrôle de rôle, l'import SBOM/manifestes/relance est exposé, et des tests d'intégration couvrent les parcours et les refus. |
| AUD-QUA-02 | Élevée | Ouvert → **Fermé avec réserve** | Correction réelle : un pg_dump en échec ou une archive incomplète font sortir le script en code 1 sans toucher aux sauvegardes existantes (essai reproduit avec un faux docker). La protection n'est vérifiée que par un essai manuel qui exige Docker, absent de la CI. |
| AUD-QUA-20 | Élevée | Ouvert → **Fermé avec réserve** | Un PURL mal encodé n'arrête plus la synchronisation : il est indexé par écosystème et nom, la source suivante est synchronisée, et des tests le démontrent ; les trois chaînes d'origine sont corrigées. |
| AUD-TST-01 | Élevée | Ouvert → **Fermé avec réserve** | Corrigé : chaque gate déclaré appelle une commande réelle du dépôt ; les gates Node rejoués dans une copie d'audit passent tous ; le gate Maven n'est pas rejouable ici (Maven Central bloqué) et la protection de main n'est pas vérifiable. |
| AUD-DEP-01 | Élevée | Ouvert → **Fermé avec réserve** | Parité atteinte et gardée : 42 variables ${ARGOS_*} lues en %prod ou sans profil, 0 absente du service argos, 0 absente de .env.example ; le contrôle échoue sur toute régression (mutation vérifiée). |
| AUD-DEP-21 | Élevée | Partiel → **Fermé avec réserve** | Image Docker corrigée : plus aucun émetteur figé dans le bundle ; desktop configurable et documenté, mais l'installeur publié par la CI reste figé sur localhost (AUD-DEP-29). |
| AUD-DEP-24 | Élevée | Ouvert → **Fermé avec réserve** | Base keycloak sauvegardée et restaurée avant tout réimport ; scénarios couverts par l'essai scripté. |
| AUD-ARC-05 | Moyenne | Ouvert → **Fermé avec réserve** | Accord avec le suivi : le noyau implicite est devenu un module transverse déclaré, sans dépendance (règle NC-3 testée), et les exceptions au cas par cas ont disparu. |
| AUD-QUA-04 | Moyenne | Ouvert → **Fermé avec réserve** | Un seul modèle transactionnel (JTA déclaratif ou programmatique) ; plus aucune gestion manuelle de l'autocommit en production, et une règle d'architecture l'interdit. |
| AUD-QUA-17 | Moyenne | Ouvert → **Fermé avec réserve** | Les cinq éléments sont atteints en production ou supprimés, et une règle d'architecture empêche le retour d'une méthode publique atteinte seulement par les tests. |
| AUD-SEC-01 | Moyenne | Ouvert → **Fermé avec réserve** | Épinglage réel : plus aucune résolution par le client HTTP, à chaque saut ; tests de rebinding actifs. Le filtrage de sortie en hébergement en ligne reste non fait (Q-2, hors code). |
| AUD-SEC-03 | Moyenne | Ouvert → **Fermé avec réserve** | Rétention et bornage en place pour les trois journaux, testés. |
| AUD-SEC-05 | Moyenne | Ouvert → **Fermé avec réserve** | En-têtes posés sur l'IHM et l'API, testés. |
| AUD-SEC-22 | Moyenne | Ouvert → **Fermé avec réserve** | Un non-expert ne déclenche plus aucune requête vers la forge ; testé. |
| AUD-SEC-29 | Moyenne | Ouvert → **Fermé avec réserve** | Royaume livré durci et contrôlé en CI ; TLS « all » seulement par une étape manuelle documentée. |
| AUD-SEC-30 | Moyenne | Ouvert → **Fermé avec réserve** | Comptes distincts et non superutilisateurs, testés par RolesBaseTest. |
| AUD-TST-05 | Moyenne | Ouvert → **Fermé avec réserve** | Corrigé : matrice exhaustive et fermée ; POST /ops/sources/{id}/synchronizations et PUT …/conditions-usage ont désormais leur refus 403 vérifié pour tous les rôles non exploitants. |
| AUD-PER-01 | Moyenne | Partiel → **Fermé avec réserve** | Le lancement manuel ne retient plus le fil HTTP et l'amorçage OSV passe par l'archive ; les attentes de reprise dorment désormais sur le fil de fond (ou du planificateur), plus sur un fil de requête. |
| AUD-PER-04 | Moyenne | Ouvert → **Fermé avec réserve** | Plus de transaction ni de requête par membre ou par canal, plus d'envoi SMTP dans l'observateur ; test du nombre fixe de requêtes. |
| AUD-PER-06 | Moyenne | Partiel → **Fermé avec réserve** | Les trois réserves du 21/11 sont levées : expressions indexées, réponse paginée, chemin des abonnements mesuré. Mesure versionnée : Ma veille p95 185,5 ms (cible 500 ms) sur un jeu avec abonnements PERSONAL et trois espaces. |
| AUD-PER-18 | Moyenne | Ouvert → **Fermé avec réserve** | Les opportunités sans date limite ne sont plus restituées indéfiniment, sont purgées et la liste est paginée. |
| AUD-DEP-03 | Moyenne | Ouvert → **Fermé avec réserve** | Restauration désormais idempotente (DROP DATABASE … WITH (FORCE) puis CREATE), arrêtée à la première erreur, documentée et rejouée par un script d'essai ; mais l'essai n'est exécuté ni en CI ni périodiquement. |
| AUD-DEP-07 | Moyenne | Ouvert → **Fermé avec réserve** | Journaux structurés, métriques et santé métier présents et testés ; traçage distribué différé par décision. |
| AUD-DEP-25 | Moyenne | Ouvert → **Fermé avec réserve** | Plus de port 8088 dans le royaume livré : la configuration par défaut est cohérente. |
| AUD-ARC-07 | Faible | Ouvert → **Fermé avec réserve** | L'action est faite et testée : un paquet io.argos non déclaré fait échouer le test. Reste une autorisation sans usage Java, que la documentation présente pourtant comme utilisée. |
| AUD-ARC-09 | Faible | Ouvert → **Fermé avec réserve** | La chaîne radar sort de la requête HTTP : ReferentielEspace publie après la validation de sa transaction (requiringNew), RadarProjets et RappelsRadar observent en asynchrone ; la règle est écrite et protégée par un test. |
| AUD-ARC-10 | Faible | Ouvert → **Fermé avec réserve** | L'action (fabrique commune dans le module transverse) est faite et testée ; tous les clients lisent leurs réponses par LectureBornee. |
| AUD-ARC-19 | Faible | Ouvert → **Fermé avec réserve** | Accord : l'événement sans observateur n'existe plus. |
| AUD-QUA-08 | Faible | Partiel → **Fermé avec réserve** | Les quatre preuves d'origine sont corrigées et une valeur inconnue lue en base lève une erreur explicite ; d'autres valeurs fermées de même nature restent des chaînes. |
| AUD-QUA-09 | Faible | Partiel → **Fermé avec réserve** | Fermé avec réserve : une langue par concept dans les identifiants Java, vérifiée par le contrôle de nommage. |
| AUD-QUA-10 | Faible | Ouvert → **Fermé avec réserve** | Plus aucune lecture de l'heure système hors du paquet horloge, verrouillée par un test d'architecture ; les LocalDate.now() sans fuseau ont disparu. Un site contredit encore la règle Q-2 (« une règle qui compare une date en base à une valeur calculée en Java reçoit l'instant Java »). |
| AUD-QUA-11 | Faible | Ouvert → **Fermé avec réserve** | Plus aucun exécuteur statique ; chaque exécuteur appartient à un bean, est arrêté à la destruction et le motif d'attente est factorisé et testé. |
| AUD-QUA-18 | Faible | Partiel → **Fermé avec réserve** | Fermé avec réserve : au plus 60 instructions par méthode et une complexité cyclomatique inférieure à 15 font échouer « mvn verify » (donc la CI). |
| AUD-QUA-23 | Faible | Ouvert → **Fermé avec réserve** | La cause est désormais passée comme Throwable : la pile est journalisée. |
| AUD-SEC-06 | Faible | Ouvert → **Fermé avec réserve** | Plus aucun repli « argos » : Compose refuse de démarrer sans mot de passe (vide compris). |
| AUD-SEC-25 | Faible | Ouvert → **Fermé avec réserve** | Code non modifiable par l'utilisateur d'exécution, conteneurs durcis ; parité contrôlée en CI. |
| AUD-TST-02 | Faible | Ouvert → **Fermé avec réserve** | Corrigé : seuil global bloquant et seuil par module en CI ; configuration conforme au guide Quarkus (agent + quarkus-jacoco sur le même fichier). |
| AUD-TST-03 | Faible (21/11 : Moyenne) | Ouvert → **Fermé avec réserve** | Pyramide redressée : 507 méthodes sur 1 018 (49,8 %) restent dans des classes exigeant Docker, contre 71 % au 21/11 ; la majorité tient de justesse (511 contre 507). |
| AUD-TST-04 | Faible | Partiel → **Fermé avec réserve** | Corrigé : plus aucun module sans classe de test ; un module sous 85 % / 70 % fait échouer le job serveur. |
| AUD-TST-06 | Faible | Ouvert → **Fermé avec réserve** | Corrigé et protégé : plus aucun Thread.sleep dans argos-server/src/test, règle d'architecture avec exemple négatif. |
| AUD-TST-08 | Faible | Ouvert → **Fermé avec réserve** | Corrigé : nettoyage dérivé du schéma, exhaustivité testée. |
| AUD-TST-09 | Faible | Partiel → **Fermé avec réserve** | Corrigé : client REST et 13 vues sur 13 testés en rendu. |
| AUD-TST-12 | Faible | Ouvert → **Fermé avec réserve** | Corrigé : le jar et l'image sont démarrés par des tests. |
| AUD-TST-19 | Faible | Ouvert → **Fermé avec réserve** | Corrigé : la CI teste sur le JDK de production et toujours après clean. |
| AUD-TST-20 | Faible | Ouvert → **Fermé avec réserve** | Corrigé dans la configuration : campagne automatisée à chaque pull request, plus de réutilisation silencieuse d'un serveur. |
| AUD-TST-23 | Faible | Ouvert → **Fermé avec réserve** | Corrigé dans le code : les trois écrans sont mesurés à chaque exécution du job e2e. |
| AUD-TST-26 | Faible | Ouvert → **Fermé avec réserve** | Corrigé : tsc --checkJs passe sans erreur et fait partie de npm run check (job desktop de la CI). |
| AUD-PER-07 | Faible | Ouvert → **Fermé avec réserve** | La sélection est bornée (1 001 candidats), la pertinence n'est calculée que sur les 1 000 plus récents et le total est plafonné ; un test couvre les deux côtés du plafond. |
| AUD-PER-09 | Faible | Ouvert → **Fermé avec réserve** | Les index cités par le constat existent et un test vérifie leur présence. |
| AUD-PER-11 | Faible | Ouvert → **Fermé avec réserve** | Les réponses OSV et KEV ne sont plus lues sans plafond. |
| AUD-PER-12 | Faible | Ouvert → **Fermé avec réserve** | Les réglages demandés sont présents et la compression est testée. |
| AUD-PER-20 | Faible | Ouvert → **Fermé avec réserve** | L'insertion composant par composant a disparu. |
| AUD-PER-27 | Faible | Ouvert → **Fermé avec réserve** | La faille d'exclusion pendant l'attente est corrigée et testée. |
| AUD-PER-28 | Faible | Ouvert → **Fermé avec réserve** | Les réglages et le budget de connexions sont explicites. |
| AUD-DEP-05 | Faible | Ouvert → **Fermé avec réserve** | Veille automatisée en place et effective ; Playwright à jour ; Electron 45.4.1 alors que 45.5.0 (publiée le 24/11) est la seule mineure supportée ; aucun scan CVE des dépendances Maven. |
| AUD-DEP-08 | Faible | Ouvert → **Fermé avec réserve** | Tous les services ont un contrôle de santé et des journaux bornés ; aucune garde automatique n'empêche leur retrait. |
| AUD-DEP-20 | Faible | Partiel → **Fermé avec réserve** | Garde atteignable sur chaque pull request et push sur main ; renommage détecté (D+A). |
| AUD-DEP-27 | Faible | Ouvert → **Fermé avec réserve** | Corrigé : variables obligatoires gardées, toutes les interpolations de compose.yaml décrites dans .env.example. |
| AUD-DOC-02 | Moyenne | Ouvert → **Fermé avec réserve** | Corrigé : plus aucune mention de Claude API, FreshRSS, changedetection, IdP d'entreprise ni « lot 1 » dans §1-§3, §5, §7, §10, §11 ; un seul registre des risques, renvoyé par §11.1. |
| AUD-DOC-03 | Moyenne | Partiel → **Fermé avec réserve** | Corrigé : les deux parcours imposent l'étape d'identité, la phrase « l'authentification n'est pas en place » a disparu, ARGOS_OIDC_URL et ARGOS_ADMIN_EMAIL figurent au tableau, la parité guide/configuration est gardée par un contrôle testé. |
| AUD-DOC-19 | Moyenne | Ouvert → **Fermé avec réserve** | Corrigé : plus aucune fiche « spec absente », « non archivée » ni « Changement en cours » pour une capacité archivée ; inventaire exact. |
| AUD-DOC-05 | Faible | Ouvert → **Fermé avec réserve** | Corrigé : Node.js 22.18 (^22.18.0 // >=23.6.0), Docker présenté comme requis par quarkus:dev (Keycloak des Dev Services), paragraphe « Connexion en développement » (royaume de test, alice administratrice, mot de passe = identifiant), e2e décrit comme portail web, client desktop et serveur MCP. |
| AUD-DOC-06 | Faible | Ouvert → **Fermé avec réserve** | Corrigé : D-19 se rend, et le rendu est désormais un gate de CI atteignable. |
| AUD-DOC-10 | Faible | Ouvert → **Fermé avec réserve** | Corrigé : périmètre étendu (102 → 123 fichiers, argos-server/README.md et e2e/README.md compris), chemins cités vérifiés (essai : un chemin inexistant injecté dans docs/architecture/README.md est signalé « chemin cité introuvable »), gate de CI atteignable. |
| AUD-DOC-11 | Faible | Ouvert → **Fermé avec réserve** | Corrigé : plus d'exemple non rempli ni de renvoi à un fichier absent ; rôle d'Archgate documenté. |
| AUD-DOC-20 | Faible | Ouvert → **Fermé avec réserve** | Corrigé dans arc42 §10 et les scénarios ; chiffres concordants avec le rapport généré. |
| AUD-ARC-06 | Faible (21/11 : Moyenne) | Ouvert → **Partiel** | Les trois lectures non couvertes ont disparu et un test rend visible tout nouveau couplage par le schéma (tâches 5.1 à 5.3 : accord). Désaccord sur le fond : ProprieteTablesTest admet toute lecture directe d'une table d'un module dont le lecteur dépend, si bien que 13 lectures étrangères directes subsistent sur 12 tables partagées, alors qu'ADR-0037 (Accepté) dit qu'un propriétaire « en publie les lectures étrangères par un service de lecture ou une vue SQL nommée ». Et la règle « lui seul l'écrit » d'arc42 §5.3.1 n'est pas vérifiée : le test autorise aussi un INSERT ou un UPDATE d'un module dépendant. |
| AUD-ARC-12 | Faible | Partiel | Un test de contrat existe des deux côtés et la CI l'exécute (job serveur « mvn … clean verify » avec Docker, job ihm « npm test ») ; il ne reste que 5 classes REST sur 23 sans appel de l'IHM (au lieu de 10). |
| AUD-ARC-14 | Faible | Partiel | Les ADR fondateurs cités par le constat sont acceptés : accord avec les tâches 2.1 à 2.6 cochées. Mais le même écart se reforme sur les ADR du sprint : six ADR que le code applique et que ses tests citent comme règle restent « Proposé », alors que l'index les définit comme « pas encore validée ». |
| AUD-ARC-15 | Faible | Ouvert → **Partiel** | Les points d'entrée utilisateur du profil artiste et la rubrique des préférences ont un client et une vue, avec un test de composant. |
| AUD-QUA-06 | Faible (21/11 : Moyenne) | Ouvert → **Partiel** | La factorisation est réelle pour les modules cœur, mais l'adoption reste partielle (« progressive », ADR-0037:30) : 31 fichiers hors du noyau gardent le squelette JDBC brut et quatre copies du convertisseur de date subsistent à côté de Conversions.instant. |
| AUD-SEC-07 | Faible | Ouvert → **Partiel** | Permissions corrigées ; archives toujours en clair (toutes les données, comptes Keycloak compris). |
| AUD-PER-02 | Faible (21/11 : Élevée) | Ouvert → **Partiel** | Désaccord partiel avec le suivi : l'enrichissement est réellement découplé et rattrapé, mais la corrélation, qui figure dans le titre du constat, reste synchrone, signal par signal, sur le fil de l'ingestion. |
| AUD-PER-10 | Faible | Ouvert → **Partiel** | Les listes citées sont paginées. |
| AUD-DEP-15 | Faible | Ouvert → **Partiel** | Mise à jour automatique réelle ; signature absente mais assumée par ADR-0042 (option A), branchable par deux secrets ; mais le canal de mise à jour livre un client figé sur localhost (AUD-DEP-29) et la publication n'a aucun gate (AUD-DEP-30). |
| AUD-DOC-04 | Moyenne | Partiel | Corrigé : VITE_ARGOS_API_URL, argos.apiUrl/ARGOS_API_URL, argos.oidcUrl/ARGOS_OIDC_URL et la valeur attendue (ARGOS_OIDC_ISSUER_PUBLIC) sont documentés, ainsi que la différence de sens d'ARGOS_OIDC_URL entre poste et serveur. |
| AUD-DOC-01 | Faible (21/11 : Moyenne) | Ouvert → **Partiel** | Corrigé pour l'essentiel : plus de « socle sans fonctionnalité métier », de Claude/AI Gateway, de FreshRSS séparé ni de baseline « active » ; seul le paragraphe Licence du README n'a pas été touché. |
| AUD-DOC-07 | Faible | Ouvert → **Partiel** | Partiel : trois des cinq passages sont corrigés ; le paragraphe du lot 18 du registre et la fiche profils-veille contredisent toujours le code et les tâches cochées. |
| AUD-DOC-08 | Faible | Ouvert → **Partiel** | Corrigé dans les sections Décision ; deux notes conditionnelles caduques subsistent. |
| AUD-DEP-06 | Faible | Décision documentée | Toujours arbitré par la décision H2 (tags sans digest), désormais complète pour toutes les images. |
| AUD-ARC-01 | Info | Info | Le code respecte la table : graphe acyclique, aucune arête hors table sauf vers les modules transverses, que la table admet explicitement. La concordance entre package-info et table, qui faisait partie de cette protection, ne tient plus (AUD-ARC-25). |
| AUD-ARC-08 | Info | Info | La protection tient : api ne contient toujours aucun accès JDBC ou SQL. |
| AUD-ARC-13 | Info | Info | La protection tient : la couche mise en page n'importe que vue, les polices et ses fichiers (plus MentionsObligatoires) ; le processus principal du desktop n'appelle toujours pas l'API. |
| AUD-ARC-18 | Info | Info | La protection tient et s'étend : tout paquet est sous un module de la table, et un paquet non déclaré fait échouer le test. En revanche, les package-info ne suivent plus la table (AUD-ARC-25). |
| AUD-ARC-24 | Info | Info | La protection tient et est testée : seuls les ressources statiques, /api/v1/system/info et la santé sont publics ; toute route d'API ou technique ajoutée aux chemins publics fait échouer le test. |
| AUD-QUA-14 | Info | Info | La protection tient : aucun catch vide, aucun printStackTrace, aucune exception nouvellement avalée sur le delta ; la régression signalée le 21/11 (isolation limitée à IllegalStateException) est corrigée pour les frontières inventoriées. |
| AUD-QUA-15 | Info | Info | Fermeture systématique des ressources JDBC confirmée. |
| AUD-QUA-16 | Info | Info | Typage strict tenu dans argos-ui : vue-tsc sans erreur, toujours un seul contournement réel. |
| AUD-SEC-10 | Info | Info | Tient : aucun secret réel versionné ni dans l'historique du delta. |
| AUD-SEC-11 | Info | Info | Tient. |
| AUD-SEC-12 | Info | Info | Tient : jeton OIDC obligatoire, audience vérifiée, aucun mode sans authentification. |
| AUD-SEC-13 | Info | Info | Tient : contrôle par espace dans chaque ressource ; pas d'IDOR trouvé sur les nouvelles routes (détachement de dépôt borné par project_id). |
| AUD-SEC-14 | Info | Info | Tient pour la veille web ; mais une prémisse de la preuve d'origine est tombée : il existe désormais une route de création de projet et de rattachement de dépôt (POST /api/v1/projects, /repositories). |
| AUD-SEC-15 | Info | Info | Tient. |
| AUD-SEC-16 | Info | Info | Tient : 8 outils, 8 appels garde.appeler. |
| AUD-SEC-17 | Info | Info | Tient : classes non transmissibles et secrets refusés avant transmission, sortie validée. |
| AUD-SEC-18 | Info | Info | Tient. |
| AUD-SEC-19 | Info | Info | Tient : aucun v-html, innerHTML, eval ni new Function. |
| AUD-SEC-20 | Info | Info | Tient : ports sur 127.0.0.1 par défaut, utilisateur non root, CORS limité, jeton porteur sans cookie. |
| AUD-SEC-21 | Info | Info | Tient. |
| AUD-SEC-26 | Info | Info | Tient. |
| AUD-SEC-27 | Info | Info | Tient pour les archives JORF et la RLS réglementaire ; la mention « aucune route d'import de SBOM » n'est plus vraie. |
| AUD-SEC-28 | Info | Info | Tient, exception AUD-SEC-24 levée. |
| AUD-SEC-32 | Info | Info | Tient : aucun mot de passe par défaut, inscription désactivée, ports sur 127.0.0.1, gestion 9000 non publiée. |
| AUD-TST-13 | Info | Info | Tient : npm test 276/276, vue-tsc 0 erreur, vite build OK, contrôle des licences OK. |
| AUD-TST-14 | Info | Info | Tient : npm run check vert (syntaxe 11 fichiers, typecheck 0 erreur, 92/92). |
| AUD-TST-15 | Info | Info | Tient : aucun test désactivé ni focalisé ; un seul test conditionnel serveur. |
| AUD-TST-16 | Info | Info | Tient. |
| AUD-TST-17 | Info | Info | Tient, et la réserve du 21/11 (INDISPONIBLE non testé) est levée. |
| AUD-TST-18 | Info | Info | Tient : 21 paquets io.argos, 21 entrées dans la table, exécuté par mvn verify en CI. |
| AUD-TST-21 | Info | Info | Tient : 29/29 rejoués à 01fea6d. |
| AUD-TST-22 | Info | Info | Tient. |
| AUD-TST-27 | Info | Info | Tient. |
| AUD-PER-14 | Info | Info | La protection tient : les 16 @Scheduled déclarent SKIP ; l'attente de reprise reste RUNNING, donc sous sync_run_one_running. |
| AUD-PER-15 | Info | Info | La protection tient : files bornées, délais, réponse IA plafonnée à 1 Mio. |
| AUD-PER-16 | Info | Info | La protection tient et s'est renforcée : six usages passent par la fabrique commune ; caches KEV et CERT-FR de 6 h. |
| AUD-PER-17 | Info | Info | Le bundle reste sous le seuil de 300 kB : 240,59 kB, 78,98 kB gzippé, transféré compressé. |
| AUD-PER-23 | Info | Info | La protection tient : un avis d'empreinte normalisée identique renvoie UNCHANGED sans publier SignalChanged, donc sans mise en file d'enrichissement ni corrélation. |
| AUD-PER-24 | Info | Info | La protection tient : 50 Mio, 500 Mio décompressés, 50 000 entrées, 5 Mio par fichier, 10 requêtes par minute. |
| AUD-DEP-11 | Info | Info | Tient : 6 vulnérabilités modérées, toutes via minimatch dans la chaîne electron-builder (outil de build), non bloquantes par décision. |
| AUD-DEP-12 | Info | Info | Tient pour le code livré (vue, vue-router : 0 vulnérabilité) mais npm audit passe de 0 à 5 vulnérabilités modérées, toutes dans l'outillage de test ; vite 8.4.0 et vue-router 5.5.3 publiées le 26/11 (dans le délai minimumReleaseAge de Renovate). |
| AUD-DEP-13 | Info | Info | Tient : Quarkus 3.40.1.2 sur la LTS 3.40 (support 12 mois), Java 17. |
| AUD-DEP-16 | Info | Info | Tient et s'améliore ; durcissement gardé par verifier-configuration.mjs. |
| AUD-DEP-17 | Info | Info | Tient : prod sans défaut pour base et OIDC, mailer simulé en dev/test seulement, STARTTLS exigé par défaut, aucun secret réel versionné. |
| AUD-DEP-18 | Info | Info | Tient : aucune licence copyleft forte ni non commerciale livrée ; 99 dépendances npm livrées admises. |
| AUD-DEP-23 | Info | Info | Tient : codes de sortie 1/2/0 vérifiés par les tests ; contrôle documentaire sans erreur. |
| AUD-DEP-28 | Info | Info | Tient : version exacte 26.7.4 suivie par Renovate, healthcheck port 9000, port lié à 127.0.0.1 par défaut, mémoire bornée, dépendance facultative. |
| AUD-DOC-13 | Info | Info | Tient : liens et ancres sains dans tout le dépôt ; verifier-docs passe sans et avec --rendu. |
| AUD-DOC-14 | Info | Info | Ne tient plus qu'en partie : 41/42 statuts concordants ; ADR-0042 absent d'arc42 §9.1 (nouveau constat AUD-DOC-24). |
| AUD-DOC-15 | Info | Info | Ne tient plus qu'en partie : D-13 couvre V1-V28, mais le tableau d'arc42 §8.7 saute V25 et V26 et §8.6 annonce 15 tâches pour 16 (nouveau constat AUD-DOC-25). |
| AUD-DOC-16 | Info | Info | Ne tient plus : trois versions citées par e2e/README.md et argos-server/README.md ne correspondent plus aux fichiers de verrouillage (nouveau constat AUD-DOC-26). Projets Playwright et textes de licence toujours concordants. |
| AUD-DOC-17 | Info | Info | Tient de nouveau : 8 outils MCP = « huit outils » ; inventaire OpenSpec (19 specs, 165 exigences, 2 changements actifs, 32 archivés) = dépôt ; chiffres de performance identiques entre rapport, arc42 §10 et QS-06. |
| AUD-DOC-18 | Info | Info | Tient : les tâches ouvertes archivées renvoient toujours aux recettes R-1 à R-5, toutes « à faire ». |
