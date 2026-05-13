package com.moveandtransportsa.booking.dto.response;

import java.util.List;

public record ServiceOfferingResponse(
        Long id,
        String name,
        String description,
        List<VehicleTypeResponse> vehicleTypes
) {
}
