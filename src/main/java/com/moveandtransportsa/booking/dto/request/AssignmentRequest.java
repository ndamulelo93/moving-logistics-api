package com.moveandtransportsa.booking.dto.request;

import jakarta.validation.constraints.NotNull;

public record AssignmentRequest(
        @NotNull Long driverId,
        @NotNull Long vehicleId
) {
}
