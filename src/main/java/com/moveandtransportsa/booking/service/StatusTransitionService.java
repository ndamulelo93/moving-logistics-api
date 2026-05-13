package com.moveandtransportsa.booking.service;

import com.moveandtransportsa.booking.domain.booking.Booking;
import com.moveandtransportsa.booking.domain.booking.BookingStatus;
import com.moveandtransportsa.booking.exception.BookingNotFoundException;
import com.moveandtransportsa.booking.exception.InvalidStatusTransitionException;
import com.moveandtransportsa.booking.repository.BookingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class StatusTransitionService {

    private static final Set<BookingStatus> NOTIFICATION_STATUSES =
            Set.of(BookingStatus.QUOTED, BookingStatus.CONFIRMED, BookingStatus.ASSIGNED, BookingStatus.COMPLETED);

    private final BookingRepository bookingRepository;
    private final StatusHistoryService statusHistoryService;
    private final NotificationService notificationService;

    /**
     * Transitions a booking to the target status.
     * Validates the transition using the BookingStatus state machine.
     * Records history and dispatches notifications for key status changes.
     */
    @Transactional
    public void transition(Long bookingId, BookingStatus targetStatus, Long adminUserId) {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new BookingNotFoundException(bookingId));

        BookingStatus previousStatus = booking.getStatus();

        if (!previousStatus.canTransitionTo(targetStatus)) {
            throw new InvalidStatusTransitionException(previousStatus, targetStatus);
        }

        booking.setStatus(targetStatus);
        booking.setUpdatedAt(Instant.now());
        bookingRepository.save(booking);

        statusHistoryService.appendStatusChange(bookingId, previousStatus, targetStatus, "ADMIN", adminUserId);

        if (NOTIFICATION_STATUSES.contains(targetStatus)) {
            notificationService.dispatch(bookingId, targetStatus.name());
        }
    }
}
