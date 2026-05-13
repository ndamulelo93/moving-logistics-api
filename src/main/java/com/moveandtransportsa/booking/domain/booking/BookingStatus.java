package com.moveandtransportsa.booking.domain.booking;

import java.util.Map;
import java.util.Set;

public enum BookingStatus {
    PENDING,
    QUOTED,
    CONFIRMED,
    ASSIGNED,
    IN_PROGRESS,
    COMPLETED,
    CANCELLED;

    private static final Map<BookingStatus, Set<BookingStatus>> ALLOWED = Map.of(
            PENDING,     Set.of(QUOTED, CANCELLED),
            QUOTED,      Set.of(CONFIRMED, CANCELLED),
            CONFIRMED,   Set.of(ASSIGNED, CANCELLED),
            ASSIGNED,    Set.of(IN_PROGRESS, CANCELLED),
            IN_PROGRESS, Set.of(COMPLETED, CANCELLED),
            COMPLETED,   Set.of(),
            CANCELLED,   Set.of()
    );

    public boolean canTransitionTo(BookingStatus target) {
        return ALLOWED.getOrDefault(this, Set.of()).contains(target);
    }
}
