package com.moveandtransportsa.booking.dto.response;

import java.time.Instant;

public record AssignmentResponse(
        String bookingReference,
        String driverName,
        String vehicleRegistration,
        Instant assignedAt
) {
}
