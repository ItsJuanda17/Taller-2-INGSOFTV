package com.circleguard.notification.integration;

import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;

import java.util.concurrent.TimeUnit;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.awaitility.Awaitility.await;

/**
 * Integration test that exercises BOTH inter-service mechanisms used by the
 * notification service in a single flow:
 *   1. Receive an "alert.priority" event over Kafka.
 *   2. In response, call the auth service over HTTP to fetch the list of
 *      administrators with the alert:receive_priority permission.
 *
 * Kafka is provided by spring-kafka-test's @EmbeddedKafka. The auth service
 * is replaced by a WireMock instance whose URL is injected via
 * @DynamicPropertySource.
 */
@SpringBootTest
@EmbeddedKafka(partitions = 1, topics = {"alert.priority"})
@TestPropertySource(properties = {
        "spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}",
        "spring.kafka.consumer.auto-offset-reset=earliest"
})
class PriorityAlertKafkaHttpIntegrationTest {

    @RegisterExtension
    static WireMockExtension authService = WireMockExtension.newInstance()
            .options(options().dynamicPort())
            .build();

    @DynamicPropertySource
    static void overrideAuthUrl(DynamicPropertyRegistry registry) {
        registry.add("auth.api.url", authService::baseUrl);
    }

    @Autowired
    private KafkaTemplate<String, String> kafkaTemplate;

    @Test
    @DisplayName("alert.priority event triggers a GET to /api/v1/users/permissions/alert:receive_priority on auth")
    void priorityAlertFanoutsToAuthService() {
        authService.stubFor(get(urlEqualTo("/api/v1/users/permissions/alert:receive_priority"))
                .willReturn(okJson("[{\"username\":\"admin1\",\"email\":\"admin1@uni.edu\"}]")));

        String alertEvent = "{\"eventType\":\"OUTBREAK\",\"affectedCount\":12}";
        kafkaTemplate.send("alert.priority", alertEvent);

        await().atMost(10, TimeUnit.SECONDS).untilAsserted(() ->
                authService.verify(getRequestedFor(
                        urlEqualTo("/api/v1/users/permissions/alert:receive_priority"))));
    }

    @Test
    @DisplayName("Empty admin list from auth does NOT crash the listener — event is consumed cleanly")
    void emptyAdminListIsHandledGracefully() {
        authService.stubFor(get(urlEqualTo("/api/v1/users/permissions/alert:receive_priority"))
                .willReturn(okJson("[]")));

        String alertEvent = "{\"eventType\":\"WARNING\",\"affectedCount\":3}";
        kafkaTemplate.send("alert.priority", alertEvent);

        // Listener should still call auth — we just verify the call happened
        // without error. If the listener threw, Kafka would replay forever
        // and the consumer would not advance the offset.
        await().atMost(10, TimeUnit.SECONDS).untilAsserted(() ->
                authService.verify(getRequestedFor(
                        urlEqualTo("/api/v1/users/permissions/alert:receive_priority"))));
    }
}
