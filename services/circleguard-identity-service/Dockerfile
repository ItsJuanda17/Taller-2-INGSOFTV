# syntax=docker/dockerfile:1.6
# =============================================================================
# CircleGuard - Identity Service
# Multi-stage Dockerfile (build context = repo root).
# Build:
#   docker build -f services/circleguard-identity-service/Dockerfile \
#                -t circleguard-identity-service:local .
# =============================================================================

# -----------------------------------------------------------------------------
# Stage 1: BUILDER  -> compiles the Spring Boot fat-jar with Gradle
# -----------------------------------------------------------------------------
FROM eclipse-temurin:21-jdk AS builder

WORKDIR /workspace

# Copy Gradle wrapper + root build files first (cache-friendly layer).
COPY gradlew gradlew.bat ./
COPY gradle ./gradle
COPY settings.gradle.kts build.gradle.kts ./

# Copy ONLY this service's build script before sources.
COPY services/circleguard-identity-service/build.gradle.kts services/circleguard-identity-service/

RUN chmod +x gradlew

# Warm dependency cache.
RUN ./gradlew :services:circleguard-identity-service:dependencies --no-daemon || true

# Now copy source code.
COPY services/circleguard-identity-service/src services/circleguard-identity-service/src

# Build the runnable Spring Boot jar (tests run in Jenkins stage instead).
RUN ./gradlew :services:circleguard-identity-service:bootJar --no-daemon -x test

# Move the fat jar (skip the Spring Boot 3 "-plain.jar") to a stable path.
RUN find services/circleguard-identity-service/build/libs \
        -maxdepth 1 -type f -name '*.jar' ! -name '*-plain.jar' \
        -exec mv {} /workspace/app.jar \;


# -----------------------------------------------------------------------------
# Stage 2: RUNTIME  -> small image with only the JRE + the fat-jar
# -----------------------------------------------------------------------------
FROM eclipse-temurin:21-jre-alpine

# curl is needed by Kubernetes liveness/readiness probes.
RUN apk add --no-cache curl

# Run as non-root for security.
RUN addgroup -S spring && adduser -S spring -G spring

WORKDIR /app

COPY --from=builder /workspace/app.jar app.jar

USER spring:spring

# Matches application.yml -> server.port: 8083
EXPOSE 8083

ENV JAVA_OPTS=""

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
