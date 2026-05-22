package com.moveandtransportsa.booking.domain.booking;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "status_history")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StatusHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "booking_id", nullable = false)
    private Booking booking;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "entry_type", nullable = false, columnDefinition = "history_entry_type")
    private HistoryEntryType entryType = HistoryEntryType.STATUS_CHANGE;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "previous_status", columnDefinition = "booking_status")
    private BookingStatus previousStatus;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "new_status", columnDefinition = "booking_status")
    private BookingStatus newStatus;

    @Column(name = "old_price", precision = 10, scale = 2)
    private BigDecimal oldPrice;

    @Column(name = "new_price", precision = 10, scale = 2)
    private BigDecimal newPrice;

    @Column(name = "actor_type", nullable = false, length = 50)
    private String actorType;

    @Column(name = "actor_admin_id")
    private Long actorAdminId;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
