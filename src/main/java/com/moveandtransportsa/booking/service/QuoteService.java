package com.moveandtransportsa.booking.service;

import com.moveandtransportsa.booking.dto.Address;
import com.moveandtransportsa.booking.dto.request.QuoteRequest;
import com.moveandtransportsa.booking.dto.request.QuoteRequestWithAddresses;
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

    private final DistanceCalculator distanceCalculator;
    private final PricingService pricingService;

    /**
     * Calculates a quote for the given request.
     * Resolves zone-based distance, active pricing rule, and applies all applicable surcharges.
     */
    public QuoteResponse calculate(QuoteRequest req) {
        BigDecimal distanceKm = distanceCalculator.getDistance(req.pickupSuburb(), req.dropoffSuburb());
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
     * Calculates a quote using full address information.
     * Uses Google Maps Distance Matrix API for accurate road distance calculation,
     * with automatic fallback to zone-based calculation if API is unavailable.
     * Applies the same pricing logic as the suburb-based calculate method.
     * 
     * @param req Quote request with full address details
     * @return QuoteResponse containing total price, distance, and itemized breakdown
     */
    public QuoteResponse calculateWithAddresses(QuoteRequestWithAddresses req) {
        // Construct Address objects from request fields
        Address pickup = new Address(
                req.pickupAddressLine(),
                req.pickupSuburb(),
                req.pickupCity()
        );
        Address dropoff = new Address(
                req.dropoffAddressLine(),
                req.dropoffSuburb(),
                req.dropoffCity()
        );

        // Calculate distance using Google Maps API with fallback to zone-based system
        BigDecimal distanceKm = distanceCalculator.getDistance(pickup, dropoff);
        
        // Get active pricing rule for the vehicle type
        PricingRuleResponse rule = pricingService.getActiveRule(req.vehicleTypeId(), LocalDate.now());

        // Determine if service is same-day or after-hours
        boolean sameDay = req.serviceDate().equals(LocalDate.now(ZoneId.of("Africa/Johannesburg")));
        boolean afterHours = req.serviceTime() != null && isAfterHours(req.serviceTime());

        // Calculate all fees and surcharges using same logic as zone-based method
        BigDecimal distanceFee = distanceKm.multiply(rule.perKmFee());
        BigDecimal helpersFee = BigDecimal.valueOf(req.helpers()).multiply(rule.helperFee());
        BigDecimal stairsSurcharge = req.stairs() ? rule.stairsSurcharge() : ZERO;
        BigDecimal sameDaySurcharge = sameDay ? rule.sameDaySurcharge() : ZERO;
        BigDecimal afterHoursSurcharge = afterHours ? rule.afterHoursSurcharge() : ZERO;

        // Calculate total price
        BigDecimal total = rule.baseFee()
                .add(distanceFee)
                .add(helpersFee)
                .add(stairsSurcharge)
                .add(sameDaySurcharge)
                .add(afterHoursSurcharge);

        // Build itemized breakdown
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
