package com.moveandtransportsa.booking.dto.request;

import com.moveandtransportsa.booking.domain.booking.BookingStatus;
import jakarta.validation.constraints.NotNull;

public record UpdateBookingStatusRequest(
        @NotNull BookingStatus status
) {
}
