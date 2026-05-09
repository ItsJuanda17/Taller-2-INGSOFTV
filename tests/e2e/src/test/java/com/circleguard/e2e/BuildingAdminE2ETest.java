package com.circleguard.e2e;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.notNullValue;

/**
 * E2E #4 — Promotion building admin flow.
 *
 * Two distinct user-facing scenarios:
 *  - A regular user (no ADMIN authority) is FORBIDDEN from creating buildings.
 *  - An ADMIN user can create a building and immediately read it back from
 *    the public listing. End-state cleanup removes the test row so reruns
 *    don't pollute the catalog.
 */
class BuildingAdminE2ETest extends BaseE2ETest {

    @Test
    @DisplayName("Non-admin JWT is rejected when trying to create a building")
    void nonAdminCannotCreateBuilding() {
        String nonAdminJwt = mintJwt(UUID.randomUUID(), List.of("ROLE_STUDENT"));

        given()
                .baseUri(PROMOTION_URL)
                .header("Authorization", "Bearer " + nonAdminJwt)
                .contentType("application/json")
                .body("""
                      {
                        "name": "Forbidden Tower",
                        "code": "FT-X",
                        "description": "should be 403",
                        "latitude": 0.0,
                        "longitude": 0.0,
                        "address": "n/a"
                      }
                      """)
        .when()
                .post("/api/v1/buildings")
        .then()
                .statusCode(403);
    }

    @Test
    @DisplayName("Admin can create a building, see it in the public listing, and delete it")
    void adminCanCreateListAndDeleteBuilding() {
        String adminJwt = mintJwt(UUID.randomUUID(), List.of("ADMIN"));
        String code = "E2E-" + UUID.randomUUID().toString().substring(0, 8);

        // CREATE
        String id = given()
                .baseUri(PROMOTION_URL)
                .header("Authorization", "Bearer " + adminJwt)
                .contentType("application/json")
                .body("""
                      {
                        "name": "E2E Test Hall",
                        "code": "%s",
                        "description": "Created by BuildingAdminE2ETest",
                        "latitude": 4.71,
                        "longitude": -74.07,
                        "address": "Test Av 100"
                      }
                      """.formatted(code))
        .when()
                .post("/api/v1/buildings")
        .then()
                .statusCode(200)
                .body("code", equalTo(code))
                .body("id", notNullValue())
                .extract().jsonPath().getString("id");

        // LIST (no auth required) — the building we just created should be in there.
        given()
                .baseUri(PROMOTION_URL)
        .when()
                .get("/api/v1/buildings")
        .then()
                .statusCode(200)
                .body("size()", greaterThanOrEqualTo(1))
                .body("findAll { it.code == '" + code + "' }.size()", equalTo(1));

        // DELETE — keep the catalog clean for reruns.
        given()
                .baseUri(PROMOTION_URL)
                .header("Authorization", "Bearer " + adminJwt)
        .when()
                .delete("/api/v1/buildings/" + id)
        .then()
                .statusCode(200);
    }
}
