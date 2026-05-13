package com.moveandtransportsa.booking.dto.response;

public record BookingAddressResponse(
        Long id,
        String addressType,
        String addressLine,
        String suburb,
        String city
) {
}
