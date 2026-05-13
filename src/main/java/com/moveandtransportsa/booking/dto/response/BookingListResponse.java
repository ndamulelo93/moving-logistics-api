package com.moveandtransportsa.booking.dto.response;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

public record BookingListResponse(
        Long id,
        String bookingReference,
        String status,
        String customerName,
        String vehicleType,
        LocalDate serviceDate,
        BigDecimal estimatedPrice,
        BigDecimal finalPrice,
        Instant createdAt
) {
}
