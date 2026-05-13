package com.moveandtransportsa.booking.notification;

import com.moveandtransportsa.booking.domain.notification.NotificationLog;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Stub WhatsApp notification provider for MVP.
 * In production this would call the WhatsApp Business API.
 */
@Slf4j
@Component
public class WhatsAppNotificationProvider {

    /**
     * Sends a WhatsApp notification for the given log entry.
     * MVP stub — logs the attempt and returns true.
     *
     * @return true if the notification was sent successfully
     */
    public boolean send(NotificationLog notificationLog) {
        log.info("WhatsApp notification sent for booking {}", notificationLog.getBooking().getId());
        return true;
    }
}
