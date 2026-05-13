package com.moveandtransportsa.booking.dto.response;

import java.math.BigDecimal;
import java.time.Instant;

public record PaymentResponse(
        Long id,
        String bookingReference,
        BigDecimal amount,
        String paymentMethod,
        String referenceNumber,
        BigDecimal totalPaid,
        Instant capturedAt
) {
}
