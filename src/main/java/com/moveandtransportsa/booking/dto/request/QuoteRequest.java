package com.moveandtransportsa.booking.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.time.LocalTime;

public record QuoteRequest(
        @NotNull Long vehicleTypeId,
        @NotBlank String pickupSuburb,
        @NotBlank String dropoffSuburb,
        @NotNull @Min(0) Integer helpers,
        @NotNull LocalDate serviceDate,
        LocalTime serviceTime,
        boolean stairs
) {
}
