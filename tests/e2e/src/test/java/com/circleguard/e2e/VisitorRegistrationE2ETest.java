package com.circleguard.e2e;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;

import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.notNullValue;

/**
 * E2E #1 — Visitor registration.
 *
 * Hits identity-service directly. The endpoint takes a freshly-introduced
 * person (no LDAP, no prior account) and returns an anonymous UUID, exactly
 * the same call shape the front-end uses when a guest signs in at the gate.
 */
@EnabledIf("identityReachable")
class VisitorRegistrationE2ETest extends BaseE2ETest {

    @Test
    @DisplayName("Registering a new visitor returns a valid anonymousId UUID")
    void registerVisitorReturnsAnonymousId() {
        String visitorEmail = "guest-" + UUID.randomUUID() + "@example.org";

        given()
                .baseUri(IDENTITY_URL)
                .contentType("application/json")
                .body("""
                      {
                        "name": "Test Guest",
                        "email": "%s",
                        "reason_for_visit": "E2E test"
                      }
                      """.formatted(visitorEmail))
        .when()
                .post("/api/v1/identities/visitor")
        .then()
                .statusCode(200)
                .body("anonymousId", notNullValue())
                .body("anonymousId", matchesPattern(
                        "^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$"));
    }

    @Test
    @DisplayName("Registering the same visitor twice returns the SAME anonymousId (deterministic mapping)")
    void sameVisitorYieldsSameAnonymousId() {
        String visitorEmail = "stable-" + UUID.randomUUID() + "@example.org";
        String body = """
                      {
                        "name": "Stable Guest",
                        "email": "%s",
                        "reason_for_visit": "E2E test"
                      }
                      """.formatted(visitorEmail);

        String first = given().baseUri(IDENTITY_URL).contentType("application/json").body(body)
                .when().post("/api/v1/identities/visitor")
                .then().statusCode(200)
                .extract().jsonPath().getString("anonymousId");

        String second = given().baseUri(IDENTITY_URL).contentType("application/json").body(body)
                .when().post("/api/v1/identities/visitor")
                .then().statusCode(200)
                .extract().jsonPath().getString("anonymousId");

        org.junit.jupiter.api.Assertions.assertEquals(first, second,
                "Identity vault should return the same anonymousId for identical real-identity input");
    }
}
