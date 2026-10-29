package com.moveandtransportsa.booking.exception.distance;

public class ApiConnectionException extends DistanceCalculationException {

    public ApiConnectionException(Throwable cause) {
        super("Failed to connect to Google Maps API", cause);
    }
}
