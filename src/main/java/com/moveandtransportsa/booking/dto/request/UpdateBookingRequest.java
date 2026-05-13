package com.moveandtransportsa.booking.dto.request;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record UpdateBookingRequest(
        String customerName,
        String customerPhone,
        String customerEmail,
        LocalDate serviceDate,
        Long vehicleTypeId,
        List<BookingAddressRequest> pickupAddresses,
        List<BookingAddressRequest> dropoffAddresses,
        Integer helpers,
        String adminNotes,
        BigDecimal finalPrice
) {
}
