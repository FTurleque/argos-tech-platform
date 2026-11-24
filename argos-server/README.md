# argos-server

Monolithe modulaire Quarkus d'ARGOS (Java 17 minimum, ADR-0019). Démarrage local et tests : voir le [README du dépôt](../README.md#démarrage-local).

| Commande | Effet |
|---|---|
| `mvn quarkus:dev` | serveur de développement sur `http://localhost:8080` (OpenAPI sur `/q/openapi`, santé sur `/q/health`) |
| `mvn -B -f argos-server/pom.xml clean verify` | compilation, tests unitaires, d'intégration et d'architecture (Docker requis : base PostgreSQL jetable des Dev Services), rapport de couverture JaCoCo (`target/site/jacoco/`) et seuil global de 85 % des lignes et 70 % des branches |
| `mvn -B -f argos-server/pom.xml -P licences validate` | liste des dépendances tierces livrées, de leurs licences et de leurs avis NOTICE (`target/THIRD-PARTY.txt`) ; échec sur une licence absente ou non admise |

## Licences des dépendances

[DÉCISION — porteur du projet, 2026-11-19 (F3, révisée par G1)] Outil des avis de licence du serveur : le [CycloneDX Maven Plugin](https://github.com/CycloneDX/cyclonedx-maven-plugin) (Apache-2.0) et le script `scripts/verifier-licences-serveur.mjs` (liste blanche) produisent la liste des dépendances livrées et de leurs licences ; `org.codehaus.mojo:license-maven-plugin` n'est pas utilisé ; `argos-server/NOTICE` garde les avis que les licences Apache-2.0 imposent de reproduire (au moins `maven-artifact`) et renvoie vers le fichier généré ; la commande est documentée.

Origine de la révision : la décision F3 désignait `license-maven-plugin` comme sous licence Apache-2.0, alors que son POM (2.7.1 dans le dépôt local, 2.9.0 sur Maven Central) déclare la **GNU Lesser General Public License, version 3.0**. CycloneDX est aussi le format SBOM retenu par ARGOS (ADR-0011 proposé).

[DÉCISION — porteur du projet, 2026-11-19 (G2)] Le contrôle des licences s'exécute au build de l'image Docker du serveur : le build échoue si une licence est absente ou non admise. L'image embarque `argos-server/NOTICE` et `THIRD-PARTY.txt` (Apache License 2.0, section 4 d). La commande dédiée `mvn -P licences validate` reste disponible ; `mvn verify` n'est pas modifié.

[DÉCISION — porteur du projet, 2026-11-19 (G3)] EPL-2.0 est admise pour les neuf API Jakarta nommées que Quarkus embarque (`jakarta.annotation-api`, `jakarta.el-api`, `jakarta.interceptor-api`, `jakarta.json-api`, `jakarta.resource-api`, `jakarta.transaction-api`, `jakarta.ws.rs-api`, `parsson`, `expressly`), et pour elles seules : toute autre dépendance sous EPL fait échouer le contrôle. Ces archives sont livrées sans modification ; l'EPL-2.0 est un copyleft faible au niveau du fichier. La décision s'applique dans le cadre d'ADR-0021 point 4 (copyleft sur décision explicite), sans modifier cet ADR.

[DÉCISION — porteur du projet, 2026-11-19 (H1)] L'image Docker du serveur livre aussi les textes des licences MIT, BSD (variantes présentes) et EPL-2.0 des dépendances du serveur, dans `/opt/argos/licenses` (propriété root, mode 0444, comme les avis existants) ; les avis npm de l'IHM ne sont pas ajoutés.

[DÉCISION — porteur du projet, 2026-11-19 (H2)] Les images de base Docker restent épinglées par tag, sans empreinte (digest) : voir le guide [`docs/deploiement/synology-et-pc.md`](../docs/deploiement/synology-et-pc.md#images-de-base).

Mise en œuvre : `mvn -B -f argos-server/pom.xml -P licences validate` exécute le profil `licences` du `pom.xml`, en deux étapes de la phase `validate` :

1. le plugin [CycloneDX Maven Plugin](https://github.com/CycloneDX/cyclonedx-maven-plugin) 2.9.3 (Apache-2.0) écrit `argos-server/target/bom.json`, inventaire CycloneDX des dépendances compile et runtime, directes et transitives, sans les dépendances de test ;
2. le plugin `exec-maven-plugin` 3.6.4 (Apache-2.0) lance `node scripts/verifier-licences-serveur.mjs`, qui lit cet inventaire et les archives du dépôt Maven local de Maven, sans accès réseau, et écrit `argos-server/target/THIRD-PARTY.txt` : pour chaque dépendance, ses licences déclarées et la licence retenue (section 1), le contenu des fichiers `META-INF/NOTICE*` de chaque archive (section 2), puis, pour chaque texte de licence, les dépendances qu'il couvre avec les titulaires des droits (licences MIT, BSD et JDOM) ou l'adresse de leur code source (EPL-2.0, section 3.2 de la licence) (section 3). Il copie dans `argos-server/target/licenses/` les textes versionnés sous `argos-server/licenses/` des seules licences retenues (décision H1).

Le script s'utilise aussi seul sur un inventaire existant : `node scripts/verifier-licences-serveur.mjs [bom.json] [--depot <dépôt Maven local>]` (dépôt par défaut : `MAVEN_REPO_LOCAL`, sinon `~/.m2/repository`). Les textes sont copiés dans le dossier `licenses/` voisin de l'inventaire, vidé au préalable ; le script refuse un inventaire placé directement dans `argos-server/`, dont le dossier `licenses/` est celui des textes versionnés.

Règles du contrôle (ADR-0021 point 4 : licences permissives admises d'office, tout copyleft sur décision explicite) :

- liste blanche : seules les licences permissives recensées dans le tableau `ADMISES` du script sont acceptées (Apache-2.0, MIT, MIT-0, BSD-2-Clause, BSD-3-Clause, 0BSD, EDL-1.0, ISC, Zlib, UPL-1.0, Unlicense, CC0-1.0, domaine public, licence de JDOM) ; une licence inconnue fait échouer le build ;
- plusieurs licences déclarées par un même POM s'appliquent ensemble : chacune doit être admise. Seule une expression SPDX `OR`, ou un composant du tableau `CHOIX` dont le projet amont publie une double licence au choix (Eclipse Vert.x, API Jakarta EE, Parsson, Expressly), permet de retenir une seule licence ;
- une licence soumise à décision (tableau `SOUS_RESERVE`, aujourd'hui EPL-2.0) n'est acceptée que pour les composants qui y sont nommés ;
- chaque licence retenue a un texte versionné (tableau `TEXTES` du script, fichiers de `argos-server/licenses/`), sauf le domaine public, CC0-1.0 et Unlicense (tableau `SANS_TEXTE`) ; une licence admise sans texte versionné fait échouer le build ;
- une licence à attribution (MIT, BSD-2-Clause, BSD-3-Clause, licence de JDOM) exige au moins un titulaire des droits, lu dans les lignes « Copyright » des fichiers `META-INF/**/LICENSE*` de l'archive, y compris ceux des bibliothèques qu'elle intègre (pilote PostgreSQL : bibliothèques OnGres), ou, pour une archive sans fichier LICENSE, dans le tableau `TITULAIRES` (relevé dans ses sources publiées : `org.crac`) ;
- le build échoue aussi si une dépendance n'a aucune licence déclarée ou si son archive manque au dépôt local.

Textes versionnés sous `argos-server/licenses/` (décision H1 ; `JDOM.txt` : décision ci-dessous) :

| Fichier | Licence | Origine du texte | Dépendances couvertes au 2026-11-19 |
|---|---|---|---|
| `Apache-2.0.txt` | Apache License 2.0 | texte de l'Apache Software Foundation | 176 dépendances |
| `MIT.txt` | MIT | modèle SPDX `MIT` (ligne « Copyright » à compléter par les titulaires listés) | `jsoup`, `slf4j-api` |
| `MIT-0.txt` | MIT No Attribution | modèle SPDX `MIT-0` | `reactive-streams` |
| `BSD-2-Clause.txt` | BSD 2-Clause | modèle SPDX `BSD-2-Clause` | `postgresql` (et les bibliothèques OnGres qu'il intègre), `crac` |
| `BSD-3-Clause.txt` | BSD 3-Clause | modèle SPDX `BSD-3-Clause` | `jakarta.activation-api`, `jakarta.xml.bind-api` |
| `EPL-2.0.txt` | Eclipse Public License 2.0 | texte de la Fondation Eclipse, partie EPL-2.0 du fichier `META-INF/LICENSE.md` de `jakarta.annotation-api` 3.0.0 (sans l'annexe GPL) | les neuf API Jakarta de la décision G3 |
| `JDOM.txt` | licence de JDOM (type BSD) | fichier `META-INF/LICENSE.txt` de `jdom2` 2.0.6.1 | `jdom2` |

[DÉCISION — porteur du projet, 2026-11-19] Le texte de la licence de JDOM est livré avec les textes MIT, BSD et EPL-2.0 : sa clause 2 impose de reproduire l'avis de droit d'auteur et l'avertissement dans la documentation d'une distribution binaire. Le domaine public (`jboss-transaction-spi`) ne demande aucun texte.

Le fichier généré reste dans `target/`, non versionné : il suit les dépendances réellement résolues, sans copie à tenir à jour à la main, et `argos-server/NOTICE` y renvoie.

Le profil `licences` n'est pas rattaché au cycle `mvn verify` : le but `makeBom` du plugin CycloneDX se déclare « requiresOnline » et refuse le mode hors ligne (`mvn -o`), même avec un dépôt local complet. `mvn verify` reste donc inchangé et sans accès réseau supplémentaire. La commande dédiée exige le mode en ligne de Maven ; elle ne télécharge que les plugins absents du dépôt local, et le script de vérification n'accède pas au réseau. Le build de l'image Docker exécute la même commande (décision G2), en ligne lui aussi. L'intégration continue la lance aussi sur toute pull request ou tout push sur `main` qui modifie `argos-server/pom.xml` ou `scripts/verifier-licences-serveur.mjs` (tâche « Licences », [arc42 §8.11](../docs/architecture/arc42/08-concepts-transverses.md#intégration-continue)). Le script est testé sur des inventaires factices par `scripts/verifier-licences-serveur.test.mjs` (dépendance GPL et licence absente refusées, inventaire absent en erreur, inventaire conforme accepté), exécuté par `node --test scripts/*.test.mjs` à chaque exécution de l'intégration continue.

État au 2026-11-19 : 194 dépendances inventoriées, aucune licence absente ni non admise ; 26 archives portent un avis NOTICE. Neuf API Jakarta (`jakarta.annotation-api`, `jakarta.el-api`, `jakarta.interceptor-api`, `jakarta.json-api`, `jakarta.resource-api`, `jakarta.transaction-api`, `jakarta.ws.rs-api`, `parsson`, `expressly`), apportées par la plateforme Quarkus, sont proposées sous EPL-2.0 ou GPL-2.0 avec exception Classpath : la licence retenue est EPL-2.0 (décision G3). Huit modules Eclipse Vert.x, proposés sous EPL ou Apache-2.0, retiennent Apache-2.0.

### Contrôle au build de l'image Docker

Le `Dockerfile` du dépôt exécute `mvn -B -P licences validate` dans l'étage `server`, avant la compilation : Node.js y est le binaire de l'étage `ui` (image officielle `node:22-slim`, licence MIT), copié dans l'image de build `maven:3.9-eclipse-temurin-17`. Le script de contrôle entre dans le contexte de build par `.dockerignore` ; les textes de `argos-server/licenses/` sont copiés dans l'étage `server` avant le contrôle. Une licence absente ou non admise arrête `docker build` (ou `docker compose up -d --build`) avec la liste des dépendances en écart.

L'image d'exécution contient, dans `/opt/argos/licenses/` :

| Fichier | Contenu |
|---|---|
| `NOTICE` | copie de `argos-server/NOTICE` |
| `THIRD-PARTY.txt` | liste générée au build de l'image : dépendances livrées, licences, avis NOTICE de leurs archives, titulaires des droits et adresses du code source EPL-2.0 |
| `Apache-2.0.txt` | texte de l'Apache License 2.0, cité par `NOTICE` |
| `MIT.txt`, `MIT-0.txt`, `BSD-2-Clause.txt`, `BSD-3-Clause.txt`, `EPL-2.0.txt`, `JDOM.txt` | textes des autres licences retenues (décision H1 ; `JDOM.txt` : décision du porteur du projet, 2026-11-19), copiés depuis `argos-server/licenses/` par le contrôle des licences |

Le dossier (mode 0755) et chaque fichier (mode 0444) appartiennent à root. Seuls les textes des licences effectivement retenues entrent dans l'image : une licence qui disparaît des dépendances retire son texte au build suivant.

Lecture sans démarrer Argos : `docker run --rm --entrypoint cat argos:local /opt/argos/licenses/THIRD-PARTY.txt` (guide [`docs/deploiement/synology-et-pc.md`](../docs/deploiement/synology-et-pc.md#avis-de-licence-de-limage)).

Vérification du 2026-11-19 (Docker Engine 29.9.3) : `docker build` réussit avec « 194 dépendances inventoriées, 26 avis NOTICE reproduits », « 7 textes de licence copiés » et « Aucune licence absente ni non admise » ; `docker run --rm --entrypoint ls <image> -l /opt/argos/licenses` liste `Apache-2.0.txt`, `BSD-2-Clause.txt`, `BSD-3-Clause.txt`, `EPL-2.0.txt`, `JDOM.txt`, `MIT-0.txt`, `MIT.txt`, `NOTICE` et `THIRD-PARTY.txt`, propriété de root, en mode 0444, dans un dossier 0755 ; l'utilisateur d'UID 1001 les lit sans pouvoir les modifier. Un inventaire CycloneDX factice (GPL-3.0-only, EPL-2.0 sur un composant hors des neuf API Jakarta, licence absente), donné au script dans l'étage `server`, fait échouer `docker build` (code 1, six écarts listés).
