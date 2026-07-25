# syntax=docker/dockerfile:1
#
# Image de production du backend ForsaLaw (Spring Boot).
# Build : docker build -t forsalaw-backend ./backend
#
# Java 17 partout : c'est la cible de pom.xml (<java.version>17</java.version>) et la version
# utilisee par la CI. Utiliser un JDK different ici ferait diverger l'image du binaire teste.

# ─── Etape 1 : compilation ──────────────────────────────────────────────────
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /build

# Le pom seul d'abord : tant qu'il ne change pas, la couche des dependances est reutilisee
# et un simple changement de code ne retelecharge pas tout le referentiel Maven.
COPY pom.xml .
RUN mvn -B dependency:go-offline

COPY src ./src

# Tests volontairement ignores ici : la suite repose sur Testcontainers, qui exige un daemon
# Docker indisponible a l'interieur d'un build d'image. La CI, elle, execute `mvn verify`
# avec la totalite des tests avant que cette image ne soit construite.
RUN mvn -B package -DskipTests

# ─── Etape 2 : execution ────────────────────────────────────────────────────
FROM eclipse-temurin:17-jre-alpine AS runtime

# Utilisateur non privilegie : une faille d'execution de code ne donne pas root dans le conteneur.
RUN addgroup -S appgroup && adduser -S appuser -G appgroup

WORKDIR /app

# *.jar exclut le .jar.original laisse par spring-boot-maven-plugin.
COPY --from=build --chown=appuser:appgroup /build/target/*.jar app.jar

USER appuser

# Le port par defaut de l'application est 8081 ; on l'aligne sur 8080 a l'interieur du conteneur.
ENV SERVER_PORT=8080

# MaxRAMPercentage : le JVM dimensionne son tas d'apres la limite memoire du CONTENEUR et non
# d'apres la RAM de l'hote. Sans cela, un conteneur limite a 512 Mo se fait tuer (OOM) par le noyau.
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75.0"

EXPOSE 8080

# `exec` est indispensable : sans lui le JVM tourne comme fils du shell, ne recoit jamais
# SIGTERM, et chaque `docker stop` se solde par un kill brutal apres 10 s (connexions coupees,
# transactions interrompues). Avec `exec`, le JVM devient PID 1 et s'arrete proprement.
# La forme shell est conservee pour que $JAVA_OPTS soit developpe au demarrage.
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]
