package com.moveandtransportsa.booking.domain.pricing;

import com.moveandtransportsa.booking.domain.vehicle.VehicleType;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "pricing_rules")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PricingRule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "vehicle_type_id", nullable = false)
    private VehicleType vehicleType;

    @Column(name = "base_fee", nullable = false, precision = 10, scale = 2)
    private BigDecimal baseFee;

    @Column(name = "per_km_fee", nullable = false, precision = 10, scale = 2)
    private BigDecimal perKmFee;

    @Column(name = "helper_fee", nullable = false, precision = 10, scale = 2)
    private BigDecimal helperFee;

    @Column(name = "stairs_surcharge", nullable = false, precision = 10, scale = 2)
    private BigDecimal stairsSurcharge;

    @Column(name = "same_day_surcharge", nullable = false, precision = 10, scale = 2)
    private BigDecimal sameDaySurcharge;

    @Column(name = "after_hours_surcharge", nullable = false, precision = 10, scale = 2)
    private BigDecimal afterHoursSurcharge;

    @Column(name = "effective_from", nullable = false)
    private LocalDate effectiveFrom;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
