# Slim runtime-only image. The fat-jar is built by Jenkins' Build & Test
# stage (gradlew :services:...:bootJar) and copied in here, so this build
# stays under ~30 s and uses negligible RAM.
FROM eclipse-temurin:21-jre-alpine

RUN apk add --no-cache curl \
 && addgroup -S spring && adduser -S spring -G spring

WORKDIR /app

# *-SNAPSHOT.jar matches the bootJar but NOT the Spring Boot 3 *-plain.jar.
COPY services/circleguard-promotion-service/build/libs/*-SNAPSHOT.jar app.jar

USER spring:spring

EXPOSE 8088
ENV JAVA_OPTS=""
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
