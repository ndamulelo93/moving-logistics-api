package com.moveandtransportsa.booking.exception;

/**
 * Exception thrown when Google Maps API key is not configured but required.
 * This is a runtime exception that will be thrown during application startup
 * if the API key is missing when google.maps.enabled=true.
 */
public class ApiKeyNotConfiguredException extends RuntimeException {
    
    public ApiKeyNotConfiguredException(String message) {
        super(message);
    }
}
