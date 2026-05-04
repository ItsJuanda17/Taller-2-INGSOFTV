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
    implementation("org.springframework.boot:spring-boot-starter-security") // for encryption utils
    implementation("org.flywaydb:flyway-core")
    runtimeOnly("org.postgresql:postgresql")
    implementation("org.springframework.kafka:spring-kafka")
    implementation("io.jsonwebtoken:jjwt-api:0.11.5")
    runtimeOnly("io.jsonwebtoken:jjwt-impl:0.11.5")
    runtimeOnly("io.jsonwebtoken:jjwt-jackson:0.11.5")
    
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.kafka:spring-kafka-test")
    testImplementation("org.springframework.security:spring-security-test")
    testImplementation("org.testcontainers:postgresql:1.20.4")
    testImplementation("org.testcontainers:junit-jupiter:1.20.4")
    // Force a docker-java that the modern Docker Desktop daemon accepts
    // (the version Testcontainers ships still hard-codes API "1.32").
    // -transport-jersey pulls in legacy javax.xml.bind which conflicts with
    // Spring Boot 3 / Hibernate 6 (both use jakarta.xml.bind), so we exclude
    // the jersey transport and keep only zerodep — the recommended one for
    // Testcontainers anyway.
    testImplementation("com.github.docker-java:docker-java:3.4.0") {
        exclude(group = "com.github.docker-java", module = "docker-java-transport-jersey")
        exclude(group = "com.github.docker-java", module = "docker-java-transport-netty")
    }
    testImplementation("com.github.docker-java:docker-java-transport-zerodep:3.4.0")
}

// Testcontainers on Windows + Docker Desktop:
//   - The default "docker_engine" pipe is a redirect stub on recent Docker
//     Desktop builds. The actual Linux daemon lives at "docker_engine_linux".
//   - The docker-java client bundled with Testcontainers 1.19.x requests API
//     version 1.32 by default; the modern Docker Desktop daemon rejects
//     anything below 1.40, so we pin DOCKER_API_VERSION explicitly.
// Linux/macOS keep the default UNIX-socket discovery and need no overrides.
tasks.withType<Test> {
    if (System.getProperty("os.name").startsWith("Windows")) {
        environment("DOCKER_HOST", "npipe:////./pipe/docker_engine_linux")
        environment("DOCKER_API_VERSION", "1.43")
        // docker-java 3.4.0 still hard-codes API "1.32" as the fallback when
        // it cannot resolve a version; set the system property the client
        // reads (api.version) so it negotiates ≥ 1.40 with Docker Desktop.
        systemProperty("api.version", "1.43")
    }
}
