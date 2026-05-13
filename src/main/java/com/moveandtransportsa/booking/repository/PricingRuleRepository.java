package com.moveandtransportsa.booking.repository;

import com.moveandtransportsa.booking.domain.pricing.PricingRule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Optional;

public interface PricingRuleRepository extends JpaRepository<PricingRule, Long> {

    @Query("SELECT p FROM PricingRule p WHERE p.vehicleType.id = :vehicleTypeId AND p.effectiveFrom <= :date ORDER BY p.effectiveFrom DESC LIMIT 1")
    Optional<PricingRule> findActiveRule(@Param("vehicleTypeId") Long vehicleTypeId, @Param("date") LocalDate date);
}
