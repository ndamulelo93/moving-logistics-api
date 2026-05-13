package com.moveandtransportsa.booking.exception;

public class SuburbNotFoundException extends RuntimeException {

    public SuburbNotFoundException(String suburb) {
        super("Suburb not found: " + suburb);
    }
}
