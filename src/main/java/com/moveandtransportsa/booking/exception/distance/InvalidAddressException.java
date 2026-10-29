package com.moveandtransportsa.booking.exception.distance;

public class InvalidAddressException extends DistanceCalculationException {

    public InvalidAddressException(String address) {
        super("Address not found: " + address);
    }
}
