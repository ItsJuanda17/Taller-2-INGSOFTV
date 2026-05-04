package com.circleguard.e2e;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import io.restassured.RestAssured;
import org.junit.jupiter.api.BeforeAll;

import java.net.HttpURLConnection;
import java.net.URI;
import java.security.Key;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Shared setup for end-to-end tests: service URLs (env-driven with
 * sensible localhost defaults), a JWT minter that matches what
 * auth-service signs, and a tiny reachability check helper used by the
 * @EnabledIf guards on each test class.
 */
abstract class BaseE2ETest {

    // --- Service URLs (env vars override the localhost defaults) -------------
    static final String AUTH_URL         = envOr("AUTH_URL",         "http://localhost:8180");
    static final String IDENTITY_URL     = envOr("IDENTITY_URL",     "http://localhost:8083");
    static final String PROMOTION_URL    = envOr("PROMOTION_URL",    "http://localhost:8088");
    static final String DASHBOARD_URL    = envOr("DASHBOARD_URL",    "http://localhost:8084");
    static final String GATEWAY_URL      = envOr("GATEWAY_URL",      "http://localhost:8087");
    static final String NOTIFICATION_URL = envOr("NOTIFICATION_URL", "http://localhost:8082");

    // Must match jwt.secret in services' application.yml. Env override lets the
    // pipeline use the K8s-provisioned secret instead of the dev default.
    static final String JWT_SECRET = envOr(
            "JWT_SECRET", "my-super-secret-dev-key-32-chars-long-12345678");

    @BeforeAll
    static void enableLogging() {
        // Print request + response bodies on assertion failure to make CI logs
        // useful. Cheap to leave on; tests are not high-volume.
        RestAssured.enableLoggingOfRequestAndResponseIfValidationFails();
    }

    // --- JWT helpers ---------------------------------------------------------

    /**
     * Mints a JWT in the same shape auth-service issues: subject = anonId,
     * "permissions" claim = list of authorities. Used to call protected
     * endpoints (e.g. ADMIN, HEALTH_CENTER).
     */
    static String mintJwt(UUID anonymousId, List<String> permissions) {
        Key key = Keys.hmacShaKeyFor(JWT_SECRET.getBytes());
        return Jwts.builder()
                .setClaims(Map.of("permissions", permissions))
                .setSubject(anonymousId.toString())
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + 3_600_000L))
                .signWith(key, SignatureAlgorithm.HS256)
                .compact();
    }

    // --- Reachability guards -------------------------------------------------

    /**
     * Used by @EnabledIf annotations on test classes. Returns "true" if the
     * given HTTP base URL responds to a HEAD on "/". We accept ANY non-error
     * status (including 401/403/404) — the goal is just to know the process
     * is running, not that a specific endpoint exists.
     */
    static boolean isReachable(String baseUrl) {
        try {
            HttpURLConnection conn = (HttpURLConnection) URI.create(baseUrl).toURL().openConnection();
            conn.setRequestMethod("HEAD");
            conn.setConnectTimeout(1_500);
            conn.setReadTimeout(1_500);
            int status = conn.getResponseCode();
            // Anything that gets a HTTP response means the process is alive.
            return status > 0 && status < 600;
        } catch (Exception e) {
            return false;
        }
    }

    // Per-service reachability shortcuts referenced from @EnabledIf("...").
    static boolean authReachable()         { return isReachable(AUTH_URL); }
    static boolean identityReachable()     { return isReachable(IDENTITY_URL); }
    static boolean promotionReachable()    { return isReachable(PROMOTION_URL); }
    static boolean dashboardReachable()    { return isReachable(DASHBOARD_URL); }
    static boolean gatewayReachable()      { return isReachable(GATEWAY_URL); }
    static boolean notificationReachable() { return isReachable(NOTIFICATION_URL); }

    private static String envOr(String key, String fallback) {
        String v = System.getenv(key);
        return (v != null && !v.isBlank()) ? v : fallback;
    }
}
