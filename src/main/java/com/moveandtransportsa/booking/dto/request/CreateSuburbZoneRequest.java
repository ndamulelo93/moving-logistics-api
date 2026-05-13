package com.moveandtransportsa.booking.dto.request;

import jakarta.validation.constraints.NotBlank;

/**
 * Request body for POST /api/admin/zones/suburbs.
 */
public record CreateSuburbZoneRequest(
        @NotBlank String suburbName,
        @NotBlank String zoneName
) {
}
