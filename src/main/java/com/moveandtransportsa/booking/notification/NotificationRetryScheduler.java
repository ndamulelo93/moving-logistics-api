package com.moveandtransportsa.booking.notification;

import com.moveandtransportsa.booking.domain.notification.NotificationLog;
import com.moveandtransportsa.booking.repository.NotificationLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;

/**
 * Scheduled job that retries failed or pending notifications with exponential backoff.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationRetryScheduler {

    private static final int MAX_ATTEMPTS = 3;

    private final NotificationLogRepository notificationLogRepository;
    private final NotificationDispatcher notificationDispatcher;

    /**
     * Polls for retryable notifications every 30 seconds.
     * Applies exponential backoff: 2^attemptCount * 30s since last attempt.
     */
    @Scheduled(fixedDelay = 30_000)
    public void retryFailed() {
        List<NotificationLog> retryable = notificationLogRepository.findRetryable(MAX_ATTEMPTS);

        for (NotificationLog notificationLog : retryable) {
            long backoffMs = (long) Math.pow(2, notificationLog.getAttemptCount()) * 30_000L;
            Instant lastAttempt = notificationLog.getLastAttemptAt();

            boolean backoffElapsed = lastAttempt == null ||
                    Instant.now().isAfter(lastAttempt.plusMillis(backoffMs));

            if (backoffElapsed) {
                log.debug("Retrying notification log id {} (attempt {})",
                        notificationLog.getId(), notificationLog.getAttemptCount() + 1);
                notificationDispatcher.dispatch(notificationLog);
            }
        }
    }
}
