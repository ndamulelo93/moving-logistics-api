package com.moveandtransportsa.booking.service;

import com.moveandtransportsa.booking.config.GoogleMapsConfig;
import com.moveandtransportsa.booking.exception.SuburbNotFoundException;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Unit tests for DistanceCalculator backward compatibility method.
 * 
 * Tests cover:
 * - Task 6.5: getDistance(String, String) delegates to ZoneService
 * - No API calls made for suburb-only requests (Requirements 5.1, 5.3)
 * - SuburbNotFoundException propagated correctly
 */
@ExtendWith(MockitoExtension.class)
class DistanceCalculatorBackwardCompatibilityTest {

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
    void getDistance_withSuburbNames_shouldDelegateToZoneService() {
        // Arrange
        String pickupSuburb = "Johannesburg";
        String dropoffSuburb = "Pretoria";
        BigDecimal expectedDistance = new BigDecimal("50.00");
        
        when(zoneService.getDistance(pickupSuburb, dropoffSuburb)).thenReturn(expectedDistance);
        
        // Act
        BigDecimal result = distanceCalculator.getDistance(pickupSuburb, dropoffSuburb);
        
        // Assert
        assertThat(result).isEqualByComparingTo(expectedDistance);
        verify(zoneService).getDistance(pickupSuburb, dropoffSuburb);
        
        // Verify no API calls or cache operations
        verifyNoInteractions(googleMapsClient);
        verifyNoInteractions(distanceCache);
    }

    @Test
    void getDistance_withSuburbNames_shouldNotMakeApiCall() {
        // Arrange
        String pickupSuburb = "Cape Town";
        String dropoffSuburb = "Durban";
        BigDecimal expectedDistance = new BigDecimal("1650.00");
        
        when(zoneService.getDistance(pickupSuburb, dropoffSuburb)).thenReturn(expectedDistance);
        
        // Act
        distanceCalculator.getDistance(pickupSuburb, dropoffSuburb);
        
        // Assert - verify Google Maps API was never called
        verify(googleMapsClient, never()).getDistance(anyString(), anyString());
    }

    @Test
    void getDistance_withSuburbNames_shouldNotUseCache() {
        // Arrange
        String pickupSuburb = "Sandton";
        String dropoffSuburb = "Midrand";
        BigDecimal expectedDistance = new BigDecimal("20.00");
        
        when(zoneService.getDistance(pickupSuburb, dropoffSuburb)).thenReturn(expectedDistance);
        
        // Act
        distanceCalculator.getDistance(pickupSuburb, dropoffSuburb);
        
        // Assert - verify cache was never accessed
        verify(distanceCache, never()).lookup(anyString(), anyString());
        verify(distanceCache, never()).store(anyString(), anyString(), any(BigDecimal.class));
        verify(distanceCache, never()).normalize(anyString());
    }

    @Test
    void getDistance_withSuburbNames_shouldPropagateSuburbNotFoundException() {
        // Arrange
        String pickupSuburb = "UnknownSuburb1";
        String dropoffSuburb = "UnknownSuburb2";
        
        when(zoneService.getDistance(pickupSuburb, dropoffSuburb))
            .thenThrow(new SuburbNotFoundException(pickupSuburb));
        
        // Act & Assert
        assertThatThrownBy(() -> 
            distanceCalculator.getDistance(pickupSuburb, dropoffSuburb)
        )
            .isInstanceOf(SuburbNotFoundException.class)
            .hasMessageContaining(pickupSuburb);
        
        verify(zoneService).getDistance(pickupSuburb, dropoffSuburb);
    }

    @Test
    void getDistance_withSuburbNames_shouldReturnSameResultAsZoneService() {
        // Arrange - Test multiple suburb pairs
        String[] pickupSuburbs = {"Johannesburg", "Cape Town", "Durban"};
        String[] dropoffSuburbs = {"Pretoria", "Stellenbosch", "Pietermaritzburg"};
        BigDecimal[] expectedDistances = {
            new BigDecimal("50.00"),
            new BigDecimal("45.00"),
            new BigDecimal("65.00")
        };
        
        for (int i = 0; i < pickupSuburbs.length; i++) {
            when(zoneService.getDistance(pickupSuburbs[i], dropoffSuburbs[i]))
                .thenReturn(expectedDistances[i]);
        }
        
        // Act & Assert
        for (int i = 0; i < pickupSuburbs.length; i++) {
            BigDecimal result = distanceCalculator.getDistance(pickupSuburbs[i], dropoffSuburbs[i]);
            assertThat(result).isEqualByComparingTo(expectedDistances[i]);
        }
        
        // Verify each call was made to ZoneService
        for (int i = 0; i < pickupSuburbs.length; i++) {
            verify(zoneService).getDistance(pickupSuburbs[i], dropoffSuburbs[i]);
        }
    }

    @Test
    void getDistance_withSuburbNames_shouldHandleZeroDistance() {
        // Arrange - Same suburb (should return 0 from ZoneService)
        String suburb = "Johannesburg";
        BigDecimal expectedDistance = BigDecimal.ZERO.setScale(2);
        
        when(zoneService.getDistance(suburb, suburb)).thenReturn(expectedDistance);
        
        // Act
        BigDecimal result = distanceCalculator.getDistance(suburb, suburb);
        
        // Assert
        assertThat(result).isEqualByComparingTo(expectedDistance);
        verify(zoneService).getDistance(suburb, suburb);
    }
}
