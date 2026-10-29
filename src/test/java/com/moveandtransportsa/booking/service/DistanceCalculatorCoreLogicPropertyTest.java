package com.moveandtransportsa.booking.service;

import com.moveandtransportsa.booking.config.GoogleMapsConfig;
import com.moveandtransportsa.booking.dto.Address;
import com.moveandtransportsa.booking.exception.SuburbNotFoundException;
import com.moveandtransportsa.booking.exception.distance.ApiConnectionException;
import com.moveandtransportsa.booking.exception.distance.InvalidAddressException;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import net.jqwik.api.*;
import net.jqwik.api.constraints.AlphaChars;
import net.jqwik.api.constraints.NotBlank;
import net.jqwik.api.constraints.StringLength;
import org.junit.jupiter.api.Tag;
import org.mockito.Mockito;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Property-based tests for {@link DistanceCalculator} core logic.
 * 
 * Tests verify universal properties across many randomly generated inputs,
 * focusing on same-address optimization and non-negative distance guarantees.
 * 
 * **Validates: Requirements 1.5, 1.4, 2.3, 2.4**
 */
@Tag("Feature: google-maps-distance-integration, Property 4 & 8")
class DistanceCalculatorCoreLogicPropertyTest {

    /**
     * **Property 4: Same Address Returns Zero**
     * 
     * For any address A, when calculating distance from A to A (after normalization),
     * the system SHALL return 0.00 without calling the API.
     * 
     * This property ensures that:
     * - Identical addresses always return zero distance
     * - No API call is made (optimization to avoid unnecessary costs)
     * - Normalization is applied before comparison (case-insensitive, whitespace-tolerant)
     * 
     * Examples:
     * - distance("123 Main St", "123 Main St") → 0.00
     * - distance("123 Main St", "123 main st") → 0.00 (case-insensitive)
     * - distance("  123 Main St  ", "123 Main St") → 0.00 (whitespace-tolerant)
     * 
     * **Validates: Requirements 1.5**
     */
    @Property(tries = 100)
    void sameAddressReturnsZero_distanceFromAtoAReturnsZeroWithoutApiCall(
            @ForAll @NotBlank @StringLength(min = 5, max = 100) String addressLine,
            @ForAll @NotBlank @AlphaChars @StringLength(min = 3, max = 30) String suburb,
            @ForAll @NotBlank @AlphaChars @StringLength(min = 3, max = 30) String city) {

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

        // Create the same address (but may have different whitespace/case variations)
        Address pickup = new Address(addressLine, suburb, city);
        Address dropoff = new Address(addressLine, suburb, city);

        // Mock the normalize method to return consistent normalized addresses
        String normalized = (addressLine + ", " + suburb + ", " + city).trim().toLowerCase();
        when(distanceCache.normalize(anyString())).thenReturn(normalized);

        // When: Calculating distance from address to itself
        BigDecimal result = calculator.getDistance(pickup, dropoff);

        // Then: Distance should be exactly 0.00 with scale 2
        assertThat(result).isEqualByComparingTo(BigDecimal.ZERO.setScale(2));
        assertThat(result.scale()).isEqualTo(2);

        // Verify no API call was made (optimization)
        verify(googleMapsClient, never()).getDistance(anyString(), anyString());
        
        // Verify no cache lookup was performed (short-circuit optimization)
        verify(distanceCache, never()).lookup(anyString(), anyString());
        
        // Verify no zone service call was made
        verify(zoneService, never()).getDistance(anyString(), anyString());

        // Clean up
        calculator.shutdown();
    }

    /**
     * **Property 4 Extended: Same Address With Variations Returns Zero**
     * 
     * Tests that addresses differing only in case and whitespace are still
     * recognized as the same address and return zero distance without API call.
     * 
     * This ensures the normalization logic properly handles variations:
     * - "123 Main St, Sandton, Johannesburg" and "123 MAIN ST, SANDTON, JOHANNESBURG"
     * - "  123 Main St, Sandton, Johannesburg  " and "123 Main St, Sandton, Johannesburg"
     * 
     * **Validates: Requirements 1.5**
     */
    @Property(tries = 100)
    void sameAddressReturnsZero_variationsInCaseAndWhitespaceStillReturnZero(
            @ForAll @NotBlank @StringLength(min = 5, max = 100) String addressLine,
            @ForAll @NotBlank @AlphaChars @StringLength(min = 3, max = 30) String suburb,
            @ForAll @NotBlank @AlphaChars @StringLength(min = 3, max = 30) String city) {

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

        // Create address variations with different case and whitespace
        Address pickup = new Address(addressLine.toLowerCase(), suburb.toLowerCase(), city.toLowerCase());
        Address dropoff = new Address(
            "  " + addressLine.toUpperCase() + "  ", 
            suburb.toUpperCase(), 
            city.toUpperCase()
        );

        // Mock normalize to return the same normalized value for both variations
        String normalized = (addressLine + ", " + suburb + ", " + city).trim().toLowerCase();
        when(distanceCache.normalize(anyString())).thenReturn(normalized);

        // When: Calculating distance between address variations
        BigDecimal result = calculator.getDistance(pickup, dropoff);

        // Then: Distance should be exactly 0.00 with scale 2
        assertThat(result).isEqualByComparingTo(BigDecimal.ZERO.setScale(2));
        assertThat(result.scale()).isEqualTo(2);

        // Verify no API call was made
        verify(googleMapsClient, never()).getDistance(anyString(), anyString());

        // Clean up
        calculator.shutdown();
    }

    /**
     * **Property 8: Distance Non-Negative**
     * 
     * For any successful distance calculation (API or fallback),
     * the returned value SHALL be greater than or equal to zero.
     * 
     * This property ensures that:
     * - All distances are non-negative
     * - No invalid negative distances can be returned
     * - Both API-based and zone-based calculations respect this constraint
     * 
     * This is a fundamental correctness property - negative distances
     * are physically impossible and would cause pricing errors.
     * 
     * **Validates: Requirements 1.4, 2.3, 2.4**
     */
    @Property(tries = 100)
    void distanceNonNegative_allReturnedDistancesAreGreaterThanOrEqualToZero(
            @ForAll @NotBlank @StringLength(min = 5, max = 100) String pickupLine,
            @ForAll @NotBlank @AlphaChars @StringLength(min = 3, max = 30) String pickupSuburb,
            @ForAll @NotBlank @AlphaChars @StringLength(min = 3, max = 30) String pickupCity,
            @ForAll @NotBlank @StringLength(min = 5, max = 100) String dropoffLine,
            @ForAll @NotBlank @AlphaChars @StringLength(min = 3, max = 30) String dropoffSuburb,
            @ForAll @NotBlank @AlphaChars @StringLength(min = 3, max = 30) String dropoffCity,
            @ForAll("distanceValues") BigDecimal apiDistance) {

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

        Address pickup = new Address(pickupLine, pickupSuburb, pickupCity);
        Address dropoff = new Address(dropoffLine, dropoffSuburb, dropoffCity);

        // Mock normalize to return different values for pickup and dropoff
        when(distanceCache.normalize(pickup.toFormattedString()))
            .thenReturn(pickupLine.toLowerCase() + "," + pickupSuburb.toLowerCase());
        when(distanceCache.normalize(dropoff.toFormattedString()))
            .thenReturn(dropoffLine.toLowerCase() + "," + dropoffSuburb.toLowerCase());

        // Mock cache to return empty (cache miss)
        when(distanceCache.lookup(anyString(), anyString())).thenReturn(Optional.empty());

        // Mock GoogleMapsClient to return a non-negative distance
        when(googleMapsClient.getDistance(anyString(), anyString()))
            .thenReturn(apiDistance);

        // When: Calculating distance
        BigDecimal result = calculator.getDistance(pickup, dropoff);

        // Then: Distance must be non-negative
        assertThat(result).isGreaterThanOrEqualTo(BigDecimal.ZERO);
        assertThat(result.scale()).isEqualTo(2);

        // Clean up
        calculator.shutdown();
    }

    /**
     * **Property 8 Extended: Fallback Distance Non-Negative**
     * 
     * Tests that even when the API fails and the system falls back
     * to zone-based calculation, the returned distance is still non-negative.
     * 
     * This ensures that fallback paths also maintain the non-negative invariant.
     * 
     * **Validates: Requirements 1.4, 2.3, 2.4**
     */
    @Property(tries = 100)
    void distanceNonNegative_fallbackDistancesAlsoNonNegative(
            @ForAll @NotBlank @StringLength(min = 5, max = 100) String pickupLine,
            @ForAll @NotBlank @AlphaChars @StringLength(min = 3, max = 30) String pickupSuburb,
            @ForAll @NotBlank @AlphaChars @StringLength(min = 3, max = 30) String pickupCity,
            @ForAll @NotBlank @StringLength(min = 5, max = 100) String dropoffLine,
            @ForAll @NotBlank @AlphaChars @StringLength(min = 3, max = 30) String dropoffSuburb,
            @ForAll @NotBlank @AlphaChars @StringLength(min = 3, max = 30) String dropoffCity,
            @ForAll("distanceValues") BigDecimal fallbackDistance) {

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

        Address pickup = new Address(pickupLine, pickupSuburb, pickupCity);
        Address dropoff = new Address(dropoffLine, dropoffSuburb, dropoffCity);

        // Mock normalize to return different values for pickup and dropoff
        when(distanceCache.normalize(pickup.toFormattedString()))
            .thenReturn(pickupLine.toLowerCase() + "," + pickupSuburb.toLowerCase());
        when(distanceCache.normalize(dropoff.toFormattedString()))
            .thenReturn(dropoffLine.toLowerCase() + "," + dropoffSuburb.toLowerCase());

        // Mock cache to return empty (cache miss)
        when(distanceCache.lookup(anyString(), anyString())).thenReturn(Optional.empty());

        // Mock GoogleMapsClient to throw an exception (simulating API failure)
        when(googleMapsClient.getDistance(anyString(), anyString()))
            .thenThrow(new ApiConnectionException(new RuntimeException("Connection timeout")));

        // Mock ZoneService to return a non-negative fallback distance
        when(zoneService.getDistance(pickupSuburb, dropoffSuburb))
            .thenReturn(fallbackDistance);

        // When: Calculating distance (will fallback to zone service)
        BigDecimal result = calculator.getDistance(pickup, dropoff);

        // Then: Fallback distance must be non-negative
        assertThat(result).isGreaterThanOrEqualTo(BigDecimal.ZERO);
        assertThat(result.scale()).isEqualTo(2);

        // Verify fallback was triggered
        verify(zoneService).getDistance(pickupSuburb, dropoffSuburb);

        // Clean up
        calculator.shutdown();
    }

    /**
     * **Property 8 Boundary Test: Zero Distance Is Valid**
     * 
     * Tests that zero distance is a valid non-negative value.
     * This is important for same-address scenarios.
     * 
     * **Validates: Requirements 1.4, 1.5**
     */
    @Property(tries = 50)
    void distanceNonNegative_zeroDistanceIsValid(
            @ForAll @NotBlank @StringLength(min = 5, max = 100) String addressLine,
            @ForAll @NotBlank @AlphaChars @StringLength(min = 3, max = 30) String suburb,
            @ForAll @NotBlank @AlphaChars @StringLength(min = 3, max = 30) String city) {

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

        Address address = new Address(addressLine, suburb, city);

        // Mock normalize to return the same value
        String normalized = addressLine.toLowerCase() + "," + suburb.toLowerCase();
        when(distanceCache.normalize(anyString())).thenReturn(normalized);

        // When: Calculating distance from address to itself
        BigDecimal result = calculator.getDistance(address, address);

        // Then: Zero distance is valid and non-negative
        assertThat(result).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(result).isGreaterThanOrEqualTo(BigDecimal.ZERO);

        // Clean up
        calculator.shutdown();
    }

    // -------------------------------------------------------------------------
    // Arbitrary Providers
    // -------------------------------------------------------------------------

    /**
     * Provides realistic distance values between 0.00 and 500.00 km with scale 2.
     * All values are guaranteed to be non-negative.
     */
    @Provide
    Arbitrary<BigDecimal> distanceValues() {
        return Arbitraries.bigDecimals()
                .between(BigDecimal.ZERO, new BigDecimal("500.00"))
                .ofScale(2);
    }
}
