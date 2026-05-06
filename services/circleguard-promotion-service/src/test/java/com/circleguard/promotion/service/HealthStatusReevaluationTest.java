package com.circleguard.promotion.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.neo4j.core.Neo4jClient;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Neo4jContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;


import static org.junit.jupiter.api.Assertions.assertEquals;

// The full Spring context tries to connect to Postgres and Kafka on boot.
// We give it an in-memory H2 (no real Postgres needed for these Neo4j-only
// tests), turn off Flyway, and prevent @KafkaListener beans from starting
// their consumer threads. KafkaTemplate is still @MockBean'd below.
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:testdb;MODE=PostgreSQL",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.flyway.enabled=false",
        "spring.kafka.listener.auto-startup=false"
})
@Testcontainers
public class HealthStatusReevaluationTest {

    @Container
    // Aligned with infra/k8s/10-middleware.yaml so the same image is reused
    // by the cluster, the integration suites, and any CI cache.
    static Neo4jContainer<?> neo4jContainer = new Neo4jContainer<>("neo4j:5.26")
            .withAdminPassword("password");

    @Container
    // Promotion service has @Cacheable beans backed by Redis. Without a real
    // Redis, lettuce fails to connect at context load and aborts the test.
    static GenericContainer<?> redisContainer = new GenericContainer<>("redis:7.2")
            .withExposedPorts(6379);

    @DynamicPropertySource
    static void backendProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.neo4j.uri", neo4jContainer::getBoltUrl);
        registry.add("spring.neo4j.authentication.username", () -> "neo4j");
        registry.add("spring.neo4j.authentication.password", () -> "password");
        registry.add("spring.data.redis.host", redisContainer::getHost);
        registry.add("spring.data.redis.port", redisContainer::getFirstMappedPort);
    }

    @Autowired
    private HealthStatusService healthStatusService;

    @Autowired
    private Neo4jClient neo4jClient;

    @MockBean
    private KafkaTemplate<String, Object> kafkaTemplate;

    @BeforeEach
    void setup() {
        neo4jClient.query("MATCH (n) DETACH DELETE n").run();
    }

    @Test
    void testSingleRelease() {
        // A (CONFIRMED) -[r1]-> B (SUSPECT)
        createNode("A", "CONFIRMED");
        createNode("B", "SUSPECT");
        createRelationship("A", "B");

        // Resolve A
        healthStatusService.resolveStatus("A");

        // B should become ACTIVE
        assertEquals("ACTIVE", getStatus("B"));
    }

    @Test
    void testBlockedRelease() {
        // A (CONFIRMED) -[r1]-> B (SUSPECT) <-[r2]- C (CONFIRMED)
        createNode("A", "CONFIRMED");
        createNode("B", "SUSPECT");
        createNode("C", "CONFIRMED");
        createRelationship("A", "B");
        createRelationship("C", "B");

        // Resolve A
        healthStatusService.resolveStatus("A");

        // B should stay SUSPECT because of C
        assertEquals("SUSPECT", getStatus("B"));
    }

    @Test
    void testMultiHopRelease() {
        // A (CONFIRMED) -> B (SUSPECT) -> C (PROBABLE)
        createNode("A", "CONFIRMED");
        createNode("B", "SUSPECT");
        createNode("C", "PROBABLE");
        createRelationship("A", "B");
        createRelationship("B", "C");

        // Resolve A
        healthStatusService.resolveStatus("A");

        // Both B and C should become ACTIVE
        assertEquals("ACTIVE", getStatus("B"));
        assertEquals("ACTIVE", getStatus("C"));
    }

    @Test
    void testPartialReleaseInMesh() {
        // A (CONFIRMED) -> B (SUSPECT) -> C (PROBABLE)
        // D (SUSPECT) -> C (PROBABLE)
        createNode("A", "CONFIRMED");
        createNode("B", "SUSPECT");
        createNode("C", "PROBABLE");
        createNode("D", "SUSPECT");
        createRelationship("A", "B");
        createRelationship("B", "C");
        createRelationship("D", "C");

        // Resolve A
        healthStatusService.resolveStatus("A");

        // B becomes ACTIVE
        assertEquals("ACTIVE", getStatus("B"));
        // C stays PROBABLE because of D
        assertEquals("PROBABLE", getStatus("C"));
    }

    private void createNode(String id, String status) {
        neo4jClient.query("CREATE (:User {anonymousId: $id, status: $status})")
                .bind(id).to("id")
                .bind(status).to("status")
                .run();
    }

    private void createRelationship(String id1, String id2) {
        neo4jClient.query("MATCH (u1:User {anonymousId: $id1}), (u2:User {anonymousId: $id2}) " +
                "CREATE (u1)-[:ENCOUNTERED {startTime: timestamp()}]->(u2)")
                .bind(id1).to("id1")
                .bind(id2).to("id2")
                .run();
    }

    private String getStatus(String id) {
        return neo4jClient.query("MATCH (u:User {anonymousId: $id}) RETURN u.status as status")
                .bind(id).to("id")
                .fetchAs(String.class).one().orElse("NOT_FOUND");
    }
}
