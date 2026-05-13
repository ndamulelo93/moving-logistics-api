package com.moveandtransportsa.booking.dto.response;

import java.math.BigDecimal;

/**
 * Public-safe representation of a VehicleType.
 */
public record VehicleTypeResponse(
        Long id,
        String name,
        String description,
        String loadCapacity,
        BigDecimal baseFee,
        boolean active
) {
}
