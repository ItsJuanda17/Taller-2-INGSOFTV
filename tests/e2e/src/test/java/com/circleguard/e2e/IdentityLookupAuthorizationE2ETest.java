package com.circleguard.e2e;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;

/**
 * E2E #5 — Identity-vault authorization.
 *
 * The /identities/lookup/{id} endpoint is the most sensitive one in the
 * system: it returns the plaintext real identity behind an anonymous UUID.
 * It must be locked down to the identity:lookup permission (Health Center
 * only). This flow proves the SecurityConfig + JwtAuthenticationFilter
 * combo enforces that.
 */
class IdentityLookupAuthorizationE2ETest extends BaseE2ETest {

    @Test
    @DisplayName("Anonymous request to /identities/lookup is rejected with 401")
    void unauthenticatedLookupReturns401() {
        given()
                .baseUri(IDENTITY_URL)
        .when()
                .get("/api/v1/identities/lookup/" + UUID.randomUUID())
        .then()
                .statusCode(401);
    }

    @Test
    @DisplayName("Authenticated user without identity:lookup permission gets 403")
    void wrongPermissionReturns403() {
        String wrongPermissionJwt = mintJwt(UUID.randomUUID(), List.of("ROLE_STUDENT"));

        given()
                .baseUri(IDENTITY_URL)
                .header("Authorization", "Bearer " + wrongPermissionJwt)
        .when()
                .get("/api/v1/identities/lookup/" + UUID.randomUUID())
        .then()
                .statusCode(403);
    }

    @Test
    @DisplayName("identity:lookup holder gets 404 for an unknown UUID — auth was accepted, just no row")
    void healthCenterReceivesNotFoundWhenIdUnknown() {
        String healthJwt = mintJwt(UUID.randomUUID(), List.of("identity:lookup"));

        given()
                .baseUri(IDENTITY_URL)
                .header("Authorization", "Bearer " + healthJwt)
        .when()
                .get("/api/v1/identities/lookup/" + UUID.randomUUID())
        .then()
                .statusCode(404)
                // Spring's RFC-7807 problem detail; "Identity not found" is
                // the message thrown by IdentityVaultService.resolveRealIdentity.
                .body("detail", equalTo("Identity not found"));
    }
}
