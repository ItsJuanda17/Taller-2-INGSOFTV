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
// the client advertises 1.32 and modern daemons reply with HTTP 400.
//   - Windows: point Testcontainers at the real WSL2 engine pipe (the default
//     "docker_engine" pipe is a Docker Desktop redirect stub).
//   - Linux (Jenkins runs here): force the Unix socket explicitly so the
//     docker-java client doesn't fall back to its legacy tcp://127.0.0.1:2375
//     default — the Jenkins container has the host socket bind-mounted at
//     /var/run/docker.sock but no exposed TCP daemon.
tasks.withType<Test> {
    systemProperty("api.version", "1.43")
    environment("DOCKER_API_VERSION", "1.43")
    // Ryuk is the small helper container Testcontainers spawns to garbage-
    // collect containers if the test JVM dies. When the JVM itself runs
    // INSIDE another container (e.g. Jenkins), Ryuk publishes its port on
    // the Docker bridge IP (172.17.0.1) which the in-container JVM cannot
    // route to. Disabling Ryuk skips that handshake; orphaned test
    // containers will be cleaned up by the next prune.
    environment("TESTCONTAINERS_RYUK_DISABLED", "true")
    environment("TESTCONTAINERS_CHECKS_DISABLE", "true")
    // After Testcontainers spawns Neo4j/Redis, it polls the published port
    // to verify readiness. By default it polls "localhost", but the test
    // JVM lives in the Jenkins container, where localhost is NOT the host.
    // Pointing the host override at host.docker.internal (auto-provided by
    // Docker Desktop in every container) makes the polling reach the right
    // host. The same value works on Windows local — it's the same alias.
    environment("TESTCONTAINERS_HOST_OVERRIDE", "host.docker.internal")
    if (System.getProperty("os.name").startsWith("Windows")) {
        environment("DOCKER_HOST", "npipe:////./pipe/docker_engine_linux")
    } else {
        environment("DOCKER_HOST", "unix:///var/run/docker.sock")
    }
}
