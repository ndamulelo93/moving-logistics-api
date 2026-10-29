package com.moveandtransportsa.booking.exception.distance;

public class InvalidRequestException extends DistanceCalculationException {

    public InvalidRequestException(String message) {
        super("Invalid API request: " + message);
    }
}
