package com.moveandtransportsa.booking.exception.distance;

public class ApiAuthenticationException extends DistanceCalculationException {

    public ApiAuthenticationException() {
        super("Google Maps API authentication failed - check API key");
    }
}
