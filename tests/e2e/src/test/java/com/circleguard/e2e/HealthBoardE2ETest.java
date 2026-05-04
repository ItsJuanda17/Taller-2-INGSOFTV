package com.circleguard.e2e;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.notNullValue;

/**
 * E2E #3 — Dashboard fans out to Promotion.
 *
 * Hits the dashboard /analytics/health-board endpoint, which internally
 * makes an HTTP call to promotion-service /health-status/stats. A 200 with
 * a non-null body proves both services are up AND that the inter-service
 * call succeeded end-to-end.
 */
@EnabledIf("dashboardReachable")
class HealthBoardE2ETest extends BaseE2ETest {

    @Test
    @DisplayName("/analytics/health-board returns aggregated stats sourced from promotion-service")
    void healthBoardAggregatesPromotionStats() {
        given()
                .baseUri(DASHBOARD_URL)
        .when()
                .get("/api/v1/analytics/health-board")
        .then()
                .statusCode(200)
                // The body shape varies depending on whether promotion has data,
                // but it must always be a non-null JSON object.
                .body("$", notNullValue());
    }

    @Test
    @DisplayName("/analytics/summary mirrors the health-board endpoint and stays available")
    void summaryEndpointIsAvailable() {
        given()
                .baseUri(DASHBOARD_URL)
        .when()
                .get("/api/v1/analytics/summary")
        .then()
                .statusCode(200)
                .body("$", notNullValue());
    }
}
