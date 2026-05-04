package com.circleguard.notification.integration;

import com.circleguard.notification.service.LmsService;
import com.circleguard.notification.service.NotificationDispatcher;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.context.TestPropertySource;

import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

/**
 * Integration test for the promotion -> notification Kafka pipe. We start an
 * embedded Kafka broker, publish a real status-changed event, and assert
 * that the listener wires the message into the dispatcher and LMS service.
 * The downstream services themselves are mocked to keep the assertion
 * focused on the Kafka boundary.
 */
@SpringBootTest
@EmbeddedKafka(partitions = 1, topics = {"promotion.status.changed"})
@TestPropertySource(properties = {
        "spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}",
        "spring.kafka.consumer.auto-offset-reset=earliest"
})
class ExposureNotificationKafkaIntegrationTest {

    @Autowired
    private KafkaTemplate<String, String> kafkaTemplate;

    @MockBean
    private NotificationDispatcher dispatcher;

    @MockBean
    private LmsService lmsService;

    @Test
    @DisplayName("Status-changed event with SUSPECT triggers dispatch + LMS sync for the same anonymousId")
    void suspectStatusTriggersDispatchAndLmsSync() {
        String json = "{\"anonymousId\":\"anon-42\",\"status\":\"SUSPECT\"}";

        kafkaTemplate.send("promotion.status.changed", json);

        // Allow up to 10s for the Kafka consumer to spin up and process.
        verify(dispatcher, timeout(10_000)).dispatch("anon-42", "SUSPECT");
        verify(lmsService, timeout(10_000)).syncRemoteAttendance("anon-42", "SUSPECT");
    }

    @Test
    @DisplayName("ACTIVE status events are filtered out — no dispatch or LMS call")
    void activeStatusIsIgnored() throws InterruptedException {
        String json = "{\"anonymousId\":\"anon-99\",\"status\":\"ACTIVE\"}";

        kafkaTemplate.send("promotion.status.changed", json);

        // Give the listener time to process; then assert nothing happened.
        Thread.sleep(2_000);
        verify(dispatcher, org.mockito.Mockito.never()).dispatch(org.mockito.ArgumentMatchers.eq("anon-99"), org.mockito.ArgumentMatchers.anyString());
        verify(lmsService, org.mockito.Mockito.never()).syncRemoteAttendance(org.mockito.ArgumentMatchers.eq("anon-99"), org.mockito.ArgumentMatchers.anyString());
    }
}
