package com.moveandtransportsa.booking.service;

import com.moveandtransportsa.booking.config.GoogleMapsConfig;
import com.moveandtransportsa.booking.dto.Address;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import net.jqwik.api.*;
import net.jqwik.api.constraints.IntRange;
import net.jqwik.api.constraints.NotBlank;
import net.jqwik.api.constraints.StringLength;
import org.junit.jupiter.api.Tag;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Property-based tests for {@link DistanceCalculator} rate limiting behavior.
 * 
 * Tests verify that exceeding the configured max requests per minute triggers
 * fallback to zone-based distance calculation without making additional API calls.
 * 
 * **Validates: Requirements 7.1, 7.2, 7.6**
 */
@Tag("Feature: google-maps-distance-integration, Property 5")
class DistanceCalculatorRateLimitPropertyTest {

    /**
     * **Property 5: Rate Limit Triggers Fallback**
     * 
     * For any request that would exceed the configured max-requests-per-minute limit,
     * the system SHALL use zone-based fallback without calling the API.
     * 
     * This property ensures that:
     * - Requests 1 through maxRequestsPerMinute call the Google Maps API
     * - Request maxRequestsPerMinute + 1 and beyond use zone fallback without API calls
     * - The fallback metric is incremented with reason="rate_limit"
     * - Zone service is called instead of Google Maps API when rate limit exceeded
     * 
     * **Validates: Requirements 7.1, 7.2, 7.6**
     */
    @Property(tries = 50)
    void rateLimitTriggersFallback_exceedingMaxRequestsUsesFallbackWithoutApiCall(
            @ForAll @IntRange(min = 1, max = 20) int maxRequestsPerMinute,
            @ForAll @IntRange(min = 1, max = 5) int extraRequests) {

        // Given: A DistanceCalculator with rate limiting configured
        GoogleMapsClient googleMapsClient = mock(GoogleMapsClient.class);
        DistanceCache distanceCache = mock(DistanceCache.class);
        ZoneService zoneService = mock(ZoneService.class);
        GoogleMapsConfig config = mock(GoogleMapsConfig.class);
        MeterRegistry meterRegistry = mock(MeterRegistry.class);
        Counter fallbackCounter = mock(Counter.class);
        Counter cacheHitsCounter = mock(Counter.class);
        Counter cacheMissesCounter = mock(Counter.class);
        Counter apiSuccessCounter = mock(Counter.class);
        
        // Configure the mocks
        when(config.getMaxRequestsPerMinute()).thenReturn(maxRequestsPerMinute);
        when(config.isEnabled()).thenReturn(true);
        when(config.isCacheEnabled()).thenReturn(false); // Disable cache to directly test rate limiting
        
        when(meterRegistry.counter(eq("distance_fallback_total"), eq("reason"), eq("rate_limit")))
                .thenReturn(fallbackCounter);
        when(meterRegistry.counter(eq("distance_cache_hits_total")))
                .thenReturn(cacheHitsCounter);
        when(meterRegistry.counter(eq("distance_cache_misses_total")))
                .thenReturn(cacheMissesCounter);
        when(meterRegistry.counter(eq("google_maps_api_calls_total"), eq("status"), eq("success")))
                .thenReturn(apiSuccessCounter);
        
        // Mock responses
        when(googleMapsClient.getDistance(anyString(), anyString()))
                .thenReturn(new BigDecimal("10.50"));
        when(zoneService.getDistance(anyString(), anyString()))
                .thenReturn(new BigDecimal("12.00"));
        
        DistanceCalculator calculator = new DistanceCalculator(
                googleMapsClient,
                distanceCache,
                zoneService,
                config,
                meterRegistry
        );

        // When: Making requests up to and beyond the rate limit
        int totalRequests = maxRequestsPerMinute + extraRequests;
        List<BigDecimal> results = new ArrayList<>();
        
        for (int i = 0; i < totalRequests; i++) {
            Address pickup = new Address("Pickup " + i, "SuburbA", "CityX");
            Address dropoff = new Address("Dropoff " + i, "SuburbB", "CityY");
            
            BigDecimal distance = calculator.getDistance(pickup, dropoff);
            results.add(distance);
        }

        // Then: First maxRequestsPerMinute requests should call Google Maps API
        verify(googleMapsClient, times(maxRequestsPerMinute))
                .getDistance(anyString(), anyString());
        
        // And: Additional requests should NOT call Google Maps API (rate limited)
        verify(googleMapsClient, atMost(maxRequestsPerMinute))
                .getDistance(anyString(), anyString());
        
        // And: Additional requests should use zone fallback
        verify(zoneService, times(extraRequests))
                .getDistance(anyString(), anyString());
        
        // And: Fallback counter should be incremented for rate-limited requests
        verify(fallbackCounter, times(extraRequests)).increment();
        
        // And: All requests should return a valid distance
        assertThat(results).hasSize(totalRequests);
        results.forEach(distance -> {
            assertThat(distance).isNotNull();
            assertThat(distance).isGreaterThanOrEqualTo(BigDecimal.ZERO);
        });
        
        // And: First maxRequestsPerMinute requests return API distance (10.50)
        for (int i = 0; i < maxRequestsPerMinute; i++) {
            assertThat(results.get(i)).isEqualByComparingTo(new BigDecimal("10.50"));
        }
        
        // And: Requests beyond limit return zone fallback distance (12.00)
        for (int i = maxRequestsPerMinute; i < totalRequests; i++) {
            assertThat(results.get(i)).isEqualByComparingTo(new BigDecimal("12.00"));
        }
        
        // Cleanup
        calculator.shutdown();
    }

    /**
     * **Property 5 Extended: Rate Limit Consistency Across Different Address Pairs**
     * 
     * Tests that rate limiting applies consistently regardless of the addresses being requested.
     * The rate limit is global across all address pairs, not per-pair.
     * 
     * **Validates: Requirements 7.1, 7.2**
     */
    @Property(tries = 30)
    void rateLimitConsistency_limitsApplyGloballyAcrossAllAddressPairs(
            @ForAll @IntRange(min = 5, max = 15) int maxRequestsPerMinute,
            @ForAll @NotBlank @StringLength(min = 3, max = 10) String suburbPrefix) {

        // Given: A DistanceCalculator with rate limiting
        GoogleMapsClient googleMapsClient = mock(GoogleMapsClient.class);
        DistanceCache distanceCache = mock(DistanceCache.class);
        ZoneService zoneService = mock(ZoneService.class);
        GoogleMapsConfig config = mock(GoogleMapsConfig.class);
        MeterRegistry meterRegistry = mock(MeterRegistry.class);
        Counter fallbackCounter = mock(Counter.class);
        Counter cacheHitsCounter = mock(Counter.class);
        Counter cacheMissesCounter = mock(Counter.class);
        Counter apiSuccessCounter = mock(Counter.class);
        
        when(config.getMaxRequestsPerMinute()).thenReturn(maxRequestsPerMinute);
        when(config.isEnabled()).thenReturn(true);
        when(config.isCacheEnabled()).thenReturn(false);
        
        when(meterRegistry.counter(eq("distance_fallback_total"), eq("reason"), eq("rate_limit")))
                .thenReturn(fallbackCounter);
        when(meterRegistry.counter(eq("distance_cache_hits_total")))
                .thenReturn(cacheHitsCounter);
        when(meterRegistry.counter(eq("distance_cache_misses_total")))
                .thenReturn(cacheMissesCounter);
        when(meterRegistry.counter(eq("google_maps_api_calls_total"), eq("status"), eq("success")))
                .thenReturn(apiSuccessCounter);
        
        when(googleMapsClient.getDistance(anyString(), anyString()))
                .thenReturn(new BigDecimal("10.00"));
        when(zoneService.getDistance(anyString(), anyString()))
                .thenReturn(new BigDecimal("15.00"));
        
        DistanceCalculator calculator = new DistanceCalculator(
                googleMapsClient,
                distanceCache,
                zoneService,
                config,
                meterRegistry
        );

        // When: Making requests with DIFFERENT address pairs (to prove rate limit is global)
        int totalRequests = maxRequestsPerMinute + 3;
        
        for (int i = 0; i < totalRequests; i++) {
            // Each request uses unique addresses
            Address pickup = new Address(
                    "Address " + i + " Main St",
                    suburbPrefix + "Pickup" + i,
                    "City" + i
            );
            Address dropoff = new Address(
                    "Address " + i + " Second St",
                    suburbPrefix + "Dropoff" + i,
                    "City" + i
            );
            
            calculator.getDistance(pickup, dropoff);
        }

        // Then: Exactly maxRequestsPerMinute API calls should be made
        verify(googleMapsClient, times(maxRequestsPerMinute))
                .getDistance(anyString(), anyString());
        
        // And: Remaining requests should use zone fallback
        verify(zoneService, times(3))
                .getDistance(anyString(), anyString());
        
        // And: Fallback counter incremented for rate-limited requests
        verify(fallbackCounter, times(3)).increment();
        
        // Cleanup
        calculator.shutdown();
    }

    /**
     * **Property 5 Extended: No API Call When Rate Limit Exceeded**
     * 
     * Verifies that when rate limit is exceeded, the GoogleMapsClient is NOT invoked at all.
     * This ensures cost control by preventing any API calls beyond the limit.
     * 
     * **Validates: Requirements 7.6**
     */
    @Property(tries = 30)
    void noApiCallWhenRateLimitExceeded_ensuresCostControl(
            @ForAll @IntRange(min = 2, max = 10) int maxRequestsPerMinute) {

        // Given: A DistanceCalculator with strict rate limiting
        GoogleMapsClient googleMapsClient = mock(GoogleMapsClient.class);
        DistanceCache distanceCache = mock(DistanceCache.class);
        ZoneService zoneService = mock(ZoneService.class);
        GoogleMapsConfig config = mock(GoogleMapsConfig.class);
        MeterRegistry meterRegistry = mock(MeterRegistry.class);
        Counter fallbackCounter = mock(Counter.class);
        Counter cacheHitsCounter = mock(Counter.class);
        Counter cacheMissesCounter = mock(Counter.class);
        Counter apiSuccessCounter = mock(Counter.class);
        
        when(config.getMaxRequestsPerMinute()).thenReturn(maxRequestsPerMinute);
        when(config.isEnabled()).thenReturn(true);
        when(config.isCacheEnabled()).thenReturn(false);
        
        when(meterRegistry.counter(eq("distance_fallback_total"), eq("reason"), eq("rate_limit")))
                .thenReturn(fallbackCounter);
        when(meterRegistry.counter(eq("distance_cache_hits_total")))
                .thenReturn(cacheHitsCounter);
        when(meterRegistry.counter(eq("distance_cache_misses_total")))
                .thenReturn(cacheMissesCounter);
        when(meterRegistry.counter(eq("google_maps_api_calls_total"), eq("status"), eq("success")))
                .thenReturn(apiSuccessCounter);
        
        when(googleMapsClient.getDistance(anyString(), anyString()))
                .thenReturn(new BigDecimal("8.75"));
        when(zoneService.getDistance(anyString(), anyString()))
                .thenReturn(new BigDecimal("11.25"));
        
        DistanceCalculator calculator = new DistanceCalculator(
                googleMapsClient,
                distanceCache,
                zoneService,
                config,
                meterRegistry
        );

        // When: Filling up the rate limit
        for (int i = 0; i < maxRequestsPerMinute; i++) {
            Address pickup = new Address("Addr" + i, "Suburb" + i, "City");
            Address dropoff = new Address("Dest" + i, "Suburb" + (i + 100), "City");
            calculator.getDistance(pickup, dropoff);
        }
        
        // Verify we've made exactly maxRequestsPerMinute API calls
        verify(googleMapsClient, times(maxRequestsPerMinute))
                .getDistance(anyString(), anyString());
        
        // Reset the mock to track subsequent calls
        reset(googleMapsClient);
        when(googleMapsClient.getDistance(anyString(), anyString()))
                .thenReturn(new BigDecimal("8.75"));

        // When: Making an additional request beyond the rate limit
        Address pickup = new Address("Extra Pickup", "ExtraSuburbA", "ExtraCity");
        Address dropoff = new Address("Extra Dropoff", "ExtraSuburbB", "ExtraCity");
        BigDecimal result = calculator.getDistance(pickup, dropoff);

        // Then: GoogleMapsClient should NOT be called
        verify(googleMapsClient, never()).getDistance(anyString(), anyString());
        
        // And: ZoneService should be called instead
        verify(zoneService, times(1)).getDistance("ExtraSuburbA", "ExtraSuburbB");
        
        // And: Result should come from zone fallback
        assertThat(result).isEqualByComparingTo(new BigDecimal("11.25"));
        
        // And: Fallback counter should be incremented
        verify(fallbackCounter, times(1)).increment();
        
        // Cleanup
        calculator.shutdown();
    }

    /**
     * **Property 5 Extended: Rate Limit Does Not Affect Zone-Based Requests**
     * 
     * Tests that rate limiting only applies to address-based requests (using Google Maps API).
     * Legacy suburb-based requests (backward compatibility method) should never be rate limited.
     * 
     * **Validates: Requirements 7.1, 7.6**
     */
    @Property(tries = 30)
    void rateLimitDoesNotAffectZoneBasedRequests_backwardCompatibilityPreserved(
            @ForAll @IntRange(min = 3, max = 10) int maxRequestsPerMinute) {

        // Given: A DistanceCalculator with rate limiting
        GoogleMapsClient googleMapsClient = mock(GoogleMapsClient.class);
        DistanceCache distanceCache = mock(DistanceCache.class);
        ZoneService zoneService = mock(ZoneService.class);
        GoogleMapsConfig config = mock(GoogleMapsConfig.class);
        MeterRegistry meterRegistry = mock(MeterRegistry.class);
        Counter fallbackCounter = mock(Counter.class);
        Counter cacheHitsCounter = mock(Counter.class);
        Counter cacheMissesCounter = mock(Counter.class);
        Counter apiSuccessCounter = mock(Counter.class);
        
        when(config.getMaxRequestsPerMinute()).thenReturn(maxRequestsPerMinute);
        when(config.isEnabled()).thenReturn(true);
        when(config.isCacheEnabled()).thenReturn(false);
        
        when(meterRegistry.counter(anyString(), anyString(), anyString()))
                .thenReturn(fallbackCounter);
        when(meterRegistry.counter(anyString()))
                .thenReturn(cacheHitsCounter);
        
        when(zoneService.getDistance(anyString(), anyString()))
                .thenReturn(new BigDecimal("20.00"));
        
        DistanceCalculator calculator = new DistanceCalculator(
                googleMapsClient,
                distanceCache,
                zoneService,
                config,
                meterRegistry
        );

        // When: Making many suburb-based (legacy) requests
        int legacyRequests = maxRequestsPerMinute * 2; // Well beyond the rate limit
        
        for (int i = 0; i < legacyRequests; i++) {
            String pickupSuburb = "SuburbA" + i;
            String dropoffSuburb = "SuburbB" + i;
            
            // Using the legacy method signature (backward compatibility)
            BigDecimal result = calculator.getDistance(pickupSuburb, dropoffSuburb);
            
            assertThat(result).isEqualByComparingTo(new BigDecimal("20.00"));
        }

        // Then: All legacy requests should be handled by ZoneService
        verify(zoneService, times(legacyRequests))
                .getDistance(anyString(), anyString());
        
        // And: GoogleMapsClient should NEVER be called for legacy requests
        verify(googleMapsClient, never()).getDistance(anyString(), anyString());
        
        // And: No rate limit fallback counter should be incremented
        // (because these requests go directly to ZoneService, not through rate limiting logic)
        verify(fallbackCounter, never()).increment();
        
        // Cleanup
        calculator.shutdown();
    }

    /**
     * **Property 5 Extended: Rate Limit Counter Increments Correctly**
     * 
     * Verifies that the request counter is incremented only when an actual API call
     * would be made, and not incremented for rate-limited requests.
     * 
     * **Validates: Requirements 7.6**
     */
    @Property(tries = 20)
    void rateLimitCounter_incrementsOnlyForApiCalls(
            @ForAll @IntRange(min = 5, max = 12) int maxRequestsPerMinute) {

        // Given: A DistanceCalculator with rate limiting
        GoogleMapsClient googleMapsClient = mock(GoogleMapsClient.class);
        DistanceCache distanceCache = mock(DistanceCache.class);
        ZoneService zoneService = mock(ZoneService.class);
        GoogleMapsConfig config = mock(GoogleMapsConfig.class);
        MeterRegistry meterRegistry = mock(MeterRegistry.class);
        Counter fallbackCounter = mock(Counter.class);
        Counter cacheHitsCounter = mock(Counter.class);
        Counter cacheMissesCounter = mock(Counter.class);
        Counter apiSuccessCounter = mock(Counter.class);
        
        when(config.getMaxRequestsPerMinute()).thenReturn(maxRequestsPerMinute);
        when(config.isEnabled()).thenReturn(true);
        when(config.isCacheEnabled()).thenReturn(false);
        
        when(meterRegistry.counter(eq("distance_fallback_total"), eq("reason"), eq("rate_limit")))
                .thenReturn(fallbackCounter);
        when(meterRegistry.counter(eq("distance_cache_hits_total")))
                .thenReturn(cacheHitsCounter);
        when(meterRegistry.counter(eq("distance_cache_misses_total")))
                .thenReturn(cacheMissesCounter);
        when(meterRegistry.counter(eq("google_maps_api_calls_total"), eq("status"), eq("success")))
                .thenReturn(apiSuccessCounter);
        
        when(googleMapsClient.getDistance(anyString(), anyString()))
                .thenReturn(new BigDecimal("5.00"));
        when(zoneService.getDistance(anyString(), anyString()))
                .thenReturn(new BigDecimal("7.00"));
        
        DistanceCalculator calculator = new DistanceCalculator(
                googleMapsClient,
                distanceCache,
                zoneService,
                config,
                meterRegistry
        );

        // When: Making requests exactly at and beyond the rate limit
        int totalRequests = maxRequestsPerMinute + 5;
        
        for (int i = 0; i < totalRequests; i++) {
            Address pickup = new Address("Loc" + i, "Sub" + i, "City");
            Address dropoff = new Address("Dest" + i, "Sub" + (i + 50), "City");
            calculator.getDistance(pickup, dropoff);
        }

        // Then: GoogleMapsClient called exactly maxRequestsPerMinute times
        verify(googleMapsClient, times(maxRequestsPerMinute))
                .getDistance(anyString(), anyString());
        
        // And: ZoneService called for the 5 rate-limited requests
        verify(zoneService, times(5))
                .getDistance(anyString(), anyString());
        
        // And: Success counter incremented for API calls
        verify(apiSuccessCounter, times(maxRequestsPerMinute)).increment();
        
        // And: Fallback counter incremented for rate-limited requests
        verify(fallbackCounter, times(5)).increment();
        
        // Cleanup
        calculator.shutdown();
    }
}
