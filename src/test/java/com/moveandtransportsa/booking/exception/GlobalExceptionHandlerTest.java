package com.moveandtransportsa.booking.exception;

import com.moveandtransportsa.booking.dto.response.ErrorEnvelope;
import com.moveandtransportsa.booking.exception.distance.ApiKeyNotConfiguredException;
import com.moveandtransportsa.booking.exception.distance.InvalidAddressException;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link GlobalExceptionHandler}.
 * 
 * Validates exception handling for distance calculation exceptions:
 * - InvalidAddressException returns 400 BAD_REQUEST
 * - ApiKeyNotConfiguredException returns 500 INTERNAL_SERVER_ERROR
 * 
 * Requirements covered: 6.2
 */
@ExtendWith(MockitoExtension.class)
class GlobalExceptionHandlerTest {

    @Mock
    private HttpServletRequest request;

    @InjectMocks
    private GlobalExceptionHandler handler;

    private static final String TEST_PATH = "/api/quote/with-addresses";

    @BeforeEach
    void setUp() {
        when(request.getRequestURI()).thenReturn(TEST_PATH);
    }

    // -----------------------------------------------------------------------
    // InvalidAddressException handler tests
    // -----------------------------------------------------------------------

    @Test
    void handleInvalidAddress_returns400BadRequest() {
        // Arrange
        InvalidAddressException exception = new InvalidAddressException("123 Main St, InvalidCity, GP");

        // Act
        ResponseEntity<ErrorEnvelope> response = handler.handleInvalidAddress(exception, request);

        // Assert
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().status()).isEqualTo(400);
        assertThat(response.getBody().error()).isEqualTo("Bad Request");
        assertThat(response.getBody().message()).isEqualTo("Invalid address provided");
        assertThat(response.getBody().path()).isEqualTo(TEST_PATH);
        assertThat(response.getBody().errors()).isNull();
    }

    @Test
    void handleInvalidAddress_doesNotExposeInternalDetails() {
        // Arrange: exception message contains address details
        InvalidAddressException exception = new InvalidAddressException("123 Secret Location, Private City, GP");

        // Act
        ResponseEntity<ErrorEnvelope> response = handler.handleInvalidAddress(exception, request);

        // Assert: internal address details are not exposed in response message
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().message()).isEqualTo("Invalid address provided");
        assertThat(response.getBody().message()).doesNotContain("Secret Location");
        assertThat(response.getBody().message()).doesNotContain("Private City");
    }

    @Test
    void handleInvalidAddress_includesTimestamp() {
        // Arrange
        InvalidAddressException exception = new InvalidAddressException("Invalid address");

        // Act
        ResponseEntity<ErrorEnvelope> response = handler.handleInvalidAddress(exception, request);

        // Assert
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().timestamp()).isNotNull();
        assertThat(response.getBody().timestamp()).isNotEmpty();
    }

    // -----------------------------------------------------------------------
    // ApiKeyNotConfiguredException handler tests
    // -----------------------------------------------------------------------

    @Test
    void handleApiKeyNotConfigured_returns500InternalServerError() {
        // Arrange
        ApiKeyNotConfiguredException exception = new ApiKeyNotConfiguredException(
            "Google Maps API key must be configured when google.maps.enabled=true"
        );

        // Act
        ResponseEntity<ErrorEnvelope> response = handler.handleApiKeyNotConfigured(exception, request);

        // Assert
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().status()).isEqualTo(500);
        assertThat(response.getBody().error()).isEqualTo("Internal Server Error");
        assertThat(response.getBody().message()).isEqualTo("Service configuration error");
        assertThat(response.getBody().path()).isEqualTo(TEST_PATH);
        assertThat(response.getBody().errors()).isNull();
    }

    @Test
    void handleApiKeyNotConfigured_doesNotExposeInternalDetails() {
        // Arrange: exception message contains configuration details
        ApiKeyNotConfiguredException exception = new ApiKeyNotConfiguredException(
            "Google Maps API key must be configured when google.maps.enabled=true"
        );

        // Act
        ResponseEntity<ErrorEnvelope> response = handler.handleApiKeyNotConfigured(exception, request);

        // Assert: configuration details are not exposed in response message
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().message()).isEqualTo("Service configuration error");
        assertThat(response.getBody().message()).doesNotContain("API key");
        assertThat(response.getBody().message()).doesNotContain("google.maps");
    }

    @Test
    void handleApiKeyNotConfigured_includesTimestamp() {
        // Arrange
        ApiKeyNotConfiguredException exception = new ApiKeyNotConfiguredException("Config error");

        // Act
        ResponseEntity<ErrorEnvelope> response = handler.handleApiKeyNotConfigured(exception, request);

        // Assert
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().timestamp()).isNotNull();
        assertThat(response.getBody().timestamp()).isNotEmpty();
    }

    // -----------------------------------------------------------------------
    // Response format consistency tests
    // -----------------------------------------------------------------------

    @Test
    void handleInvalidAddress_followsExistingErrorResponsePattern() {
        // Arrange
        InvalidAddressException exception = new InvalidAddressException("Test address");

        // Act
        ResponseEntity<ErrorEnvelope> response = handler.handleInvalidAddress(exception, request);

        // Assert: response follows the same structure as other handlers
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().timestamp()).isNotNull();
        assertThat(response.getBody().status()).isNotNull();
        assertThat(response.getBody().error()).isNotNull();
        assertThat(response.getBody().message()).isNotNull();
        assertThat(response.getBody().path()).isNotNull();
    }

    @Test
    void handleApiKeyNotConfigured_followsExistingErrorResponsePattern() {
        // Arrange
        ApiKeyNotConfiguredException exception = new ApiKeyNotConfiguredException("Config error");

        // Act
        ResponseEntity<ErrorEnvelope> response = handler.handleApiKeyNotConfigured(exception, request);

        // Assert: response follows the same structure as other handlers
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().timestamp()).isNotNull();
        assertThat(response.getBody().status()).isNotNull();
        assertThat(response.getBody().error()).isNotNull();
        assertThat(response.getBody().message()).isNotNull();
        assertThat(response.getBody().path()).isNotNull();
    }
}
