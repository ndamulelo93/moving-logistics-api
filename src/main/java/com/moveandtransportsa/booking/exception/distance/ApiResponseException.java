package com.moveandtransportsa.booking.exception.distance;

public class ApiResponseException extends DistanceCalculationException {

    public ApiResponseException(String message) {
        super("Invalid API response: " + message);
    }
}
