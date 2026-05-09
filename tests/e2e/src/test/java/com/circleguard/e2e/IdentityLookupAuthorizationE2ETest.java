package com.circleguard.e2e;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static io.restassured.RestAssured.given;

/**
 * E2E #5 — Identity-vault authorization.
 *
 * The /identities/lookup/{id} endpoint is the most sensitive one in the
 * system: it returns the plaintext real identity behind an anonymous UUID.
 * It must be locked down behind authentication.
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
}
