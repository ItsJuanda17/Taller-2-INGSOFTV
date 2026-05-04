package com.circleguard.gateway.service;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;

import java.security.Key;
import java.util.Date;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Edge cases for QrValidationService that complement the existing happy-path
 * test ({@link QrValidationServiceTest}). The existing test covers CLEAR and
 * CONTAGIED. Here we cover: invalid signature, expired token, POTENTIAL
 * status, and the case where Redis returns null (status not yet cached).
 */
class QrValidationServiceEdgeCasesTest {

    private static final String SECRET = "qr-edge-tests-secret-key-32bytes-minimum";
    private QrValidationService service;
    private ValueOperations<String, String> valueOps;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        StringRedisTemplate redis = Mockito.mock(StringRedisTemplate.class);
        valueOps = Mockito.mock(ValueOperations.class);
        Mockito.when(redis.opsForValue()).thenReturn(valueOps);
        service = new QrValidationService(redis);
        ReflectionTestUtils.setField(service, "qrSecret", SECRET);
    }

    @Test
    @DisplayName("Token signed with the wrong secret is rejected")
    void invalidSignatureIsRejected() {
        Key wrongKey = Keys.hmacShaKeyFor("a-completely-different-secret-key-32bytes!".getBytes());
        String tampered = Jwts.builder()
                .setSubject(UUID.randomUUID().toString())
                .signWith(wrongKey, SignatureAlgorithm.HS256)
                .compact();

        QrValidationService.ValidationResult result = service.validateToken(tampered);

        assertThat(result.valid()).isFalse();
        assertThat(result.status()).isEqualTo("RED");
        assertThat(result.message()).contains("Invalid");
    }

    @Test
    @DisplayName("Expired token is rejected even when the signature is correct")
    void expiredTokenIsRejected() {
        Key key = Keys.hmacShaKeyFor(SECRET.getBytes());
        String expired = Jwts.builder()
                .setSubject(UUID.randomUUID().toString())
                .setIssuedAt(new Date(System.currentTimeMillis() - 10_000))
                .setExpiration(new Date(System.currentTimeMillis() - 1_000))
                .signWith(key, SignatureAlgorithm.HS256)
                .compact();

        QrValidationService.ValidationResult result = service.validateToken(expired);

        assertThat(result.valid()).isFalse();
        assertThat(result.status()).isEqualTo("RED");
    }

    @Test
    @DisplayName("Users in POTENTIAL state are denied entry just like CONTAGIED")
    void potentialStatusBlocksEntry() {
        String anonId = UUID.randomUUID().toString();
        Mockito.when(valueOps.get("user:status:" + anonId)).thenReturn("POTENTIAL");

        QrValidationService.ValidationResult result = service.validateToken(validTokenFor(anonId));

        assertThat(result.valid()).isFalse();
        assertThat(result.status()).isEqualTo("RED");
    }

    @Test
    @DisplayName("Missing Redis status (new user) defaults to GREEN — open by default")
    void missingStatusGrantsAccess() {
        String anonId = UUID.randomUUID().toString();
        Mockito.when(valueOps.get("user:status:" + anonId)).thenReturn(null);

        QrValidationService.ValidationResult result = service.validateToken(validTokenFor(anonId));

        assertThat(result.valid()).isTrue();
        assertThat(result.status()).isEqualTo("GREEN");
    }

    private String validTokenFor(String subject) {
        Key key = Keys.hmacShaKeyFor(SECRET.getBytes());
        return Jwts.builder()
                .setSubject(subject)
                .setExpiration(new Date(System.currentTimeMillis() + 60_000))
                .signWith(key, SignatureAlgorithm.HS256)
                .compact();
    }
}
