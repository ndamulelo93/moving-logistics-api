package com.moveandtransportsa.booking.domain.booking;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;

@Entity
@Table(name = "booking_addresses")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BookingAddress {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "booking_id", nullable = false)
    private Booking booking;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "address_type", nullable = false, columnDefinition = "address_type")
    private AddressType addressType;

    @Column(name = "address_line", nullable = false, length = 500)
    private String addressLine;

    @Column(length = 200)
    private String suburb;

    @Column(length = 200)
    private String city;

    @Builder.Default
    @Column(name = "sort_order", nullable = false)
    private Short sortOrder = 0;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
