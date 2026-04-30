# =============================================================================
# Stage 1 — Build the application
# =============================================================================
FROM maven:3.9-eclipse-temurin-21 AS build

WORKDIR /workspace

# Cache dependencies first
COPY pom.xml .
RUN mvn -B -e -ntp dependency:go-offline

# Build the application
COPY src ./src
RUN mvn -B -e -ntp package -DskipTests

# =============================================================================
# Stage 2 — Runtime image (Quarkus fast-jar layout on UBI 9 OpenJDK 21)
# =============================================================================
FROM registry.access.redhat.com/ubi9/openjdk-21-runtime:1.21

ENV LANGUAGE='en_US:en' \
    JAVA_OPTS_APPEND="-Dquarkus.http.host=0.0.0.0 -Djava.util.logging.manager=org.jboss.logmanager.LogManager" \
    JAVA_APP_JAR="/deployments/quarkus-run.jar"

# Copy fast-jar layout — keeping layers separate maximises cache reuse
COPY --from=build --chown=185 /workspace/target/quarkus-app/lib/      /deployments/lib/
COPY --from=build --chown=185 /workspace/target/quarkus-app/*.jar     /deployments/
COPY --from=build --chown=185 /workspace/target/quarkus-app/app/      /deployments/app/
COPY --from=build --chown=185 /workspace/target/quarkus-app/quarkus/  /deployments/quarkus/

EXPOSE 8080
USER 185

ENTRYPOINT [ "/opt/jboss/container/java/run/run-java.sh" ]
