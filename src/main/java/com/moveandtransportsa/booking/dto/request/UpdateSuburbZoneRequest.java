package com.moveandtransportsa.booking.dto.request;

/**
 * Request body for PUT /api/admin/zones/suburbs/{id}.
 * All fields are optional — only non-null values are applied.
 */
public record UpdateSuburbZoneRequest(
        String suburbName,
        String zoneName,
        Boolean active
) {
}
