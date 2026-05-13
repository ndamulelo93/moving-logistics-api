package com.moveandtransportsa.booking.exception;

public class InactiveEntityException extends RuntimeException {

    public InactiveEntityException(String message) {
        super(message);
    }

    public InactiveEntityException(String entityType, Long id) {
        super(entityType + " with id " + id + " is inactive");
    }
}
