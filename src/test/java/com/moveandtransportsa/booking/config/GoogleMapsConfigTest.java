package com.moveandtransportsa.booking.config;

import com.moveandtransportsa.booking.exception.ApiKeyNotConfiguredException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for GoogleMapsConfig.
 * Tests validation logic and default values.
 */
class GoogleMapsConfigTest {

    @Test
    void shouldHaveDefaultValues() {
        // Given
        GoogleMapsConfig config = new GoogleMapsConfig();

        // Then
        assertTrue(config.isEnabled());
        assertEquals(5, config.getTimeoutSeconds());
        assertEquals(100, config.getMaxRequestsPerMinute());
        assertTrue(config.isCacheEnabled());
        assertEquals(7, config.getCacheTtlDays());
    }

    @Test
    void shouldPassValidationWhenEnabledWithValidApiKey() {
        // Given
        GoogleMapsConfig config = new GoogleMapsConfig();
        config.setEnabled(true);
        config.setApiKey("valid-api-key-12345");

        // When/Then - should not throw
        assertDoesNotThrow(config::validate);
    }

    @Test
    void shouldPassValidationWhenDisabledWithoutApiKey() {
        // Given
        GoogleMapsConfig config = new GoogleMapsConfig();
        config.setEnabled(false);
        config.setApiKey(null);

        // When/Then - should not throw
        assertDoesNotThrow(config::validate);
    }

    @Test
    void shouldThrowExceptionWhenEnabledWithNullApiKey() {
        // Given
        GoogleMapsConfig config = new GoogleMapsConfig();
        config.setEnabled(true);
        config.setApiKey(null);

        // When/Then
        ApiKeyNotConfiguredException exception = assertThrows(
            ApiKeyNotConfiguredException.class,
            config::validate
        );
        assertTrue(exception.getMessage().contains("Google Maps API key must be configured"));
    }

    @Test
    void shouldThrowExceptionWhenEnabledWithBlankApiKey() {
        // Given
        GoogleMapsConfig config = new GoogleMapsConfig();
        config.setEnabled(true);
        config.setApiKey("   ");

        // When/Then
        ApiKeyNotConfiguredException exception = assertThrows(
            ApiKeyNotConfiguredException.class,
            config::validate
        );
        assertTrue(exception.getMessage().contains("Google Maps API key must be configured"));
    }

    @Test
    void shouldThrowExceptionWhenEnabledWithEmptyApiKey() {
        // Given
        GoogleMapsConfig config = new GoogleMapsConfig();
        config.setEnabled(true);
        config.setApiKey("");

        // When/Then
        ApiKeyNotConfiguredException exception = assertThrows(
            ApiKeyNotConfiguredException.class,
            config::validate
        );
        assertTrue(exception.getMessage().contains("Google Maps API key must be configured"));
    }

    @Test
    void shouldAllowSettingCustomValues() {
        // Given
        GoogleMapsConfig config = new GoogleMapsConfig();

        // When
        config.setApiKey("custom-key");
        config.setEnabled(false);
        config.setTimeoutSeconds(10);
        config.setMaxRequestsPerMinute(50);
        config.setCacheEnabled(false);
        config.setCacheTtlDays(14);

        // Then
        assertEquals("custom-key", config.getApiKey());
        assertFalse(config.isEnabled());
        assertEquals(10, config.getTimeoutSeconds());
        assertEquals(50, config.getMaxRequestsPerMinute());
        assertFalse(config.isCacheEnabled());
        assertEquals(14, config.getCacheTtlDays());
    }
}
