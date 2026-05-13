package com.moveandtransportsa.booking.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

/**
 * Request body for POST /api/admin/zones/distances.
 */
public record CreateZoneDistanceRequest(
        @NotBlank String zoneFrom,
        @NotBlank String zoneTo,
        @NotNull @DecimalMin("0.0") BigDecimal distanceKm
) {
}
