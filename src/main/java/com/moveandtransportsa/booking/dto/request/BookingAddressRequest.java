package com.moveandtransportsa.booking.dto.request;

import jakarta.validation.constraints.NotBlank;

public record BookingAddressRequest(
        @NotBlank String addressLine,
        String suburb,
        String city
) {
}
