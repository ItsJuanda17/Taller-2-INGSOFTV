plugins {
    id("org.springframework.boot")
    id("io.spring.dependency-management")
    kotlin("jvm")
    kotlin("plugin.spring")
    kotlin("plugin.jpa")
}

dependencies {
    implementation(platform("org.springframework.boot:spring-boot-dependencies:3.2.4"))
    testImplementation(platform("org.springframework.boot:spring-boot-dependencies:3.2.4"))

    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-data-neo4j")
    implementation("org.springframework.boot:spring-boot-starter-data-redis")
    implementation("org.springframework.kafka:spring-kafka")
    implementation("org.springframework.boot:spring-boot-starter-cache")
    implementation("com.github.ben-manes.caffeine:caffeine")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("io.jsonwebtoken:jjwt-api:0.11.5")
    runtimeOnly("io.jsonwebtoken:jjwt-impl:0.11.5")
    runtimeOnly("io.jsonwebtoken:jjwt-jackson:0.11.5")
    implementation("org.flywaydb:flyway-core")
    runtimeOnly("org.postgresql:postgresql")
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.security:spring-security-test")
    // Testcontainers 1.20.x ships docker-java 3.3.x which still hard-codes
    // API "1.32" — modern Docker Desktop and recent dockerd both reject
    // anything below 1.40. We bump Testcontainers AND force docker-java
    // 3.4.0, then drop the legacy jersey transport (it pulls javax.xml.bind
    // and clashes with Spring Boot 3 / Hibernate 6's jakarta namespace).
    testImplementation("org.testcontainers:junit-jupiter:1.20.4")
    testImplementation("org.testcontainers:postgresql:1.20.4")
    testImplementation("org.testcontainers:neo4j:1.20.4")
    testImplementation("com.github.docker-java:docker-java:3.4.0") {
        exclude(group = "com.github.docker-java", module = "docker-java-transport-jersey")
        exclude(group = "com.github.docker-java", module = "docker-java-transport-netty")
    }
    testImplementation("com.github.docker-java:docker-java-transport-zerodep:3.4.0")
}

// Pin the Docker API version used by docker-java's negotiation. Without this
// the client advertises 1.32 and modern daemons reply with HTTP 400. On
// Windows we additionally point Testcontainers at the real WSL2 engine pipe
// (the default "docker_engine" pipe is a Docker Desktop redirect stub).
tasks.withType<Test> {
    systemProperty("api.version", "1.43")
    if (System.getProperty("os.name").startsWith("Windows")) {
        environment("DOCKER_HOST", "npipe:////./pipe/docker_engine_linux")
        environment("DOCKER_API_VERSION", "1.43")
    }
}
