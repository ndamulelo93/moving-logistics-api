package com.moveandtransportsa.booking.notification;

import com.moveandtransportsa.booking.domain.notification.NotificationChannel;
import com.moveandtransportsa.booking.domain.notification.NotificationLog;
import com.moveandtransportsa.booking.repository.NotificationLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * Orchestrates notification delivery across available channels.
 * Tries WhatsApp first; falls back to email if WhatsApp fails.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationDispatcher {

    private final WhatsAppNotificationProvider whatsAppProvider;
    private final EmailNotificationProvider emailProvider;
    private final NotificationLogRepository notificationLogRepository;

    /**
     * Attempts to deliver the notification via WhatsApp, then email as fallback.
     * Updates the log entry with the result and persists it.
     */
    public void dispatch(NotificationLog notificationLog) {
        notificationLog.setAttemptCount((short) (notificationLog.getAttemptCount() + 1));
        notificationLog.setLastAttemptAt(Instant.now());

        try {
            boolean sent = whatsAppProvider.send(notificationLog);
            if (!sent) {
                sent = emailProvider.send(notificationLog);
                if (sent) {
                    notificationLog.markSent(NotificationChannel.EMAIL);
                } else {
                    notificationLog.recordFailure("All channels failed");
                }
            } else {
                notificationLog.markSent(NotificationChannel.WHATSAPP);
            }
        } catch (Exception e) {
            log.error("Notification dispatch failed for log id {}: {}", notificationLog.getId(), e.getMessage());
            notificationLog.recordFailure(e.getMessage());
        }

        notificationLogRepository.save(notificationLog);
    }
}
