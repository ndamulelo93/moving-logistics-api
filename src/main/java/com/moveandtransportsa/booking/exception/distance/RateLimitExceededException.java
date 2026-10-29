package com.moveandtransportsa.booking.exception.distance;

public class RateLimitExceededException extends DistanceCalculationException {

    public RateLimitExceededException() {
        super("Google Maps API rate limit exceeded");
    }
}
