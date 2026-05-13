package com.moveandtransportsa.booking.service;

import com.moveandtransportsa.booking.domain.pricing.PricingRule;
import com.moveandtransportsa.booking.domain.vehicle.VehicleType;
import com.moveandtransportsa.booking.dto.request.CreatePricingRuleRequest;
import com.moveandtransportsa.booking.dto.request.UpdatePricingRuleRequest;
import com.moveandtransportsa.booking.dto.response.PricingRuleResponse;
import com.moveandtransportsa.booking.exception.BookingNotFoundException;
import com.moveandtransportsa.booking.exception.PricingUnavailableException;
import com.moveandtransportsa.booking.repository.PricingRuleRepository;
import com.moveandtransportsa.booking.repository.VehicleTypeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;

@Service
@RequiredArgsConstructor
public class PricingService {

    private static final BigDecimal ZERO = BigDecimal.ZERO;

    private final PricingRuleRepository pricingRuleRepository;
    private final VehicleTypeRepository vehicleTypeRepository;

    /**
     * Creates a new pricing rule for the given vehicle type.
     * Throws {@link BookingNotFoundException} if the vehicle type does not exist.
     */
    @Transactional
    public PricingRuleResponse create(CreatePricingRuleRequest request) {
        VehicleType vehicleType = vehicleTypeRepository.findById(request.vehicleTypeId())
                .orElseThrow(() -> new BookingNotFoundException(
                        "VehicleType not found with id: " + request.vehicleTypeId()));

        PricingRule rule = PricingRule.builder()
                .vehicleType(vehicleType)
                .baseFee(request.baseFee())
                .perKmFee(request.perKmFee())
                .helperFee(request.helperFee())
                .stairsSurcharge(request.stairsSurcharge() != null ? request.stairsSurcharge() : ZERO)
                .sameDaySurcharge(request.sameDaySurcharge() != null ? request.sameDaySurcharge() : ZERO)
                .afterHoursSurcharge(request.afterHoursSurcharge() != null ? request.afterHoursSurcharge() : ZERO)
                .effectiveFrom(request.effectiveFrom())
                .createdAt(Instant.now())
                .build();

        PricingRule saved = pricingRuleRepository.save(rule);
        return toResponse(saved);
    }

    /**
     * Updates a pricing rule by creating a NEW rule with the updated values and
     * effectiveFrom=today, preserving the old rule for historical reference.
     * Throws {@link BookingNotFoundException} if the existing rule is not found.
     */
    @Transactional
    public PricingRuleResponse update(Long id, UpdatePricingRuleRequest request) {
        PricingRule existing = pricingRuleRepository.findById(id)
                .orElseThrow(() -> new BookingNotFoundException(
                        "PricingRule not found with id: " + id));

        // Resolve vehicle type — use updated one if provided, otherwise keep existing
        VehicleType vehicleType = existing.getVehicleType();
        if (request.vehicleTypeId() != null) {
            vehicleType = vehicleTypeRepository.findById(request.vehicleTypeId())
                    .orElseThrow(() -> new BookingNotFoundException(
                            "VehicleType not found with id: " + request.vehicleTypeId()));
        }

        // Create a new rule version; old rule is preserved for history
        PricingRule newRule = PricingRule.builder()
                .vehicleType(vehicleType)
                .baseFee(request.baseFee() != null ? request.baseFee() : existing.getBaseFee())
                .perKmFee(request.perKmFee() != null ? request.perKmFee() : existing.getPerKmFee())
                .helperFee(request.helperFee() != null ? request.helperFee() : existing.getHelperFee())
                .stairsSurcharge(request.stairsSurcharge() != null ? request.stairsSurcharge() : existing.getStairsSurcharge())
                .sameDaySurcharge(request.sameDaySurcharge() != null ? request.sameDaySurcharge() : existing.getSameDaySurcharge())
                .afterHoursSurcharge(request.afterHoursSurcharge() != null ? request.afterHoursSurcharge() : existing.getAfterHoursSurcharge())
                .effectiveFrom(LocalDate.now())
                .createdAt(Instant.now())
                .build();

        PricingRule saved = pricingRuleRepository.save(newRule);
        return toResponse(saved);
    }

    /**
     * Returns all pricing rules ordered by vehicle type name (asc) then effectiveFrom (desc).
     */
    @Transactional(readOnly = true)
    public List<PricingRuleResponse> listAll() {
        return pricingRuleRepository.findAll()
                .stream()
                .sorted(Comparator
                        .comparing((PricingRule r) -> r.getVehicleType().getName())
                        .thenComparing(Comparator.comparing(PricingRule::getEffectiveFrom).reversed()))
                .map(this::toResponse)
                .toList();
    }

    /**
     * Returns the active pricing rule for the given vehicle type and date.
     * Throws {@link PricingUnavailableException} if no rule is found.
     */
    @Transactional(readOnly = true)
    public PricingRuleResponse getActiveRule(Long vehicleTypeId, LocalDate date) {
        PricingRule rule = pricingRuleRepository.findActiveRule(vehicleTypeId, date)
                .orElseThrow(() -> new PricingUnavailableException(vehicleTypeId));
        return toResponse(rule);
    }

    private PricingRuleResponse toResponse(PricingRule rule) {
        return new PricingRuleResponse(
                rule.getId(),
                rule.getVehicleType().getId(),
                rule.getVehicleType().getName(),
                rule.getBaseFee(),
                rule.getPerKmFee(),
                rule.getHelperFee(),
                rule.getStairsSurcharge(),
                rule.getSameDaySurcharge(),
                rule.getAfterHoursSurcharge(),
                rule.getEffectiveFrom()
        );
    }
}
