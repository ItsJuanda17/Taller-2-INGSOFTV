package com.circleguard.e2e;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;

/**
 * E2E #2 — Auth ↔ Gateway QR flow.
 *
 * 1. Mint a JWT for a fake anonymous user (auth-service would normally do
 *    this after LDAP login; we inline the same signing key).
 * 2. Ask auth-service to produce a short-lived QR token for that user.
 * 3. Hand that QR token to gateway-service /gate/validate. Since the user
 *    has no health status in Redis, the gate should reply GREEN.
 */
class QrToGateE2ETest extends BaseE2ETest {

    @Test
    @DisplayName("Auth-issued QR token is accepted by the gateway and grants GREEN access")
    void qrTokenFromAuthGrantsGreenAtGateway() {
        UUID anonId = UUID.randomUUID();
        String userJwt = mintJwt(anonId, List.of("ROLE_STUDENT"));

        // Step 1: auth-service issues the QR token for this user.
        String qrToken = given()
                .baseUri(AUTH_URL)
                .header("Authorization", "Bearer " + userJwt)
        .when()
                .get("/api/v1/auth/qr/generate")
        .then()
                .statusCode(200)
                .body("qrToken", notNullValue())
                .extract().jsonPath().getString("qrToken");

        // Step 2: gateway validates the QR token. No status set in Redis for
        // this brand-new anonId, so the validator falls through to GREEN.
        given()
                .baseUri(GATEWAY_URL)
                .contentType("application/json")
                .body("{\"token\":\"" + qrToken + "\"}")
        .when()
                .post("/api/v1/gate/validate")
        .then()
                .statusCode(200)
                .body("valid", equalTo(true))
                .body("status", equalTo("GREEN"));
    }

    @Test
    @DisplayName("Gateway rejects a JWT-shaped string signed with the wrong secret")
    void gatewayRejectsTokenSignedWithWrongKey() {
        // Manually crafted token signed with an unrelated secret -> RED.
        String bogusToken = io.jsonwebtoken.Jwts.builder()
                .setSubject(UUID.randomUUID().toString())
                .signWith(io.jsonwebtoken.security.Keys.hmacShaKeyFor(
                        "completely-unrelated-secret-key-for-tests!".getBytes()),
                        io.jsonwebtoken.SignatureAlgorithm.HS256)
                .compact();

        given()
                .baseUri(GATEWAY_URL)
                .contentType("application/json")
                .body("{\"token\":\"" + bogusToken + "\"}")
        .when()
                .post("/api/v1/gate/validate")
        .then()
                .statusCode(200)
                .body("valid", equalTo(false))
                .body("status", equalTo("RED"));
    }
}
