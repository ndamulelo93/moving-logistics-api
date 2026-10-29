package com.moveandtransportsa.booking.config;

import com.moveandtransportsa.booking.exception.ApiKeyNotConfiguredException;
import net.jqwik.api.*;
import net.jqwik.api.constraints.NotBlank;
import net.jqwik.api.constraints.StringLength;
import org.junit.jupiter.api.Tag;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Property-based tests for {@link GoogleMapsConfig} API key validation.
 * 
 * Tests verify that the configuration correctly validates API key requirements
 * based on the enabled flag at application startup.
 * 
 * **Validates: Requirements 6.3**
 */
@Tag("Feature: google-maps-distance-integration, Property 9")
class GoogleMapsConfigPropertyTest {

    /**
     * **Property 9: API Key Required When Enabled**
     * 
     * For any application startup where google.maps.enabled=true and api-key is blank or null,
     * the system SHALL throw ApiKeyNotConfiguredException.
     * 
     * This property ensures that:
     * - When enabled=true and API key is blank/null, validation fails with ApiKeyNotConfiguredException
     * - When enabled=false, API key can be blank (fallback mode)
     * - When enabled=true and API key is present, no exception is thrown
     * - Configuration validation happens at startup via @PostConstruct
     * 
     * Examples:
     * - enabled=true, apiKey=null → ApiKeyNotConfiguredException
     * - enabled=true, apiKey="" → ApiKeyNotConfiguredException
     * - enabled=true, apiKey="  " → ApiKeyNotConfiguredException
     * - enabled=false, apiKey=null → No exception
     * - enabled=false, apiKey="" → No exception
     * - enabled=true, apiKey="valid-key" → No exception
     * 
     * **Validates: Requirements 6.3**
     */
    @Property(tries = 100)
    @Tag("Feature: google-maps-distance-integration, Property 9")
    void apiKeyRequiredWhenEnabled_startupFailsIfEnabledTrueAndApiKeyBlank(
            @ForAll boolean enabled,
            @ForAll("apiKeyVariants") String apiKey) {

        // Given: A GoogleMapsConfig with various combinations of enabled flag and API key
        GoogleMapsConfig config = new GoogleMapsConfig();
        config.setEnabled(enabled);
        config.setApiKey(apiKey);

        // Define whether the API key is effectively blank
        boolean apiKeyBlank = apiKey == null || apiKey.isBlank();

        // When & Then: Validate configuration
        if (enabled && apiKeyBlank) {
            // Should throw ApiKeyNotConfiguredException when enabled=true and key is blank
            assertThatThrownBy(config::validate)
                .isInstanceOf(ApiKeyNotConfiguredException.class)
                .hasMessageContaining("Google Maps API key must be configured when google.maps.enabled=true");
        } else {
            // Should not throw exception in all other cases:
            // - enabled=false (regardless of API key)
            // - enabled=true and API key is present (not blank)
            config.validate();
            
            // If we reach here, no exception was thrown (which is correct)
            assertThat(true).isTrue();
        }
    }

    /**
     * **Property 9.1: API Key Validation - Enabled True with Null Key**
     * 
     * When enabled=true and apiKey is explicitly null, startup SHALL fail
     * with ApiKeyNotConfiguredException.
     * 
     * This is a focused test on the null case.
     * 
     * **Validates: Requirements 6.3**
     */
    @Property(tries = 100)
    @Tag("Feature: google-maps-distance-integration, Property 9")
    void apiKeyRequiredWhenEnabled_nullKeyWithEnabledThrowsException() {
        // Given: enabled=true and apiKey=null
        GoogleMapsConfig config = new GoogleMapsConfig();
        config.setEnabled(true);
        config.setApiKey(null);

        // When & Then: validate() should throw ApiKeyNotConfiguredException
        assertThatThrownBy(config::validate)
            .isInstanceOf(ApiKeyNotConfiguredException.class)
            .hasMessageContaining("Google Maps API key must be configured")
            .hasMessageContaining("google.maps.enabled=true");
    }

    /**
     * **Property 9.2: API Key Validation - Enabled True with Empty String**
     * 
     * When enabled=true and apiKey is an empty string, startup SHALL fail
     * with ApiKeyNotConfiguredException.
     * 
     * **Validates: Requirements 6.3**
     */
    @Property(tries = 100)
    @Tag("Feature: google-maps-distance-integration, Property 9")
    void apiKeyRequiredWhenEnabled_emptyKeyWithEnabledThrowsException() {
        // Given: enabled=true and apiKey=""
        GoogleMapsConfig config = new GoogleMapsConfig();
        config.setEnabled(true);
        config.setApiKey("");

        // When & Then: validate() should throw ApiKeyNotConfiguredException
        assertThatThrownBy(config::validate)
            .isInstanceOf(ApiKeyNotConfiguredException.class)
            .hasMessageContaining("Google Maps API key must be configured")
            .hasMessageContaining("google.maps.enabled=true");
    }

    /**
     * **Property 9.3: API Key Validation - Enabled True with Whitespace-Only Key**
     * 
     * When enabled=true and apiKey contains only whitespace, startup SHALL fail
     * with ApiKeyNotConfiguredException.
     * 
     * **Validates: Requirements 6.3**
     */
    @Property(tries = 100)
    @Tag("Feature: google-maps-distance-integration, Property 9")
    void apiKeyRequiredWhenEnabled_whitespaceKeyWithEnabledThrowsException(
            @ForAll @StringLength(min = 1, max = 10) String whitespace) {
        
        // Given: enabled=true and apiKey contains only whitespace
        String whitespaceKey = whitespace.replaceAll(".", " ");
        
        GoogleMapsConfig config = new GoogleMapsConfig();
        config.setEnabled(true);
        config.setApiKey(whitespaceKey);

        // When & Then: validate() should throw ApiKeyNotConfiguredException
        assertThatThrownBy(config::validate)
            .isInstanceOf(ApiKeyNotConfiguredException.class)
            .hasMessageContaining("Google Maps API key must be configured")
            .hasMessageContaining("google.maps.enabled=true");
    }

    /**
     * **Property 9.4: API Key Validation - Disabled with Blank Key**
     * 
     * When enabled=false, the API key can be blank (null, empty, or whitespace)
     * without throwing an exception, as the system uses fallback mode.
     * 
     * **Validates: Requirements 6.3**
     */
    @Property(tries = 100)
    @Tag("Feature: google-maps-distance-integration, Property 9")
    void apiKeyNotRequiredWhenDisabled_disabledWithBlankKeyDoesNotThrow(
            @ForAll("blankApiKeys") String apiKey) {

        // Given: enabled=false and apiKey is blank (null, empty, or whitespace)
        GoogleMapsConfig config = new GoogleMapsConfig();
        config.setEnabled(false);
        config.setApiKey(apiKey);

        // When & Then: validate() should NOT throw any exception
        config.validate();
        
        // If we reach here, no exception was thrown (which is correct)
        assertThat(config.isEnabled()).isFalse();
    }

    /**
     * **Property 9.5: API Key Validation - Enabled with Valid Key**
     * 
     * When enabled=true and apiKey has a non-blank value, validation SHALL succeed
     * without throwing an exception.
     * 
     * **Validates: Requirements 6.3**
     */
    @Property(tries = 100)
    @Tag("Feature: google-maps-distance-integration, Property 9")
    void apiKeyValidWhenEnabled_enabledWithValidKeyDoesNotThrow(
            @ForAll @NotBlank @StringLength(min = 1, max = 100) String validApiKey) {

        // Given: enabled=true and apiKey is a valid non-blank string
        GoogleMapsConfig config = new GoogleMapsConfig();
        config.setEnabled(true);
        config.setApiKey(validApiKey);

        // When & Then: validate() should NOT throw any exception
        config.validate();
        
        // If we reach here, no exception was thrown (which is correct)
        assertThat(config.isEnabled()).isTrue();
        assertThat(config.getApiKey()).isEqualTo(validApiKey);
    }

    /**
     * **Property 9.6: API Key Validation - Disabled with Valid Key**
     * 
     * When enabled=false and apiKey has a valid value, validation SHALL succeed.
     * The system will use fallback mode regardless of having a valid key.
     * 
     * **Validates: Requirements 6.3**
     */
    @Property(tries = 100)
    @Tag("Feature: google-maps-distance-integration, Property 9")
    void apiKeyValidWhenDisabled_disabledWithValidKeyDoesNotThrow(
            @ForAll @NotBlank @StringLength(min = 1, max = 100) String validApiKey) {

        // Given: enabled=false and apiKey is a valid non-blank string
        GoogleMapsConfig config = new GoogleMapsConfig();
        config.setEnabled(false);
        config.setApiKey(validApiKey);

        // When & Then: validate() should NOT throw any exception
        config.validate();
        
        // If we reach here, no exception was thrown (which is correct)
        assertThat(config.isEnabled()).isFalse();
        assertThat(config.getApiKey()).isEqualTo(validApiKey);
    }

    /**
     * Provides various API key variants for testing including:
     * - null values
     * - empty strings
     * - whitespace-only strings
     * - valid API keys
     */
    @Provide
    Arbitrary<String> apiKeyVariants() {
        return Arbitraries.oneOf(
            Arbitraries.just(null),                                     // null key
            Arbitraries.just(""),                                       // empty string
            Arbitraries.just("   "),                                    // whitespace only
            Arbitraries.just("\t\n"),                                   // various whitespace
            Arbitraries.strings().alpha().ofMinLength(10).ofMaxLength(50) // valid keys
        );
    }

    /**
     * Provides blank API key values for testing:
     * - null
     * - empty string
     * - whitespace-only strings
     */
    @Provide
    Arbitrary<String> blankApiKeys() {
        return Arbitraries.oneOf(
            Arbitraries.just(null),
            Arbitraries.just(""),
            Arbitraries.just("   "),
            Arbitraries.just("\t"),
            Arbitraries.just("\n"),
            Arbitraries.just("  \t\n  ")
        );
    }
}
