# argos-server

Monolithe modulaire Quarkus d'ARGOS (Java 17 minimum, ADR-0019). Démarrage local et tests : voir le [README du dépôt](../README.md#démarrage-local).

| Commande | Effet |
|---|---|
| `mvn quarkus:dev` | serveur de développement sur `http://localhost:8080` (OpenAPI sur `/q/openapi`, santé sur `/q/health`) |
| `mvn -B -f argos-server/pom.xml verify` | compilation, tests unitaires, d'intégration et d'architecture (Docker requis : base PostgreSQL jetable des Dev Services) |
| `mvn -B -f argos-server/pom.xml -P licences validate` puis `node scripts/verifier-licences-serveur.mjs` | liste des dépendances tierces livrées, de leurs licences et de leurs avis NOTICE ; échec sur une licence non permissive |

## Licences des dépendances

[DÉCISION — porteur du projet, 2026-11-19 (F3)] Les avis de licence du serveur sont générés à partir du build par un plugin Maven gratuit, qui produit la liste des dépendances et de leurs licences. `argos-server/NOTICE` garde les avis que l'Apache License 2.0 impose de reproduire pour les dépendances introduites par une décision du projet (`maven-artifact`) et renvoie au fichier généré.

Mise en œuvre :

1. `mvn -B -f argos-server/pom.xml -P licences validate` exécute le profil `licences` du `pom.xml` : le plugin [CycloneDX Maven Plugin](https://github.com/CycloneDX/cyclonedx-maven-plugin) 2.9.3 (Apache-2.0, dernière version stable au 2026-11-19) écrit `argos-server/target/bom.json`, inventaire CycloneDX des dépendances compile et runtime, directes et transitives, sans les dépendances de test.
2. `node scripts/verifier-licences-serveur.mjs` lit cet inventaire et les archives du dépôt Maven local (`MAVEN_REPO_LOCAL`, sinon `~/.m2/repository`, ou l'option `--depot`), sans accès réseau, et écrit `argos-server/target/THIRD-PARTY.txt` : pour chaque dépendance, ses licences déclarées et la licence retenue, puis le contenu des fichiers `META-INF/NOTICE*` de chaque archive.
3. Le script se termine en échec (code 1) si une dépendance n'a aucune licence déclarée, si son archive est absente du dépôt local, ou si toutes ses licences sont non permissives : GPL, LGPL, AGPL, MPL, SSPL, BUSL, CDDL, ou licence restreignant l'usage commercial (ADR-0021 point 4). Une dépendance proposée au choix sous plusieurs licences est acceptée si l'une d'elles est admise ; c'est la licence retenue.

Le fichier généré reste dans `target/`, non versionné : il suit les dépendances réellement résolues, sans copie à tenir à jour à la main, et `argos-server/NOTICE` y renvoie.

Le profil `licences` n'est pas rattaché au cycle `mvn verify` : le but `makeBom` du plugin CycloneDX se déclare « requiresOnline » et échoue en mode hors ligne (`mvn -o`). `mvn verify` reste donc inchangé et sans accès réseau supplémentaire. La commande dédiée ne demande le réseau que pour la résolution habituelle des artefacts, au premier téléchargement du plugin.

État au 2026-11-19 : 194 dépendances inventoriées, aucune licence absente ni non permissive retenue ; 26 archives portent un avis NOTICE. Neuf API Jakarta (`jakarta.annotation-api`, `jakarta.el-api`, `jakarta.interceptor-api`, `jakarta.json-api`, `jakarta.resource-api`, `jakarta.transaction-api`, `jakarta.ws.rs-api`, `parsson`, `expressly`), apportées par la plateforme Quarkus, sont proposées sous EPL-2.0 ou GPL-2.0 avec exception Classpath : la licence retenue est EPL-2.0.

[PROPOSITION] Outil : le plugin désigné par la décision F3, `org.codehaus.mojo:license-maven-plugin` (2.9.0, dernière version stable), est distribué sous **LGPL-3.0** (POM publié sur Maven Central), et non sous Apache-2.0. Faute de pouvoir faire confirmer un outil sous licence non permissive, le CycloneDX Maven Plugin (Apache-2.0), qui produit le format SBOM déjà retenu par ARGOS (CycloneDX, ADR-0011 proposé), le remplace, complété par le script de vérification. À confirmer par le porteur du projet : maintien de cette substitution, ou adoption de `license-maven-plugin` comme outil de build non livré (but `add-third-party`, `failOnBlacklist`, `useRepositoryMissingFiles=false`).

[PROPOSITION] EPL-2.0 (copyleft faible, au niveau du fichier) est admise pour les archives non modifiées de la plateforme Quarkus ci-dessus, à confirmer au regard d'ADR-0021 point 4.

[PROPOSITION] Livraison : l'image Docker (`Dockerfile`) n'embarque aujourd'hui ni `argos-server/NOTICE` ni `THIRD-PARTY.txt` ; leur copie dans l'image et l'exécution du profil `licences` dans la chaîne de publication restent à décider.
