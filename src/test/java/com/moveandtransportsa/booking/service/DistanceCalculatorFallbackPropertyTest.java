package com.moveandtransportsa.booking.service;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.moveandtransportsa.booking.config.GoogleMapsConfig;
import com.moveandtransportsa.booking.dto.Address;
import com.moveandtransportsa.booking.exception.SuburbNotFoundException;
import com.moveandtransportsa.booking.exception.distance.ApiAuthenticationException;
import com.moveandtransportsa.booking.exception.distance.ApiConnectionException;
import com.moveandtransportsa.booking.exception.distance.InvalidAddressException;
import com.moveandtransportsa.booking.exception.distance.RateLimitExceededException;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import net.jqwik.api.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Property-based tests for {@link DistanceCalculator} fallback behavior.
 * 
 * Tests verify that the system correctly falls back to zone-based distance calculation
 * when Google Maps API failures occur, including connection failures, rate limits,
 * authentication errors, and invalid addresses.
 * 
 * **Validates: Requirements 3.1, 3.2, 3.3, 3.5, 3.4**
 */
@Tag("Feature: google-maps-distance-integration, Property 6, 7 & 12")
class DistanceCalculatorFallbackPropertyTest {

    private DistanceCalculator distanceCalculator;
    private GoogleMapsClient mockGoogleMapsClient;
    private DistanceCache mockDistanceCache;
    private ZoneService mockZoneService;
    private GoogleMapsConfig config;
    private MeterRegistry meterRegistry;
    private ListAppender<ILoggingEvent> logAppender;
    private Logger logger;

    /**
     * Set up test fixtures before each property test.
     * 
     * This method is called by jqwik before each property test execution.
     * It initializes mocks, configuration, and logging capture.
     */
    @BeforeProperty
    void setUp() {
        // Create mocks
        mockGoogleMapsClient = mock(GoogleMapsClient.class);
        mockDistanceCache = mock(DistanceCache.class);
        mockZoneService = mock(ZoneService.class);
        
        // Create meter registry
        meterRegistry = new SimpleMeterRegistry();
        
        // Create configuration
        config = new GoogleMapsConfig();
        config.setEnabled(true);
        config.setApiKey("test-api-key");
        config.setMaxRequestsPerMinute(100);
        config.setTimeoutSeconds(5);
        config.setCacheEnabled(true);
        config.setCacheTtlDays(7);
        
        // Set up logging capture
        logger = (Logger) LoggerFactory.getLogger(DistanceCalculator.class);
        logAppender = new ListAppender<>();
        logAppender.start();
        logger.addAppender(logAppender);
        
        // Create the calculator
        distanceCalculator = new DistanceCalculator(
            mockGoogleMapsClient,
            mockDistanceCache,
            mockZoneService,
            config,
            meterRegistry
        );
    }

    @AfterEach
    void tearDown() {
        if (distanceCalculator != null) {
            distanceCalculator.shutdown();
        }
        if (logger != null && logAppender != null) {
            logger.detachAppender(logAppender);
        }
    }

    /**
     * **Property 6: API Failure Falls Back to Zone**
     * 
     * For any API connection failure, rate limit exception, or authentication exception,
     * the system SHALL attempt zone-based fallback using the suburb from the address.
     * 
     * This property tests that when Google Maps API throws exceptions indicating
     * infrastructure or operational failures (connection timeouts, rate limits,
     * authentication issues), the system gracefully falls back to zone-based
     * distance calculation using the suburb extracted from the addresses.
     * 
     * **Validates: Requirements 3.1, 3.2, 3.3**
     */
    @Property(tries = 50)
    void apiFailureFallback_connectionFailureTriggersZoneFallback(
            @ForAll("validAddressPair") AddressPair addressPair) {
        
        // Given: Cache returns empty (cache miss)
        when(mockDistanceCache.normalize(anyString())).thenReturn("normalized");
        when(mockDistanceCache.lookup(anyString(), anyString())).thenReturn(Optional.empty());
        
        // Given: GoogleMapsClient throws ApiConnectionException (simulating network failure)
        when(mockGoogleMapsClient.getDistance(anyString(), anyString()))
            .thenThrow(new ApiConnectionException(new RuntimeException("Network timeout")));
        
        // Given: ZoneService returns a fallback distance
        BigDecimal expectedFallbackDistance = new BigDecimal("25.50");
        when(mockZoneService.getDistance(addressPair.pickup.getSuburb(), addressPair.dropoff.getSuburb()))
            .thenReturn(expectedFallbackDistance);
        
        // When: Calculating distance
        BigDecimal result = distanceCalculator.getDistance(addressPair.pickup, addressPair.dropoff);
        
        // Then: Should return zone-based distance
        assertThat(result).isEqualByComparingTo(expectedFallbackDistance);
        
        // Then: Should call Google Maps API first (failed)
        verify(mockGoogleMapsClient).getDistance(anyString(), anyString());
        
        // Then: Should fall back to zone service
        verify(mockZoneService).getDistance(addressPair.pickup.getSuburb(), addressPair.dropoff.getSuburb());
        
        // Then: Should increment fallback metric with correct reason
        assertThat(meterRegistry.counter("distance_fallback_total", "reason", "ApiConnectionException").count())
            .isGreaterThan(0);
    }

    /**
     * **Property 6: API Failure Falls Back to Zone (Rate Limit)**
     * 
     * For any rate limit exception, the system SHALL fall back to zone-based calculation.
     * 
     * **Validates: Requirements 3.2**
     */
    @Property(tries = 50)
    void apiFailureFallback_rateLimitExceededTriggersZoneFallback(
            @ForAll("validAddressPair") AddressPair addressPair) {
        
        // Given: Cache returns empty (cache miss)
        when(mockDistanceCache.normalize(anyString())).thenReturn("normalized");
        when(mockDistanceCache.lookup(anyString(), anyString())).thenReturn(Optional.empty());
        
        // Given: GoogleMapsClient throws RateLimitExceededException
        when(mockGoogleMapsClient.getDistance(anyString(), anyString()))
            .thenThrow(new RateLimitExceededException());
        
        // Given: ZoneService returns a fallback distance
        BigDecimal expectedFallbackDistance = new BigDecimal("18.75");
        when(mockZoneService.getDistance(addressPair.pickup.getSuburb(), addressPair.dropoff.getSuburb()))
            .thenReturn(expectedFallbackDistance);
        
        // When: Calculating distance
        BigDecimal result = distanceCalculator.getDistance(addressPair.pickup, addressPair.dropoff);
        
        // Then: Should return zone-based distance
        assertThat(result).isEqualByComparingTo(expectedFallbackDistance);
        
        // Then: Should call Google Maps API first (failed)
        verify(mockGoogleMapsClient).getDistance(anyString(), anyString());
        
        // Then: Should fall back to zone service
        verify(mockZoneService).getDistance(addressPair.pickup.getSuburb(), addressPair.dropoff.getSuburb());
        
        // Then: Should increment fallback metric with correct reason
        assertThat(meterRegistry.counter("distance_fallback_total", "reason", "RateLimitExceededException").count())
            .isGreaterThan(0);
    }

    /**
     * **Property 6: API Failure Falls Back to Zone (Authentication)**
     * 
     * For any authentication exception, the system SHALL fall back to zone-based calculation.
     * 
     * **Validates: Requirements 3.3**
     */
    @Property(tries = 50)
    void apiFailureFallback_authenticationFailureTriggersZoneFallback(
            @ForAll("validAddressPair") AddressPair addressPair) {
        
        // Given: Cache returns empty (cache miss)
        when(mockDistanceCache.normalize(anyString())).thenReturn("normalized");
        when(mockDistanceCache.lookup(anyString(), anyString())).thenReturn(Optional.empty());
        
        // Given: GoogleMapsClient throws ApiAuthenticationException
        when(mockGoogleMapsClient.getDistance(anyString(), anyString()))
            .thenThrow(new ApiAuthenticationException());
        
        // Given: ZoneService returns a fallback distance
        BigDecimal expectedFallbackDistance = new BigDecimal("32.00");
        when(mockZoneService.getDistance(addressPair.pickup.getSuburb(), addressPair.dropoff.getSuburb()))
            .thenReturn(expectedFallbackDistance);
        
        // When: Calculating distance
        BigDecimal result = distanceCalculator.getDistance(addressPair.pickup, addressPair.dropoff);
        
        // Then: Should return zone-based distance
        assertThat(result).isEqualByComparingTo(expectedFallbackDistance);
        
        // Then: Should call Google Maps API first (failed)
        verify(mockGoogleMapsClient).getDistance(anyString(), anyString());
        
        // Then: Should fall back to zone service
        verify(mockZoneService).getDistance(addressPair.pickup.getSuburb(), addressPair.dropoff.getSuburb());
        
        // Then: Should increment fallback metric with correct reason
        assertThat(meterRegistry.counter("distance_fallback_total", "reason", "ApiAuthenticationException").count())
            .isGreaterThan(0);
    }

    /**
     * **Property 7: Invalid Address Attempts Fallback**
     * 
     * For any address pair where Google Maps returns NOT_FOUND or ZERO_RESULTS,
     * the system SHALL attempt zone-based fallback before throwing InvalidAddressException.
     * 
     * When Google Maps cannot find an address (returns NOT_FOUND status), the system
     * should extract the suburb from the address and attempt to use the zone-based
     * distance calculation as a fallback. Only if the suburb is also not found in
     * the zone system should the system throw InvalidAddressException.
     * 
     * **Validates: Requirements 3.5**
     */
    @Property(tries = 50)
    void invalidAddressFallback_notFoundAttemptsZoneLookup(
            @ForAll("validAddressPair") AddressPair addressPair) {
        
        // Given: Cache returns empty (cache miss)
        when(mockDistanceCache.normalize(anyString())).thenReturn("normalized");
        when(mockDistanceCache.lookup(anyString(), anyString())).thenReturn(Optional.empty());
        
        // Given: GoogleMapsClient throws InvalidAddressException (NOT_FOUND)
        when(mockGoogleMapsClient.getDistance(anyString(), anyString()))
            .thenThrow(new InvalidAddressException("123 Unknown St"));
        
        // Given: ZoneService successfully returns a fallback distance
        BigDecimal expectedFallbackDistance = new BigDecimal("22.80");
        when(mockZoneService.getDistance(addressPair.pickup.getSuburb(), addressPair.dropoff.getSuburb()))
            .thenReturn(expectedFallbackDistance);
        
        // When: Calculating distance
        BigDecimal result = distanceCalculator.getDistance(addressPair.pickup, addressPair.dropoff);
        
        // Then: Should return zone-based fallback distance
        assertThat(result).isEqualByComparingTo(expectedFallbackDistance);
        
        // Then: Should call Google Maps API first (failed)
        verify(mockGoogleMapsClient).getDistance(anyString(), anyString());
        
        // Then: Should attempt zone service fallback
        verify(mockZoneService).getDistance(addressPair.pickup.getSuburb(), addressPair.dropoff.getSuburb());
        
        // Then: Should increment fallback metric with invalid_address reason
        assertThat(meterRegistry.counter("distance_fallback_total", "reason", "invalid_address").count())
            .isGreaterThan(0);
    }

    /**
     * **Property 7 Extended: Invalid Address with Suburb Not Found**
     * 
     * When both Google Maps and zone system fail to find the address,
     * the system SHALL throw InvalidAddressException.
     * 
     * **Validates: Requirements 3.5**
     */
    @Property(tries = 50)
    void invalidAddressFallback_notFoundAndSuburbNotFoundThrowsException(
            @ForAll("validAddressPair") AddressPair addressPair) {
        
        // Given: Cache returns empty (cache miss)
        when(mockDistanceCache.normalize(anyString())).thenReturn("normalized");
        when(mockDistanceCache.lookup(anyString(), anyString())).thenReturn(Optional.empty());
        
        // Given: GoogleMapsClient throws InvalidAddressException
        when(mockGoogleMapsClient.getDistance(anyString(), anyString()))
            .thenThrow(new InvalidAddressException("123 Unknown St"));
        
        // Given: ZoneService also throws SuburbNotFoundException
        when(mockZoneService.getDistance(addressPair.pickup.getSuburb(), addressPair.dropoff.getSuburb()))
            .thenThrow(new SuburbNotFoundException(addressPair.pickup.getSuburb()));
        
        // When & Then: Should throw InvalidAddressException
        assertThatThrownBy(() -> distanceCalculator.getDistance(addressPair.pickup, addressPair.dropoff))
            .isInstanceOf(InvalidAddressException.class)
            .hasMessageContaining("Address not found in Google Maps and suburb not found in zone system")
            .hasMessageContaining(addressPair.pickup.getSuburb())
            .hasMessageContaining(addressPair.dropoff.getSuburb());
        
        // Then: Should have called both Google Maps and Zone Service
        verify(mockGoogleMapsClient).getDistance(anyString(), anyString());
        verify(mockZoneService).getDistance(addressPair.pickup.getSuburb(), addressPair.dropoff.getSuburb());
        
        // Then: Should still increment fallback metric even though fallback failed
        assertThat(meterRegistry.counter("distance_fallback_total", "reason", "invalid_address").count())
            .isGreaterThan(0);
    }

    /**
     * **Property 12: Fallback Logs Warning**
     * 
     * For any fallback to zone-based calculation, the system SHALL log a warning message
     * including the suburb names and reason for fallback.
     * 
     * This property ensures that all fallback scenarios (connection failure, rate limit,
     * authentication failure, invalid address) produce proper warning logs that include:
     * - The pickup suburb
     * - The dropoff suburb
     * - The reason for the fallback
     * 
     * **Validates: Requirements 3.4**
     */
    @Property(tries = 50)
    void fallbackLogging_connectionFailureLogsWarning(
            @ForAll("validAddressPair") AddressPair addressPair) {
        
        // Given: Cache returns empty (cache miss)
        when(mockDistanceCache.normalize(anyString())).thenReturn("normalized");
        when(mockDistanceCache.lookup(anyString(), anyString())).thenReturn(Optional.empty());
        
        // Given: GoogleMapsClient throws ApiConnectionException
        when(mockGoogleMapsClient.getDistance(anyString(), anyString()))
            .thenThrow(new ApiConnectionException(new RuntimeException("Network timeout")));
        
        // Given: ZoneService returns a fallback distance
        when(mockZoneService.getDistance(addressPair.pickup.getSuburb(), addressPair.dropoff.getSuburb()))
            .thenReturn(new BigDecimal("20.00"));
        
        // When: Calculating distance
        distanceCalculator.getDistance(addressPair.pickup, addressPair.dropoff);
        
        // Then: Should log warning with pickup, dropoff, and reason
        List<ILoggingEvent> logEvents = logAppender.list;
        assertThat(logEvents)
            .anyMatch(event -> 
                event.getLevel() == Level.WARN &&
                event.getFormattedMessage().contains("Falling back to zone-based distance") &&
                event.getFormattedMessage().contains(addressPair.pickup.getSuburb()) &&
                event.getFormattedMessage().contains(addressPair.dropoff.getSuburb()) &&
                event.getFormattedMessage().contains("ApiConnectionException")
            );
    }

    /**
     * **Property 12: Fallback Logs Warning (Rate Limit)**
     * 
     * Verifies that rate limit fallbacks log warning messages.
     * 
     * **Validates: Requirements 3.4**
     */
    @Property(tries = 50)
    void fallbackLogging_rateLimitLogsWarning(
            @ForAll("validAddressPair") AddressPair addressPair) {
        
        // Given: Cache returns empty (cache miss)
        when(mockDistanceCache.normalize(anyString())).thenReturn("normalized");
        when(mockDistanceCache.lookup(anyString(), anyString())).thenReturn(Optional.empty());
        
        // Given: GoogleMapsClient throws RateLimitExceededException
        when(mockGoogleMapsClient.getDistance(anyString(), anyString()))
            .thenThrow(new RateLimitExceededException());
        
        // Given: ZoneService returns a fallback distance
        when(mockZoneService.getDistance(addressPair.pickup.getSuburb(), addressPair.dropoff.getSuburb()))
            .thenReturn(new BigDecimal("15.00"));
        
        // When: Calculating distance
        distanceCalculator.getDistance(addressPair.pickup, addressPair.dropoff);
        
        // Then: Should log warning with pickup, dropoff, and reason
        List<ILoggingEvent> logEvents = logAppender.list;
        assertThat(logEvents)
            .anyMatch(event -> 
                event.getLevel() == Level.WARN &&
                event.getFormattedMessage().contains("Falling back to zone-based distance") &&
                event.getFormattedMessage().contains(addressPair.pickup.getSuburb()) &&
                event.getFormattedMessage().contains(addressPair.dropoff.getSuburb()) &&
                event.getFormattedMessage().contains("RateLimitExceededException")
            );
    }

    /**
     * **Property 12: Fallback Logs Warning (Authentication)**
     * 
     * Verifies that authentication failure fallbacks log warning messages.
     * 
     * **Validates: Requirements 3.4**
     */
    @Property(tries = 50)
    void fallbackLogging_authenticationFailureLogsWarning(
            @ForAll("validAddressPair") AddressPair addressPair) {
        
        // Given: Cache returns empty (cache miss)
        when(mockDistanceCache.normalize(anyString())).thenReturn("normalized");
        when(mockDistanceCache.lookup(anyString(), anyString())).thenReturn(Optional.empty());
        
        // Given: GoogleMapsClient throws ApiAuthenticationException
        when(mockGoogleMapsClient.getDistance(anyString(), anyString()))
            .thenThrow(new ApiAuthenticationException());
        
        // Given: ZoneService returns a fallback distance
        when(mockZoneService.getDistance(addressPair.pickup.getSuburb(), addressPair.dropoff.getSuburb()))
            .thenReturn(new BigDecimal("28.50"));
        
        // When: Calculating distance
        distanceCalculator.getDistance(addressPair.pickup, addressPair.dropoff);
        
        // Then: Should log warning with pickup, dropoff, and reason
        List<ILoggingEvent> logEvents = logAppender.list;
        assertThat(logEvents)
            .anyMatch(event -> 
                event.getLevel() == Level.WARN &&
                event.getFormattedMessage().contains("Falling back to zone-based distance") &&
                event.getFormattedMessage().contains(addressPair.pickup.getSuburb()) &&
                event.getFormattedMessage().contains(addressPair.dropoff.getSuburb()) &&
                event.getFormattedMessage().contains("ApiAuthenticationException")
            );
    }

    /**
     * **Property 12: Fallback Logs Warning (Invalid Address)**
     * 
     * Verifies that invalid address fallbacks log warning messages.
     * 
     * **Validates: Requirements 3.4**
     */
    @Property(tries = 50)
    void fallbackLogging_invalidAddressLogsWarning(
            @ForAll("validAddressPair") AddressPair addressPair) {
        
        // Given: Cache returns empty (cache miss)
        when(mockDistanceCache.normalize(anyString())).thenReturn("normalized");
        when(mockDistanceCache.lookup(anyString(), anyString())).thenReturn(Optional.empty());
        
        // Given: GoogleMapsClient throws InvalidAddressException
        when(mockGoogleMapsClient.getDistance(anyString(), anyString()))
            .thenThrow(new InvalidAddressException("Address not found"));
        
        // Given: ZoneService returns a fallback distance
        when(mockZoneService.getDistance(addressPair.pickup.getSuburb(), addressPair.dropoff.getSuburb()))
            .thenReturn(new BigDecimal("12.00"));
        
        // When: Calculating distance
        distanceCalculator.getDistance(addressPair.pickup, addressPair.dropoff);
        
        // Then: Should log warning messages
        List<ILoggingEvent> logEvents = logAppender.list;
        
        // Should log "Address not found in Google Maps, attempting zone fallback"
        assertThat(logEvents)
            .anyMatch(event -> 
                event.getLevel() == Level.WARN &&
                event.getFormattedMessage().contains("Address not found in Google Maps, attempting zone fallback") &&
                event.getFormattedMessage().contains(addressPair.pickup.getSuburb()) &&
                event.getFormattedMessage().contains(addressPair.dropoff.getSuburb())
            );
        
        // Should also log "Falling back to zone-based distance"
        assertThat(logEvents)
            .anyMatch(event -> 
                event.getLevel() == Level.WARN &&
                event.getFormattedMessage().contains("Falling back to zone-based distance") &&
                event.getFormattedMessage().contains(addressPair.pickup.getSuburb()) &&
                event.getFormattedMessage().contains(addressPair.dropoff.getSuburb()) &&
                event.getFormattedMessage().contains("invalid_address")
            );
    }

    /**
     * **Property 12 Extended: All Fallback Scenarios Log Suburbs**
     * 
     * Verifies that all fallback types include both pickup and dropoff suburbs in logs.
     * 
     * **Validates: Requirements 3.4**
     */
    @Property(tries = 50)
    void fallbackLogging_allFallbackTypesIncludeSuburbs(
            @ForAll("validAddressPair") AddressPair addressPair,
            @ForAll("apiException") Exception apiException) {
        
        // Given: Cache returns empty (cache miss)
        when(mockDistanceCache.normalize(anyString())).thenReturn("normalized");
        when(mockDistanceCache.lookup(anyString(), anyString())).thenReturn(Optional.empty());
        
        // Given: GoogleMapsClient throws the generated exception
        when(mockGoogleMapsClient.getDistance(anyString(), anyString()))
            .thenThrow(apiException);
        
        // Given: ZoneService returns a fallback distance (or might throw for InvalidAddressException)
        when(mockZoneService.getDistance(addressPair.pickup.getSuburb(), addressPair.dropoff.getSuburb()))
            .thenReturn(new BigDecimal("15.00"));
        
        // When: Calculating distance
        distanceCalculator.getDistance(addressPair.pickup, addressPair.dropoff);
        
        // Then: Should log warning with both suburbs
        List<ILoggingEvent> logEvents = logAppender.list;
        assertThat(logEvents)
            .anyMatch(event -> 
                event.getLevel() == Level.WARN &&
                event.getFormattedMessage().contains(addressPair.pickup.getSuburb()) &&
                event.getFormattedMessage().contains(addressPair.dropoff.getSuburb())
            );
    }

    // -------------------------------------------------------------------------
    // Arbitrary Providers
    // -------------------------------------------------------------------------

    /**
     * Provides valid address pairs for testing.
     * Generates pairs of addresses with different suburbs.
     */
    @Provide
    Arbitrary<AddressPair> validAddressPair() {
        Arbitrary<Address> pickupAddress = Arbitraries.of(
            new Address("123 Main Street", "Sandton", "Johannesburg"),
            new Address("45 Oak Avenue", "Rosebank", "Johannesburg"),
            new Address("789 Park Lane", "Bryanston", "Johannesburg"),
            new Address("12 Beach Road", "Umhlanga", "Durban"),
            new Address("34 Mountain Drive", "Camps Bay", "Cape Town")
        );
        
        Arbitrary<Address> dropoffAddress = Arbitraries.of(
            new Address("67 Pine Street", "Randburg", "Johannesburg"),
            new Address("89 Elm Road", "Midrand", "Johannesburg"),
            new Address("111 Cedar Avenue", "Centurion", "Pretoria"),
            new Address("22 Coral Drive", "Durban North", "Durban"),
            new Address("55 Sea Point Boulevard", "Sea Point", "Cape Town")
        );
        
        return Combinators.combine(pickupAddress, dropoffAddress)
            .as((pickup, dropoff) -> new AddressPair(pickup, dropoff));
    }

    /**
     * Provides API exceptions that should trigger fallback behavior.
     */
    @Provide
    Arbitrary<Exception> apiException() {
        return Arbitraries.of(
            new ApiConnectionException(new RuntimeException("Network timeout")),
            new RateLimitExceededException(),
            new ApiAuthenticationException(),
            new InvalidAddressException("Address not found")
        );
    }

    // -------------------------------------------------------------------------
    // Helper Classes
    // -------------------------------------------------------------------------

    /**
     * Helper record to hold address pairs for testing.
     */
    private record AddressPair(Address pickup, Address dropoff) {}
}
