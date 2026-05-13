package com.moveandtransportsa.booking.dto.response;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

/**
 * Public-safe booking response — excludes id, adminNotes, and driver details.
 */
public record PublicBookingResponse(
        String bookingReference,
        String status,
        String vehicleType,
        LocalDate serviceDate,
        LocalTime serviceTime,
        List<BookingAddressResponse> pickupAddresses,
        List<BookingAddressResponse> dropoffAddresses,
        BigDecimal estimatedPrice,
        BigDecimal finalPrice
) {
}
