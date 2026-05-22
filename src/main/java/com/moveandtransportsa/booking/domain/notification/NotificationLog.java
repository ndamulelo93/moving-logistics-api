package com.moveandtransportsa.booking.domain.notification;

import com.moveandtransportsa.booking.domain.booking.Booking;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;

@Entity
@Table(name = "notification_logs")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NotificationLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "booking_id", nullable = false)
    private Booking booking;

    @Column(name = "event_type", nullable = false, length = 100)
    private String eventType;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(columnDefinition = "notification_channel")
    private NotificationChannel channel;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(nullable = false, columnDefinition = "notification_status")
    private NotificationStatus status = NotificationStatus.PENDING;

    @Builder.Default
    @Column(name = "attempt_count", nullable = false)
    private Short attemptCount = 0;

    @Column(name = "last_attempt_at")
    private Instant lastAttemptAt;

    @Column(name = "error_detail", columnDefinition = "TEXT")
    private String errorDetail;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /**
     * Records a failed delivery attempt. Increments attempt count, sets last attempt time,
     * stores the error detail, and marks the log as FAILED if max attempts (3) reached.
     */
    public void recordFailure(String errorMessage) {
        this.attemptCount = (short) (this.attemptCount + 1);
        this.lastAttemptAt = Instant.now();
        this.errorDetail = errorMessage;
        if (this.attemptCount >= 3) {
            this.status = NotificationStatus.FAILED;
        }
    }

    /**
     * Marks the notification as successfully sent via the given channel.
     */
    public void markSent(NotificationChannel channel) {
        this.channel = channel;
        this.status = NotificationStatus.SENT;
        this.lastAttemptAt = Instant.now();
    }
}
