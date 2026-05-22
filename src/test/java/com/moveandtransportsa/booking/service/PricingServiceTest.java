package com.moveandtransportsa.booking.service;

import com.moveandtransportsa.booking.domain.pricing.PricingRule;
import com.moveandtransportsa.booking.domain.vehicle.VehicleType;
import com.moveandtransportsa.booking.dto.response.PricingRuleResponse;
import com.moveandtransportsa.booking.exception.PricingUnavailableException;
import com.moveandtransportsa.booking.repository.PricingRuleRepository;
import com.moveandtransportsa.booking.repository.VehicleTypeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link PricingService} active rule selection.
 *
 * Validates that the service returns the most recent effective rule on or before
 * the requested date, ignores future-dated rules, and throws
 * {@link PricingUnavailableException} when no matching rule exists.
 *
 * Requirements covered: 13.3
 */
@ExtendWith(MockitoExtension.class)
class PricingServiceTest {

    @Mock
    private PricingRuleRepository pricingRuleRepository;

    @Mock
    private VehicleTypeRepository vehicleTypeRepository;

    @InjectMocks
    private PricingService pricingService;

    private static final Long VEHICLE_TYPE_ID = 1L;

    private VehicleType vehicleType;

    @BeforeEach
    void setUp() {
        vehicleType = VehicleType.builder()
                .id(VEHICLE_TYPE_ID)
                .name("Bakkie")
                .description("Small bakkie")
                .loadCapacity("1 ton")
                .baseFee(new BigDecimal("500.00"))
                .active(true)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
    }

    // -----------------------------------------------------------------------
    // Returns rule with most recent effectiveFrom <= today
    // -----------------------------------------------------------------------

    @Test
    void getActiveRule_withRuleEffectiveToday_returnsRule() {
        // Arrange: a rule whose effectiveFrom is exactly today
        LocalDate today = LocalDate.now();
        PricingRule rule = buildRule(1L, today);

        when(pricingRuleRepository.findActiveRule(VEHICLE_TYPE_ID, today))
                .thenReturn(Optional.of(rule));

        // Act
        PricingRuleResponse response = pricingService.getActiveRule(VEHICLE_TYPE_ID, today);

        // Assert
        assertThat(response).isNotNull();
        assertThat(response.effectiveFrom()).isEqualTo(today);
        assertThat(response.vehicleTypeId()).isEqualTo(VEHICLE_TYPE_ID);
        verify(pricingRuleRepository).findActiveRule(VEHICLE_TYPE_ID, today);
    }

    @Test
    void getActiveRule_withRuleEffectiveInPast_returnsRule() {
        // Arrange: a rule whose effectiveFrom is 30 days in the past
        LocalDate today = LocalDate.now();
        LocalDate pastDate = today.minusDays(30);
        PricingRule rule = buildRule(2L, pastDate);

        when(pricingRuleRepository.findActiveRule(VEHICLE_TYPE_ID, today))
                .thenReturn(Optional.of(rule));

        // Act
        PricingRuleResponse response = pricingService.getActiveRule(VEHICLE_TYPE_ID, today);

        // Assert: the past-dated rule is returned as the active rule
        assertThat(response).isNotNull();
        assertThat(response.effectiveFrom()).isEqualTo(pastDate);
    }

    @Test
    void getActiveRule_withMultipleRules_returnsMostRecentEffectiveRule() {
        // Arrange: repository returns the most recent rule (the query handles ordering;
        // the service delegates selection entirely to the repository)
        LocalDate today = LocalDate.now();
        LocalDate olderDate = today.minusDays(60);
        LocalDate recentDate = today.minusDays(10);

        // The repository is expected to return the most recent rule <= today
        PricingRule mostRecentRule = buildRule(3L, recentDate);

        when(pricingRuleRepository.findActiveRule(VEHICLE_TYPE_ID, today))
                .thenReturn(Optional.of(mostRecentRule));

        // Act
        PricingRuleResponse response = pricingService.getActiveRule(VEHICLE_TYPE_ID, today);

        // Assert: the most recent rule is returned, not the older one
        assertThat(response.effectiveFrom()).isEqualTo(recentDate);
        assertThat(response.effectiveFrom()).isAfter(olderDate);
    }

    @Test
    void getActiveRule_returnsCorrectFeeValues() {
        // Arrange: verify all fee fields are mapped correctly to the response
        LocalDate today = LocalDate.now();
        PricingRule rule = PricingRule.builder()
                .id(4L)
                .vehicleType(vehicleType)
                .baseFee(new BigDecimal("600.00"))
                .perKmFee(new BigDecimal("12.50"))
                .helperFee(new BigDecimal("150.00"))
                .stairsSurcharge(new BigDecimal("75.00"))
                .sameDaySurcharge(new BigDecimal("100.00"))
                .afterHoursSurcharge(new BigDecimal("200.00"))
                .effectiveFrom(today)
                .createdAt(Instant.now())
                .build();

        when(pricingRuleRepository.findActiveRule(VEHICLE_TYPE_ID, today))
                .thenReturn(Optional.of(rule));

        // Act
        PricingRuleResponse response = pricingService.getActiveRule(VEHICLE_TYPE_ID, today);

        // Assert: all fee fields are correctly mapped
        assertThat(response.baseFee()).isEqualByComparingTo("600.00");
        assertThat(response.perKmFee()).isEqualByComparingTo("12.50");
        assertThat(response.helperFee()).isEqualByComparingTo("150.00");
        assertThat(response.stairsSurcharge()).isEqualByComparingTo("75.00");
        assertThat(response.sameDaySurcharge()).isEqualByComparingTo("100.00");
        assertThat(response.afterHoursSurcharge()).isEqualByComparingTo("200.00");
    }

    // -----------------------------------------------------------------------
    // Ignores rules with future effectiveFrom
    // -----------------------------------------------------------------------

    @Test
    void getActiveRule_whenOnlyFutureRuleExists_throwsPricingUnavailableException() {
        // Arrange: the repository finds no rule with effectiveFrom <= today
        // (the future-dated rule is excluded by the query predicate)
        LocalDate today = LocalDate.now();

        when(pricingRuleRepository.findActiveRule(VEHICLE_TYPE_ID, today))
                .thenReturn(Optional.empty());

        // Act & Assert: future rules must not be returned; exception is expected
        assertThatThrownBy(() -> pricingService.getActiveRule(VEHICLE_TYPE_ID, today))
                .isInstanceOf(PricingUnavailableException.class);
    }

    @Test
    void getActiveRule_repositoryQueryFiltersOutFutureDates() {
        // Arrange: verify the service passes the requested date to the repository,
        // so the repository's effectiveFrom <= date predicate correctly excludes future rules
        LocalDate today = LocalDate.now();
        PricingRule rule = buildRule(5L, today.minusDays(1));

        when(pricingRuleRepository.findActiveRule(eq(VEHICLE_TYPE_ID), eq(today)))
                .thenReturn(Optional.of(rule));

        // Act
        pricingService.getActiveRule(VEHICLE_TYPE_ID, today);

        // Assert: the exact date is forwarded to the repository (not a future date)
        verify(pricingRuleRepository).findActiveRule(VEHICLE_TYPE_ID, today);
    }

    // -----------------------------------------------------------------------
    // Throws PricingUnavailableException when no rule exists
    // -----------------------------------------------------------------------

    @Test
    void getActiveRule_whenNoRuleExists_throwsPricingUnavailableException() {
        // Arrange: no pricing rule at all for this vehicle type
        LocalDate today = LocalDate.now();

        when(pricingRuleRepository.findActiveRule(VEHICLE_TYPE_ID, today))
                .thenReturn(Optional.empty());

        // Act & Assert
        assertThatThrownBy(() -> pricingService.getActiveRule(VEHICLE_TYPE_ID, today))
                .isInstanceOf(PricingUnavailableException.class)
                .hasMessageContaining(String.valueOf(VEHICLE_TYPE_ID));
    }

    @Test
    void getActiveRule_whenNoRuleExists_exceptionMessageContainsVehicleTypeId() {
        // Arrange
        LocalDate today = LocalDate.now();
        Long unknownVehicleTypeId = 999L;

        when(pricingRuleRepository.findActiveRule(unknownVehicleTypeId, today))
                .thenReturn(Optional.empty());

        // Act & Assert: the exception message must identify the vehicle type
        assertThatThrownBy(() -> pricingService.getActiveRule(unknownVehicleTypeId, today))
                .isInstanceOf(PricingUnavailableException.class)
                .hasMessageContaining(String.valueOf(unknownVehicleTypeId));
    }

    @Test
    void getActiveRule_whenNoRuleExistsForDifferentVehicleType_throwsPricingUnavailableException() {
        // Arrange: rules exist for vehicle type 1 but not for vehicle type 2
        LocalDate today = LocalDate.now();
        Long otherVehicleTypeId = 2L;

        when(pricingRuleRepository.findActiveRule(otherVehicleTypeId, today))
                .thenReturn(Optional.empty());

        // Act & Assert
        assertThatThrownBy(() -> pricingService.getActiveRule(otherVehicleTypeId, today))
                .isInstanceOf(PricingUnavailableException.class);
    }

    // -----------------------------------------------------------------------
    // Helper
    // -----------------------------------------------------------------------

    private PricingRule buildRule(Long id, LocalDate effectiveFrom) {
        return PricingRule.builder()
                .id(id)
                .vehicleType(vehicleType)
                .baseFee(new BigDecimal("500.00"))
                .perKmFee(new BigDecimal("10.00"))
                .helperFee(new BigDecimal("100.00"))
                .stairsSurcharge(new BigDecimal("50.00"))
                .sameDaySurcharge(new BigDecimal("80.00"))
                .afterHoursSurcharge(new BigDecimal("150.00"))
                .effectiveFrom(effectiveFrom)
                .createdAt(Instant.now())
                .build();
    }
}
