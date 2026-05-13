package com.moveandtransportsa.booking.dto.request;

/**
 * Request body for PUT /api/admin/vehicles/{id}.
 * All fields are optional — only non-null values are applied.
 */
public record UpdateVehicleRequest(
        String registrationNumber,
        String make,
        String model,
        Short year,
        Long vehicleTypeId,
        Boolean active
) {
}
