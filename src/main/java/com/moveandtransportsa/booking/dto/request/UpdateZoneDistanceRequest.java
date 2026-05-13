package com.moveandtransportsa.booking.dto.request;

import java.math.BigDecimal;

/**
 * Request body for PUT /api/admin/zones/distances/{id}.
 * All fields are optional — only non-null values are applied.
 */
public record UpdateZoneDistanceRequest(
        String zoneFrom,
        String zoneTo,
        BigDecimal distanceKm,
        Boolean active
) {
}
