// =============================================================================
// CircleGuard - End-to-End test module
// -----------------------------------------------------------------------------
// This module is NOT a Spring Boot service: it only contains tests that drive
// the running services over HTTP using REST Assured. Run it AFTER the six
// services are up, either locally (gradlew :services:<name>:bootRun) or in
// Kubernetes (kubectl port-forward) — see this directory's README for details.
//
// Common invocations (from repo root):
//   ./gradlew :tests:e2e:test
//   AUTH_URL=http://auth.dev:8180 ./gradlew :tests:e2e:test
// =============================================================================
plugins {
    id("io.spring.dependency-management")
}

// The root project adds Lombok to every subproject, but the E2E module
// doesn't need annotation processing and doesn't import any Lombok types.
// Drop the dep so the build doesn't try to resolve an unversioned coordinate.
configurations.all {
    exclude(group = "org.projectlombok", module = "lombok")
}

dependencies {
    // Spring Boot platform gives us a managed JUnit 5 + RestAssured BOM.
    testImplementation(platform("org.springframework.boot:spring-boot-dependencies:3.2.4"))
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("io.rest-assured:rest-assured")
    testImplementation("io.rest-assured:json-path")
    // We need to mint JWTs in tests so we can call permission-protected
    // endpoints (e.g. ADMIN-only building creation, HEALTH_CENTER lookup).
    testImplementation("io.jsonwebtoken:jjwt-api:0.11.5")
    testRuntimeOnly("io.jsonwebtoken:jjwt-impl:0.11.5")
    testRuntimeOnly("io.jsonwebtoken:jjwt-jackson:0.11.5")
}
