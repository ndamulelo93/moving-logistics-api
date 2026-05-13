package com.moveandtransportsa.booking.domain.zone;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Entity
@Table(name = "suburb_zones")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SuburbZone {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "suburb_name", nullable = false, length = 200)
    private String suburbName;

    @Column(name = "zone_name", nullable = false, length = 50)
    private String zoneName;

    @Builder.Default
    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
