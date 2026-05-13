package com.moveandtransportsa.booking.exception;

public class BookingNotFoundException extends RuntimeException {

    public BookingNotFoundException(String message) {
        super(message);
    }

    public BookingNotFoundException(Long id) {
        super("Booking not found with id: " + id);
    }

    public BookingNotFoundException(String field, String value) {
        super("Booking not found with " + field + ": " + value);
    }
}
