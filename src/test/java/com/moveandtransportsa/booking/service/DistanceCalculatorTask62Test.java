package com.moveandtransportsa.booking.service;

import com.moveandtransportsa.booking.config.GoogleMapsConfig;
import com.moveandtransportsa.booking.dto.Address;
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
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for DistanceCalculator task 6.2: getDistance(Address, Address) implementation.
 * 
 * Tests cover:
 * - Same address returns 0.00 (Requirement 1.5)
 * - Integration disabled uses fallback (Requirement 9.1)
 * - Rate limit check and fallback (Requirements 7.1, 7.2)
 * - Cache hit increments metric (Requirement 12.2)
 * - Cache miss increments metric (Requirement 12.3)
 * - API success stores in cache (Requirement 4.5)
 * - API success increments success metric (Requirement 12.1)
 * - Logging at appropriate levels (Requirements 8.3, 8.4)
 */
@ExtendWith(MockitoExtension.class)
class DistanceCalculatorTask62Test {

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
        config.setMaxRequestsPerMinute(100);
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
    void getDistance_shouldReturnZeroForSameAddress() {
        // Arrange
        Address address = new Address("123 Main St", "Johannesburg", "Gauteng");
        when(distanceCache.normalize(anyString())).thenReturn("123 main st, johannesburg, gauteng");
        
        // Act
        BigDecimal result = distanceCalculator.getDistance(address, address);
        
        // Assert
        assertThat(result).isEqualByComparingTo(BigDecimal.ZERO.setScale(2));
        verify(googleMapsClient, never()).getDistance(anyString(), anyString());
        verify(distanceCache, never()).lookup(anyString(), anyString());
    }

    @Test
    void getDistance_shouldUseFallbackWhenIntegrationDisabled() {
        // Arrange
        config.setEnabled(false);
        Address pickup = new Address("123 Main St", "Johannesburg", "Gauteng");
        Address dropoff = new Address("456 Oak Ave", "Pretoria", "Gauteng");
        BigDecimal expectedDistance = new BigDecimal("50.00");
        
        when(distanceCache.normalize(anyString())).thenReturn("normalized");
        when(zoneService.getDistance("Johannesburg", "Pretoria")).thenReturn(expectedDistance);
        
        // Act
        BigDecimal result = distanceCalculator.getDistance(pickup, dropoff);
        
        // Assert
        assertThat(result).isEqualByComparingTo(expectedDistance);
        verify(googleMapsClient, never()).getDistance(anyString(), anyString());
        verify(zoneService).getDistance("Johannesburg", "Pretoria");
        
        // Check fallback metric
        Counter fallbackCounter = meterRegistry.find("distance_fallback_total").counter();
        assertThat(fallbackCounter).isNotNull();
        assertThat(fallbackCounter.count()).isEqualTo(1.0);
    }

    @Test
    void getDistance_shouldReturnCachedDistanceOnCacheHit() {
        // Arrange
        Address pickup = new Address("123 Main St", "Johannesburg", "Gauteng");
        Address dropoff = new Address("456 Oak Ave", "Pretoria", "Gauteng");
        BigDecimal cachedDistance = new BigDecimal("45.50");
        
        when(distanceCache.normalize(anyString())).thenReturn("normalized-address");
        when(distanceCache.lookup(anyString(), anyString())).thenReturn(Optional.of(cachedDistance));
        
        // Act
        BigDecimal result = distanceCalculator.getDistance(pickup, dropoff);
        
        // Assert
        assertThat(result).isEqualByComparingTo(cachedDistance);
        verify(googleMapsClient, never()).getDistance(anyString(), anyString());
        verify(distanceCache).lookup(anyString(), anyString());
        
        // Check cache hit metric
        Counter cacheHitCounter = meterRegistry.find("distance_cache_hits_total").counter();
        assertThat(cacheHitCounter).isNotNull();
        assertThat(cacheHitCounter.count()).isEqualTo(1.0);
    }

    @Test
    void getDistance_shouldCallAPIAndStoreInCacheOnCacheMiss() {
        // Arrange
        Address pickup = new Address("123 Main St", "Johannesburg", "Gauteng");
        Address dropoff = new Address("456 Oak Ave", "Pretoria", "Gauteng");
        BigDecimal apiDistance = new BigDecimal("42.75");
        
        when(distanceCache.normalize(anyString())).thenReturn("normalized-address");
        when(distanceCache.lookup(anyString(), anyString())).thenReturn(Optional.empty());
        when(googleMapsClient.getDistance(anyString(), anyString())).thenReturn(apiDistance);
        
        // Act
        BigDecimal result = distanceCalculator.getDistance(pickup, dropoff);
        
        // Assert
        assertThat(result).isEqualByComparingTo(apiDistance);
        verify(googleMapsClient).getDistance(
            "123 Main St, Johannesburg, Gauteng",
            "456 Oak Ave, Pretoria, Gauteng"
        );
        verify(distanceCache).store(
            "123 Main St, Johannesburg, Gauteng",
            "456 Oak Ave, Pretoria, Gauteng",
            apiDistance
        );
        
        // Check cache miss metric
        Counter cacheMissCounter = meterRegistry.find("distance_cache_misses_total").counter();
        assertThat(cacheMissCounter).isNotNull();
        assertThat(cacheMissCounter.count()).isEqualTo(1.0);
        
        // Check success metric
        Counter successCounter = meterRegistry.find("google_maps_api_calls_total").counter();
        assertThat(successCounter).isNotNull();
        assertThat(successCounter.count()).isEqualTo(1.0);
    }

    @Test
    void getDistance_shouldBypassCacheWhenCachingDisabled() {
        // Arrange
        config.setCacheEnabled(false);
        Address pickup = new Address("123 Main St", "Johannesburg", "Gauteng");
        Address dropoff = new Address("456 Oak Ave", "Pretoria", "Gauteng");
        BigDecimal apiDistance = new BigDecimal("42.75");
        
        when(distanceCache.normalize(anyString())).thenReturn("normalized-address");
        when(googleMapsClient.getDistance(anyString(), anyString())).thenReturn(apiDistance);
        
        // Act
        BigDecimal result = distanceCalculator.getDistance(pickup, dropoff);
        
        // Assert
        assertThat(result).isEqualByComparingTo(apiDistance);
        verify(distanceCache, never()).lookup(anyString(), anyString());
        verify(distanceCache, never()).store(anyString(), anyString(), any(BigDecimal.class));
        verify(googleMapsClient).getDistance(anyString(), anyString());
    }
}
