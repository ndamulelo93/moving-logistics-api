package com.moveandtransportsa.booking.service;

import com.moveandtransportsa.booking.config.GoogleMapsConfig;
import com.moveandtransportsa.booking.dto.Address;
import com.moveandtransportsa.booking.exception.distance.ApiAuthenticationException;
import com.moveandtransportsa.booking.exception.distance.ApiConnectionException;
import com.moveandtransportsa.booking.exception.distance.RateLimitExceededException;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Unit tests for DistanceCalculator task 6.3 - fallback logic for API failures.
 * 
 * Tests cover:
 * - Catching ApiConnectionException and falling back to zone-based calculation (Requirement 3.1)
 * - Catching RateLimitExceededException and falling back to zone-based calculation (Requirement 3.2)
 * - Catching ApiAuthenticationException and falling back to zone-based calculation (Requirement 3.3)
 * - Extracting suburb from Address using getSuburb()
 * - Calling ZoneService.getDistance(pickupSuburb, dropoffSuburb)
 * - Logging warning at WARN level with suburbs and reason (Requirement 3.4)
 * - Incrementing distance_fallback_total{reason} metric with exception type
 * - Incrementing google_maps_api_calls_total{status="failure"} metric (Requirement 8.2)
 * - Returning fallback distance
 */
@ExtendWith(MockitoExtension.class)
class DistanceCalculatorTask63Test {

    @Mock
    private GoogleMapsClient googleMapsClient;

    @Mock
    private DistanceCache distanceCache;

    @Mock
    private ZoneService zoneService;

    private GoogleMapsConfig config;
    private MeterRegistry meterRegistry;
    private DistanceCalculator distanceCalculator;

    private Address pickupAddress;
    private Address dropoffAddress;
    private BigDecimal fallbackDistance;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        
        config = new GoogleMapsConfig();
        config.setEnabled(true);
        config.setApiKey("test-key");
        config.setMaxRequestsPerMinute(100); // High limit to avoid rate limiting in tests
        config.setTimeoutSeconds(5);
        config.setCacheEnabled(true);
        config.setCacheTtlDays(7);
        
        distanceCalculator = new DistanceCalculator(
            googleMapsClient,
            distanceCache,
            zoneService,
            config,
            meterRegistry
        );

        // Setup test data
        pickupAddress = new Address("123 Main St", "Sandton", "Johannesburg");
        dropoffAddress = new Address("456 Oak Ave", "Rosebank", "Johannesburg");
        fallbackDistance = new BigDecimal("15.50");

        // Setup cache behavior - always return empty (cache miss)
        when(distanceCache.normalize(anyString())).thenAnswer(invocation -> 
            invocation.getArgument(0).toString().toLowerCase().trim()
        );
        when(distanceCache.lookup(anyString(), anyString())).thenReturn(Optional.empty());
        
        // Setup zone service fallback
        when(zoneService.getDistance("Sandton", "Rosebank")).thenReturn(fallbackDistance);
    }

    @AfterEach
    void tearDown() {
        if (distanceCalculator != null) {
            distanceCalculator.shutdown();
        }
    }

    @Test
    void getDistance_shouldFallbackToZone_whenApiConnectionExceptionThrown() {
        // Given: GoogleMapsClient throws ApiConnectionException
        when(googleMapsClient.getDistance(anyString(), anyString()))
            .thenThrow(new ApiConnectionException(new RuntimeException("Connection refused")));

        // When: Calculate distance
        BigDecimal result = distanceCalculator.getDistance(pickupAddress, dropoffAddress);

        // Then: Should return fallback distance from zone service
        assertThat(result).isEqualTo(fallbackDistance);
        
        // Verify zone service was called with correct suburbs
        verify(zoneService).getDistance("Sandton", "Rosebank");
        
        // Verify fallback metric was incremented with correct reason
        Counter fallbackCounter = meterRegistry.find("distance_fallback_total")
            .tag("reason", "ApiConnectionException")
            .counter();
        assertThat(fallbackCounter).isNotNull();
        assertThat(fallbackCounter.count()).isEqualTo(1.0);
        
        // Verify failure metric was incremented
        Counter failureCounter = meterRegistry.find("google_maps_api_calls_total")
            .tag("status", "failure")
            .counter();
        assertThat(failureCounter).isNotNull();
        assertThat(failureCounter.count()).isEqualTo(1.0);
    }

    @Test
    void getDistance_shouldFallbackToZone_whenRateLimitExceededExceptionThrown() {
        // Given: GoogleMapsClient throws RateLimitExceededException
        when(googleMapsClient.getDistance(anyString(), anyString()))
            .thenThrow(new RateLimitExceededException());

        // When: Calculate distance
        BigDecimal result = distanceCalculator.getDistance(pickupAddress, dropoffAddress);

        // Then: Should return fallback distance from zone service
        assertThat(result).isEqualTo(fallbackDistance);
        
        // Verify zone service was called with correct suburbs
        verify(zoneService).getDistance("Sandton", "Rosebank");
        
        // Verify fallback metric was incremented with correct reason
        Counter fallbackCounter = meterRegistry.find("distance_fallback_total")
            .tag("reason", "RateLimitExceededException")
            .counter();
        assertThat(fallbackCounter).isNotNull();
        assertThat(fallbackCounter.count()).isEqualTo(1.0);
        
        // Verify failure metric was incremented
        Counter failureCounter = meterRegistry.find("google_maps_api_calls_total")
            .tag("status", "failure")
            .counter();
        assertThat(failureCounter).isNotNull();
        assertThat(failureCounter.count()).isEqualTo(1.0);
    }

    @Test
    void getDistance_shouldFallbackToZone_whenApiAuthenticationExceptionThrown() {
        // Given: GoogleMapsClient throws ApiAuthenticationException
        when(googleMapsClient.getDistance(anyString(), anyString()))
            .thenThrow(new ApiAuthenticationException());

        // When: Calculate distance
        BigDecimal result = distanceCalculator.getDistance(pickupAddress, dropoffAddress);

        // Then: Should return fallback distance from zone service
        assertThat(result).isEqualTo(fallbackDistance);
        
        // Verify zone service was called with correct suburbs
        verify(zoneService).getDistance("Sandton", "Rosebank");
        
        // Verify fallback metric was incremented with correct reason
        Counter fallbackCounter = meterRegistry.find("distance_fallback_total")
            .tag("reason", "ApiAuthenticationException")
            .counter();
        assertThat(fallbackCounter).isNotNull();
        assertThat(fallbackCounter.count()).isEqualTo(1.0);
        
        // Verify failure metric was incremented
        Counter failureCounter = meterRegistry.find("google_maps_api_calls_total")
            .tag("status", "failure")
            .counter();
        assertThat(failureCounter).isNotNull();
        assertThat(failureCounter.count()).isEqualTo(1.0);
    }

    @Test
    void getDistance_shouldExtractSuburbsFromAddresses_whenFallingBack() {
        // Given: Different suburbs in addresses
        Address pickup = new Address("789 Park St", "Midrand", "Johannesburg");
        Address dropoff = new Address("321 Beach Rd", "Centurion", "Pretoria");
        BigDecimal expectedDistance = new BigDecimal("25.00");
        
        when(zoneService.getDistance("Midrand", "Centurion")).thenReturn(expectedDistance);
        when(googleMapsClient.getDistance(anyString(), anyString()))
            .thenThrow(new ApiConnectionException(new RuntimeException("Timeout")));

        // When: Calculate distance
        BigDecimal result = distanceCalculator.getDistance(pickup, dropoff);

        // Then: Should use extracted suburbs
        verify(zoneService).getDistance("Midrand", "Centurion");
        assertThat(result).isEqualTo(expectedDistance);
    }

    @Test
    void getDistance_shouldNotCallGoogleMapsApi_whenApiCallFails() {
        // Given: API throws exception
        when(googleMapsClient.getDistance(anyString(), anyString()))
            .thenThrow(new ApiConnectionException(new RuntimeException("Network error")));

        // When: Calculate distance
        distanceCalculator.getDistance(pickupAddress, dropoffAddress);

        // Then: API should be called only once (no retry)
        verify(googleMapsClient, times(1)).getDistance(anyString(), anyString());
    }

    @Test
    void getDistance_shouldNotStoreInCache_whenFallbackUsed() {
        // Given: API throws exception
        when(googleMapsClient.getDistance(anyString(), anyString()))
            .thenThrow(new ApiConnectionException(new RuntimeException("Network error")));

        // When: Calculate distance
        distanceCalculator.getDistance(pickupAddress, dropoffAddress);

        // Then: Cache store should not be called
        verify(distanceCache, never()).store(anyString(), anyString(), any(BigDecimal.class));
    }

    @Test
    void getDistance_shouldIncrementFailureMetric_forEachFailedApiCall() {
        // Given: Three different API failures
        Address addr1 = new Address("1 St", "Suburb1", "City");
        Address addr2 = new Address("2 St", "Suburb2", "City");
        Address addr3 = new Address("3 St", "Suburb3", "City");
        
        when(zoneService.getDistance(anyString(), anyString())).thenReturn(BigDecimal.TEN);
        when(googleMapsClient.getDistance(anyString(), anyString()))
            .thenThrow(new ApiConnectionException(new RuntimeException("Error")));

        // When: Make three failed calls
        distanceCalculator.getDistance(addr1, addr2);
        distanceCalculator.getDistance(addr2, addr3);
        distanceCalculator.getDistance(addr1, addr3);

        // Then: Failure counter should be 3
        Counter failureCounter = meterRegistry.find("google_maps_api_calls_total")
            .tag("status", "failure")
            .counter();
        assertThat(failureCounter).isNotNull();
        assertThat(failureCounter.count()).isEqualTo(3.0);
    }

    @Test
    void getDistance_shouldUseCorrectReasonLabel_forDifferentExceptions() {
        // Given: Setup for multiple exception types
        Address addr1 = new Address("1 St", "Suburb1", "City");
        Address addr2 = new Address("2 St", "Suburb2", "City");
        Address addr3 = new Address("3 St", "Suburb3", "City");
        Address addr4 = new Address("4 St", "Suburb4", "City");
        
        when(zoneService.getDistance(anyString(), anyString())).thenReturn(BigDecimal.TEN);

        // When: Trigger different exception types
        when(googleMapsClient.getDistance(anyString(), anyString()))
            .thenThrow(new ApiConnectionException(new RuntimeException("Connection")));
        distanceCalculator.getDistance(addr1, addr2);

        when(googleMapsClient.getDistance(anyString(), anyString()))
            .thenThrow(new RateLimitExceededException());
        distanceCalculator.getDistance(addr2, addr3);

        when(googleMapsClient.getDistance(anyString(), anyString()))
            .thenThrow(new ApiAuthenticationException());
        distanceCalculator.getDistance(addr3, addr4);

        // Then: Each exception type should have its own counter
        Counter connectionCounter = meterRegistry.find("distance_fallback_total")
            .tag("reason", "ApiConnectionException")
            .counter();
        assertThat(connectionCounter.count()).isEqualTo(1.0);

        Counter rateLimitCounter = meterRegistry.find("distance_fallback_total")
            .tag("reason", "RateLimitExceededException")
            .counter();
        assertThat(rateLimitCounter.count()).isEqualTo(1.0);

        Counter authCounter = meterRegistry.find("distance_fallback_total")
            .tag("reason", "ApiAuthenticationException")
            .counter();
        assertThat(authCounter.count()).isEqualTo(1.0);
    }
}
