package com.moveandtransportsa.booking.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.moveandtransportsa.booking.config.GoogleMapsConfig;
import com.moveandtransportsa.booking.exception.distance.*;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.net.SocketTimeoutException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link GoogleMapsClient}.
 * 
 * Tests cover:
 * - Request construction (Requirements 2.1, 2.2, 9.3, 9.4)
 * - Response parsing (Requirements 2.3, 2.4)
 * - Error handling for various API statuses (Requirements 2.5-2.10, 10.1-10.7)
 * - Timeout configuration (Requirement 9.4)
 * - Metrics recording (Requirement 12.5)
 */
@ExtendWith(MockitoExtension.class)
class GoogleMapsClientTest {

    @Mock
    private GoogleMapsConfig config;

    @Mock
    private RestTemplateBuilder restTemplateBuilder;

    @Mock
    private RestTemplate restTemplate;

    private MeterRegistry meterRegistry;
    private ObjectMapper objectMapper;
    private GoogleMapsClient googleMapsClient;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        objectMapper = new ObjectMapper();
        
        when(config.getApiKey()).thenReturn("test-api-key");
        when(config.getTimeoutSeconds()).thenReturn(5);
        
        when(restTemplateBuilder.setConnectTimeout(any()))
            .thenReturn(restTemplateBuilder);
        when(restTemplateBuilder.setReadTimeout(any()))
            .thenReturn(restTemplateBuilder);
        when(restTemplateBuilder.build())
            .thenReturn(restTemplate);
        
        googleMapsClient = new GoogleMapsClient(config, meterRegistry, objectMapper, restTemplateBuilder);
    }

    // -------------------------------------------------------------------------
    // Success scenarios
    // -------------------------------------------------------------------------

    @Test
    void getDistance_shouldReturnDistance_whenApiReturnsSuccess() {
        // Requirements 2.3, 2.4: extract distance.value and convert to km
        String responseJson = """
            {
                "status": "OK",
                "rows": [
                    {
                        "elements": [
                            {
                                "status": "OK",
                                "distance": {
                                    "value": 45500,
                                    "text": "45.5 km"
                                },
                                "duration": {
                                    "value": 2700,
                                    "text": "45 mins"
                                }
                            }
                        ]
                    }
                ]
            }
            """;

        when(restTemplate.getForEntity(anyString(), eq(String.class)))
            .thenReturn(ResponseEntity.ok(responseJson));

        BigDecimal distance = googleMapsClient.getDistance(
            "123 Main St, Johannesburg",
            "456 Oak Ave, Pretoria"
        );

        assertThat(distance).isEqualByComparingTo(new BigDecimal("45.50"));
    }

    @Test
    void getDistance_shouldConstructCorrectUrl_withAllParameters() {
        // Requirements 2.1, 2.2: construct request with proper parameters
        String responseJson = """
            {
                "status": "OK",
                "rows": [{"elements": [{"status": "OK", "distance": {"value": 1000}}]}]
            }
            """;

        when(restTemplate.getForEntity(anyString(), eq(String.class)))
            .thenReturn(ResponseEntity.ok(responseJson));

        googleMapsClient.getDistance("Origin Address", "Destination Address");

        ArgumentCaptor<String> urlCaptor = ArgumentCaptor.forClass(String.class);
        verify(restTemplate).getForEntity(urlCaptor.capture(), eq(String.class));

        String url = urlCaptor.getValue();
        assertThat(url).contains("https://maps.googleapis.com/maps/api/distancematrix/json");
        assertThat(url).contains("origins=Origin%20Address");
        assertThat(url).contains("destinations=Destination%20Address");
        assertThat(url).contains("mode=driving");
        assertThat(url).contains("units=metric");
        assertThat(url).contains("key=test-api-key");
    }

    @Test
    void getDistance_shouldApplyConfiguredTimeout() {
        // Requirement 9.4: apply configured timeout
        String responseJson = """
            {
                "status": "OK",
                "rows": [{"elements": [{"status": "OK", "distance": {"value": 1000}}]}]
            }
            """;

        when(restTemplate.getForEntity(anyString(), eq(String.class)))
            .thenReturn(ResponseEntity.ok(responseJson));

        googleMapsClient.getDistance("Origin", "Destination");

        verify(restTemplateBuilder).setConnectTimeout(argThat(duration -> 
            duration.getSeconds() == 5));
        verify(restTemplateBuilder).setReadTimeout(argThat(duration -> 
            duration.getSeconds() == 5));
    }

    @Test
    void getDistance_shouldRecordDurationMetric() {
        // Requirement 12.5: record histogram metric for API duration
        String responseJson = """
            {
                "status": "OK",
                "rows": [{"elements": [{"status": "OK", "distance": {"value": 1000}}]}]
            }
            """;

        when(restTemplate.getForEntity(anyString(), eq(String.class)))
            .thenReturn(ResponseEntity.ok(responseJson));

        googleMapsClient.getDistance("Origin", "Destination");

        Timer timer = meterRegistry.find("google_maps_api_duration_seconds").timer();
        assertThat(timer).isNotNull();
        assertThat(timer.count()).isEqualTo(1);
    }

    @Test
    void getDistance_shouldRoundDistanceToTwoDecimalPlaces() {
        // Requirement 2.4: convert to km with scale 2
        String responseJson = """
            {
                "status": "OK",
                "rows": [{"elements": [{"status": "OK", "distance": {"value": 12345}}]}]
            }
            """;

        when(restTemplate.getForEntity(anyString(), eq(String.class)))
            .thenReturn(ResponseEntity.ok(responseJson));

        BigDecimal distance = googleMapsClient.getDistance("Origin", "Destination");

        assertThat(distance).isEqualByComparingTo(new BigDecimal("12.35"));
        assertThat(distance.scale()).isEqualTo(2);
    }

    @Test
    void getDistance_shouldHandleZeroDistance() {
        // Requirement 1.5: handle zero distance
        String responseJson = """
            {
                "status": "OK",
                "rows": [{"elements": [{"status": "OK", "distance": {"value": 0}}]}]
            }
            """;

        when(restTemplate.getForEntity(anyString(), eq(String.class)))
            .thenReturn(ResponseEntity.ok(responseJson));

        BigDecimal distance = googleMapsClient.getDistance("Same Address", "Same Address");

        assertThat(distance).isEqualByComparingTo(BigDecimal.ZERO);
    }

    // -------------------------------------------------------------------------
    // API status error scenarios
    // -------------------------------------------------------------------------

    @Test
    void getDistance_shouldThrowRateLimitException_whenOverQueryLimit() {
        // Requirement 2.7: throw RateLimitExceededException on OVER_QUERY_LIMIT
        String responseJson = """
            {
                "status": "OVER_QUERY_LIMIT",
                "error_message": "You have exceeded your daily request quota"
            }
            """;

        when(restTemplate.getForEntity(anyString(), eq(String.class)))
            .thenReturn(ResponseEntity.ok(responseJson));

        assertThatThrownBy(() -> googleMapsClient.getDistance("Origin", "Destination"))
            .isInstanceOf(RateLimitExceededException.class);
    }

    @Test
    void getDistance_shouldThrowRateLimitException_whenOverDailyLimit() {
        // Requirement 2.7: handle OVER_DAILY_LIMIT
        String responseJson = """
            {
                "status": "OVER_DAILY_LIMIT"
            }
            """;

        when(restTemplate.getForEntity(anyString(), eq(String.class)))
            .thenReturn(ResponseEntity.ok(responseJson));

        assertThatThrownBy(() -> googleMapsClient.getDistance("Origin", "Destination"))
            .isInstanceOf(RateLimitExceededException.class);
    }

    @Test
    void getDistance_shouldThrowAuthException_whenRequestDenied() {
        // Requirement 2.8: throw ApiAuthenticationException on REQUEST_DENIED
        String responseJson = """
            {
                "status": "REQUEST_DENIED",
                "error_message": "The provided API key is invalid"
            }
            """;

        when(restTemplate.getForEntity(anyString(), eq(String.class)))
            .thenReturn(ResponseEntity.ok(responseJson));

        assertThatThrownBy(() -> googleMapsClient.getDistance("Origin", "Destination"))
            .isInstanceOf(ApiAuthenticationException.class);
    }

    @Test
    void getDistance_shouldThrowInvalidRequestException_whenInvalidRequest() {
        // Requirement 2.9: throw InvalidRequestException on INVALID_REQUEST
        String responseJson = """
            {
                "status": "INVALID_REQUEST",
                "error_message": "Invalid request parameters"
            }
            """;

        when(restTemplate.getForEntity(anyString(), eq(String.class)))
            .thenReturn(ResponseEntity.ok(responseJson));

        assertThatThrownBy(() -> googleMapsClient.getDistance("Origin", "Destination"))
            .isInstanceOf(InvalidRequestException.class);
    }

    @Test
    void getDistance_shouldThrowConnectionException_whenUnknownError() {
        // Handle UNKNOWN_ERROR status
        String responseJson = """
            {
                "status": "UNKNOWN_ERROR"
            }
            """;

        when(restTemplate.getForEntity(anyString(), eq(String.class)))
            .thenReturn(ResponseEntity.ok(responseJson));

        assertThatThrownBy(() -> googleMapsClient.getDistance("Origin", "Destination"))
            .isInstanceOf(ApiConnectionException.class);
    }

    // -------------------------------------------------------------------------
    // Element status error scenarios
    // -------------------------------------------------------------------------

    @Test
    void getDistance_shouldThrowInvalidAddressException_whenNotFound() {
        // Requirement 2.5: throw InvalidAddressException on NOT_FOUND
        String responseJson = """
            {
                "status": "OK",
                "rows": [{"elements": [{"status": "NOT_FOUND"}]}]
            }
            """;

        when(restTemplate.getForEntity(anyString(), eq(String.class)))
            .thenReturn(ResponseEntity.ok(responseJson));

        assertThatThrownBy(() -> googleMapsClient.getDistance("Invalid Origin", "Destination"))
            .isInstanceOf(InvalidAddressException.class)
            .hasMessageContaining("Address not found");
    }

    @Test
    void getDistance_shouldThrowInvalidAddressException_whenZeroResults() {
        // Requirement 2.6: throw InvalidAddressException on ZERO_RESULTS
        String responseJson = """
            {
                "status": "OK",
                "rows": [{"elements": [{"status": "ZERO_RESULTS"}]}]
            }
            """;

        when(restTemplate.getForEntity(anyString(), eq(String.class)))
            .thenReturn(ResponseEntity.ok(responseJson));

        assertThatThrownBy(() -> googleMapsClient.getDistance("Origin", "Destination"))
            .isInstanceOf(InvalidAddressException.class)
            .hasMessageContaining("No route found");
    }

    @Test
    void getDistance_shouldThrowInvalidRequestException_whenMaxRouteLengthExceeded() {
        // Handle MAX_ROUTE_LENGTH_EXCEEDED element status
        String responseJson = """
            {
                "status": "OK",
                "rows": [{"elements": [{"status": "MAX_ROUTE_LENGTH_EXCEEDED"}]}]
            }
            """;

        when(restTemplate.getForEntity(anyString(), eq(String.class)))
            .thenReturn(ResponseEntity.ok(responseJson));

        assertThatThrownBy(() -> googleMapsClient.getDistance("Origin", "Destination"))
            .isInstanceOf(InvalidRequestException.class)
            .hasMessageContaining("Route exceeds maximum length");
    }

    // -------------------------------------------------------------------------
    // Network error scenarios
    // -------------------------------------------------------------------------

    @Test
    void getDistance_shouldThrowConnectionException_onTimeout() {
        // Requirement 2.10: throw ApiConnectionException on timeout
        when(restTemplate.getForEntity(anyString(), eq(String.class)))
            .thenThrow(new ResourceAccessException("Read timed out", 
                new SocketTimeoutException("Read timed out")));

        assertThatThrownBy(() -> googleMapsClient.getDistance("Origin", "Destination"))
            .isInstanceOf(ApiConnectionException.class)
            .hasCauseInstanceOf(ResourceAccessException.class);
    }

    @Test
    void getDistance_shouldThrowConnectionException_onHttpClientError() {
        // Requirement 2.10: handle HTTP client errors
        when(restTemplate.getForEntity(anyString(), eq(String.class)))
            .thenThrow(new HttpClientErrorException(HttpStatus.BAD_REQUEST, "Bad Request"));

        assertThatThrownBy(() -> googleMapsClient.getDistance("Origin", "Destination"))
            .isInstanceOf(ApiConnectionException.class);
    }

    @Test
    void getDistance_shouldThrowConnectionException_onHttpServerError() {
        // Requirement 2.10: handle HTTP server errors
        when(restTemplate.getForEntity(anyString(), eq(String.class)))
            .thenThrow(new HttpServerErrorException(HttpStatus.INTERNAL_SERVER_ERROR, "Server Error"));

        assertThatThrownBy(() -> googleMapsClient.getDistance("Origin", "Destination"))
            .isInstanceOf(ApiConnectionException.class);
    }

    // -------------------------------------------------------------------------
    // Response validation scenarios
    // -------------------------------------------------------------------------

    @Test
    void getDistance_shouldThrowResponseException_whenResponseIsNotJson() {
        // Requirement 10.1: throw ApiResponseException on malformed JSON
        when(restTemplate.getForEntity(anyString(), eq(String.class)))
            .thenReturn(ResponseEntity.ok("Not valid JSON"));

        assertThatThrownBy(() -> googleMapsClient.getDistance("Origin", "Destination"))
            .isInstanceOf(ApiResponseException.class)
            .hasMessageContaining("Malformed JSON");
    }

    @Test
    void getDistance_shouldThrowResponseException_whenMissingStatus() {
        // Requirement 10.2: throw ApiResponseException when status field missing
        String responseJson = """
            {
                "rows": [{"elements": [{"status": "OK", "distance": {"value": 1000}}]}]
            }
            """;

        when(restTemplate.getForEntity(anyString(), eq(String.class)))
            .thenReturn(ResponseEntity.ok(responseJson));

        assertThatThrownBy(() -> googleMapsClient.getDistance("Origin", "Destination"))
            .isInstanceOf(ApiResponseException.class)
            .hasMessageContaining("Missing status field");
    }

    @Test
    void getDistance_shouldThrowResponseException_whenMissingRows() {
        // Requirement 10.3: throw ApiResponseException when rows field missing
        String responseJson = """
            {
                "status": "OK"
            }
            """;

        when(restTemplate.getForEntity(anyString(), eq(String.class)))
            .thenReturn(ResponseEntity.ok(responseJson));

        assertThatThrownBy(() -> googleMapsClient.getDistance("Origin", "Destination"))
            .isInstanceOf(ApiResponseException.class)
            .hasMessageContaining("Missing rows field");
    }

    @Test
    void getDistance_shouldThrowResponseException_whenRowsEmpty() {
        // Requirement 10.4: throw ApiResponseException when rows array empty
        String responseJson = """
            {
                "status": "OK",
                "rows": []
            }
            """;

        when(restTemplate.getForEntity(anyString(), eq(String.class)))
            .thenReturn(ResponseEntity.ok(responseJson));

        assertThatThrownBy(() -> googleMapsClient.getDistance("Origin", "Destination"))
            .isInstanceOf(ApiResponseException.class)
            .hasMessageContaining("Empty rows array");
    }

    @Test
    void getDistance_shouldThrowResponseException_whenElementsEmpty() {
        // Requirement 10.5: throw ApiResponseException when elements array empty
        String responseJson = """
            {
                "status": "OK",
                "rows": [{"elements": []}]
            }
            """;

        when(restTemplate.getForEntity(anyString(), eq(String.class)))
            .thenReturn(ResponseEntity.ok(responseJson));

        assertThatThrownBy(() -> googleMapsClient.getDistance("Origin", "Destination"))
            .isInstanceOf(ApiResponseException.class)
            .hasMessageContaining("Empty elements array");
    }

    @Test
    void getDistance_shouldThrowResponseException_whenMissingDistanceValue() {
        // Handle missing distance.value field
        String responseJson = """
            {
                "status": "OK",
                "rows": [{"elements": [{"status": "OK", "distance": {}}]}]
            }
            """;

        when(restTemplate.getForEntity(anyString(), eq(String.class)))
            .thenReturn(ResponseEntity.ok(responseJson));

        assertThatThrownBy(() -> googleMapsClient.getDistance("Origin", "Destination"))
            .isInstanceOf(ApiResponseException.class)
            .hasMessageContaining("Missing distance.value");
    }

    @Test
    void getDistance_shouldThrowResponseException_whenDistanceIsNegative() {
        // Requirement 10.6: throw ApiResponseException when distance.value < 0
        String responseJson = """
            {
                "status": "OK",
                "rows": [{"elements": [{"status": "OK", "distance": {"value": -1000}}]}]
            }
            """;

        when(restTemplate.getForEntity(anyString(), eq(String.class)))
            .thenReturn(ResponseEntity.ok(responseJson));

        assertThatThrownBy(() -> googleMapsClient.getDistance("Origin", "Destination"))
            .isInstanceOf(ApiResponseException.class)
            .hasMessageContaining("Negative distance value");
    }

    @Test
    void getDistance_shouldLogWarning_whenDistanceExceedsReasonableLimit() {
        // Requirement 10.7: log warning when distance > 5,000 km
        String responseJson = """
            {
                "status": "OK",
                "rows": [{"elements": [{"status": "OK", "distance": {"value": 6000000}}]}]
            }
            """;

        when(restTemplate.getForEntity(anyString(), eq(String.class)))
            .thenReturn(ResponseEntity.ok(responseJson));

        BigDecimal distance = googleMapsClient.getDistance("Origin", "Destination");

        assertThat(distance).isEqualByComparingTo(new BigDecimal("6000.00"));
        // Warning should be logged: "Unusually large distance returned: 6000000m (6000.0km)"
    }
}
