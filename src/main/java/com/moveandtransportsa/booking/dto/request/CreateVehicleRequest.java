package com.moveandtransportsa.booking.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * Request body for POST /api/admin/vehicles.
 */
public record CreateVehicleRequest(
        @NotBlank String registrationNumber,
        @NotBlank String make,
        @NotBlank String model,
        @NotNull Short year,
        @NotNull Long vehicleTypeId,
        boolean active
) {
}
