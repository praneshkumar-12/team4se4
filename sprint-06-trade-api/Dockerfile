# syntax=docker/dockerfile:1

# ---------------------------------------------------------------------------
# Build stage: Maven + JDK 21. The Sprint 5 domain is a separate Maven module,
# resolved from the local repository, so this stage installs it first and then
# builds the service. The build context is the repository root because both
# modules are needed here and nowhere in the final image.
# ---------------------------------------------------------------------------
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /workspace

# Resolve dependencies against the two POMs first, so an unrelated source edit
# does not invalidate the cached dependency layer.
COPY sprint-05-domain-engine/pom.xml sprint-05-domain-engine/pom.xml
COPY sprint-06-trade-api/pom.xml     sprint-06-trade-api/pom.xml
RUN mvn -q -f sprint-05-domain-engine/pom.xml dependency:go-offline

COPY sprint-05-domain-engine sprint-05-domain-engine
RUN mvn -q -f sprint-05-domain-engine/pom.xml install -DskipTests

RUN mvn -q -f sprint-06-trade-api/pom.xml dependency:go-offline
COPY sprint-06-trade-api sprint-06-trade-api
RUN mvn -q -f sprint-06-trade-api/pom.xml package -DskipTests

# ---------------------------------------------------------------------------
# Runtime stage: a JRE only. No Maven, no JDK, no build cache, no source.
# Runs as a non-root user and answers the actuator health check.
# ---------------------------------------------------------------------------
FROM eclipse-temurin:21-jre AS runtime
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/* \
    && groupadd --system app && useradd --system --gid app --home /app app
WORKDIR /app

COPY --from=build /workspace/sprint-06-trade-api/target/trade-api-*.jar app.jar

USER app
EXPOSE 8085
HEALTHCHECK --interval=30s --timeout=3s --start-period=40s --retries=3 \
    CMD curl -fsS http://localhost:8085/actuator/health | grep -q '"status":"UP"'
ENTRYPOINT ["java", "-jar", "app.jar"]
