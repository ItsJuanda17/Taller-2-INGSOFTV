package com.circleguard.auth.service;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.security.Key;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for the JWT generation logic. The service is plain Spring with
 * two @Value-injected fields, so we instantiate it directly instead of
 * spinning up a context.
 */
class JwtTokenServiceTest {

    private static final String SECRET = "unit-test-signing-key-must-be-at-least-32-bytes-long-aaaa";
    private static final long ONE_HOUR_MS = 3_600_000L;

    private JwtTokenService service;
    private Key signingKey;

    @BeforeEach
    void setUp() {
        service = new JwtTokenService(SECRET, ONE_HOUR_MS);
        signingKey = Keys.hmacShaKeyFor(SECRET.getBytes());
    }

    @Test
    @DisplayName("Generated token carries the anonymousId as its subject")
    void tokenSubjectIsAnonymousId() {
        UUID anonId = UUID.randomUUID();
        Authentication auth = new UsernamePasswordAuthenticationToken(
                "alice", "n/a", List.of(new SimpleGrantedAuthority("ROLE_STUDENT")));

        String token = service.generateToken(anonId, auth);
        Claims claims = parse(token);

        assertThat(claims.getSubject()).isEqualTo(anonId.toString());
    }

    @Test
    @DisplayName("Token embeds every granted authority under the 'permissions' claim")
    void tokenContainsPermissionsClaim() {
        UUID anonId = UUID.randomUUID();
        Authentication auth = new UsernamePasswordAuthenticationToken(
                "bob", "n/a", List.of(
                        new SimpleGrantedAuthority("ROLE_STUDENT"),
                        new SimpleGrantedAuthority("HEALTH_CENTER")));

        String token = service.generateToken(anonId, auth);
        Claims claims = parse(token);

        @SuppressWarnings("unchecked")
        List<String> permissions = claims.get("permissions", List.class);
        assertThat(permissions).containsExactlyInAnyOrder("ROLE_STUDENT", "HEALTH_CENTER");
    }

    @Test
    @DisplayName("Token expiration is set to (now + configured TTL)")
    void expirationMatchesConfiguredTtl() {
        long before = System.currentTimeMillis();
        Authentication auth = new UsernamePasswordAuthenticationToken(
                "carol", "n/a", List.of());

        String token = service.generateToken(UUID.randomUUID(), auth);
        long after = System.currentTimeMillis();
        Date exp = parse(token).getExpiration();

        // JWT exp is encoded in seconds, so the millisecond component is
        // truncated on the way out. Allow up to 1s of slack on the lower bound.
        assertThat(exp.getTime())
                .isBetween(before + ONE_HOUR_MS - 1_000, after + ONE_HOUR_MS);
    }

    @Test
    @DisplayName("Token without authorities still includes an empty 'permissions' list")
    void noAuthoritiesYieldsEmptyPermissionsClaim() {
        Authentication auth = new UsernamePasswordAuthenticationToken(
                "guest", "n/a", List.of());

        String token = service.generateToken(UUID.randomUUID(), auth);
        Claims claims = parse(token);

        @SuppressWarnings("unchecked")
        List<String> permissions = claims.get("permissions", List.class);
        assertThat(permissions).isEmpty();
    }

    private Claims parse(String token) {
        return Jwts.parserBuilder()
                .setSigningKey(signingKey)
                .build()
                .parseClaimsJws(token)
                .getBody();
    }
}
