package com.moveandtransportsa.booking.service;

import com.moveandtransportsa.booking.domain.auth.AdminRole;
import com.moveandtransportsa.booking.domain.auth.AdminUser;
import com.moveandtransportsa.booking.domain.booking.Booking;
import com.moveandtransportsa.booking.domain.booking.BookingAssignment;
import com.moveandtransportsa.booking.domain.booking.BookingStatus;
import com.moveandtransportsa.booking.domain.driver.Driver;
import com.moveandtransportsa.booking.domain.vehicle.Vehicle;
import com.moveandtransportsa.booking.domain.vehicle.VehicleType;
import com.moveandtransportsa.booking.dto.response.AssignmentResponse;
import com.moveandtransportsa.booking.exception.BookingNotFoundException;
import com.moveandtransportsa.booking.exception.ConflictException;
import com.moveandtransportsa.booking.exception.InactiveEntityException;
import com.moveandtransportsa.booking.exception.VehicleTypeMismatchException;
import com.moveandtransportsa.booking.repository.AdminUserRepository;
import com.moveandtransportsa.booking.repository.BookingAssignmentRepository;
import com.moveandtransportsa.booking.repository.BookingRepository;
import com.moveandtransportsa.booking.repository.DriverRepository;
import com.moveandtransportsa.booking.repository.VehicleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link AssignmentService}.
 *
 * Covers:
 * - Requirement 11.1: Successful assignment transitions booking to ASSIGNED.
 * - Requirement 11.2: 409 when driver has a conflicting assignment on the same date.
 * - Requirement 11.3: 409 when vehicle has a conflicting assignment on the same date.
 * - Requirement 11.5: 409 when booking is not in CONFIRMED status.
 * - Requirement 11.6: 422 when vehicle type does not match the booking's vehicle type.
 * - Requirement 11.7: 422 when driver or vehicle is inactive.
 */
@ExtendWith(MockitoExtension.class)
class AssignmentServiceTest {

    @Mock
    private BookingRepository bookingRepository;

    @Mock
    private DriverRepository driverRepository;

    @Mock
    private VehicleRepository vehicleRepository;

    @Mock
    private BookingAssignmentRepository bookingAssignmentRepository;

    @Mock
    private StatusTransitionService statusTransitionService;

    @Mock
    private AdminUserRepository adminUserRepository;

    @InjectMocks
    private AssignmentService assignmentService;

    private static final Long BOOKING_ID   = 1L;
    private static final Long DRIVER_ID    = 10L;
    private static final Long VEHICLE_ID   = 20L;
    private static final Long ADMIN_ID     = 99L;
    private static final Long VEHICLE_TYPE_ID = 5L;

    private VehicleType vehicleType;
    private Booking confirmedBooking;
    private Driver activeDriver;
    private Vehicle activeVehicle;
    private AdminUser adminUser;

    @BeforeEach
    void setUp() {
        vehicleType = VehicleType.builder()
                .id(VEHICLE_TYPE_ID)
                .name("Bakkie")
                .baseFee(BigDecimal.valueOf(500))
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        confirmedBooking = Booking.builder()
                .id(BOOKING_ID)
                .bookingReference("MTSA-24001")
                .status(BookingStatus.CONFIRMED)
                .customerName("Jane Doe")
                .customerPhone("+27821234567")
                .customerEmail("jane@example.com")
                .vehicleType(vehicleType)
                .serviceDate(LocalDate.now().plusDays(2))
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        activeDriver = Driver.builder()
                .id(DRIVER_ID)
                .fullName("John Driver")
                .phone("+27831234567")
                .licenceNumber("DL123456")
                .active(true)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        activeVehicle = Vehicle.builder()
                .id(VEHICLE_ID)
                .registrationNumber("CA 123-456")
                .make("Toyota")
                .model("Hilux")
                .year((short) 2022)
                .vehicleType(vehicleType)
                .active(true)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        adminUser = AdminUser.builder()
                .id(ADMIN_ID)
                .username("dispatcher1")
                .displayName("Dispatcher One")
                .passwordHash("$2a$12$hash")
                .role(AdminRole.DISPATCHER)
                .build();
    }

    // -----------------------------------------------------------------------
    // Requirement 11.1 — successful assignment
    // -----------------------------------------------------------------------

    /**
     * Validates: Requirements 11.1
     *
     * A successful assignment persists a BookingAssignment and transitions the
     * booking to ASSIGNED, returning the correct response fields.
     */
    @Test
    void assign_confirmedBookingWithNoConflicts_createsAssignmentAndTransitionsToAssigned() {
        // Arrange
        when(bookingRepository.findById(BOOKING_ID)).thenReturn(Optional.of(confirmedBooking));
        when(driverRepository.findByIdWithLock(DRIVER_ID)).thenReturn(Optional.of(activeDriver));
        when(vehicleRepository.findByIdWithLock(VEHICLE_ID)).thenReturn(Optional.of(activeVehicle));
        when(bookingAssignmentRepository.existsConflictForDriver(DRIVER_ID, confirmedBooking.getServiceDate()))
                .thenReturn(false);
        when(bookingAssignmentRepository.existsConflictForVehicle(VEHICLE_ID, confirmedBooking.getServiceDate()))
                .thenReturn(false);
        when(adminUserRepository.findById(ADMIN_ID)).thenReturn(Optional.of(adminUser));
        when(bookingAssignmentRepository.save(any(BookingAssignment.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        // Act
        AssignmentResponse response = assignmentService.assign(BOOKING_ID, DRIVER_ID, VEHICLE_ID, ADMIN_ID);

        // Assert: assignment was persisted
        ArgumentCaptor<BookingAssignment> captor = ArgumentCaptor.forClass(BookingAssignment.class);
        verify(bookingAssignmentRepository).save(captor.capture());
        BookingAssignment saved = captor.getValue();
        assertThat(saved.getDriver()).isEqualTo(activeDriver);
        assertThat(saved.getVehicle()).isEqualTo(activeVehicle);
        assertThat(saved.getBooking()).isEqualTo(confirmedBooking);
        assertThat(saved.getAssignedBy()).isEqualTo(adminUser);
        assertThat(saved.getAssignedAt()).isNotNull();

        // Assert: status transition to ASSIGNED was triggered
        verify(statusTransitionService).transition(BOOKING_ID, BookingStatus.ASSIGNED, ADMIN_ID);

        // Assert: response contains correct data
        assertThat(response.bookingReference()).isEqualTo("MTSA-24001");
        assertThat(response.driverName()).isEqualTo("John Driver");
        assertThat(response.vehicleRegistration()).isEqualTo("CA 123-456");
        assertThat(response.assignedAt()).isNotNull();
    }

    // -----------------------------------------------------------------------
    // Requirement 11.2 — driver conflict
    // -----------------------------------------------------------------------

    /**
     * Validates: Requirements 11.2
     *
     * When the driver already has an assignment on the booking's service date,
     * a ConflictException must be thrown and no assignment must be persisted.
     */
    @Test
    void assign_driverHasConflictOnSameDate_throwsConflictException() {
        // Arrange
        when(bookingRepository.findById(BOOKING_ID)).thenReturn(Optional.of(confirmedBooking));
        when(driverRepository.findByIdWithLock(DRIVER_ID)).thenReturn(Optional.of(activeDriver));
        when(vehicleRepository.findByIdWithLock(VEHICLE_ID)).thenReturn(Optional.of(activeVehicle));
        when(bookingAssignmentRepository.existsConflictForDriver(DRIVER_ID, confirmedBooking.getServiceDate()))
                .thenReturn(true);

        // Act & Assert
        assertThatThrownBy(() -> assignmentService.assign(BOOKING_ID, DRIVER_ID, VEHICLE_ID, ADMIN_ID))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("Driver already assigned");

        verify(bookingAssignmentRepository, never()).save(any());
        verify(statusTransitionService, never()).transition(any(), any(), any());
    }

    // -----------------------------------------------------------------------
    // Requirement 11.3 — vehicle conflict
    // -----------------------------------------------------------------------

    /**
     * Validates: Requirements 11.3
     *
     * When the vehicle already has an assignment on the booking's service date,
     * a ConflictException must be thrown and no assignment must be persisted.
     */
    @Test
    void assign_vehicleHasConflictOnSameDate_throwsConflictException() {
        // Arrange
        when(bookingRepository.findById(BOOKING_ID)).thenReturn(Optional.of(confirmedBooking));
        when(driverRepository.findByIdWithLock(DRIVER_ID)).thenReturn(Optional.of(activeDriver));
        when(vehicleRepository.findByIdWithLock(VEHICLE_ID)).thenReturn(Optional.of(activeVehicle));
        when(bookingAssignmentRepository.existsConflictForDriver(DRIVER_ID, confirmedBooking.getServiceDate()))
                .thenReturn(false);
        when(bookingAssignmentRepository.existsConflictForVehicle(VEHICLE_ID, confirmedBooking.getServiceDate()))
                .thenReturn(true);

        // Act & Assert
        assertThatThrownBy(() -> assignmentService.assign(BOOKING_ID, DRIVER_ID, VEHICLE_ID, ADMIN_ID))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("Vehicle already assigned");

        verify(bookingAssignmentRepository, never()).save(any());
        verify(statusTransitionService, never()).transition(any(), any(), any());
    }

    // -----------------------------------------------------------------------
    // Requirement 11.6 — vehicle type mismatch
    // -----------------------------------------------------------------------

    /**
     * Validates: Requirements 11.6
     *
     * When the vehicle's type does not match the booking's requested vehicle type,
     * a VehicleTypeMismatchException must be thrown.
     */
    @Test
    void assign_vehicleTypeMismatch_throwsVehicleTypeMismatchException() {
        // Arrange: vehicle belongs to a different vehicle type
        VehicleType differentType = VehicleType.builder()
                .id(99L)
                .name("Large Truck")
                .baseFee(BigDecimal.valueOf(1500))
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        Vehicle wrongTypeVehicle = Vehicle.builder()
                .id(VEHICLE_ID)
                .registrationNumber("CA 999-999")
                .make("Mercedes")
                .model("Actros")
                .year((short) 2021)
                .vehicleType(differentType)
                .active(true)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        when(bookingRepository.findById(BOOKING_ID)).thenReturn(Optional.of(confirmedBooking));
        when(driverRepository.findByIdWithLock(DRIVER_ID)).thenReturn(Optional.of(activeDriver));
        when(vehicleRepository.findByIdWithLock(VEHICLE_ID)).thenReturn(Optional.of(wrongTypeVehicle));

        // Act & Assert
        assertThatThrownBy(() -> assignmentService.assign(BOOKING_ID, DRIVER_ID, VEHICLE_ID, ADMIN_ID))
                .isInstanceOf(VehicleTypeMismatchException.class);

        verify(bookingAssignmentRepository, never()).save(any());
        verify(statusTransitionService, never()).transition(any(), any(), any());
    }

    // -----------------------------------------------------------------------
    // Requirement 11.7 — inactive driver
    // -----------------------------------------------------------------------

    /**
     * Validates: Requirements 11.7
     *
     * When the specified driver is inactive, an InactiveEntityException must be thrown.
     */
    @Test
    void assign_inactiveDriver_throwsInactiveEntityException() {
        // Arrange
        Driver inactiveDriver = Driver.builder()
                .id(DRIVER_ID)
                .fullName("Inactive Driver")
                .phone("+27831111111")
                .licenceNumber("DL999999")
                .active(false)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        when(bookingRepository.findById(BOOKING_ID)).thenReturn(Optional.of(confirmedBooking));
        when(driverRepository.findByIdWithLock(DRIVER_ID)).thenReturn(Optional.of(inactiveDriver));
        when(vehicleRepository.findByIdWithLock(VEHICLE_ID)).thenReturn(Optional.of(activeVehicle));

        // Act & Assert
        assertThatThrownBy(() -> assignmentService.assign(BOOKING_ID, DRIVER_ID, VEHICLE_ID, ADMIN_ID))
                .isInstanceOf(InactiveEntityException.class)
                .hasMessageContaining("Driver");

        verify(bookingAssignmentRepository, never()).save(any());
        verify(statusTransitionService, never()).transition(any(), any(), any());
    }

    /**
     * Validates: Requirements 11.7
     *
     * When the specified vehicle is inactive, an InactiveEntityException must be thrown.
     */
    @Test
    void assign_inactiveVehicle_throwsInactiveEntityException() {
        // Arrange
        Vehicle inactiveVehicle = Vehicle.builder()
                .id(VEHICLE_ID)
                .registrationNumber("CA 000-000")
                .make("Ford")
                .model("Ranger")
                .year((short) 2019)
                .vehicleType(vehicleType)
                .active(false)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        when(bookingRepository.findById(BOOKING_ID)).thenReturn(Optional.of(confirmedBooking));
        when(driverRepository.findByIdWithLock(DRIVER_ID)).thenReturn(Optional.of(activeDriver));
        when(vehicleRepository.findByIdWithLock(VEHICLE_ID)).thenReturn(Optional.of(inactiveVehicle));

        // Act & Assert
        assertThatThrownBy(() -> assignmentService.assign(BOOKING_ID, DRIVER_ID, VEHICLE_ID, ADMIN_ID))
                .isInstanceOf(InactiveEntityException.class)
                .hasMessageContaining("Vehicle");

        verify(bookingAssignmentRepository, never()).save(any());
        verify(statusTransitionService, never()).transition(any(), any(), any());
    }

    // -----------------------------------------------------------------------
    // Requirement 11.5 — booking not in CONFIRMED status
    // -----------------------------------------------------------------------

    /**
     * Validates: Requirements 11.5
     *
     * When the booking is in PENDING status (not CONFIRMED), a ConflictException
     * must be thrown before any lock or conflict check is performed.
     */
    @Test
    void assign_bookingInPendingStatus_throwsConflictException() {
        // Arrange
        Booking pendingBooking = Booking.builder()
                .id(BOOKING_ID)
                .bookingReference("MTSA-24002")
                .status(BookingStatus.PENDING)
                .customerName("Bob Smith")
                .customerPhone("+27829999999")
                .customerEmail("bob@example.com")
                .vehicleType(vehicleType)
                .serviceDate(LocalDate.now().plusDays(1))
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        when(bookingRepository.findById(BOOKING_ID)).thenReturn(Optional.of(pendingBooking));

        // Act & Assert
        assertThatThrownBy(() -> assignmentService.assign(BOOKING_ID, DRIVER_ID, VEHICLE_ID, ADMIN_ID))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("CONFIRMED");

        verify(driverRepository, never()).findByIdWithLock(any());
        verify(vehicleRepository, never()).findByIdWithLock(any());
        verify(bookingAssignmentRepository, never()).save(any());
        verify(statusTransitionService, never()).transition(any(), any(), any());
    }

    /**
     * Validates: Requirements 11.5
     *
     * When the booking is in ASSIGNED status (already assigned), a ConflictException
     * must be thrown.
     */
    @Test
    void assign_bookingAlreadyAssigned_throwsConflictException() {
        // Arrange
        Booking assignedBooking = Booking.builder()
                .id(BOOKING_ID)
                .bookingReference("MTSA-24003")
                .status(BookingStatus.ASSIGNED)
                .customerName("Alice Jones")
                .customerPhone("+27828888888")
                .customerEmail("alice@example.com")
                .vehicleType(vehicleType)
                .serviceDate(LocalDate.now().plusDays(3))
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        when(bookingRepository.findById(BOOKING_ID)).thenReturn(Optional.of(assignedBooking));

        // Act & Assert
        assertThatThrownBy(() -> assignmentService.assign(BOOKING_ID, DRIVER_ID, VEHICLE_ID, ADMIN_ID))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("CONFIRMED");

        verify(bookingAssignmentRepository, never()).save(any());
        verify(statusTransitionService, never()).transition(any(), any(), any());
    }

    /**
     * Validates: Requirements 11.5
     *
     * When the booking is in QUOTED status (not yet confirmed), a ConflictException
     * must be thrown.
     */
    @Test
    void assign_bookingInQuotedStatus_throwsConflictException() {
        // Arrange
        Booking quotedBooking = Booking.builder()
                .id(BOOKING_ID)
                .bookingReference("MTSA-24004")
                .status(BookingStatus.QUOTED)
                .customerName("Carol White")
                .customerPhone("+27827777777")
                .customerEmail("carol@example.com")
                .vehicleType(vehicleType)
                .serviceDate(LocalDate.now().plusDays(5))
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        when(bookingRepository.findById(BOOKING_ID)).thenReturn(Optional.of(quotedBooking));

        // Act & Assert
        assertThatThrownBy(() -> assignmentService.assign(BOOKING_ID, DRIVER_ID, VEHICLE_ID, ADMIN_ID))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("CONFIRMED");

        verify(bookingAssignmentRepository, never()).save(any());
        verify(statusTransitionService, never()).transition(any(), any(), any());
    }

    // -----------------------------------------------------------------------
    // Booking not found
    // -----------------------------------------------------------------------

    /**
     * When the booking ID does not exist, a BookingNotFoundException must be thrown.
     */
    @Test
    void assign_bookingNotFound_throwsBookingNotFoundException() {
        // Arrange
        when(bookingRepository.findById(BOOKING_ID)).thenReturn(Optional.empty());

        // Act & Assert
        assertThatThrownBy(() -> assignmentService.assign(BOOKING_ID, DRIVER_ID, VEHICLE_ID, ADMIN_ID))
                .isInstanceOf(BookingNotFoundException.class);

        verify(driverRepository, never()).findByIdWithLock(any());
        verify(vehicleRepository, never()).findByIdWithLock(any());
        verify(bookingAssignmentRepository, never()).save(any());
        verify(statusTransitionService, never()).transition(any(), any(), any());
    }
}
