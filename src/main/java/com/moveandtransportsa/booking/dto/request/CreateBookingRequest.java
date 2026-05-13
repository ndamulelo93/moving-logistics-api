package com.moveandtransportsa.booking.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

public record CreateBookingRequest(
        @NotBlank String customerName,
        @NotBlank @Pattern(regexp = "^(\\+27|0)[0-9]{9}$") String customerPhone,
        @NotBlank @Email String customerEmail,
        @NotNull Long vehicleTypeId,
        @NotNull LocalDate serviceDate,
        LocalTime serviceTime,
        @Min(0) int helpers,
        boolean stairs,
        BigDecimal estimatedPrice,
        @NotEmpty @Valid List<BookingAddressRequest> pickupAddresses,
        @NotEmpty @Valid List<BookingAddressRequest> dropoffAddresses
) {
}
