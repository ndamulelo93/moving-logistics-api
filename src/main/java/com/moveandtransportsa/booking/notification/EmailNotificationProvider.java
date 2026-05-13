package com.moveandtransportsa.booking.notification;

import com.moveandtransportsa.booking.domain.notification.NotificationLog;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

/**
 * Email notification provider using Spring's JavaMailSender.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EmailNotificationProvider {

    private final JavaMailSender mailSender;

    /**
     * Sends an email notification for the given log entry.
     *
     * @return true if the email was sent successfully, false on failure
     */
    public boolean send(NotificationLog notificationLog) {
        try {
            String customerEmail = notificationLog.getBooking().getCustomerEmail();
            String bookingRef = notificationLog.getBooking().getBookingReference();
            String eventType = notificationLog.getEventType();

            SimpleMailMessage message = new SimpleMailMessage();
            message.setTo(customerEmail);
            message.setSubject("Booking Update — " + bookingRef);
            message.setText("Your booking " + bookingRef + " has been updated. Status: " + eventType);

            mailSender.send(message);
            log.info("Email notification sent for booking {} (event: {})", bookingRef, eventType);
            return true;
        } catch (MailException e) {
            log.warn("Failed to send email notification for booking {}: {}",
                    notificationLog.getBooking().getId(), e.getMessage());
            return false;
        }
    }
}
