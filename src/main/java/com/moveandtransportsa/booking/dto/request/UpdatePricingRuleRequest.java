package com.moveandtransportsa.booking.dto.request;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Request body for PUT /api/admin/pricing-rules/{id}.
 * All fields are optional — only non-null values are applied.
 */
public record UpdatePricingRuleRequest(
        Long vehicleTypeId,
        BigDecimal baseFee,
        BigDecimal perKmFee,
        BigDecimal helperFee,
        BigDecimal stairsSurcharge,
        BigDecimal sameDaySurcharge,
        BigDecimal afterHoursSurcharge,
        LocalDate effectiveFrom
) {
}
