package com.moveandtransportsa.booking.exception;

public class PricingUnavailableException extends RuntimeException {

    public PricingUnavailableException(String message) {
        super(message);
    }

    public PricingUnavailableException(Long vehicleTypeId) {
        super("No active pricing rule found for vehicle type id: " + vehicleTypeId);
    }
}
