# argos-server

Monolithe modulaire Quarkus d'ARGOS (Java 17 minimum, ADR-0019). Démarrage local et tests : voir le [README du dépôt](../README.md#démarrage-local).

| Commande | Effet |
|---|---|
| `mvn quarkus:dev` | serveur de développement sur `http://localhost:8080` (OpenAPI sur `/q/openapi`, santé sur `/q/health`) |
| `mvn -B -f argos-server/pom.xml verify` | compilation, tests unitaires, d'intégration et d'architecture (Docker requis : base PostgreSQL jetable des Dev Services) |
| `mvn -B -f argos-server/pom.xml -P licences validate` | liste des dépendances tierces livrées, de leurs licences et de leurs avis NOTICE (`target/THIRD-PARTY.txt`) ; échec sur une licence absente ou non admise |

## Licences des dépendances

[DÉCISION — porteur du projet, 2026-11-19 (F3, révisée par G1)] Outil des avis de licence du serveur : le [CycloneDX Maven Plugin](https://github.com/CycloneDX/cyclonedx-maven-plugin) (Apache-2.0) et le script `scripts/verifier-licences-serveur.mjs` (liste blanche) produisent la liste des dépendances livrées et de leurs licences ; `org.codehaus.mojo:license-maven-plugin` n'est pas utilisé ; `argos-server/NOTICE` garde les avis que les licences Apache-2.0 imposent de reproduire (au moins `maven-artifact`) et renvoie vers le fichier généré ; la commande est documentée.

Origine de la révision : la décision F3 désignait `license-maven-plugin` comme sous licence Apache-2.0, alors que son POM (2.7.1 dans le dépôt local, 2.9.0 sur Maven Central) déclare la **GNU Lesser General Public License, version 3.0**. CycloneDX est aussi le format SBOM retenu par ARGOS (ADR-0011 proposé).

[DÉCISION — porteur du projet, 2026-11-19 (G2)] Le contrôle des licences s'exécute au build de l'image Docker du serveur : le build échoue si une licence est absente ou non admise. L'image embarque `argos-server/NOTICE` et `THIRD-PARTY.txt` (Apache License 2.0, section 4 d). La commande dédiée `mvn -P licences validate` reste disponible ; `mvn verify` n'est pas modifié.

[DÉCISION — porteur du projet, 2026-11-19 (G3)] EPL-2.0 est admise pour les neuf API Jakarta nommées que Quarkus embarque (`jakarta.annotation-api`, `jakarta.el-api`, `jakarta.interceptor-api`, `jakarta.json-api`, `jakarta.resource-api`, `jakarta.transaction-api`, `jakarta.ws.rs-api`, `parsson`, `expressly`), et pour elles seules : toute autre dépendance sous EPL fait échouer le contrôle. Ces archives sont livrées sans modification ; l'EPL-2.0 est un copyleft faible au niveau du fichier. La décision s'applique dans le cadre d'ADR-0021 point 4 (copyleft sur décision explicite), sans modifier cet ADR.

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

Le profil `licences` n'est pas rattaché au cycle `mvn verify` : le but `makeBom` du plugin CycloneDX se déclare « requiresOnline » et refuse le mode hors ligne (`mvn -o`), même avec un dépôt local complet. `mvn verify` reste donc inchangé et sans accès réseau supplémentaire. La commande dédiée exige le mode en ligne de Maven ; elle ne télécharge que les plugins absents du dépôt local, et le script de vérification n'accède pas au réseau. Le build de l'image Docker exécute la même commande (décision G2), en ligne lui aussi ; hors de l'image, elle se lance à la main avant une livraison.

État au 2026-11-19 : 194 dépendances inventoriées, aucune licence absente ni non admise ; 26 archives portent un avis NOTICE. Neuf API Jakarta (`jakarta.annotation-api`, `jakarta.el-api`, `jakarta.interceptor-api`, `jakarta.json-api`, `jakarta.resource-api`, `jakarta.transaction-api`, `jakarta.ws.rs-api`, `parsson`, `expressly`), apportées par la plateforme Quarkus, sont proposées sous EPL-2.0 ou GPL-2.0 avec exception Classpath : la licence retenue est EPL-2.0 (décision G3). Huit modules Eclipse Vert.x, proposés sous EPL ou Apache-2.0, retiennent Apache-2.0.

### Contrôle au build de l'image Docker

Le `Dockerfile` du dépôt exécute `mvn -B -P licences validate` dans l'étage `server`, avant la compilation : Node.js y est le binaire de l'image officielle `node:22-slim` (licence MIT), copié dans l'image de build `maven:3.9-eclipse-temurin-17`. Le script de contrôle entre dans le contexte de build par `.dockerignore`. Une licence absente ou non admise arrête `docker build` (ou `docker compose up -d --build`) avec la liste des dépendances en écart.

L'image d'exécution contient, dans `/opt/argos/licenses/` :

| Fichier | Contenu |
|---|---|
| `NOTICE` | copie de `argos-server/NOTICE` |
| `THIRD-PARTY.txt` | liste générée au build de l'image : dépendances livrées, licences, avis NOTICE de leurs archives |
| `Apache-2.0.txt` | texte de l'Apache License 2.0 (`argos-server/licenses/Apache-2.0.txt`), cité par `NOTICE` |

Lecture sans démarrer Argos : `docker run --rm --entrypoint cat argos:local /opt/argos/licenses/THIRD-PARTY.txt` (guide [`docs/deploiement/synology-et-pc.md`](../docs/deploiement/synology-et-pc.md#avis-de-licence-de-limage)).

Vérification du 2026-11-19 (Docker Engine 29.9.3) : `docker build` réussit avec « 194 dépendances inventoriées, 26 avis NOTICE reproduits » et « Aucune licence absente ni non admise » ; `docker run --rm --entrypoint ls <image> -l /opt/argos/licenses` liste `Apache-2.0.txt`, `NOTICE` et `THIRD-PARTY.txt`. Un inventaire CycloneDX factice (GPL-3.0-only, EPL-2.0 sur un composant hors des neuf API Jakarta, licence absente), donné au script dans l'étage `server`, fait échouer `docker build` (code 1, six écarts listés).
