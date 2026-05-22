package com.moveandtransportsa.booking.service;

import com.moveandtransportsa.booking.domain.booking.Booking;
import com.moveandtransportsa.booking.domain.booking.BookingAssignment;
import com.moveandtransportsa.booking.domain.vehicle.Vehicle;
import com.moveandtransportsa.booking.domain.vehicle.VehicleType;
import com.moveandtransportsa.booking.exception.BookingNotFoundException;
import com.moveandtransportsa.booking.exception.ConflictException;
import com.moveandtransportsa.booking.repository.BookingAssignmentRepository;
import com.moveandtransportsa.booking.repository.VehicleRepository;
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
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link VehicleService} deactivation guard.
 *
 * Validates that a vehicle cannot be deactivated when it has future assignments,
 * and that deactivation succeeds when no future assignments exist.
 *
 * Requirements covered: 25.3
 */
@ExtendWith(MockitoExtension.class)
class VehicleServiceTest {

    @Mock
    private VehicleRepository vehicleRepository;

    @Mock
    private VehicleTypeRepository vehicleTypeRepository;

    @Mock
    private BookingAssignmentRepository bookingAssignmentRepository;

    @InjectMocks
    private VehicleService vehicleService;

    private static final Long VEHICLE_ID = 1L;

    private VehicleType vehicleType;
    private Vehicle activeVehicle;

    @BeforeEach
    void setUp() {
        Instant now = Instant.now();

        vehicleType = VehicleType.builder()
                .id(10L)
                .name("Bakkie")
                .description("Small bakkie")
                .loadCapacity("1 ton")
                .baseFee(new BigDecimal("500.00"))
                .active(true)
                .createdAt(now)
                .updatedAt(now)
                .build();

        activeVehicle = Vehicle.builder()
                .id(VEHICLE_ID)
                .registrationNumber("GP 123 ABC")
                .make("Toyota")
                .model("Hilux")
                .year((short) 2022)
                .vehicleType(vehicleType)
                .active(true)
                .createdAt(now)
                .updatedAt(now)
                .build();
    }

    // -----------------------------------------------------------------------
    // Deactivation blocked when future assignment exists
    // -----------------------------------------------------------------------

    @Test
    void deactivate_withFutureAssignment_throwsConflictException() {
        // Arrange: vehicle exists and has one future assignment
        Booking futureBooking = Booking.builder()
                .id(100L)
                .bookingReference("MTSA-24001")
                .serviceDate(LocalDate.now().plusDays(3))
                .build();

        BookingAssignment futureAssignment = BookingAssignment.builder()
                .id(200L)
                .booking(futureBooking)
                .vehicle(activeVehicle)
                .assignedAt(Instant.now())
                .build();

        when(vehicleRepository.findById(VEHICLE_ID)).thenReturn(Optional.of(activeVehicle));
        when(bookingAssignmentRepository.findFutureAssignmentsByVehicle(eq(VEHICLE_ID), any(LocalDate.class)))
                .thenReturn(List.of(futureAssignment));

        // Act & Assert
        assertThatThrownBy(() -> vehicleService.deactivate(VEHICLE_ID))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("MTSA-24001");

        // Vehicle must NOT have been saved as inactive
        verify(vehicleRepository, never()).save(any());
    }

    @Test
    void deactivate_withMultipleFutureAssignments_conflictMessageContainsAllBookingRefs() {
        // Arrange: vehicle has two future assignments
        Booking booking1 = Booking.builder()
                .id(101L)
                .bookingReference("MTSA-24001")
                .serviceDate(LocalDate.now().plusDays(2))
                .build();

        Booking booking2 = Booking.builder()
                .id(102L)
                .bookingReference("MTSA-24002")
                .serviceDate(LocalDate.now().plusDays(5))
                .build();

        BookingAssignment assignment1 = BookingAssignment.builder()
                .id(201L)
                .booking(booking1)
                .vehicle(activeVehicle)
                .assignedAt(Instant.now())
                .build();

        BookingAssignment assignment2 = BookingAssignment.builder()
                .id(202L)
                .booking(booking2)
                .vehicle(activeVehicle)
                .assignedAt(Instant.now())
                .build();

        when(vehicleRepository.findById(VEHICLE_ID)).thenReturn(Optional.of(activeVehicle));
        when(bookingAssignmentRepository.findFutureAssignmentsByVehicle(eq(VEHICLE_ID), any(LocalDate.class)))
                .thenReturn(List.of(assignment1, assignment2));

        // Act & Assert
        assertThatThrownBy(() -> vehicleService.deactivate(VEHICLE_ID))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("MTSA-24001")
                .hasMessageContaining("MTSA-24002");

        verify(vehicleRepository, never()).save(any());
    }

    // -----------------------------------------------------------------------
    // Deactivation succeeds when no future assignments exist
    // -----------------------------------------------------------------------

    @Test
    void deactivate_withNoFutureAssignments_setsActiveToFalse() {
        // Arrange: vehicle exists and has no future assignments
        when(vehicleRepository.findById(VEHICLE_ID)).thenReturn(Optional.of(activeVehicle));
        when(bookingAssignmentRepository.findFutureAssignmentsByVehicle(eq(VEHICLE_ID), any(LocalDate.class)))
                .thenReturn(Collections.emptyList());
        when(vehicleRepository.save(any(Vehicle.class))).thenAnswer(inv -> inv.getArgument(0));

        // Act
        vehicleService.deactivate(VEHICLE_ID);

        // Assert: vehicle was saved with active=false
        verify(vehicleRepository).save(argThat(v -> !v.isActive()));
        assertThat(activeVehicle.isActive()).isFalse();
    }

    @Test
    void deactivate_withNoFutureAssignments_updatesUpdatedAt() {
        // Arrange
        Instant beforeDeactivation = activeVehicle.getUpdatedAt();

        when(vehicleRepository.findById(VEHICLE_ID)).thenReturn(Optional.of(activeVehicle));
        when(bookingAssignmentRepository.findFutureAssignmentsByVehicle(eq(VEHICLE_ID), any(LocalDate.class)))
                .thenReturn(Collections.emptyList());
        when(vehicleRepository.save(any(Vehicle.class))).thenAnswer(inv -> inv.getArgument(0));

        // Act
        vehicleService.deactivate(VEHICLE_ID);

        // Assert: updatedAt was refreshed
        assertThat(activeVehicle.getUpdatedAt()).isAfterOrEqualTo(beforeDeactivation);
    }

    // -----------------------------------------------------------------------
    // Deactivation of non-existent vehicle
    // -----------------------------------------------------------------------

    @Test
    void deactivate_vehicleNotFound_throwsBookingNotFoundException() {
        // Arrange: no vehicle with this ID
        when(vehicleRepository.findById(VEHICLE_ID)).thenReturn(Optional.empty());

        // Act & Assert
        assertThatThrownBy(() -> vehicleService.deactivate(VEHICLE_ID))
                .isInstanceOf(BookingNotFoundException.class)
                .hasMessageContaining(String.valueOf(VEHICLE_ID));

        verify(bookingAssignmentRepository, never()).findFutureAssignmentsByVehicle(any(), any());
        verify(vehicleRepository, never()).save(any());
    }

    // -----------------------------------------------------------------------
    // Future assignment check uses today's date as the cutoff
    // -----------------------------------------------------------------------

    @Test
    void deactivate_queriesFutureAssignmentsWithTodayAsCutoff() {
        // Arrange
        when(vehicleRepository.findById(VEHICLE_ID)).thenReturn(Optional.of(activeVehicle));
        when(bookingAssignmentRepository.findFutureAssignmentsByVehicle(eq(VEHICLE_ID), any(LocalDate.class)))
                .thenReturn(Collections.emptyList());
        when(vehicleRepository.save(any(Vehicle.class))).thenAnswer(inv -> inv.getArgument(0));

        // Act
        vehicleService.deactivate(VEHICLE_ID);

        // Assert: the date passed to the repository is today (not null, not in the past)
        verify(bookingAssignmentRepository).findFutureAssignmentsByVehicle(
                eq(VEHICLE_ID),
                argThat(date -> !date.isAfter(LocalDate.now()) && !date.isBefore(LocalDate.now()))
        );
    }
}
