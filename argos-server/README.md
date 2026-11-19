# argos-server

Monolithe modulaire Quarkus d'ARGOS (Java 17 minimum, ADR-0019). Démarrage local et tests : voir le [README du dépôt](../README.md#démarrage-local).

| Commande | Effet |
|---|---|
| `mvn quarkus:dev` | serveur de développement sur `http://localhost:8080` (OpenAPI sur `/q/openapi`, santé sur `/q/health`) |
| `mvn -B -f argos-server/pom.xml verify` | compilation, tests unitaires, d'intégration et d'architecture (Docker requis : base PostgreSQL jetable des Dev Services) |
| `mvn -B -f argos-server/pom.xml -P licences validate` | liste des dépendances tierces livrées, de leurs licences et de leurs avis NOTICE (`target/THIRD-PARTY.txt`) ; échec sur une licence absente ou non admise |

## Licences des dépendances

[DÉCISION — porteur du projet, 2026-11-19 (F3)] Avis de licence du serveur : générés au build par un plugin Maven gratuit (`org.codehaus.mojo:license-maven-plugin`, désigné comme sous licence Apache-2.0, licence à vérifier), qui produit la liste des dépendances et de leurs licences ; `argos-server/NOTICE` garde les avis que les licences Apache-2.0 imposent de reproduire (au moins `maven-artifact`) et renvoie vers le fichier généré ; la commande est documentée.

Vérification de la licence de l'outil désigné : le POM de `org.codehaus.mojo:license-maven-plugin` (2.7.1 dans le dépôt local, 2.9.0 sur Maven Central) déclare la **GNU Lesser General Public License, version 3.0**, et non l'Apache-2.0. La substitution décrite ci-dessous en découle ([PROPOSITION] plus bas).

Mise en œuvre : `mvn -B -f argos-server/pom.xml -P licences validate` exécute le profil `licences` du `pom.xml`, en deux étapes de la phase `validate` :

1. le plugin [CycloneDX Maven Plugin](https://github.com/CycloneDX/cyclonedx-maven-plugin) 2.9.3 (Apache-2.0) écrit `argos-server/target/bom.json`, inventaire CycloneDX des dépendances compile et runtime, directes et transitives, sans les dépendances de test ;
2. le plugin `exec-maven-plugin` 3.6.4 (Apache-2.0) lance `node scripts/verifier-licences-serveur.mjs`, qui lit cet inventaire et les archives du dépôt Maven local de Maven, sans accès réseau, et écrit `argos-server/target/THIRD-PARTY.txt` : pour chaque dépendance, ses licences déclarées et la licence retenue, puis le contenu des fichiers `META-INF/NOTICE*` de chaque archive.

Le script s'utilise aussi seul sur un inventaire existant : `node scripts/verifier-licences-serveur.mjs [bom.json] [--depot <dépôt Maven local>]` (dépôt par défaut : `MAVEN_REPO_LOCAL`, sinon `~/.m2/repository`).

Règles du contrôle (ADR-0021 point 4 : licences permissives admises d'office, tout copyleft sur décision explicite) :

- liste blanche : seules les licences permissives recensées dans le tableau `ADMISES` du script sont acceptées (Apache-2.0, MIT, MIT-0, BSD-2-Clause, BSD-3-Clause, 0BSD, EDL-1.0, ISC, Zlib, UPL-1.0, Unlicense, CC0-1.0, domaine public, licence de JDOM) ; une licence inconnue fait échouer le build ;
- plusieurs licences déclarées par un même POM s'appliquent ensemble : chacune doit être admise. Seule une expression SPDX `OR`, ou un composant du tableau `CHOIX` dont le projet amont publie une double licence au choix (Eclipse Vert.x, API Jakarta EE, Parsson, Expressly), permet de retenir une seule licence ;
- une licence soumise à décision (tableau `SOUS_RESERVE`, aujourd'hui EPL-2.0) n'est acceptée que pour les composants qui y sont nommés ;
- le build échoue aussi si une dépendance n'a aucune licence déclarée ou si son archive manque au dépôt local.

Le fichier généré reste dans `target/`, non versionné : il suit les dépendances réellement résolues, sans copie à tenir à jour à la main, et `argos-server/NOTICE` y renvoie.

Le profil `licences` n'est pas rattaché au cycle `mvn verify` : le but `makeBom` du plugin CycloneDX se déclare « requiresOnline » et refuse le mode hors ligne (`mvn -o`), même avec un dépôt local complet. `mvn verify` reste donc inchangé et sans accès réseau supplémentaire. La commande dédiée exige le mode en ligne de Maven ; elle ne télécharge que les plugins absents du dépôt local, et le script de vérification n'accède pas au réseau. Aucune chaîne d'intégration continue n'existe dans le dépôt ; la commande se lance à la main avant une livraison.

État au 2026-11-19 : 194 dépendances inventoriées, aucune licence absente ni non admise ; 26 archives portent un avis NOTICE. Neuf API Jakarta (`jakarta.annotation-api`, `jakarta.el-api`, `jakarta.interceptor-api`, `jakarta.json-api`, `jakarta.resource-api`, `jakarta.transaction-api`, `jakarta.ws.rs-api`, `parsson`, `expressly`), apportées par la plateforme Quarkus, sont proposées sous EPL-2.0 ou GPL-2.0 avec exception Classpath : la licence retenue est EPL-2.0. Huit modules Eclipse Vert.x, proposés sous EPL ou Apache-2.0, retiennent Apache-2.0.

[PROPOSITION] Outil : faute de licence permissive pour `license-maven-plugin` (LGPL-3.0), le CycloneDX Maven Plugin (Apache-2.0), qui produit le format SBOM déjà retenu par ARGOS (CycloneDX, ADR-0011 proposé), le remplace, complété par le script de vérification. ADR-0021 point 4 vise les dépendances livrées, et un plugin de build n'est pas livré : l'adoption de `license-maven-plugin` comme outil de build non livré (but `add-third-party`, `failOnBlacklist`, `useRepositoryMissingFiles=false`) reste possible si le porteur du projet le décide.

[PROPOSITION] EPL-2.0 (copyleft faible, au niveau du fichier) est admise pour les neuf archives Jakarta non modifiées ci-dessus, et pour elles seules, à confirmer au regard d'ADR-0021 point 4.

[PROPOSITION] Livraison : l'image Docker (`Dockerfile`) n'embarque aujourd'hui ni `argos-server/NOTICE` ni `THIRD-PARTY.txt`, alors que l'Apache License 2.0 (section 4 d) demande de fournir les avis NOTICE à la redistribution ; leur copie dans l'image et l'exécution du profil `licences` dans la chaîne de publication restent à décider.
