package com.moveandtransportsa.booking.dto.response;

import java.math.BigDecimal;

/**
 * Response DTO for a ZoneDistance.
 */
public record ZoneDistanceResponse(
        Long id,
        String zoneFrom,
        String zoneTo,
        BigDecimal distanceKm,
        boolean active
) {
}
