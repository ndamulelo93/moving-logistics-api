package com.moveandtransportsa.booking.service;

import com.moveandtransportsa.booking.dto.request.QuoteRequest;
import com.moveandtransportsa.booking.dto.response.PricingRuleResponse;
import com.moveandtransportsa.booking.dto.response.QuoteBreakdown;
import com.moveandtransportsa.booking.dto.response.QuoteResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;

@Service
@RequiredArgsConstructor
public class QuoteService {

    private static final BigDecimal ZERO = BigDecimal.ZERO;

    private final ZoneService zoneService;
    private final PricingService pricingService;

    /**
     * Calculates a quote for the given request.
     * Resolves zone-based distance, active pricing rule, and applies all applicable surcharges.
     */
    public QuoteResponse calculate(QuoteRequest req) {
        BigDecimal distanceKm = zoneService.getDistance(req.pickupSuburb(), req.dropoffSuburb());
        PricingRuleResponse rule = pricingService.getActiveRule(req.vehicleTypeId(), LocalDate.now());

        boolean sameDay = req.serviceDate().equals(LocalDate.now(ZoneId.of("Africa/Johannesburg")));
        boolean afterHours = req.serviceTime() != null && isAfterHours(req.serviceTime());

        BigDecimal distanceFee = distanceKm.multiply(rule.perKmFee());
        BigDecimal helpersFee = BigDecimal.valueOf(req.helpers()).multiply(rule.helperFee());
        BigDecimal stairsSurcharge = req.stairs() ? rule.stairsSurcharge() : ZERO;
        BigDecimal sameDaySurcharge = sameDay ? rule.sameDaySurcharge() : ZERO;
        BigDecimal afterHoursSurcharge = afterHours ? rule.afterHoursSurcharge() : ZERO;

        BigDecimal total = rule.baseFee()
                .add(distanceFee)
                .add(helpersFee)
                .add(stairsSurcharge)
                .add(sameDaySurcharge)
                .add(afterHoursSurcharge);

        QuoteBreakdown breakdown = new QuoteBreakdown(
                rule.baseFee(),
                distanceFee,
                helpersFee,
                stairsSurcharge,
                sameDaySurcharge,
                afterHoursSurcharge
        );

        return new QuoteResponse(total, distanceKm, breakdown);
    }

    /**
     * Returns true if the given time is outside business hours (before 07:00 or after 18:00).
     */
    private boolean isAfterHours(LocalTime time) {
        return time.isBefore(LocalTime.of(7, 0)) || time.isAfter(LocalTime.of(18, 0));
    }
}
