package com.moveandtransportsa.booking.exception;

public class InvalidStatusTransitionException extends RuntimeException {

    public InvalidStatusTransitionException(String message) {
        super(message);
    }

    public InvalidStatusTransitionException(Object from, Object to) {
        super("Invalid status transition from " + from + " to " + to);
    }
}
