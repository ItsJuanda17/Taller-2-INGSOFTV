package com.circleguard.dashboard.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for the privacy filter that masks small-population stats so an
 * outsider cannot single out an individual. Default K is 5.
 */
class KAnonymityFilterTest {

    private final KAnonymityFilter filter = new KAnonymityFilter();

    @Test
    @DisplayName("Counts strictly below K are replaced with the literal '<K' string")
    void smallCountsAreMasked() {
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("totalUsers", 100);
        stats.put("suspectCount", 3);   // < 5 -> mask
        stats.put("probableCount", 8);  // >= 5 -> keep

        Map<String, Object> filtered = filter.apply(stats);

        assertThat(filtered).containsEntry("suspectCount", "<5");
        assertThat(filtered).containsEntry("probableCount", 8);
    }

    @Test
    @DisplayName("When totalUsers itself is below K, every count is hidden behind 'Insufficient data'")
    void smallPopulationIsFullyMasked() {
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("totalUsers", 2);
        stats.put("suspectCount", 1);
        stats.put("department", "Engineering");

        Map<String, Object> filtered = filter.apply(stats);

        assertThat(filtered).containsEntry("note", "Insufficient data for privacy");
        assertThat(filtered).containsEntry("totalUsers", "<5");
        // Department is preserved so the caller knows what was masked.
        assertThat(filtered).containsEntry("department", "Engineering");
        // Counts must NOT leak through.
        assertThat(filtered).doesNotContainKey("suspectCount");
    }

    @Test
    @DisplayName("Counts equal to K stay visible (boundary check on the K threshold)")
    void countEqualToKPassesThrough() {
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("totalUsers", 100);
        stats.put("suspectCount", 5); // == K -> keep

        Map<String, Object> filtered = filter.apply(stats);

        assertThat(filtered).containsEntry("suspectCount", 5);
    }

    @Test
    @DisplayName("A count of zero is left as zero — masking only applies to positive sub-K values")
    void zeroCountIsNotMasked() {
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("totalUsers", 100);
        stats.put("suspectCount", 0);

        Map<String, Object> filtered = filter.apply(stats);

        assertThat(filtered).containsEntry("suspectCount", 0);
    }

    @Test
    @DisplayName("A null input map returns an empty map instead of throwing NPE")
    void nullStatsMapDefaultsToEmpty() {
        assertThat(filter.apply(null)).isEmpty();
    }

    @Test
    @DisplayName("Custom K is honored — same data, different cutoffs, different masking")
    void customKChangesMaskingThreshold() {
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("totalUsers", 100);
        stats.put("suspectCount", 7);

        // With K=10, suspectCount=7 should be masked.
        assertThat(filter.apply(stats, 10)).containsEntry("suspectCount", "<10");
        // With K=5, the same value passes through.
        assertThat(filter.apply(stats, 5)).containsEntry("suspectCount", 7);
    }
}
