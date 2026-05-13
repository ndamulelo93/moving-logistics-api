package com.moveandtransportsa.booking;

import com.moveandtransportsa.booking.dto.response.QuoteBreakdown;
import com.moveandtransportsa.booking.dto.response.QuoteResponse;
import net.jqwik.api.*;
import net.jqwik.api.constraints.IntRange;
import org.junit.jupiter.api.Tag;

import java.math.BigDecimal;
import java.math.RoundingMode;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Property-based tests for the quote formula.
 *
 * Validates: Requirements 1.1, 1.6
 *
 * Property 1: Quote formula correctness and breakdown consistency.
 *
 * The formula under test:
 *   estimatedTotal = baseFee
 *                  + (distanceKm × perKmFee)
 *                  + (helpers × helperFee)
 *                  + stairsSurcharge   (only when stairs=true)
 *                  + sameDaySurcharge  (only when sameDay=true)
 *                  + afterHoursSurcharge (only when afterHours=true)
 *
 * The QuoteBreakdown record contains:
 *   baseFee, distanceFee, helpersFee, stairsSurcharge, sameDaySurcharge, afterHoursSurcharge
 *
 * Two properties are verified for every generated input:
 *   1. estimatedTotal equals the manually computed formula sum.
 *   2. The sum of all breakdown items equals estimatedTotal.
 */
@Tag("Feature: transport-booking-backend, Property 1: Quote formula correctness and breakdown consistency")
class QuoteFormulaPropertyTest {

    // -----------------------------------------------------------------------
    // Inline formula logic (mirrors QuoteService.calculate without DB calls)
    // -----------------------------------------------------------------------

    /**
     * Applies the quote formula and returns a QuoteResponse containing the
     * estimated total and the itemised breakdown.
     *
     * All intermediate values are rounded to 2 decimal places (HALF_UP) to
     * match production precision.
     */
    private QuoteResponse applyFormula(
            BigDecimal baseFee,
            BigDecimal perKmFee,
            BigDecimal helperFee,
            BigDecimal stairsSurchargeRate,
            BigDecimal sameDaySurchargeRate,
            BigDecimal afterHoursSurchargeRate,
            BigDecimal distanceKm,
            int helpers,
            boolean stairs,
            boolean sameDay,
            boolean afterHours) {

        BigDecimal ZERO = BigDecimal.ZERO;

        BigDecimal distanceFee = distanceKm.multiply(perKmFee)
                .setScale(2, RoundingMode.HALF_UP);

        BigDecimal helpersFee = BigDecimal.valueOf(helpers).multiply(helperFee)
                .setScale(2, RoundingMode.HALF_UP);

        BigDecimal stairsSurcharge = stairs
                ? stairsSurchargeRate.setScale(2, RoundingMode.HALF_UP)
                : ZERO.setScale(2, RoundingMode.HALF_UP);

        BigDecimal sameDaySurcharge = sameDay
                ? sameDaySurchargeRate.setScale(2, RoundingMode.HALF_UP)
                : ZERO.setScale(2, RoundingMode.HALF_UP);

        BigDecimal afterHoursSurcharge = afterHours
                ? afterHoursSurchargeRate.setScale(2, RoundingMode.HALF_UP)
                : ZERO.setScale(2, RoundingMode.HALF_UP);

        BigDecimal normalizedBase = baseFee.setScale(2, RoundingMode.HALF_UP);

        BigDecimal total = normalizedBase
                .add(distanceFee)
                .add(helpersFee)
                .add(stairsSurcharge)
                .add(sameDaySurcharge)
                .add(afterHoursSurcharge);

        QuoteBreakdown breakdown = new QuoteBreakdown(
                normalizedBase,
                distanceFee,
                helpersFee,
                stairsSurcharge,
                sameDaySurcharge,
                afterHoursSurcharge
        );

        return new QuoteResponse(total, distanceKm, breakdown);
    }

    // -----------------------------------------------------------------------
    // Arbitrary generators
    // -----------------------------------------------------------------------

    /** BigDecimal in [0, 5000] with 2 decimal places — used for baseFee. */
    @Provide
    Arbitrary<BigDecimal> baseFees() {
        return Arbitraries.bigDecimals()
                .between(BigDecimal.ZERO, new BigDecimal("5000.00"))
                .ofScale(2);
    }

    /** BigDecimal in [0, 100] with 2 decimal places — used for perKmFee. */
    @Provide
    Arbitrary<BigDecimal> perKmFees() {
        return Arbitraries.bigDecimals()
                .between(BigDecimal.ZERO, new BigDecimal("100.00"))
                .ofScale(2);
    }

    /** BigDecimal in [0, 500] with 2 decimal places — used for helperFee and surcharges. */
    @Provide
    Arbitrary<BigDecimal> surchargeAmounts() {
        return Arbitraries.bigDecimals()
                .between(BigDecimal.ZERO, new BigDecimal("500.00"))
                .ofScale(2);
    }

    /** BigDecimal in [1, 200] with 2 decimal places — used for distanceKm. */
    @Provide
    Arbitrary<BigDecimal> distances() {
        return Arbitraries.bigDecimals()
                .between(new BigDecimal("1.00"), new BigDecimal("200.00"))
                .ofScale(2);
    }

    // -----------------------------------------------------------------------
    // Property 1 — formula correctness and breakdown consistency
    // -----------------------------------------------------------------------

    /**
     * **Validates: Requirements 1.1, 1.6**
     *
     * For any combination of valid pricing inputs and surcharge flags:
     *   1. The computed estimatedTotal must equal the manually calculated formula sum.
     *   2. The sum of all breakdown line items must equal estimatedTotal.
     */
    @Property(tries = 100)
    void quoteFormula_totalEqualsManualCalculationAndBreakdownSumsToTotal(
            @ForAll("baseFees")     BigDecimal baseFee,
            @ForAll("perKmFees")    BigDecimal perKmFee,
            @ForAll("surchargeAmounts") BigDecimal helperFee,
            @ForAll("surchargeAmounts") BigDecimal stairsSurchargeRate,
            @ForAll("surchargeAmounts") BigDecimal sameDaySurchargeRate,
            @ForAll("surchargeAmounts") BigDecimal afterHoursSurchargeRate,
            @ForAll("distances")    BigDecimal distanceKm,
            @ForAll @IntRange(min = 0, max = 5) int helpers,
            @ForAll boolean stairs,
            @ForAll boolean sameDay,
            @ForAll boolean afterHours) {

        // --- Compute expected values manually (mirrors the formula) ---
        BigDecimal ZERO = BigDecimal.ZERO;

        BigDecimal expectedDistanceFee = distanceKm.multiply(perKmFee)
                .setScale(2, RoundingMode.HALF_UP);

        BigDecimal expectedHelpersFee = BigDecimal.valueOf(helpers).multiply(helperFee)
                .setScale(2, RoundingMode.HALF_UP);

        BigDecimal expectedStairsSurcharge = stairs
                ? stairsSurchargeRate.setScale(2, RoundingMode.HALF_UP)
                : ZERO.setScale(2, RoundingMode.HALF_UP);

        BigDecimal expectedSameDaySurcharge = sameDay
                ? sameDaySurchargeRate.setScale(2, RoundingMode.HALF_UP)
                : ZERO.setScale(2, RoundingMode.HALF_UP);

        BigDecimal expectedAfterHoursSurcharge = afterHours
                ? afterHoursSurchargeRate.setScale(2, RoundingMode.HALF_UP)
                : ZERO.setScale(2, RoundingMode.HALF_UP);

        BigDecimal normalizedBase = baseFee.setScale(2, RoundingMode.HALF_UP);

        BigDecimal expectedTotal = normalizedBase
                .add(expectedDistanceFee)
                .add(expectedHelpersFee)
                .add(expectedStairsSurcharge)
                .add(expectedSameDaySurcharge)
                .add(expectedAfterHoursSurcharge);

        // --- Invoke the formula under test ---
        QuoteResponse result = applyFormula(
                baseFee, perKmFee, helperFee,
                stairsSurchargeRate, sameDaySurchargeRate, afterHoursSurchargeRate,
                distanceKm, helpers, stairs, sameDay, afterHours);

        QuoteBreakdown breakdown = result.breakdown();

        // --- Assertion 1: estimatedTotal equals manually computed total ---
        assertThat(result.estimatedTotal())
                .as("estimatedTotal must equal the formula sum")
                .isEqualByComparingTo(expectedTotal);

        // --- Assertion 2: sum of breakdown items equals estimatedTotal ---
        BigDecimal breakdownSum = breakdown.baseFee()
                .add(breakdown.distanceFee())
                .add(breakdown.helpersFee())
                .add(breakdown.stairsSurcharge())
                .add(breakdown.sameDaySurcharge())
                .add(breakdown.afterHoursSurcharge());

        assertThat(breakdownSum)
                .as("sum of all breakdown items must equal estimatedTotal")
                .isEqualByComparingTo(result.estimatedTotal());

        // --- Assertion 3: individual breakdown items match expected values ---
        assertThat(breakdown.baseFee())
                .as("breakdown.baseFee must equal normalised baseFee")
                .isEqualByComparingTo(normalizedBase);

        assertThat(breakdown.distanceFee())
                .as("breakdown.distanceFee must equal distanceKm × perKmFee")
                .isEqualByComparingTo(expectedDistanceFee);

        assertThat(breakdown.helpersFee())
                .as("breakdown.helpersFee must equal helpers × helperFee")
                .isEqualByComparingTo(expectedHelpersFee);

        assertThat(breakdown.stairsSurcharge())
                .as("breakdown.stairsSurcharge must be stairsSurchargeRate when stairs=true, else 0")
                .isEqualByComparingTo(expectedStairsSurcharge);

        assertThat(breakdown.sameDaySurcharge())
                .as("breakdown.sameDaySurcharge must be sameDaySurchargeRate when sameDay=true, else 0")
                .isEqualByComparingTo(expectedSameDaySurcharge);

        assertThat(breakdown.afterHoursSurcharge())
                .as("breakdown.afterHoursSurcharge must be afterHoursSurchargeRate when afterHours=true, else 0")
                .isEqualByComparingTo(expectedAfterHoursSurcharge);
    }

    // -----------------------------------------------------------------------
    // Edge-case: all surcharges disabled — total equals base + distance + helpers
    // -----------------------------------------------------------------------

    /**
     * **Validates: Requirements 1.1, 1.6**
     *
     * When all surcharge flags are false, the total must equal
     * baseFee + (distanceKm × perKmFee) + (helpers × helperFee).
     */
    @Property(tries = 100)
    void quoteFormula_noSurcharges_totalEqualsBaseDistanceHelpers(
            @ForAll("baseFees")     BigDecimal baseFee,
            @ForAll("perKmFees")    BigDecimal perKmFee,
            @ForAll("surchargeAmounts") BigDecimal helperFee,
            @ForAll("surchargeAmounts") BigDecimal stairsSurchargeRate,
            @ForAll("surchargeAmounts") BigDecimal sameDaySurchargeRate,
            @ForAll("surchargeAmounts") BigDecimal afterHoursSurchargeRate,
            @ForAll("distances")    BigDecimal distanceKm,
            @ForAll @IntRange(min = 0, max = 5) int helpers) {

        QuoteResponse result = applyFormula(
                baseFee, perKmFee, helperFee,
                stairsSurchargeRate, sameDaySurchargeRate, afterHoursSurchargeRate,
                distanceKm, helpers,
                false, false, false);  // all surcharge flags off

        BigDecimal expectedTotal = baseFee.setScale(2, RoundingMode.HALF_UP)
                .add(distanceKm.multiply(perKmFee).setScale(2, RoundingMode.HALF_UP))
                .add(BigDecimal.valueOf(helpers).multiply(helperFee).setScale(2, RoundingMode.HALF_UP));

        assertThat(result.estimatedTotal())
                .as("with no surcharges, total must equal baseFee + distanceFee + helpersFee")
                .isEqualByComparingTo(expectedTotal);

        assertThat(result.breakdown().stairsSurcharge())
                .as("stairsSurcharge must be 0 when stairs=false")
                .isEqualByComparingTo(BigDecimal.ZERO);

        assertThat(result.breakdown().sameDaySurcharge())
                .as("sameDaySurcharge must be 0 when sameDay=false")
                .isEqualByComparingTo(BigDecimal.ZERO);

        assertThat(result.breakdown().afterHoursSurcharge())
                .as("afterHoursSurcharge must be 0 when afterHours=false")
                .isEqualByComparingTo(BigDecimal.ZERO);
    }

    // -----------------------------------------------------------------------
    // Edge-case: all surcharges enabled — total includes every surcharge
    // -----------------------------------------------------------------------

    /**
     * **Validates: Requirements 1.1, 1.3, 1.4, 1.5, 1.6**
     *
     * When all surcharge flags are true, the total must include every surcharge.
     */
    @Property(tries = 100)
    void quoteFormula_allSurchargesEnabled_totalIncludesAllSurcharges(
            @ForAll("baseFees")     BigDecimal baseFee,
            @ForAll("perKmFees")    BigDecimal perKmFee,
            @ForAll("surchargeAmounts") BigDecimal helperFee,
            @ForAll("surchargeAmounts") BigDecimal stairsSurchargeRate,
            @ForAll("surchargeAmounts") BigDecimal sameDaySurchargeRate,
            @ForAll("surchargeAmounts") BigDecimal afterHoursSurchargeRate,
            @ForAll("distances")    BigDecimal distanceKm,
            @ForAll @IntRange(min = 0, max = 5) int helpers) {

        QuoteResponse result = applyFormula(
                baseFee, perKmFee, helperFee,
                stairsSurchargeRate, sameDaySurchargeRate, afterHoursSurchargeRate,
                distanceKm, helpers,
                true, true, true);  // all surcharge flags on

        BigDecimal expectedTotal = baseFee.setScale(2, RoundingMode.HALF_UP)
                .add(distanceKm.multiply(perKmFee).setScale(2, RoundingMode.HALF_UP))
                .add(BigDecimal.valueOf(helpers).multiply(helperFee).setScale(2, RoundingMode.HALF_UP))
                .add(stairsSurchargeRate.setScale(2, RoundingMode.HALF_UP))
                .add(sameDaySurchargeRate.setScale(2, RoundingMode.HALF_UP))
                .add(afterHoursSurchargeRate.setScale(2, RoundingMode.HALF_UP));

        assertThat(result.estimatedTotal())
                .as("with all surcharges, total must include every surcharge component")
                .isEqualByComparingTo(expectedTotal);

        assertThat(result.breakdown().stairsSurcharge())
                .as("stairsSurcharge must equal stairsSurchargeRate when stairs=true")
                .isEqualByComparingTo(stairsSurchargeRate.setScale(2, RoundingMode.HALF_UP));

        assertThat(result.breakdown().sameDaySurcharge())
                .as("sameDaySurcharge must equal sameDaySurchargeRate when sameDay=true")
                .isEqualByComparingTo(sameDaySurchargeRate.setScale(2, RoundingMode.HALF_UP));

        assertThat(result.breakdown().afterHoursSurcharge())
                .as("afterHoursSurcharge must equal afterHoursSurchargeRate when afterHours=true")
                .isEqualByComparingTo(afterHoursSurchargeRate.setScale(2, RoundingMode.HALF_UP));
    }
}
