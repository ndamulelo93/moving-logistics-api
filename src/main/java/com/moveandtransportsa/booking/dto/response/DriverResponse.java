package com.moveandtransportsa.booking.dto.response;

/**
 * Public-safe representation of a Driver.
 */
public record DriverResponse(
        Long id,
        String fullName,
        String phone,
        String licenceNumber,
        boolean active
) {
}
