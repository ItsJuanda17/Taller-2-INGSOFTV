package com.circleguard.promotion.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Unit tests for the WiFi MAC -> anonymousId session registry. The registry
 * is a thin Redis facade, so we mock StringRedisTemplate and just verify
 * keys, values and TTLs.
 */
class MacSessionRegistryTest {

    private StringRedisTemplate redis;
    private ValueOperations<String, String> valueOps;
    private MacSessionRegistry registry;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        redis = mock(StringRedisTemplate.class);
        valueOps = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(valueOps);
        registry = new MacSessionRegistry(redis);
    }

    @Test
    @DisplayName("registerSession stores anonymousId at key 'session:mac:<mac>' with an 8h TTL")
    void registerSessionUsesPrefixedKeyAndTtl() {
        registry.registerSession("AA:BB:CC:11:22:33", "anon-1");

        verify(valueOps).set(
                eq("session:mac:aa:bb:cc:11:22:33"),
                eq("anon-1"),
                eq(Duration.ofHours(8)));
    }

    @Test
    @DisplayName("MAC addresses are lowercased before being used as Redis keys")
    void macAddressIsCaseInsensitive() {
        registry.registerSession("AA:bb:CC:11:22:33", "anon-2");

        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
        verify(valueOps).set(keyCaptor.capture(), any(), any(Duration.class));
        assertThat(keyCaptor.getValue()).isEqualTo("session:mac:aa:bb:cc:11:22:33");
    }

    @Test
    @DisplayName("getAnonymousId returns the stored value for a registered MAC")
    void getAnonymousIdReturnsStoredValue() {
        when(valueOps.get("session:mac:aa:bb:cc:11:22:33")).thenReturn("anon-3");

        assertThat(registry.getAnonymousId("AA:BB:CC:11:22:33")).isEqualTo("anon-3");
    }

    @Test
    @DisplayName("getAnonymousId returns null when the MAC has no active session")
    void getAnonymousIdReturnsNullWhenAbsent() {
        when(valueOps.get(anyString())).thenReturn(null);

        assertThat(registry.getAnonymousId("DE:AD:BE:EF:00:01")).isNull();
    }

    @Test
    @DisplayName("closeSession deletes the Redis key for the given MAC")
    void closeSessionDeletesKey() {
        registry.closeSession("AA:BB:CC:11:22:33");

        verify(redis).delete("session:mac:aa:bb:cc:11:22:33");
    }
}
