# syntax=docker/dockerfile:1
# Image unique d'Argos : IHM Vue.js 3 + API Quarkus (ADR-0026).
# Construite pour x86-64 : Synology DS1522+ (AMD Ryzen R1600) et PC.

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
COPY argos-server/pom.xml ./
RUN mvn -B -q dependency:go-offline
COPY argos-server/src ./src
COPY --from=ui /src/argos-ui/dist ./src/main/resources/META-INF/resources
RUN mvn -B package -DskipTests

# 3. Exécution : JRE 17 seul, utilisateur non privilégié.
FROM eclipse-temurin:17-jre
WORKDIR /opt/argos
RUN useradd --system --uid 1001 --no-create-home argos
COPY --from=server --chown=1001:0 /src/argos-server/target/quarkus-app/ ./
USER 1001
EXPOSE 8080
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75 -Djava.util.logging.manager=org.jboss.logmanager.LogManager"
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar quarkus-run.jar"]
