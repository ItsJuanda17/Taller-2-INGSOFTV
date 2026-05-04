package com.circleguard.notification.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Edge cases that round out the existing {@link TemplateServiceTest} (which
 * only covers the SUSPECT/PROBABLE happy paths). We focus on:
 *  - statuses that fall through the if-chain in generatePushContent
 *  - empty metadata for non-fenced statuses
 *  - the null userName fallback in the email template
 */
@SpringBootTest
class TemplateServiceEdgeCasesTest {

    @Autowired
    private TemplateService templateService;

    @Test
    @DisplayName("CONFIRMED status hits the generic push fallback (not the SUSPECT/PROBABLE branches)")
    void confirmedStatusUsesGenericPushMessage() {
        String content = templateService.generatePushContent("CONFIRMED");

        assertThat(content)
                .contains("CONFIRMED")
                .doesNotContain("isolation steps")
                .doesNotContain("Monitor symptoms");
    }

    @Test
    @DisplayName("CLEAR status returns empty push metadata — no deep-link to guidelines")
    void clearStatusHasNoPushMetadata() {
        assertThat(templateService.generatePushMetadata("CLEAR")).isEmpty();
    }

    @Test
    @DisplayName("Email template falls back to 'User' when no name is provided")
    void nullUserNameFallsBackToGenericGreeting() {
        String content = templateService.generateEmailContent("SUSPECT", null);

        assertThat(content).contains("User");
        assertThat(content).doesNotContain("null");
    }

    @Test
    @DisplayName("SMS content always includes the status, regardless of severity")
    void smsContainsStatusForAnyValue() {
        assertThat(templateService.generateSmsContent("CONFIRMED")).contains("CONFIRMED");
        assertThat(templateService.generateSmsContent("PROBABLE")).contains("PROBABLE");
        assertThat(templateService.generateSmsContent("CLEAR")).contains("CLEAR");
    }

    @Test
    @DisplayName("PROBABLE push metadata routes to the same guidelines URL as SUSPECT")
    void probableStatusHasGuidelinesDeepLink() {
        var meta = templateService.generatePushMetadata("PROBABLE");

        assertThat(meta).containsKey("url");
        assertThat(meta.get("url")).asString().startsWith("circleguard://");
    }
}
