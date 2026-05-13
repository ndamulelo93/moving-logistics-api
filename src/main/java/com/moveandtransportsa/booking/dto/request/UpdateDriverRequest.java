package com.moveandtransportsa.booking.dto.request;

/**
 * Request body for PUT /api/admin/drivers/{id}.
 * All fields are optional — only non-null values are applied.
 */
public record UpdateDriverRequest(
        String fullName,
        String phone,
        String licenceNumber,
        Boolean active
) {
}
