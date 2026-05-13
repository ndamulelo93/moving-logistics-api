package com.moveandtransportsa.booking.dto.response;

import java.math.BigDecimal;

public record QuoteBreakdown(
        BigDecimal baseFee,
        BigDecimal distanceFee,
        BigDecimal helpersFee,
        BigDecimal stairsSurcharge,
        BigDecimal sameDaySurcharge,
        BigDecimal afterHoursSurcharge
) {
}
