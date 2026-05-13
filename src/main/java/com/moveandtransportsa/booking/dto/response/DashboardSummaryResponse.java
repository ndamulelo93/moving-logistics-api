package com.moveandtransportsa.booking.dto.response;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;

public record DashboardSummaryResponse(
        LocalDate date,
        Map<String, Long> bookingsByStatus,
        BigDecimal confirmedRevenue,
        long unassignedConfirmedCount
) {
}
