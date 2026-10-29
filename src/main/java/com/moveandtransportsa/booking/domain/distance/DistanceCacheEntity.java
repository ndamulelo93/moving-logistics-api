package com.moveandtransportsa.booking.domain.distance;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(
    name = "distance_cache",
    uniqueConstraints = @UniqueConstraint(
        name = "distance_cache_unique",
        columnNames = {"pickup_address", "dropoff_address"}
    )
)
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DistanceCacheEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "pickup_address", nullable = false, length = 500)
    private String pickupAddress;

    @Column(name = "dropoff_address", nullable = false, length = 500)
    private String dropoffAddress;

    @Column(name = "distance_km", nullable = false, precision = 10, scale = 2)
    private BigDecimal distanceKm;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "accessed_at", nullable = false)
    private Instant accessedAt;
}
