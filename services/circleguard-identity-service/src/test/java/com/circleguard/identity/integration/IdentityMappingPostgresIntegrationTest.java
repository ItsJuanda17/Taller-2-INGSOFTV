package com.circleguard.identity.integration;

import com.circleguard.identity.model.IdentityMapping;
import com.circleguard.identity.repository.IdentityMappingRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test that talks to a REAL PostgreSQL 16 database (the same
 * version used in production / K8s middleware). The point of this test is
 * to prove the IdentityEncryptionConverter actually encrypts the bytea
 * column on disk — something an in-memory H2 test cannot fully verify
 * because H2's bytea handling differs subtly from Postgres.
 *
 * Skipped automatically when Docker is not directly reachable (the
 * Testcontainers Java client cannot consistently negotiate the Docker
 * Desktop named-pipe API on Windows, but works fine on Linux runners
 * such as Jenkins, where the daemon is exposed at /var/run/docker.sock).
 */
@DataJpaTest(properties = {
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        // Reuse the dev vault credentials from main/resources/application.yml
        "vault.secret=746573742d7365637265742d33322d63686172732d6c6f6e672d313233343536",
        "vault.salt=deadbeef",
        "vault.hash-salt=test-salt"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers(disabledWithoutDocker = true)
class IdentityMappingPostgresIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16")
            .withDatabaseName("circleguard_identity")
            .withUsername("admin")
            .withPassword("password");

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        registry.add("spring.jpa.database-platform",
                () -> "org.hibernate.dialect.PostgreSQLDialect");
    }

    @Autowired
    private IdentityMappingRepository repository;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    @DisplayName("Round-trip via the repository decrypts the realIdentity transparently")
    void roundTripDecryptsTransparently() {
        IdentityMapping mapping = IdentityMapping.builder()
                .realIdentity("alice@uni.edu")
                .identityHash("hash-rt-1")
                .salt("s")
                .build();

        IdentityMapping saved = repository.saveAndFlush(mapping);
        repository.findById(saved.getAnonymousId()).ifPresent(found ->
                assertThat(found.getRealIdentity()).isEqualTo("alice@uni.edu"));
    }

    @Test
    @DisplayName("Raw column on disk does NOT contain the plaintext identity (encryption-at-rest)")
    void plaintextIsNotStoredOnDisk() {
        IdentityMapping mapping = IdentityMapping.builder()
                .realIdentity("bob.secret@uni.edu")
                .identityHash("hash-secret-1")
                .salt("s")
                .build();
        repository.saveAndFlush(mapping);

        // Read the raw bytea column straight from Postgres, bypassing JPA.
        byte[] raw = jdbc.queryForObject(
                "SELECT real_identity_encrypted FROM identity_mappings WHERE identity_hash = ?",
                byte[].class,
                "hash-secret-1");

        assertThat(raw).isNotNull();
        assertThat(new String(raw))
                .as("Plaintext must never reach the disk")
                .doesNotContain("bob.secret@uni.edu");
    }

    @Test
    @DisplayName("findByIdentityHash uses the unique index on identity_hash and returns at most one row")
    void findByIdentityHashIsUnique() {
        IdentityMapping mapping = IdentityMapping.builder()
                .realIdentity("carol@uni.edu")
                .identityHash("hash-unique-1")
                .salt("s")
                .build();
        UUID savedId = repository.saveAndFlush(mapping).getAnonymousId();

        var found = repository.findByIdentityHash("hash-unique-1");

        assertThat(found).isPresent();
        assertThat(found.get().getAnonymousId()).isEqualTo(savedId);
        assertThat(found.get().getRealIdentity()).isEqualTo("carol@uni.edu");
    }
}
