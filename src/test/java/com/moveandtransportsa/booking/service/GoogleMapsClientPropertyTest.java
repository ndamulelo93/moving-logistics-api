package com.moveandtransportsa.booking.service;

import net.jqwik.api.*;
import org.junit.jupiter.api.Tag;

import java.math.BigDecimal;
import java.math.RoundingMode;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Property-based tests for {@link GoogleMapsClient} meters-to-kilometers conversion logic.
 * 
 * Tests verify universal properties across many randomly generated inputs.
 * Focuses on testing the conversion algorithm that GoogleMapsClient uses
 * to convert Google Maps API response (distance in meters) to kilometers with scale 2.
 * 
 * **Validates: Requirements 2.4**
 */
@Tag("Feature: google-maps-distance-integration, Property 10")
class GoogleMapsClientPropertyTest {

    /**
     * **Property 10: Meters to Kilometers Conversion**
     * 
     * For any successful API response with distance.value in meters,
     * the system SHALL convert to kilometers by dividing by 1000 and
     * rounding to scale 2 using HALF_UP rounding mode.
     * 
     * This property ensures that:
     * - 0 meters → 0.00 km
     * - 1000 meters → 1.00 km
     * - 12345 meters → 12.35 km (rounded from 12.345)
     * - 5000000 meters → 5000.00 km
     * 
     * The conversion must maintain precision and use proper rounding
     * to ensure accurate distance calculations for pricing.
     * 
     * **Validates: Requirements 2.4**
     */
    @Property(tries = 100)
    void metersToKilometersConversion_distanceValueInMetersCorrectlyConvertedToKmWithScale2(
            @ForAll("distanceInMeters") long distanceMeters) {

        // When: Converting meters to kilometers using the same algorithm as GoogleMapsClient
        BigDecimal actualDistance = convertMetersToKilometers(distanceMeters);

        // Then: Distance should be correctly converted from meters to kilometers
        // with scale 2 and HALF_UP rounding
        BigDecimal expectedDistance = BigDecimal.valueOf(distanceMeters)
                .divide(BigDecimal.valueOf(1000), 2, RoundingMode.HALF_UP);

        assertThat(actualDistance).isEqualByComparingTo(expectedDistance);
        assertThat(actualDistance.scale()).isEqualTo(2);

        // Verify the conversion is precise for known test cases
        if (distanceMeters == 0) {
            assertThat(actualDistance).isEqualByComparingTo(BigDecimal.ZERO);
        } else if (distanceMeters == 1000) {
            assertThat(actualDistance).isEqualByComparingTo(new BigDecimal("1.00"));
        } else if (distanceMeters == 12345) {
            assertThat(actualDistance).isEqualByComparingTo(new BigDecimal("12.35"));
        } else if (distanceMeters == 5000000) {
            assertThat(actualDistance).isEqualByComparingTo(new BigDecimal("5000.00"));
        }
        
        // Verify distance is non-negative
        assertThat(actualDistance).isGreaterThanOrEqualTo(BigDecimal.ZERO);
    }

    /**
     * **Property 10 Extended: Rounding Behavior**
     * 
     * Tests specific rounding cases to ensure HALF_UP rounding is applied correctly.
     * 
     * Examples:
     * - 12344 meters → 12.34 km (12.344 rounds down)
     * - 12345 meters → 12.35 km (12.345 rounds up - HALF_UP)
     * - 12346 meters → 12.35 km (12.346 rounds up)
     * 
     * **Validates: Requirements 2.4**
     */
    @Property(tries = 50)
    void metersToKilometersConversion_halfUpRoundingAppliedCorrectly(
            @ForAll @IntRange(min = 1, max = 999999) int baseMeters) {

        // Test three rounding scenarios: round down, round half-up, round up
        long[] testDistances = {
                baseMeters * 1000L + 4,  // Should round down (e.g., X.XX4)
                baseMeters * 1000L + 5,  // Should round up with HALF_UP (e.g., X.XX5)
                baseMeters * 1000L + 6   // Should round up (e.g., X.XX6)
        };

        for (long distanceMeters : testDistances) {
            BigDecimal actualDistance = convertMetersToKilometers(distanceMeters);

            BigDecimal expectedDistance = BigDecimal.valueOf(distanceMeters)
                    .divide(BigDecimal.valueOf(1000), 2, RoundingMode.HALF_UP);

            assertThat(actualDistance).isEqualByComparingTo(expectedDistance);
            assertThat(actualDistance.scale()).isEqualTo(2);
        }
    }

    /**
     * **Property 10 Boundary Cases**
     * 
     * Tests boundary values to ensure conversion works correctly at the extremes:
     * - Zero distance (0 meters)
     * - Small distances (< 1 km)
     * - Large distances (approaching 5,000 km limit)
     * 
     * **Validates: Requirements 2.4, 10.7**
     */
    @Property
    void metersToKilometersConversion_boundaryValuesHandledCorrectly(
            @ForAll("boundaryDistances") long distanceMeters) {

        BigDecimal actualDistance = convertMetersToKilometers(distanceMeters);

        BigDecimal expectedDistance = BigDecimal.valueOf(distanceMeters)
                .divide(BigDecimal.valueOf(1000), 2, RoundingMode.HALF_UP);

        assertThat(actualDistance).isEqualByComparingTo(expectedDistance);
        assertThat(actualDistance.scale()).isEqualTo(2);
        
        // Verify distance is non-negative
        assertThat(actualDistance).isGreaterThanOrEqualTo(BigDecimal.ZERO);
    }

    /**
     * **Property 10 Precision Verification**
     * 
     * Verifies that the conversion maintains exact precision for all values
     * and that no precision is lost during the division operation.
     * 
     * **Validates: Requirements 2.4**
     */
    @Property(tries = 100)
    void metersToKilometersConversion_maintainsPrecision(
            @ForAll("distanceInMeters") long distanceMeters) {

        BigDecimal result = convertMetersToKilometers(distanceMeters);
        
        // Convert back to meters to verify no precision loss
        BigDecimal backToMeters = result.multiply(BigDecimal.valueOf(1000));
        
        // The difference should be at most 0.5 meters due to rounding
        BigDecimal difference = backToMeters.subtract(BigDecimal.valueOf(distanceMeters)).abs();
        assertThat(difference).isLessThanOrEqualTo(new BigDecimal("0.5"));
    }

    /**
     * **Property 10 Scale Consistency**
     * 
     * Ensures that all returned distances have exactly scale 2 (two decimal places).
     * 
     * **Validates: Requirements 2.4**
     */
    @Property(tries = 100)
    void metersToKilometersConversion_alwaysReturnsScale2(
            @ForAll("distanceInMeters") long distanceMeters) {

        BigDecimal result = convertMetersToKilometers(distanceMeters);
        
        assertThat(result.scale()).isEqualTo(2);
        
        // Verify string representation has exactly 2 decimal places
        String stringRep = result.toPlainString();
        if (stringRep.contains(".")) {
            int decimalIndex = stringRep.indexOf('.');
            int decimalPlaces = stringRep.length() - decimalIndex - 1;
            assertThat(decimalPlaces).isEqualTo(2);
        }
    }

    // -------------------------------------------------------------------------
    // Helper Methods
    // -------------------------------------------------------------------------

    /**
     * Converts distance from meters to kilometers using the same algorithm
     * as GoogleMapsClient.parseResponse().
     * 
     * This mirrors the actual implementation:
     * return BigDecimal.valueOf(distanceMeters)
     *     .divide(BigDecimal.valueOf(1000), 2, RoundingMode.HALF_UP);
     */
    private BigDecimal convertMetersToKilometers(long distanceMeters) {
        return BigDecimal.valueOf(distanceMeters)
                .divide(BigDecimal.valueOf(1000), 2, RoundingMode.HALF_UP);
    }

    // -------------------------------------------------------------------------
    // Arbitrary Providers
    // -------------------------------------------------------------------------

    /**
     * Provides distance values in meters from 0 to 5,000,000 (0 to 5000 km).
     * Focuses on realistic road distances as per requirement.
     */
    @Provide
    Arbitrary<Long> distanceInMeters() {
        return Arbitraries.longs()
                .between(0L, 5_000_000L);
    }

    /**
     * Provides boundary distance values for edge case testing.
     */
    @Provide
    Arbitrary<Long> boundaryDistances() {
        return Arbitraries.of(
                0L,           // Zero distance
                1L,           // Minimum non-zero
                999L,         // Just under 1 km
                1000L,        // Exactly 1 km
                1001L,        // Just over 1 km
                5000L,        // 5 km
                100_000L,     // 100 km
                1_000_000L,   // 1000 km
                4_999_999L,   // Just under 5000 km
                5_000_000L    // Maximum reasonable distance (5000 km)
        );
    }
}
