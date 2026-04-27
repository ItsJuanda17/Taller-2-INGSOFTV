# syntax=docker/dockerfile:1.6
# =============================================================================
# CircleGuard - Auth Service
# Multi-stage Dockerfile (build context = repo root).
# Build:
#   docker build -f services/circleguard-auth-service/Dockerfile \
#                -t circleguard-auth-service:local .
# =============================================================================

# -----------------------------------------------------------------------------
# Stage 1: BUILDER  -> compiles the Spring Boot fat-jar with Gradle
# -----------------------------------------------------------------------------
FROM eclipse-temurin:21-jdk AS builder

WORKDIR /workspace

# 1) Copy the Gradle wrapper + root build files FIRST.
#    Doing this in a separate layer means Docker can cache the dependency
#    download as long as build.gradle.kts / settings.gradle.kts don't change.
COPY gradlew gradlew.bat ./
COPY gradle ./gradle
COPY settings.gradle.kts build.gradle.kts ./

# 2) Copy ONLY this service's build script (still no source code).
COPY services/circleguard-auth-service/build.gradle.kts services/circleguard-auth-service/

# 3) Make the wrapper executable (Windows checkouts often lose +x bit).
RUN chmod +x gradlew

# 4) Warm up the dependency cache. The "|| true" keeps the layer cacheable
#    even if the dependencies task complains because src/ is missing.
RUN ./gradlew :services:circleguard-auth-service:dependencies --no-daemon || true

# 5) Now copy the actual source code.
COPY services/circleguard-auth-service/src services/circleguard-auth-service/src

# 6) Build the runnable Spring Boot jar.
#    -x test  -> tests are NOT run inside the image build; Jenkins runs them
#                in an earlier pipeline stage so we don't bloat the image.
RUN ./gradlew :services:circleguard-auth-service:bootJar --no-daemon -x test

# 7) Spring Boot 3 produces both a "fat" bootJar and a "-plain.jar".
#    We only want the fat one. Move it to a stable, predictable path.
RUN find services/circleguard-auth-service/build/libs \
        -maxdepth 1 -type f -name '*.jar' ! -name '*-plain.jar' \
        -exec mv {} /workspace/app.jar \;


# -----------------------------------------------------------------------------
# Stage 2: RUNTIME  -> small image with only the JRE + the fat-jar
# -----------------------------------------------------------------------------
FROM eclipse-temurin:21-jre-alpine

# Install curl so Kubernetes liveness/readiness probes can hit /actuator/health
# (alpine doesn't ship curl by default).
RUN apk add --no-cache curl

# Best practice: never run as root inside the container.
RUN addgroup -S spring && adduser -S spring -G spring

WORKDIR /app

# Pull ONLY the jar from the builder stage. The JDK, sources and Gradle cache
# stay behind -> final image is ~80 MB instead of ~500 MB.
COPY --from=builder /workspace/app.jar app.jar

USER spring:spring

# Document the port the JVM listens on (matches application.yml: server.port: 8180).
EXPOSE 8180

# JAVA_OPTS lets ops override JVM flags from Kubernetes without rebuilding.
ENV JAVA_OPTS=""

# Exec form (JSON array) so PID 1 is the JVM and SIGTERM from K8s is forwarded
# directly -> graceful shutdown works.
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
