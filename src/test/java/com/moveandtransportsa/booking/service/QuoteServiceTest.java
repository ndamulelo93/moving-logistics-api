package com.moveandtransportsa.booking.service;

import com.moveandtransportsa.booking.dto.request.QuoteRequest;
import com.moveandtransportsa.booking.dto.response.PricingRuleResponse;
import com.moveandtransportsa.booking.dto.response.QuoteResponse;
import com.moveandtransportsa.booking.exception.PricingUnavailableException;
import com.moveandtransportsa.booking.exception.SuburbNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link QuoteService}.
 *
 * ZoneService and PricingService are mocked so no database is required.
 *
 * Requirements covered: 1.1, 1.6
 */
@ExtendWith(MockitoExtension.class)
class QuoteServiceTest {

    @Mock
    private ZoneService zoneService;

    @Mock
    private PricingService pricingService;

    @InjectMocks
    private QuoteService quoteService;

    // -----------------------------------------------------------------------
    // Shared test fixtures
    // -----------------------------------------------------------------------

    private static final BigDecimal BASE_FEE              = new BigDecimal("500.00");
    private static final BigDecimal PER_KM_FEE            = new BigDecimal("10.00");
    private static final BigDecimal HELPER_FEE            = new BigDecimal("100.00");
    private static final BigDecimal STAIRS_SURCHARGE      = new BigDecimal("150.00");
    private static final BigDecimal SAME_DAY_SURCHARGE    = new BigDecimal("200.00");
    private static final BigDecimal AFTER_HOURS_SURCHARGE = new BigDecimal("250.00");
    private static final BigDecimal DISTANCE_KM           = new BigDecimal("20.00");

    private static final Long VEHICLE_TYPE_ID = 1L;
    private static final String PICKUP  = "Sandton";
    private static final String DROPOFF = "Soweto";

    /** A pricing rule with all surcharges configured. */
    private PricingRuleResponse pricingRule;

    @BeforeEach
    void setUp() {
        pricingRule = new PricingRuleResponse(
                1L,
                VEHICLE_TYPE_ID,
                "Bakkie",
                BASE_FEE,
                PER_KM_FEE,
                HELPER_FEE,
                STAIRS_SURCHARGE,
                SAME_DAY_SURCHARGE,
                AFTER_HOURS_SURCHARGE,
                LocalDate.now()
        );
    }

    // -----------------------------------------------------------------------
    // Helper: build a QuoteRequest
    // -----------------------------------------------------------------------

    private QuoteRequest buildRequest(int helpers, LocalDate serviceDate, LocalTime serviceTime, boolean stairs) {
        return new QuoteRequest(
                VEHICLE_TYPE_ID,
                PICKUP,
                DROPOFF,
                helpers,
                serviceDate,
                serviceTime,
                stairs
        );
    }

    // -----------------------------------------------------------------------
    // Test 1: No surcharges — total equals baseFee + distanceFee + helpersFee
    // -----------------------------------------------------------------------

    @Test
    void calculate_noSurcharges_returnsCorrectTotal() {
        // Arrange: future date (not same-day), business hours, no stairs, 0 helpers
        LocalDate futureDate = LocalDate.now().plusDays(3);
        LocalTime businessHours = LocalTime.of(10, 0);

        when(zoneService.getDistance(PICKUP, DROPOFF)).thenReturn(DISTANCE_KM);
        when(pricingService.getActiveRule(eq(VEHICLE_TYPE_ID), any(LocalDate.class))).thenReturn(pricingRule);

        QuoteRequest request = buildRequest(0, futureDate, businessHours, false);

        // Act
        QuoteResponse response = quoteService.calculate(request);

        // Assert: total = 500 + (20 × 10) + (0 × 100) = 700
        BigDecimal expectedTotal = new BigDecimal("700.00");
        assertThat(response.estimatedTotal()).isEqualByComparingTo(expectedTotal);
        assertThat(response.distanceKm()).isEqualByComparingTo(DISTANCE_KM);

        // Breakdown checks
        assertThat(response.breakdown().baseFee()).isEqualByComparingTo(BASE_FEE);
        assertThat(response.breakdown().distanceFee()).isEqualByComparingTo(new BigDecimal("200.00"));
        assertThat(response.breakdown().helpersFee()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(response.breakdown().stairsSurcharge()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(response.breakdown().sameDaySurcharge()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(response.breakdown().afterHoursSurcharge()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    // -----------------------------------------------------------------------
    // Test 2: Same-day surcharge applied when serviceDate = today
    // -----------------------------------------------------------------------

    @Test
    void calculate_sameDayService_appliesSameDaySurcharge() {
        // Arrange: serviceDate = today in Africa/Johannesburg
        LocalDate today = LocalDate.now(ZoneId.of("Africa/Johannesburg"));
        LocalTime businessHours = LocalTime.of(10, 0);

        when(zoneService.getDistance(PICKUP, DROPOFF)).thenReturn(DISTANCE_KM);
        when(pricingService.getActiveRule(eq(VEHICLE_TYPE_ID), any(LocalDate.class))).thenReturn(pricingRule);

        QuoteRequest request = buildRequest(0, today, businessHours, false);

        // Act
        QuoteResponse response = quoteService.calculate(request);

        // Assert: same-day surcharge (200) is included
        assertThat(response.breakdown().sameDaySurcharge()).isEqualByComparingTo(SAME_DAY_SURCHARGE);
        // Total = 500 + 200 + 0 + 0 + 200 + 0 = 900
        assertThat(response.estimatedTotal()).isEqualByComparingTo(new BigDecimal("900.00"));
    }

    // -----------------------------------------------------------------------
    // Test 3: After-hours surcharge applied when serviceTime before 07:00
    // -----------------------------------------------------------------------

    @Test
    void calculate_serviceTimeBeforeSevenAm_appliesAfterHoursSurcharge() {
        // Arrange: future date, time before 07:00
        LocalDate futureDate = LocalDate.now().plusDays(1);
        LocalTime earlyMorning = LocalTime.of(6, 59);

        when(zoneService.getDistance(PICKUP, DROPOFF)).thenReturn(DISTANCE_KM);
        when(pricingService.getActiveRule(eq(VEHICLE_TYPE_ID), any(LocalDate.class))).thenReturn(pricingRule);

        QuoteRequest request = buildRequest(0, futureDate, earlyMorning, false);

        // Act
        QuoteResponse response = quoteService.calculate(request);

        // Assert: after-hours surcharge (250) is included
        assertThat(response.breakdown().afterHoursSurcharge()).isEqualByComparingTo(AFTER_HOURS_SURCHARGE);
        // Total = 500 + 200 + 0 + 0 + 0 + 250 = 950
        assertThat(response.estimatedTotal()).isEqualByComparingTo(new BigDecimal("950.00"));
    }

    @Test
    void calculate_serviceTimeAfterSixPm_appliesAfterHoursSurcharge() {
        // Arrange: future date, time after 18:00
        LocalDate futureDate = LocalDate.now().plusDays(1);
        LocalTime lateEvening = LocalTime.of(18, 1);

        when(zoneService.getDistance(PICKUP, DROPOFF)).thenReturn(DISTANCE_KM);
        when(pricingService.getActiveRule(eq(VEHICLE_TYPE_ID), any(LocalDate.class))).thenReturn(pricingRule);

        QuoteRequest request = buildRequest(0, futureDate, lateEvening, false);

        // Act
        QuoteResponse response = quoteService.calculate(request);

        // Assert: after-hours surcharge (250) is included
        assertThat(response.breakdown().afterHoursSurcharge()).isEqualByComparingTo(AFTER_HOURS_SURCHARGE);
    }

    // -----------------------------------------------------------------------
    // Test 4: Stairs surcharge applied when stairs=true
    // -----------------------------------------------------------------------

    @Test
    void calculate_stairsTrue_appliesStairsSurcharge() {
        // Arrange: future date, business hours, stairs=true
        LocalDate futureDate = LocalDate.now().plusDays(2);
        LocalTime businessHours = LocalTime.of(9, 0);

        when(zoneService.getDistance(PICKUP, DROPOFF)).thenReturn(DISTANCE_KM);
        when(pricingService.getActiveRule(eq(VEHICLE_TYPE_ID), any(LocalDate.class))).thenReturn(pricingRule);

        QuoteRequest request = buildRequest(0, futureDate, businessHours, true);

        // Act
        QuoteResponse response = quoteService.calculate(request);

        // Assert: stairs surcharge (150) is included
        assertThat(response.breakdown().stairsSurcharge()).isEqualByComparingTo(STAIRS_SURCHARGE);
        // Total = 500 + 200 + 0 + 150 + 0 + 0 = 850
        assertThat(response.estimatedTotal()).isEqualByComparingTo(new BigDecimal("850.00"));
    }

    // -----------------------------------------------------------------------
    // Test 5: SuburbNotFoundException propagated from ZoneService
    // -----------------------------------------------------------------------

    @Test
    void calculate_unknownPickupSuburb_throwsSuburbNotFoundException() {
        // Arrange: ZoneService throws for unknown suburb
        when(zoneService.getDistance(PICKUP, DROPOFF))
                .thenThrow(new SuburbNotFoundException(PICKUP));

        QuoteRequest request = buildRequest(0, LocalDate.now().plusDays(1), LocalTime.of(10, 0), false);

        // Act & Assert
        assertThatThrownBy(() -> quoteService.calculate(request))
                .isInstanceOf(SuburbNotFoundException.class)
                .hasMessageContaining(PICKUP);
    }

    // -----------------------------------------------------------------------
    // Test 6: PricingUnavailableException propagated from PricingService
    // -----------------------------------------------------------------------

    @Test
    void calculate_noPricingRule_throwsPricingUnavailableException() {
        // Arrange: ZoneService returns a distance, but PricingService has no active rule
        when(zoneService.getDistance(PICKUP, DROPOFF)).thenReturn(DISTANCE_KM);
        when(pricingService.getActiveRule(eq(VEHICLE_TYPE_ID), any(LocalDate.class)))
                .thenThrow(new PricingUnavailableException(VEHICLE_TYPE_ID));

        QuoteRequest request = buildRequest(0, LocalDate.now().plusDays(1), LocalTime.of(10, 0), false);

        // Act & Assert
        assertThatThrownBy(() -> quoteService.calculate(request))
                .isInstanceOf(PricingUnavailableException.class)
                .hasMessageContaining(String.valueOf(VEHICLE_TYPE_ID));
    }

    // -----------------------------------------------------------------------
    // Test 7: Helpers fee included in total
    // -----------------------------------------------------------------------

    @Test
    void calculate_withHelpers_includesHelpersFeeInTotal() {
        // Arrange: 2 helpers, future date, business hours, no surcharges
        LocalDate futureDate = LocalDate.now().plusDays(3);
        LocalTime businessHours = LocalTime.of(11, 0);

        when(zoneService.getDistance(PICKUP, DROPOFF)).thenReturn(DISTANCE_KM);
        when(pricingService.getActiveRule(eq(VEHICLE_TYPE_ID), any(LocalDate.class))).thenReturn(pricingRule);

        QuoteRequest request = buildRequest(2, futureDate, businessHours, false);

        // Act
        QuoteResponse response = quoteService.calculate(request);

        // Assert: helpersFee = 2 × 100 = 200
        assertThat(response.breakdown().helpersFee()).isEqualByComparingTo(new BigDecimal("200.00"));
        // Total = 500 + 200 + 200 + 0 + 0 + 0 = 900
        assertThat(response.estimatedTotal()).isEqualByComparingTo(new BigDecimal("900.00"));
    }
}
