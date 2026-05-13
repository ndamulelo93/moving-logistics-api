package com.moveandtransportsa.booking.dto.response;

import java.math.BigDecimal;
import java.time.Instant;

public record StatusHistoryResponse(
        Long id,
        String entryType,
        String previousStatus,
        String newStatus,
        BigDecimal oldPrice,
        BigDecimal newPrice,
        String actorType,
        Long actorAdminId,
        Instant createdAt
) {
}
