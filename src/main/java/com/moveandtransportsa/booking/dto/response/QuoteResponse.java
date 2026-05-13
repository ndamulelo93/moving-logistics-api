package com.moveandtransportsa.booking.dto.response;

import java.math.BigDecimal;

public record QuoteResponse(
        BigDecimal estimatedTotal,
        BigDecimal distanceKm,
        QuoteBreakdown breakdown
) {
}
