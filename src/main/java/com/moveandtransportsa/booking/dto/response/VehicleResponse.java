package com.moveandtransportsa.booking.dto.response;

/**
 * Public-safe representation of a Vehicle.
 */
public record VehicleResponse(
        Long id,
        String registrationNumber,
        String make,
        String model,
        Short year,
        VehicleTypeResponse vehicleType,
        boolean active
) {
}
