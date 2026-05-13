package com.moveandtransportsa.booking.dto.request;

import java.math.BigDecimal;

/**
 * Request body for PUT /api/admin/vehicle-types/{id}.
 * All fields are optional — only non-null values are applied.
 */
public record UpdateVehicleTypeRequest(
        String name,
        String description,
        String loadCapacity,
        BigDecimal baseFee,
        Boolean active
) {
}
