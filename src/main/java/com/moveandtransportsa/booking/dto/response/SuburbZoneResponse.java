package com.moveandtransportsa.booking.dto.response;

/**
 * Response DTO for a SuburbZone.
 */
public record SuburbZoneResponse(
        Long id,
        String suburbName,
        String zoneName,
        boolean active
) {
}
