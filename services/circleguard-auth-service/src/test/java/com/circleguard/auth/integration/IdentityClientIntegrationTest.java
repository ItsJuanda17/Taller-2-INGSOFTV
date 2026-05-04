package com.circleguard.auth.integration;

import com.circleguard.auth.client.IdentityClient;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.UUID;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test for the auth -> identity HTTP boundary. The identity
 * service is replaced with a WireMock server that records every request and
 * lets us assert on the wire-level contract (path, method, JSON body) plus
 * the client-side parsing.
 */
class IdentityClientIntegrationTest {

    @RegisterExtension
    static WireMockExtension identity = WireMockExtension.newInstance()
            .options(options().dynamicPort())
            .build();

    private IdentityClient client;

    @BeforeEach
    void setUp() {
        client = new IdentityClient();
        ReflectionTestUtils.setField(client, "identityServiceUrl", identity.baseUrl());
    }

    @Test
    @DisplayName("Sends POST /api/v1/identities/map with the correct realIdentity JSON body")
    void postsRealIdentityToTheMappingEndpoint() {
        String anonId = "11111111-2222-3333-4444-555555555555";
        identity.stubFor(post(urlEqualTo("/api/v1/identities/map"))
                .willReturn(okJson("{\"anonymousId\": \"" + anonId + "\"}")));

        UUID result = client.getAnonymousId("alice@uni.edu");

        assertThat(result).isEqualTo(UUID.fromString(anonId));
        identity.verify(postRequestedFor(urlEqualTo("/api/v1/identities/map"))
                .withHeader("Content-Type", containing("application/json"))
                .withRequestBody(equalToJson("{\"realIdentity\": \"alice@uni.edu\"}")));
    }

    @Test
    @DisplayName("Different real identities produce independent calls (no caching at the client)")
    void clientIsStatelessBetweenCalls() {
        identity.stubFor(post(urlEqualTo("/api/v1/identities/map"))
                .withRequestBody(matchingJsonPath("$.realIdentity", equalTo("alice")))
                .willReturn(okJson("{\"anonymousId\":\"00000000-0000-0000-0000-000000000001\"}")));
        identity.stubFor(post(urlEqualTo("/api/v1/identities/map"))
                .withRequestBody(matchingJsonPath("$.realIdentity", equalTo("bob")))
                .willReturn(okJson("{\"anonymousId\":\"00000000-0000-0000-0000-000000000002\"}")));

        UUID a = client.getAnonymousId("alice");
        UUID b = client.getAnonymousId("bob");

        assertThat(a).isNotEqualTo(b);
        identity.verify(2, postRequestedFor(urlEqualTo("/api/v1/identities/map")));
    }
}
