package com.moveandtransportsa.booking.service;

import com.moveandtransportsa.booking.config.GoogleMapsConfig;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import net.jqwik.api.*;
import net.jqwik.api.constraints.AlphaChars;
import net.jqwik.api.constraints.NotBlank;
import net.jqwik.api.constraints.StringLength;
import org.junit.jupiter.api.Tag;
import org.mockito.Mockito;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Property-based tests for backward compatibility with legacy zone-based quote system.
 * 
 * These tests ensure that the existing suburb-only quote functionality continues to work
 * exactly as before the Google Maps integration, with no breaking changes.
 * 
 * **Validates: Requirements 5.1, 5.3**
 */
@Tag("Feature: google-maps-distance-integration, Property 13")
class BackwardCompatibilityPropertyTest {

    /**
     * **Property 13: Backward Compatibility Preserved**
     * 
     * For any quote request using the legacy method signature with suburb names only,
     * the system SHALL delegate to zone-based calculation and return the same result
     * as before integration.
     * 
     * This property ensures that:
     * - Legacy getDistance(String, String) method works exactly as before
     * - No Google Maps API calls are made for suburb-only requests
     * - Results are identical to pre-integration behavior
     * - ZoneService is called directly without any intermediate processing
     * - No caching, rate limiting, or API logic is involved
     * 
     * This is critical for backward compatibility - existing clients using suburb-based
     * quotes must continue to work without any changes or unexpected behavior.
     * 
     * **Validates: Requirements 5.1, 5.3**
     */
    @Property(tries = 100)
    void backwardCompatibility_legacySuburbBasedQuotesReturnSameResultsWithoutApiCalls(
            @ForAll @NotBlank @AlphaChars @StringLength(min = 3, max = 30) String pickupSuburb,
            @ForAll @NotBlank @AlphaChars @StringLength(min = 3, max = 30) String dropoffSuburb,
            @ForAll("distanceValues") BigDecimal expectedDistance) {

        // Given: A DistanceCalculator with mocked dependencies
        GoogleMapsClient googleMapsClient = Mockito.mock(GoogleMapsClient.class);
        DistanceCache distanceCache = Mockito.mock(DistanceCache.class);
        ZoneService zoneService = Mockito.mock(ZoneService.class);
        MeterRegistry meterRegistry = new SimpleMeterRegistry();
        
        GoogleMapsConfig config = new GoogleMapsConfig();
        config.setEnabled(true);
        config.setApiKey("test-key");
        config.setMaxRequestsPerMinute(100);
        config.setCacheEnabled(true);
        config.setCacheTtlDays(7);
        
        DistanceCalculator calculator = new DistanceCalculator(
            googleMapsClient,
            distanceCache,
            zoneService,
            config,
            meterRegistry
        );

        // Mock ZoneService to return a specific distance (simulating pre-integration behavior)
        when(zoneService.getDistance(pickupSuburb, dropoffSuburb))
            .thenReturn(expectedDistance);

        // When: Calling the legacy method with suburb names only
        BigDecimal result = calculator.getDistance(pickupSuburb, dropoffSuburb);

        // Then: Result should be exactly what ZoneService returned
        assertThat(result).isEqualByComparingTo(expectedDistance);
        assertThat(result.scale()).isEqualTo(2);

        // Verify ZoneService was called with the exact suburbs
        verify(zoneService).getDistance(pickupSuburb, dropoffSuburb);

        // Verify no Google Maps API call was made (backward compatibility requirement)
        verify(googleMapsClient, never()).getDistance(anyString(), anyString());
        
        // Verify no cache operations were performed
        verify(distanceCache, never()).lookup(anyString(), anyString());
        verify(distanceCache, never()).store(anyString(), anyString(), any(BigDecimal.class));
        verify(distanceCache, never()).normalize(anyString());

        // Verify no metrics were recorded for API calls or cache operations
        assertThat(meterRegistry.find("google_maps_api_calls_total").counters()).isEmpty();
        assertThat(meterRegistry.find("distance_cache_hits_total").counters()).isEmpty();
        assertThat(meterRegistry.find("distance_cache_misses_total").counters()).isEmpty();

        // Clean up
        calculator.shutdown();
    }

    /**
     * **Property 13 Extended: Legacy Method Independent of Google Maps Configuration**
     * 
     * Tests that the legacy suburb-based method works consistently regardless of
     * Google Maps configuration settings (enabled/disabled, cache enabled/disabled, etc.).
     * 
     * This ensures that:
     * - Legacy behavior is not affected by Google Maps settings
     * - Even if Google Maps is disabled, suburb-based quotes still work
     * - Configuration changes don't break existing functionality
     * 
     * **Validates: Requirements 5.1, 5.3**
     */
    @Property(tries = 100)
    void backwardCompatibility_legacyMethodWorksRegardlessOfGoogleMapsConfiguration(
            @ForAll @NotBlank @AlphaChars @StringLength(min = 3, max = 30) String pickupSuburb,
            @ForAll @NotBlank @AlphaChars @StringLength(min = 3, max = 30) String dropoffSuburb,
            @ForAll("distanceValues") BigDecimal expectedDistance,
            @ForAll boolean googleMapsEnabled,
            @ForAll boolean cacheEnabled) {

        // Given: A DistanceCalculator with various configuration settings
        GoogleMapsClient googleMapsClient = Mockito.mock(GoogleMapsClient.class);
        DistanceCache distanceCache = Mockito.mock(DistanceCache.class);
        ZoneService zoneService = Mockito.mock(ZoneService.class);
        MeterRegistry meterRegistry = new SimpleMeterRegistry();
        
        GoogleMapsConfig config = new GoogleMapsConfig();
        config.setEnabled(googleMapsEnabled);
        config.setApiKey("test-key");
        config.setMaxRequestsPerMinute(100);
        config.setCacheEnabled(cacheEnabled);
        config.setCacheTtlDays(7);
        
        DistanceCalculator calculator = new DistanceCalculator(
            googleMapsClient,
            distanceCache,
            zoneService,
            config,
            meterRegistry
        );

        // Mock ZoneService to return expected distance
        when(zoneService.getDistance(pickupSuburb, dropoffSuburb))
            .thenReturn(expectedDistance);

        // When: Calling the legacy method
        BigDecimal result = calculator.getDistance(pickupSuburb, dropoffSuburb);

        // Then: Result should always be from ZoneService, regardless of config
        assertThat(result).isEqualByComparingTo(expectedDistance);

        // Verify ZoneService was called
        verify(zoneService).getDistance(pickupSuburb, dropoffSuburb);

        // Verify no Google Maps API call was made (regardless of enabled flag)
        verify(googleMapsClient, never()).getDistance(anyString(), anyString());

        // Clean up
        calculator.shutdown();
    }

    /**
     * **Property 13 Extended: Same Suburb Pair Returns Consistent Distance**
     * 
     * Tests that calling the legacy method multiple times with the same suburb pair
     * returns consistent results, demonstrating deterministic behavior.
     * 
     * This ensures:
     * - No unexpected variations in distance calculations
     * - Deterministic behavior for the same input
     * - Consistency across multiple invocations
     * 
     * **Validates: Requirements 5.1, 5.3**
     */
    @Property(tries = 100)
    void backwardCompatibility_sameSuburbPairReturnsConsistentDistance(
            @ForAll @NotBlank @AlphaChars @StringLength(min = 3, max = 30) String pickupSuburb,
            @ForAll @NotBlank @AlphaChars @StringLength(min = 3, max = 30) String dropoffSuburb,
            @ForAll("distanceValues") BigDecimal expectedDistance) {

        // Given: A DistanceCalculator
        GoogleMapsClient googleMapsClient = Mockito.mock(GoogleMapsClient.class);
        DistanceCache distanceCache = Mockito.mock(DistanceCache.class);
        ZoneService zoneService = Mockito.mock(ZoneService.class);
        MeterRegistry meterRegistry = new SimpleMeterRegistry();
        
        GoogleMapsConfig config = new GoogleMapsConfig();
        config.setEnabled(true);
        config.setApiKey("test-key");
        config.setMaxRequestsPerMinute(100);
        config.setCacheEnabled(true);
        config.setCacheTtlDays(7);
        
        DistanceCalculator calculator = new DistanceCalculator(
            googleMapsClient,
            distanceCache,
            zoneService,
            config,
            meterRegistry
        );

        // Mock ZoneService to return consistent distance
        when(zoneService.getDistance(pickupSuburb, dropoffSuburb))
            .thenReturn(expectedDistance);

        // When: Calling the legacy method multiple times with same suburbs
        BigDecimal result1 = calculator.getDistance(pickupSuburb, dropoffSuburb);
        BigDecimal result2 = calculator.getDistance(pickupSuburb, dropoffSuburb);
        BigDecimal result3 = calculator.getDistance(pickupSuburb, dropoffSuburb);

        // Then: All results should be identical
        assertThat(result1).isEqualByComparingTo(expectedDistance);
        assertThat(result2).isEqualByComparingTo(expectedDistance);
        assertThat(result3).isEqualByComparingTo(expectedDistance);
        
        assertThat(result1).isEqualByComparingTo(result2);
        assertThat(result2).isEqualByComparingTo(result3);

        // Verify ZoneService was called 3 times (no caching for legacy method)
        verify(zoneService, times(3)).getDistance(pickupSuburb, dropoffSuburb);

        // Verify no Google Maps API calls
        verify(googleMapsClient, never()).getDistance(anyString(), anyString());

        // Clean up
        calculator.shutdown();
    }

    /**
     * **Property 13 Extended: Direct Delegation to ZoneService**
     * 
     * Tests that the legacy method is a simple pass-through to ZoneService
     * with no additional processing, transformation, or side effects.
     * 
     * This ensures:
     * - No hidden modifications to the distance value
     * - No additional rounding or scaling changes
     * - Direct delegation pattern is maintained
     * - Simplicity and predictability of legacy API
     * 
     * **Validates: Requirements 5.1, 5.3**
     */
    @Property(tries = 100)
    void backwardCompatibility_directDelegationToZoneServiceWithNoModifications(
            @ForAll @NotBlank @AlphaChars @StringLength(min = 3, max = 30) String pickupSuburb,
            @ForAll @NotBlank @AlphaChars @StringLength(min = 3, max = 30) String dropoffSuburb,
            @ForAll("variousScaleDistances") BigDecimal zoneServiceDistance) {

        // Given: A DistanceCalculator
        GoogleMapsClient googleMapsClient = Mockito.mock(GoogleMapsClient.class);
        DistanceCache distanceCache = Mockito.mock(DistanceCache.class);
        ZoneService zoneService = Mockito.mock(ZoneService.class);
        MeterRegistry meterRegistry = new SimpleMeterRegistry();
        
        GoogleMapsConfig config = new GoogleMapsConfig();
        config.setEnabled(true);
        config.setApiKey("test-key");
        config.setMaxRequestsPerMinute(100);
        config.setCacheEnabled(true);
        config.setCacheTtlDays(7);
        
        DistanceCalculator calculator = new DistanceCalculator(
            googleMapsClient,
            distanceCache,
            zoneService,
            config,
            meterRegistry
        );

        // Mock ZoneService to return distance with potentially different scale
        when(zoneService.getDistance(pickupSuburb, dropoffSuburb))
            .thenReturn(zoneServiceDistance);

        // When: Calling the legacy method
        BigDecimal result = calculator.getDistance(pickupSuburb, dropoffSuburb);

        // Then: Result should be EXACTLY what ZoneService returned
        // No modifications, no rounding, no scale changes
        assertThat(result).isEqualByComparingTo(zoneServiceDistance);
        assertThat(result.scale()).isEqualTo(zoneServiceDistance.scale());
        // Note: We expect reference equality since DistanceCalculator.getDistance(String, String)
        // directly returns the result from ZoneService without creating a new BigDecimal object
        assertThat(result).isSameAs(zoneServiceDistance);

        // Verify exact method call
        verify(zoneService).getDistance(pickupSuburb, dropoffSuburb);
        verifyNoMoreInteractions(zoneService);

        // Verify no other services were invoked
        verifyNoInteractions(googleMapsClient);
        verifyNoInteractions(distanceCache);

        // Clean up
        calculator.shutdown();
    }

    /**
     * **Property 13 Extended: Zero Distance Handling in Legacy Method**
     * 
     * Tests that when ZoneService returns zero distance (e.g., same zone),
     * the legacy method preserves this value without modification.
     * 
     * **Validates: Requirements 5.1, 5.3**
     */
    @Property(tries = 50)
    void backwardCompatibility_zeroDistancePreservedForSameZone(
            @ForAll @NotBlank @AlphaChars @StringLength(min = 3, max = 30) String suburb) {

        // Given: A DistanceCalculator
        GoogleMapsClient googleMapsClient = Mockito.mock(GoogleMapsClient.class);
        DistanceCache distanceCache = Mockito.mock(DistanceCache.class);
        ZoneService zoneService = Mockito.mock(ZoneService.class);
        MeterRegistry meterRegistry = new SimpleMeterRegistry();
        
        GoogleMapsConfig config = new GoogleMapsConfig();
        config.setEnabled(true);
        config.setApiKey("test-key");
        config.setMaxRequestsPerMinute(100);
        config.setCacheEnabled(true);
        config.setCacheTtlDays(7);
        
        DistanceCalculator calculator = new DistanceCalculator(
            googleMapsClient,
            distanceCache,
            zoneService,
            config,
            meterRegistry
        );

        // Mock ZoneService to return zero for same suburb (same zone scenario)
        when(zoneService.getDistance(suburb, suburb))
            .thenReturn(BigDecimal.ZERO.setScale(2));

        // When: Calling the legacy method with same suburb
        BigDecimal result = calculator.getDistance(suburb, suburb);

        // Then: Result should be zero
        assertThat(result).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(result).isGreaterThanOrEqualTo(BigDecimal.ZERO);

        // Verify ZoneService was called (not optimized away like Address-based method)
        verify(zoneService).getDistance(suburb, suburb);

        // Verify no Google Maps API call
        verify(googleMapsClient, never()).getDistance(anyString(), anyString());

        // Clean up
        calculator.shutdown();
    }

    // -------------------------------------------------------------------------
    // Arbitrary Providers
    // -------------------------------------------------------------------------

    /**
     * Provides realistic distance values between 0.00 and 500.00 km with scale 2.
     * Used for testing standard distance calculations.
     */
    @Provide
    Arbitrary<BigDecimal> distanceValues() {
        return Arbitraries.bigDecimals()
                .between(BigDecimal.ZERO, new BigDecimal("500.00"))
                .ofScale(2);
    }

    /**
     * Provides distance values with various scales to test pass-through behavior.
     * Tests that DistanceCalculator doesn't modify the scale of values from ZoneService.
     */
    @Provide
    Arbitrary<BigDecimal> variousScaleDistances() {
        return Arbitraries.integers()
                .between(0, 4)
                .flatMap(scale -> 
                    Arbitraries.bigDecimals()
                        .between(BigDecimal.ZERO, new BigDecimal("500.00"))
                        .map(bd -> bd.setScale(scale, java.math.RoundingMode.HALF_UP))
                );
    }
}
