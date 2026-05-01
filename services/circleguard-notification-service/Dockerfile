# syntax=docker/dockerfile:1.6
# =============================================================================
# CircleGuard - Notification Service
# Multi-stage Dockerfile (build context = repo root).
# Build:
#   docker build -f services/circleguard-notification-service/Dockerfile \
#                -t circleguard-notification-service:local .
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
COPY services/circleguard-notification-service/build.gradle.kts services/circleguard-notification-service/

RUN chmod +x gradlew

# Warm dependency cache (Kafka client + Spring Mail are the heavy deps).
RUN ./gradlew :services:circleguard-notification-service:dependencies --no-daemon || true

# Now copy source code.
COPY services/circleguard-notification-service/src services/circleguard-notification-service/src

# Build the runnable Spring Boot jar (tests run in Jenkins stage instead).
RUN ./gradlew :services:circleguard-notification-service:bootJar --no-daemon -x test

# Move the fat jar (skip the Spring Boot 3 "-plain.jar") to a stable path.
RUN find services/circleguard-notification-service/build/libs \
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

# Matches application.yml -> server.port: 8082
EXPOSE 8082

ENV JAVA_OPTS=""

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
