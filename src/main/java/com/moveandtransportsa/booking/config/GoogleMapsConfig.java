package com.moveandtransportsa.booking.config;

import com.moveandtransportsa.booking.exception.ApiKeyNotConfiguredException;
import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Binds Google Maps configuration properties from application.yml under the prefix "google.maps".
 * Provides type-safe configuration for Google Maps Distance Matrix API integration.
 */
@Configuration
@ConfigurationProperties(prefix = "google.maps")
@Getter
@Setter
public class GoogleMapsConfig {

    /**
     * Google Maps API key.
     * Required when enabled=true.
     * Can be provided via environment variable GOOGLE_MAPS_API_KEY.
     */
    private String apiKey;

    /**
     * Base URL for Google Maps Distance Matrix API.
     * Defaults to production URL, can be overridden for testing.
     * Default: https://maps.googleapis.com/maps/api/distancematrix/json
     */
    private String baseUrl = "https://maps.googleapis.com/maps/api/distancematrix/json";

    /**
     * Enable/disable Google Maps integration.
     * When false, always use zone-based fallback.
     * Default: true
     */
    private boolean enabled = true;

    /**
     * API request timeout in seconds.
     * Default: 5
     */
    private int timeoutSeconds = 5;

    /**
     * Maximum API requests per minute.
     * Used for rate limiting to control costs.
     * Default: 100
     */
    private int maxRequestsPerMinute = 100;

    /**
     * Enable/disable response caching.
     * Default: true
     */
    private boolean cacheEnabled = true;

    /**
     * Cache entry time-to-live in days.
     * Entries older than this are considered stale.
     * Default: 7
     */
    private int cacheTtlDays = 7;

    /**
     * Validates configuration at application startup.
     * Throws ApiKeyNotConfiguredException if enabled=true and apiKey is blank.
     */
    @PostConstruct
    public void validate() {
        if (enabled && (apiKey == null || apiKey.isBlank())) {
            throw new ApiKeyNotConfiguredException(
                "Google Maps API key must be configured when google.maps.enabled=true. " +
                "Set the property 'google.maps.api-key' or environment variable 'GOOGLE_MAPS_API_KEY'."
            );
        }
    }
}
