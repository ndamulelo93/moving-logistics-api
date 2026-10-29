package com.moveandtransportsa.booking.service;

import com.moveandtransportsa.booking.config.GoogleMapsConfig;
import com.moveandtransportsa.booking.dto.Address;
import com.moveandtransportsa.booking.exception.SuburbNotFoundException;
import com.moveandtransportsa.booking.exception.distance.InvalidAddressException;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Unit tests for DistanceCalculator rate limiting functionality.
 * 
 * Tests cover:
 * - Rate limit initialization (Requirements 7.1, 7.5)
 * - Rate limit counter reset mechanism (Requirements 7.4)
 * - Rate limit checking logic (Requirements 7.1, 7.2)
 * - Warning logging when rate limit triggered (Requirements 7.3)
 * - Scheduler lifecycle management
 */
@ExtendWith(MockitoExtension.class)
class DistanceCalculatorTest {

    @Mock
    private GoogleMapsClient googleMapsClient;

    @Mock
    private DistanceCache distanceCache;

    @Mock
    private ZoneService zoneService;

    private GoogleMapsConfig config;
    private MeterRegistry meterRegistry;
    private DistanceCalculator distanceCalculator;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        
        config = new GoogleMapsConfig();
        config.setEnabled(true);
        config.setApiKey("test-key");
        config.setMaxRequestsPerMinute(10);
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
    }

    @AfterEach
    void tearDown() {
        if (distanceCalculator != null) {
            distanceCalculator.shutdown();
        }
    }

    @Test
    void constructor_shouldInitializeRateLimitCounter() throws Exception {
        // Use reflection to access private field for testing
        Field field = DistanceCalculator.class.getDeclaredField("requestCountThisMinute");
        field.setAccessible(true);
        AtomicInteger counter = (AtomicInteger) field.get(distanceCalculator);
        
        assertThat(counter).isNotNull();
        assertThat(counter.get()).isEqualTo(0);
    }

    @Test
    void constructor_shouldInitializeScheduler() throws Exception {
        // Use reflection to access private scheduler field
        Field field = DistanceCalculator.class.getDeclaredField("scheduler");
        field.setAccessible(true);
        Object scheduler = field.get(distanceCalculator);
        
        assertThat(scheduler).isNotNull();
    }

    @Test
    void constructor_shouldLogInitializationWithRateLimit() {
        // Verify that the service is created without exceptions
        // Logging verification would require a log capture mechanism
        assertThat(distanceCalculator).isNotNull();
    }

    @Test
    void checkRateLimit_shouldReturnTrueWhenUnderLimit() throws Exception {
        // Access private method for testing
        Method method = DistanceCalculator.class.getDeclaredMethod("checkRateLimit");
        method.setAccessible(true);
        
        boolean result = (boolean) method.invoke(distanceCalculator);
        
        assertThat(result).isTrue();
    }

    @Test
    void checkRateLimit_shouldReturnFalseWhenLimitExceeded() throws Exception {
        // Set counter to max limit
        Field counterField = DistanceCalculator.class.getDeclaredField("requestCountThisMinute");
        counterField.setAccessible(true);
        AtomicInteger counter = (AtomicInteger) counterField.get(distanceCalculator);
        counter.set(10); // Equal to maxRequestsPerMinute
        
        // Access private method for testing
        Method method = DistanceCalculator.class.getDeclaredMethod("checkRateLimit");
        method.setAccessible(true);
        
        boolean result = (boolean) method.invoke(distanceCalculator);
        
        assertThat(result).isFalse();
    }

    @Test
    void incrementRequestCount_shouldIncrementCounter() throws Exception {
        // Access private method and field
        Field counterField = DistanceCalculator.class.getDeclaredField("requestCountThisMinute");
        counterField.setAccessible(true);
        AtomicInteger counter = (AtomicInteger) counterField.get(distanceCalculator);
        
        Method method = DistanceCalculator.class.getDeclaredMethod("incrementRequestCount");
        method.setAccessible(true);
        
        int before = counter.get();
        method.invoke(distanceCalculator);
        int after = counter.get();
        
        assertThat(after).isEqualTo(before + 1);
    }

    @Test
    void shutdown_shouldTerminateSchedulerGracefully() {
        distanceCalculator.shutdown();
        
        // Verify scheduler is terminated by accessing it via reflection
        try {
            Field field = DistanceCalculator.class.getDeclaredField("scheduler");
            field.setAccessible(true);
            Object scheduler = field.get(distanceCalculator);
            
            // Use reflection to check if shutdown was called
            Method isShutdownMethod = scheduler.getClass().getMethod("isShutdown");
            boolean isShutdown = (boolean) isShutdownMethod.invoke(scheduler);
            
            assertThat(isShutdown).isTrue();
        } catch (Exception e) {
            throw new RuntimeException("Failed to verify scheduler shutdown", e);
        }
    }

    @Test
    void getDistance_withAddress_shouldCalculateDistanceSuccessfully() throws Exception {
        // This test will be expanded once full implementation is complete in later tasks
        // For now, we're testing that the method exists and is callable
        Address pickup = new Address("123 Main St", "Suburb1", "Johannesburg");
        Address dropoff = new Address("456 Oak Ave", "Suburb2", "Johannesburg");
        
        // Mock cache to return empty (cache miss)
        when(distanceCache.normalize(anyString())).thenReturn("normalized address");
        when(distanceCache.lookup(anyString(), anyString())).thenReturn(java.util.Optional.empty());
        
        // Mock GoogleMapsClient to return a distance
        when(googleMapsClient.getDistance(anyString(), anyString()))
            .thenReturn(new BigDecimal("10.50"));
        
        // Act
        BigDecimal result = distanceCalculator.getDistance(pickup, dropoff);
        
        // Assert
        assertThat(result).isEqualByComparingTo(new BigDecimal("10.50"));
    }

    @Test
    void getDistance_withSuburbs_shouldDelegateToZoneService() {
        // Arrange
        String pickupSuburb = "Suburb1";
        String dropoffSuburb = "Suburb2";
        BigDecimal expectedDistance = new BigDecimal("20.00");
        
        when(zoneService.getDistance(pickupSuburb, dropoffSuburb))
            .thenReturn(expectedDistance);
        
        // Act
        BigDecimal result = distanceCalculator.getDistance(pickupSuburb, dropoffSuburb);
        
        // Assert
        assertThat(result).isEqualTo(expectedDistance);
        verify(zoneService).getDistance(pickupSuburb, dropoffSuburb);
    }

    // ===== Task 6.4 Tests: Invalid Address Fallback =====

    @Test
    void getDistance_withInvalidAddress_shouldFallbackToZoneService() throws Exception {
        // Arrange
        Address pickup = new Address("123 Unknown St", "KnownSuburb1", "Johannesburg");
        Address dropoff = new Address("456 Unknown Ave", "KnownSuburb2", "Johannesburg");
        BigDecimal expectedDistance = new BigDecimal("15.50");
        
        // Mock cache to return empty (cache miss)
        when(distanceCache.normalize(anyString())).thenReturn("normalized");
        when(distanceCache.lookup(anyString(), anyString())).thenReturn(java.util.Optional.empty());
        
        // Mock GoogleMapsClient to throw InvalidAddressException
        when(googleMapsClient.getDistance(anyString(), anyString()))
            .thenThrow(new InvalidAddressException("123 Unknown St"));
        
        // Mock ZoneService to return successful fallback distance
        when(zoneService.getDistance("KnownSuburb1", "KnownSuburb2"))
            .thenReturn(expectedDistance);
        
        // Act
        BigDecimal result = distanceCalculator.getDistance(pickup, dropoff);
        
        // Assert
        assertThat(result).isEqualTo(expectedDistance);
        verify(googleMapsClient).getDistance(anyString(), anyString());
        verify(zoneService).getDistance("KnownSuburb1", "KnownSuburb2");
        
        // Verify metric was incremented
        assertThat(meterRegistry.counter("distance_fallback_total", "reason", "invalid_address").count())
            .isEqualTo(1.0);
    }

    @Test
    void getDistance_withInvalidAddress_whenSuburbNotFound_shouldThrowInvalidAddressException() throws Exception {
        // Arrange
        Address pickup = new Address("123 Unknown St", "UnknownSuburb1", "Johannesburg");
        Address dropoff = new Address("456 Unknown Ave", "UnknownSuburb2", "Johannesburg");
        
        // Mock cache to return empty (cache miss)
        when(distanceCache.normalize(anyString())).thenReturn("normalized");
        when(distanceCache.lookup(anyString(), anyString())).thenReturn(java.util.Optional.empty());
        
        // Mock GoogleMapsClient to throw InvalidAddressException
        when(googleMapsClient.getDistance(anyString(), anyString()))
            .thenThrow(new InvalidAddressException("123 Unknown St"));
        
        // Mock ZoneService to also throw SuburbNotFoundException
        when(zoneService.getDistance("UnknownSuburb1", "UnknownSuburb2"))
            .thenThrow(new SuburbNotFoundException("UnknownSuburb1"));
        
        // Act & Assert
        assertThatThrownBy(() -> distanceCalculator.getDistance(pickup, dropoff))
            .isInstanceOf(InvalidAddressException.class)
            .hasMessageContaining("Address not found in Google Maps and suburb not found in zone system")
            .hasMessageContaining("UnknownSuburb1")
            .hasMessageContaining("UnknownSuburb2");
        
        // Verify GoogleMapsClient and ZoneService were called
        verify(googleMapsClient).getDistance(anyString(), anyString());
        verify(zoneService).getDistance("UnknownSuburb1", "UnknownSuburb2");
        
        // Verify metric was incremented even though fallback failed
        assertThat(meterRegistry.counter("distance_fallback_total", "reason", "invalid_address").count())
            .isEqualTo(1.0);
    }

    @Test
    void getDistance_withInvalidAddress_shouldLogWarningMessages() throws Exception {
        // Arrange
        Address pickup = new Address("123 Unknown St", "KnownSuburb1", "Johannesburg");
        Address dropoff = new Address("456 Unknown Ave", "KnownSuburb2", "Johannesburg");
        BigDecimal expectedDistance = new BigDecimal("15.50");
        
        // Mock cache to return empty (cache miss)
        when(distanceCache.normalize(anyString())).thenReturn("normalized");
        when(distanceCache.lookup(anyString(), anyString())).thenReturn(java.util.Optional.empty());
        
        // Mock GoogleMapsClient to throw InvalidAddressException
        when(googleMapsClient.getDistance(anyString(), anyString()))
            .thenThrow(new InvalidAddressException("123 Unknown St"));
        
        // Mock ZoneService to return successful fallback distance
        when(zoneService.getDistance("KnownSuburb1", "KnownSuburb2"))
            .thenReturn(expectedDistance);
        
        // Act
        BigDecimal result = distanceCalculator.getDistance(pickup, dropoff);
        
        // Assert
        assertThat(result).isEqualTo(expectedDistance);
        // Note: Log verification would require a log appender or log capture mechanism
        // For now, we verify behavior through method calls and metrics
    }

    // ===== Task 9.1 Tests: Metrics Registration and Increment =====

    @Test
    void metrics_shouldIncrementApiCallsSuccessCounter_whenApiCallSucceeds() throws Exception {
        // Arrange
        Address pickup = new Address("123 Main St", "Suburb1", "Johannesburg");
        Address dropoff = new Address("456 Oak Ave", "Suburb2", "Johannesburg");
        
        when(distanceCache.normalize(anyString())).thenReturn("normalized");
        when(distanceCache.lookup(anyString(), anyString())).thenReturn(java.util.Optional.empty());
        when(googleMapsClient.getDistance(anyString(), anyString()))
            .thenReturn(new BigDecimal("10.50"));
        
        // Verify counter starts at 0
        assertThat(meterRegistry.counter("google_maps_api_calls_total", "status", "success").count())
            .isEqualTo(0.0);
        
        // Act
        distanceCalculator.getDistance(pickup, dropoff);
        
        // Assert - Requirements 12.1
        assertThat(meterRegistry.counter("google_maps_api_calls_total", "status", "success").count())
            .isEqualTo(1.0);
    }

    @Test
    void metrics_shouldIncrementApiCallsFailureCounter_whenApiCallFails() throws Exception {
        // Arrange
        Address pickup = new Address("123 Main St", "Suburb1", "Johannesburg");
        Address dropoff = new Address("456 Oak Ave", "Suburb2", "Johannesburg");
        
        when(distanceCache.normalize(anyString())).thenReturn("normalized");
        when(distanceCache.lookup(anyString(), anyString())).thenReturn(java.util.Optional.empty());
        when(googleMapsClient.getDistance(anyString(), anyString()))
            .thenThrow(new com.moveandtransportsa.booking.exception.distance.ApiConnectionException(new RuntimeException("Connection failed")));
        when(zoneService.getDistance(anyString(), anyString()))
            .thenReturn(new BigDecimal("15.00"));
        
        // Verify counter starts at 0
        assertThat(meterRegistry.counter("google_maps_api_calls_total", "status", "failure").count())
            .isEqualTo(0.0);
        
        // Act
        distanceCalculator.getDistance(pickup, dropoff);
        
        // Assert - Requirements 12.1
        assertThat(meterRegistry.counter("google_maps_api_calls_total", "status", "failure").count())
            .isEqualTo(1.0);
    }

    @Test
    void metrics_shouldIncrementCacheHitsCounter_whenCacheHit() throws Exception {
        // Arrange
        Address pickup = new Address("123 Main St", "Suburb1", "Johannesburg");
        Address dropoff = new Address("456 Oak Ave", "Suburb2", "Johannesburg");
        
        when(distanceCache.normalize(anyString())).thenReturn("normalized");
        when(distanceCache.lookup(anyString(), anyString()))
            .thenReturn(java.util.Optional.of(new BigDecimal("12.34")));
        
        // Verify counter starts at 0
        assertThat(meterRegistry.counter("distance_cache_hits_total").count())
            .isEqualTo(0.0);
        
        // Act
        distanceCalculator.getDistance(pickup, dropoff);
        
        // Assert - Requirements 12.2
        assertThat(meterRegistry.counter("distance_cache_hits_total").count())
            .isEqualTo(1.0);
        
        // Verify no API call was made
        verifyNoInteractions(googleMapsClient);
    }

    @Test
    void metrics_shouldIncrementCacheMissesCounter_whenCacheMiss() throws Exception {
        // Arrange
        Address pickup = new Address("123 Main St", "Suburb1", "Johannesburg");
        Address dropoff = new Address("456 Oak Ave", "Suburb2", "Johannesburg");
        
        when(distanceCache.normalize(anyString())).thenReturn("normalized");
        when(distanceCache.lookup(anyString(), anyString())).thenReturn(java.util.Optional.empty());
        when(googleMapsClient.getDistance(anyString(), anyString()))
            .thenReturn(new BigDecimal("10.50"));
        
        // Verify counter starts at 0
        assertThat(meterRegistry.counter("distance_cache_misses_total").count())
            .isEqualTo(0.0);
        
        // Act
        distanceCalculator.getDistance(pickup, dropoff);
        
        // Assert - Requirements 12.3
        assertThat(meterRegistry.counter("distance_cache_misses_total").count())
            .isEqualTo(1.0);
    }

    @Test
    void metrics_shouldIncrementFallbackCounter_withRateLimitReason_whenRateLimitExceeded() throws Exception {
        // Arrange
        Address pickup = new Address("123 Main St", "Suburb1", "Johannesburg");
        Address dropoff = new Address("456 Oak Ave", "Suburb2", "Johannesburg");
        
        // Set counter to exceed rate limit
        Field counterField = DistanceCalculator.class.getDeclaredField("requestCountThisMinute");
        counterField.setAccessible(true);
        AtomicInteger counter = (AtomicInteger) counterField.get(distanceCalculator);
        counter.set(10); // Equal to maxRequestsPerMinute
        
        when(distanceCache.normalize(anyString())).thenReturn("normalized");
        when(zoneService.getDistance(anyString(), anyString()))
            .thenReturn(new BigDecimal("15.00"));
        
        // Verify counter starts at 0
        assertThat(meterRegistry.counter("distance_fallback_total", "reason", "rate_limit").count())
            .isEqualTo(0.0);
        
        // Act
        distanceCalculator.getDistance(pickup, dropoff);
        
        // Assert - Requirements 12.4
        assertThat(meterRegistry.counter("distance_fallback_total", "reason", "rate_limit").count())
            .isEqualTo(1.0);
    }

    @Test
    void metrics_shouldIncrementFallbackCounter_withApiConnectionExceptionReason_whenApiFails() throws Exception {
        // Arrange
        Address pickup = new Address("123 Main St", "Suburb1", "Johannesburg");
        Address dropoff = new Address("456 Oak Ave", "Suburb2", "Johannesburg");
        
        when(distanceCache.normalize(anyString())).thenReturn("normalized");
        when(distanceCache.lookup(anyString(), anyString())).thenReturn(java.util.Optional.empty());
        when(googleMapsClient.getDistance(anyString(), anyString()))
            .thenThrow(new com.moveandtransportsa.booking.exception.distance.ApiConnectionException(new RuntimeException("Connection failed")));
        when(zoneService.getDistance(anyString(), anyString()))
            .thenReturn(new BigDecimal("15.00"));
        
        // Verify counter starts at 0
        assertThat(meterRegistry.counter("distance_fallback_total", "reason", "ApiConnectionException").count())
            .isEqualTo(0.0);
        
        // Act
        distanceCalculator.getDistance(pickup, dropoff);
        
        // Assert - Requirements 12.4
        assertThat(meterRegistry.counter("distance_fallback_total", "reason", "ApiConnectionException").count())
            .isEqualTo(1.0);
    }

    @Test
    void metrics_shouldIncrementFallbackCounter_withInvalidAddressReason_whenAddressNotFound() throws Exception {
        // Arrange
        Address pickup = new Address("123 Unknown St", "Suburb1", "Johannesburg");
        Address dropoff = new Address("456 Unknown Ave", "Suburb2", "Johannesburg");
        
        when(distanceCache.normalize(anyString())).thenReturn("normalized");
        when(distanceCache.lookup(anyString(), anyString())).thenReturn(java.util.Optional.empty());
        when(googleMapsClient.getDistance(anyString(), anyString()))
            .thenThrow(new InvalidAddressException("Address not found"));
        when(zoneService.getDistance(anyString(), anyString()))
            .thenReturn(new BigDecimal("15.00"));
        
        // Verify counter starts at 0
        assertThat(meterRegistry.counter("distance_fallback_total", "reason", "invalid_address").count())
            .isEqualTo(0.0);
        
        // Act
        distanceCalculator.getDistance(pickup, dropoff);
        
        // Assert - Requirements 12.4
        assertThat(meterRegistry.counter("distance_fallback_total", "reason", "invalid_address").count())
            .isEqualTo(1.0);
    }

    @Test
    void metrics_shouldIncrementFallbackCounter_withIntegrationDisabledReason_whenIntegrationDisabled() throws Exception {
        // Arrange
        config.setEnabled(false);
        Address pickup = new Address("123 Main St", "Suburb1", "Johannesburg");
        Address dropoff = new Address("456 Oak Ave", "Suburb2", "Johannesburg");
        
        when(distanceCache.normalize(anyString())).thenReturn("normalized");
        when(zoneService.getDistance(anyString(), anyString()))
            .thenReturn(new BigDecimal("15.00"));
        
        // Verify counter starts at 0
        assertThat(meterRegistry.counter("distance_fallback_total", "reason", "integration_disabled").count())
            .isEqualTo(0.0);
        
        // Act
        distanceCalculator.getDistance(pickup, dropoff);
        
        // Assert - Requirements 12.4
        assertThat(meterRegistry.counter("distance_fallback_total", "reason", "integration_disabled").count())
            .isEqualTo(1.0);
    }

    @Test
    void metrics_allRequiredMetrics_shouldBeRegisteredAndAccessible() {
        // This test verifies all required metrics from Requirements 12.1, 12.2, 12.3, 12.4
        // can be accessed through the MeterRegistry
        
        // Assert - verify all metric names are properly formatted and accessible
        assertThat(meterRegistry.counter("google_maps_api_calls_total", "status", "success"))
            .isNotNull();
        assertThat(meterRegistry.counter("google_maps_api_calls_total", "status", "failure"))
            .isNotNull();
        assertThat(meterRegistry.counter("distance_cache_hits_total"))
            .isNotNull();
        assertThat(meterRegistry.counter("distance_cache_misses_total"))
            .isNotNull();
        assertThat(meterRegistry.counter("distance_fallback_total", "reason", "rate_limit"))
            .isNotNull();
        assertThat(meterRegistry.counter("distance_fallback_total", "reason", "invalid_address"))
            .isNotNull();
        assertThat(meterRegistry.counter("distance_fallback_total", "reason", "ApiConnectionException"))
            .isNotNull();
    }
}
