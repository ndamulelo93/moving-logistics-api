package com.moveandtransportsa.booking.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.time.LocalTime;

public record QuoteRequestWithAddresses(
        @NotBlank String pickupAddressLine,
        @NotBlank String pickupSuburb,
        @NotBlank String pickupCity,
        @NotBlank String dropoffAddressLine,
        @NotBlank String dropoffSuburb,
        @NotBlank String dropoffCity,
        @NotNull Long vehicleTypeId,
        @NotNull @Min(0) Integer helpers,
        @NotNull LocalDate serviceDate,
        LocalTime serviceTime,
        boolean stairs
) {
}
