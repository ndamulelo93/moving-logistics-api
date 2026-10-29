package com.moveandtransportsa.booking.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.moveandtransportsa.booking.config.GoogleMapsConfig;
import com.moveandtransportsa.booking.exception.distance.*;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;

/**
 * Service for communicating with Google Maps Distance Matrix API.
 * Handles HTTP requests, response parsing, and error handling.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GoogleMapsClient {

    private static final long MAX_REASONABLE_DISTANCE_METERS = 5_000_000L; // 5000 km

    private final GoogleMapsConfig config;
    private final MeterRegistry meterRegistry;
    private final ObjectMapper objectMapper;
    private final RestTemplateBuilder restTemplateBuilder;

    /**
     * Calls Google Maps Distance Matrix API to get road distance.
     * Constructs request with proper parameters, applies timeout, and parses response.
     *
     * @param origin Full origin address
     * @param destination Full destination address
     * @return Distance in kilometers with scale 2
     * @throws InvalidAddressException if address not found
     * @throws RateLimitExceededException if quota exceeded
     * @throws ApiAuthenticationException if API key invalid
     * @throws ApiConnectionException if network failure
     * @throws ApiResponseException if response malformed
     * @throws InvalidRequestException if request parameters invalid
     */
    public BigDecimal getDistance(String origin, String destination) {
        Timer.Sample timerSample = Timer.start(meterRegistry);

        try {
            // Construct request URL with parameters
            String url = buildRequestUrl(origin, destination);

            log.debug("Calling Google Maps API: origin={}, destination={}", origin, destination);

            // Create RestTemplate with configured timeout
            RestTemplate restTemplate = restTemplateBuilder
                .setConnectTimeout(Duration.ofSeconds(config.getTimeoutSeconds()))
                .setReadTimeout(Duration.ofSeconds(config.getTimeoutSeconds()))
                .build();

            // Make API call
            ResponseEntity<String> response;
            try {
                response = restTemplate.getForEntity(url, String.class);
            } catch (ResourceAccessException e) {
                log.error("Google Maps API connection failed: {}", e.getMessage());
                throw new ApiConnectionException(e);
            } catch (HttpClientErrorException | HttpServerErrorException e) {
                log.error("Google Maps API HTTP error: status={}, body={}", 
                    e.getStatusCode(), e.getResponseBodyAsString());
                throw new ApiConnectionException(e);
            }

            // Parse and validate response
            BigDecimal distance = parseResponse(response.getBody(), origin, destination);

            log.info("Google Maps API call succeeded: origin={}, destination={}, distance={}km",
                origin, destination, distance);

            return distance;

        } catch (ApiConnectionException | RateLimitExceededException | 
                 ApiAuthenticationException | InvalidAddressException | 
                 InvalidRequestException | ApiResponseException e) {
            // Re-throw expected exceptions
            throw e;
        } catch (Exception e) {
            log.error("Unexpected error calling Google Maps API: {}", e.getMessage(), e);
            throw new ApiConnectionException(e);
        } finally {
            // Record API call duration
            timerSample.stop(meterRegistry.timer("google_maps_api_duration_seconds"));
        }
    }

    /**
     * Builds the request URL with all required parameters.
     * Uses configurable base URL from GoogleMapsConfig to support testing.
     */
    private String buildRequestUrl(String origin, String destination) {
        return UriComponentsBuilder.fromHttpUrl(config.getBaseUrl())
            .queryParam("origins", origin)
            .queryParam("destinations", destination)
            .queryParam("mode", "driving")
            .queryParam("units", "metric")
            .queryParam("key", config.getApiKey())
            .build()
            .toUriString();
    }

    /**
     * Parses and validates the API response.
     * Handles various error conditions and response statuses.
     */
    private BigDecimal parseResponse(String responseBody, String origin, String destination) {
        try {
            JsonNode root = objectMapper.readTree(responseBody);

            // Validate response structure
            validateResponseStructure(root);

            // Check top-level status
            String status = root.get("status").asText();
            handleTopLevelStatus(status, origin, destination);

            // Extract rows and elements
            JsonNode rows = root.get("rows");
            if (rows.isEmpty()) {
                throw new ApiResponseException("Empty rows array in response");
            }

            JsonNode elements = rows.get(0).get("elements");
            if (elements.isEmpty()) {
                throw new ApiResponseException("Empty elements array in response");
            }

            JsonNode element = elements.get(0);
            String elementStatus = element.get("status").asText();

            // Handle element status
            handleElementStatus(elementStatus, origin, destination);

            // Extract distance
            JsonNode distance = element.get("distance");
            if (distance == null || !distance.has("value")) {
                throw new ApiResponseException("Missing distance.value in response");
            }

            long distanceMeters = distance.get("value").asLong();

            // Validate distance value
            if (distanceMeters < 0) {
                throw new ApiResponseException("Negative distance value: " + distanceMeters);
            }

            if (distanceMeters > MAX_REASONABLE_DISTANCE_METERS) {
                log.warn("Unusually large distance returned: {}m ({}km) for origin={}, destination={}",
                    distanceMeters, distanceMeters / 1000.0, origin, destination);
            }

            // Convert meters to kilometers with scale 2
            return BigDecimal.valueOf(distanceMeters)
                .divide(BigDecimal.valueOf(1000), 2, RoundingMode.HALF_UP);

        } catch (ApiConnectionException | RateLimitExceededException | 
                 ApiAuthenticationException | InvalidAddressException | 
                 InvalidRequestException | ApiResponseException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to parse Google Maps API response: {}", e.getMessage());
            throw new ApiResponseException("Malformed JSON response: " + e.getMessage());
        }
    }

    /**
     * Validates that the response has required fields.
     */
    private void validateResponseStructure(JsonNode root) {
        if (root == null) {
            throw new ApiResponseException("Null response body");
        }
        if (!root.has("status")) {
            throw new ApiResponseException("Missing status field");
        }
        if (!root.has("rows")) {
            throw new ApiResponseException("Missing rows field");
        }
    }

    /**
     * Handles top-level API status codes.
     */
    private void handleTopLevelStatus(String status, String origin, String destination) {
        switch (status) {
            case "OK":
                // Success case, continue processing
                break;
            case "OVER_QUERY_LIMIT":
            case "OVER_DAILY_LIMIT":
                log.warn("Google Maps API rate limit exceeded");
                throw new RateLimitExceededException();
            case "REQUEST_DENIED":
                log.error("Google Maps API request denied - check API key and restrictions");
                throw new ApiAuthenticationException();
            case "INVALID_REQUEST":
                log.error("Invalid request to Google Maps API: origin={}, destination={}", origin, destination);
                throw new InvalidRequestException("Invalid request parameters");
            case "UNKNOWN_ERROR":
                log.error("Google Maps API returned UNKNOWN_ERROR");
                throw new ApiConnectionException(new RuntimeException("API returned UNKNOWN_ERROR"));
            default:
                log.error("Unexpected Google Maps API status: {}", status);
                throw new ApiResponseException("Unexpected status: " + status);
        }
    }

    /**
     * Handles element-level status codes.
     */
    private void handleElementStatus(String elementStatus, String origin, String destination) {
        switch (elementStatus) {
            case "OK":
                // Success case, continue processing
                break;
            case "NOT_FOUND":
                log.warn("Address not found in Google Maps: origin={}, destination={}", origin, destination);
                throw new InvalidAddressException("Address not found: " + origin + " or " + destination);
            case "ZERO_RESULTS":
                log.warn("No route found between addresses: origin={}, destination={}", origin, destination);
                throw new InvalidAddressException("No route found between: " + origin + " and " + destination);
            case "MAX_ROUTE_LENGTH_EXCEEDED":
                log.warn("Route too long for Google Maps API: origin={}, destination={}", origin, destination);
                throw new InvalidRequestException("Route exceeds maximum length");
            default:
                log.error("Unexpected element status: {}", elementStatus);
                throw new ApiResponseException("Unexpected element status: " + elementStatus);
        }
    }
}
