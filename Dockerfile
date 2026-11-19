# syntax=docker/dockerfile:1
# Image unique d'Argos : IHM Vue.js 3 + API Quarkus (ADR-0026).
# Construite pour x86-64 : Synology DS1522+ (AMD Ryzen R1600) et PC.
# Images de base épinglées par tag, sans empreinte (digest) : décision H2 du porteur du projet,
# 2026-11-19 (docs/deploiement/synology-et-pc.md, « Images de base »).

# 1. IHM : servie par le même serveur, donc appels API relatifs (VITE_ARGOS_API_URL vide).
FROM node:22-slim AS ui
WORKDIR /src/argos-ui
COPY argos-ui/package.json argos-ui/package-lock.json ./
RUN npm ci --no-audit --no-fund
COPY argos-ui/ ./
ENV VITE_ARGOS_API_URL=
RUN npm run build

# 2. Serveur : l'IHM est copiée dans les ressources statiques de Quarkus.
#    Les tests demandent Docker (base jetable) : ils se lancent hors de l'image avec « mvn verify ».
FROM maven:3.9-eclipse-temurin-17 AS server
WORKDIR /src/argos-server
# Node.js, pour le contrôle des licences : binaire de l'étage « ui » (image officielle node:22-slim,
# licence MIT), si bien que la version de Node n'est écrite qu'une fois.
COPY --from=ui /usr/local/bin/node /usr/local/bin/node
COPY argos-server/pom.xml ./
RUN mvn -B -q -P licences dependency:go-offline
# Contrôle des licences des dépendances livrées (décision G2 du porteur du projet, 2026-11-19 ;
# ADR-0021 points 4 et 6) : profil « licences » (inventaire CycloneDX, puis
# scripts/verifier-licences-serveur.mjs). Le build de l'image échoue si une dépendance n'a aucune
# licence déclarée ou porte une licence non admise ; target/THIRD-PARTY.txt est produit. Le script copie
# aussi dans target/licenses/ les textes versionnés (argos-server/licenses/) des licences retenues :
# Apache-2.0, MIT, BSD (variantes présentes) et EPL-2.0 (décision H1 du porteur du projet, 2026-11-19).
COPY argos-server/licenses ./licenses
COPY scripts/verifier-licences-serveur.mjs ../scripts/
RUN mvn -B -P licences validate
COPY argos-server/src ./src
COPY --from=ui /src/argos-ui/dist ./src/main/resources/META-INF/resources
RUN mvn -B package -DskipTests

# 3. Exécution : JRE 17 seul, utilisateur non privilégié.
#    /opt/argos/licenses : avis NOTICE du serveur, liste générée des dépendances tierces, de leurs
#    licences, des titulaires des droits et des adresses du code source EPL-2.0 (THIRD-PARTY.txt), et
#    textes des licences retenues (Apache-2.0, MIT, MIT-0, BSD-2-Clause, BSD-3-Clause, EPL-2.0, JDOM).
#    Ce dossier (0755) et ces avis (0444) appartiennent à root : l'utilisateur d'exécution les lit
#    sans pouvoir les modifier.
FROM eclipse-temurin:17-jre
WORKDIR /opt/argos
RUN useradd --system --uid 1001 --no-create-home argos && mkdir -m 0755 licenses
COPY --from=server --chown=1001:0 /src/argos-server/target/quarkus-app/ ./
COPY --chmod=0444 argos-server/NOTICE ./licenses/
COPY --from=server --chmod=0444 /src/argos-server/target/THIRD-PARTY.txt /src/argos-server/target/licenses/ ./licenses/
USER 1001
EXPOSE 8080
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75 -Djava.util.logging.manager=org.jboss.logmanager.LogManager"
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar quarkus-run.jar"]
