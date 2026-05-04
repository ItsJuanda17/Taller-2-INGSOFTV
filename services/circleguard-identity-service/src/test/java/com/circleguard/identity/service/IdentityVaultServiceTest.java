package com.circleguard.identity.service;

import com.circleguard.identity.model.IdentityMapping;
import com.circleguard.identity.repository.IdentityMappingRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for the anonymous-ID vault. The repository is mocked so we
 * exercise only the service's branching logic, hashing, and lookup paths.
 */
class IdentityVaultServiceTest {

    private IdentityMappingRepository repository;
    private IdentityVaultService service;

    @BeforeEach
    void setUp() {
        repository = mock(IdentityMappingRepository.class);
        service = new IdentityVaultService(repository);
        ReflectionTestUtils.setField(service, "hashSalt", "unit-test-salt");
    }

    @Test
    @DisplayName("Returns the existing anonymousId for a real identity already in the vault")
    void returnsExistingAnonymousId() {
        UUID existing = UUID.randomUUID();
        IdentityMapping stored = IdentityMapping.builder()
                .anonymousId(existing)
                .realIdentity("alice@uni.edu")
                .identityHash("ignored")
                .salt("s")
                .build();
        when(repository.findByIdentityHash(anyString())).thenReturn(Optional.of(stored));

        UUID result = service.getOrCreateAnonymousId("alice@uni.edu");

        assertThat(result).isEqualTo(existing);
        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("Creates a fresh mapping when the real identity has no existing hash")
    void createsNewMappingWhenAbsent() {
        UUID generated = UUID.randomUUID();
        when(repository.findByIdentityHash(anyString())).thenReturn(Optional.empty());
        when(repository.save(any(IdentityMapping.class))).thenAnswer(inv -> {
            IdentityMapping m = inv.getArgument(0);
            m.setAnonymousId(generated);
            return m;
        });

        UUID result = service.getOrCreateAnonymousId("bob@uni.edu");

        assertThat(result).isEqualTo(generated);
        verify(repository).save(argThat(m ->
                m.getRealIdentity().equals("bob@uni.edu")
                        && m.getIdentityHash() != null
                        && m.getSalt() != null));
    }

    @Test
    @DisplayName("Hash function is deterministic — same identity always lookups the same hash")
    void hashIsDeterministic() {
        when(repository.findByIdentityHash(anyString())).thenReturn(Optional.empty());
        when(repository.save(any(IdentityMapping.class))).thenAnswer(inv -> {
            IdentityMapping m = inv.getArgument(0);
            m.setAnonymousId(UUID.randomUUID());
            return m;
        });

        service.getOrCreateAnonymousId("dave@uni.edu");
        service.getOrCreateAnonymousId("dave@uni.edu");

        // Capture the hashes used for the two repository lookups; they must match.
        var hashCaptor = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(repository, times(2)).findByIdentityHash(hashCaptor.capture());
        assertThat(hashCaptor.getAllValues().get(0))
                .isEqualTo(hashCaptor.getAllValues().get(1));
    }

    @Test
    @DisplayName("resolveRealIdentity throws 404 when the anonymousId is unknown")
    void resolveThrows404WhenMissing() {
        UUID unknown = UUID.randomUUID();
        when(repository.findById(unknown)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.resolveRealIdentity(unknown))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Identity not found");
    }

    @Test
    @DisplayName("resolveRealIdentity returns the plaintext when mapping is present")
    void resolveReturnsRealIdentity() {
        UUID id = UUID.randomUUID();
        IdentityMapping stored = IdentityMapping.builder()
                .anonymousId(id)
                .realIdentity("eve@uni.edu")
                .identityHash("h")
                .salt("s")
                .build();
        when(repository.findById(id)).thenReturn(Optional.of(stored));

        assertThat(service.resolveRealIdentity(id)).isEqualTo("eve@uni.edu");
    }
}
