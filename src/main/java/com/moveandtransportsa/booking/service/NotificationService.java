package com.moveandtransportsa.booking.service;

import com.moveandtransportsa.booking.domain.booking.Booking;
import com.moveandtransportsa.booking.domain.notification.NotificationLog;
import com.moveandtransportsa.booking.domain.notification.NotificationStatus;
import com.moveandtransportsa.booking.notification.NotificationDispatcher;
import com.moveandtransportsa.booking.repository.BookingRepository;
import com.moveandtransportsa.booking.repository.NotificationLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationLogRepository notificationLogRepository;
    private final BookingRepository bookingRepository;
    private final NotificationDispatcher notificationDispatcher;

    /**
     * Asynchronously dispatches a notification for the given booking event.
     * Persists a NotificationLog record first, then dispatches.
     * Notification failures must never block the booking workflow.
     */
    @Async("notificationExecutor")
    @Transactional
    public void dispatch(Long bookingId, String eventType) {
        Optional<Booking> bookingOpt = bookingRepository.findById(bookingId);
        if (bookingOpt.isEmpty()) {
            log.warn("Notification dispatch skipped — booking not found: {}", bookingId);
            return;
        }

        Booking booking = bookingOpt.get();
        Instant now = Instant.now();

        NotificationLog logEntry = NotificationLog.builder()
                .booking(booking)
                .eventType(eventType)
                .status(NotificationStatus.PENDING)
                .attemptCount((short) 0)
                .createdAt(now)
                .updatedAt(now)
                .build();

        notificationLogRepository.save(logEntry);
        notificationDispatcher.dispatch(logEntry);
    }
}
