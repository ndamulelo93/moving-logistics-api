package com.moveandtransportsa.booking.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Request body for POST /api/admin/pricing-rules.
 */
public record CreatePricingRuleRequest(
        @NotNull Long vehicleTypeId,
        @NotNull @DecimalMin("0.0") BigDecimal baseFee,
        @NotNull @DecimalMin("0.0") BigDecimal perKmFee,
        @NotNull @DecimalMin("0.0") BigDecimal helperFee,
        BigDecimal stairsSurcharge,
        BigDecimal sameDaySurcharge,
        BigDecimal afterHoursSurcharge,
        @NotNull LocalDate effectiveFrom
) {
}
