package com.moveandtransportsa.booking.dto.response;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Response DTO for a PricingRule.
 */
public record PricingRuleResponse(
        Long id,
        Long vehicleTypeId,
        String vehicleTypeName,
        BigDecimal baseFee,
        BigDecimal perKmFee,
        BigDecimal helperFee,
        BigDecimal stairsSurcharge,
        BigDecimal sameDaySurcharge,
        BigDecimal afterHoursSurcharge,
        LocalDate effectiveFrom
) {
}
