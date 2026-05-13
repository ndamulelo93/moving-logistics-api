package com.moveandtransportsa.booking.dto.response;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

public record AdminBookingResponse(
        Long id,
        String bookingReference,
        String status,
        String customerName,
        String customerPhone,
        String customerEmail,
        String vehicleType,
        LocalDate serviceDate,
        LocalTime serviceTime,
        int helpers,
        boolean stairs,
        BigDecimal estimatedPrice,
        BigDecimal finalPrice,
        BigDecimal totalPaid,
        String adminNotes,
        List<BookingAddressResponse> pickupAddresses,
        List<BookingAddressResponse> dropoffAddresses,
        Instant createdAt,
        Instant updatedAt
) {
}
