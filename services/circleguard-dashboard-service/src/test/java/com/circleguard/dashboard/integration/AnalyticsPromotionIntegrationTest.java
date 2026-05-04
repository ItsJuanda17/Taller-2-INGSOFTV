package com.circleguard.dashboard.integration;

import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.util.Map;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test for the dashboard -> promotion HTTP boundary. The full
 * dashboard Spring Boot context is started against a random port; the
 * promotion service is replaced with a WireMock server whose URL is injected
 * via @DynamicPropertySource so the real PromotionClient bean talks to it.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class AnalyticsPromotionIntegrationTest {

    @RegisterExtension
    static WireMockExtension promotion = WireMockExtension.newInstance()
            .options(options().dynamicPort())
            .build();

    @DynamicPropertySource
    static void overridePromotionUrl(DynamicPropertyRegistry registry) {
        registry.add("circleguard.promotion-service.url", promotion::baseUrl);
    }

    @Autowired
    private TestRestTemplate rest;

    @Test
    @DisplayName("/analytics/health-board forwards the call to promotion-service /health-status/stats")
    @SuppressWarnings("rawtypes")
    void healthBoardCallsPromotionStats() {
        promotion.stubFor(get(urlEqualTo("/api/v1/health-status/stats"))
                .willReturn(okJson("{\"totalUsers\": 100, \"suspectCount\": 12}")));

        ResponseEntity<Map> response = rest.getForEntity("/api/v1/analytics/health-board", Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).containsEntry("totalUsers", 100);
        assertThat(response.getBody()).containsEntry("suspectCount", 12);
        promotion.verify(getRequestedFor(urlEqualTo("/api/v1/health-status/stats")));
    }

    @Test
    @DisplayName("/analytics/department/{dept} forwards to promotion AND applies K-anonymity masking on the way back")
    @SuppressWarnings("rawtypes")
    void departmentEndpointMasksLowCounts() {
        promotion.stubFor(get(urlEqualTo("/api/v1/health-status/stats/department/Engineering"))
                .willReturn(okJson("{\"totalUsers\": 100, \"suspectCount\": 2, \"probableCount\": 8}")));

        ResponseEntity<Map> response = rest.getForEntity(
                "/api/v1/analytics/department/Engineering", Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        // suspectCount=2 is below K (5) -> should be masked.
        assertThat(response.getBody()).containsEntry("suspectCount", "<5");
        // probableCount=8 is above K -> kept as-is.
        assertThat(response.getBody()).containsEntry("probableCount", 8);
    }

    @Test
    @DisplayName("If promotion-service is unreachable the client returns a graceful error payload (no 5xx)")
    @SuppressWarnings("rawtypes")
    void unreachablePromotionDoesNotCrashTheRequest() {
        promotion.stubFor(get(urlEqualTo("/api/v1/health-status/stats"))
                .willReturn(aResponse().withStatus(503)));

        ResponseEntity<Map> response = rest.getForEntity("/api/v1/analytics/health-board", Map.class);

        // PromotionClient swallows the exception and returns an "error" map,
        // so the dashboard endpoint stays 200 OK.
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).containsKey("error");
    }
}
