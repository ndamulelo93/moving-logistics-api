package com.moveandtransportsa.booking.dto.response;

import java.math.BigDecimal;
import java.time.Instant;

public record CreateBookingResponse(
        String bookingReference,
        String status,
        BigDecimal estimatedPrice,
        Instant createdAt
) {
}
