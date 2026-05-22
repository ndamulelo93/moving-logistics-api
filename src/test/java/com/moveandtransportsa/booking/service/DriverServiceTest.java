package com.moveandtransportsa.booking.service;

import com.moveandtransportsa.booking.domain.booking.Booking;
import com.moveandtransportsa.booking.domain.booking.BookingAssignment;
import com.moveandtransportsa.booking.domain.driver.Driver;
import com.moveandtransportsa.booking.dto.request.CreateDriverRequest;
import com.moveandtransportsa.booking.dto.response.DriverResponse;
import com.moveandtransportsa.booking.exception.BookingNotFoundException;
import com.moveandtransportsa.booking.exception.ConflictException;
import com.moveandtransportsa.booking.repository.BookingAssignmentRepository;
import com.moveandtransportsa.booking.repository.DriverRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

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
 * Unit tests for {@link DriverService}.
 *
 * Covers:
 * - Requirement 12.2: Driver creation persists all required fields correctly.
 * - Requirement 25.3: Deactivation is blocked when the driver has future assignments.
 */
@ExtendWith(MockitoExtension.class)
class DriverServiceTest {

    @Mock
    private DriverRepository driverRepository;

    @Mock
    private BookingAssignmentRepository bookingAssignmentRepository;

    @InjectMocks
    private DriverService driverService;

    private static final Long DRIVER_ID = 1L;

    private Driver activeDriver;

    @BeforeEach
    void setUp() {
        Instant now = Instant.now();
        activeDriver = Driver.builder()
                .id(DRIVER_ID)
                .fullName("John Doe")
                .phone("+27821234567")
                .licenceNumber("LIC-001")
                .active(true)
                .createdAt(now)
                .updatedAt(now)
                .build();
    }

    // -----------------------------------------------------------------------
    // Requirement 12.2 — create: valid data persists correctly
    // -----------------------------------------------------------------------

    /**
     * Validates: Requirements 12.2
     *
     * When a CreateDriverRequest with valid data is submitted, the service must
     * persist a Driver entity with all fields mapped correctly and return a
     * DriverResponse reflecting those values.
     */
    @Test
    void create_withValidData_persistsAllFieldsCorrectly() {
        // Arrange
        CreateDriverRequest request = new CreateDriverRequest(
                "Jane Smith",
                "+27831234567",
                "LIC-002",
                true
        );

        Driver savedDriver = Driver.builder()
                .id(2L)
                .fullName("Jane Smith")
                .phone("+27831234567")
                .licenceNumber("LIC-002")
                .active(true)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        when(driverRepository.save(any(Driver.class))).thenReturn(savedDriver);

        // Act
        DriverResponse response = driverService.create(request);

        // Assert: response fields match the request
        assertThat(response.id()).isEqualTo(2L);
        assertThat(response.fullName()).isEqualTo("Jane Smith");
        assertThat(response.phone()).isEqualTo("+27831234567");
        assertThat(response.licenceNumber()).isEqualTo("LIC-002");
        assertThat(response.active()).isTrue();
    }

    @Test
    void create_withValidData_savesDriverWithCorrectFields() {
        // Arrange
        CreateDriverRequest request = new CreateDriverRequest(
                "John Driver",
                "0721234567",
                "LIC-003",
                true
        );

        when(driverRepository.save(any(Driver.class))).thenAnswer(inv -> {
            Driver d = inv.getArgument(0);
            // Simulate DB assigning an ID
            return Driver.builder()
                    .id(3L)
                    .fullName(d.getFullName())
                    .phone(d.getPhone())
                    .licenceNumber(d.getLicenceNumber())
                    .active(d.isActive())
                    .createdAt(d.getCreatedAt())
                    .updatedAt(d.getUpdatedAt())
                    .build();
        });

        // Act
        driverService.create(request);

        // Assert: the entity passed to save has the correct field values
        ArgumentCaptor<Driver> captor = ArgumentCaptor.forClass(Driver.class);
        verify(driverRepository).save(captor.capture());

        Driver persisted = captor.getValue();
        assertThat(persisted.getFullName()).isEqualTo("John Driver");
        assertThat(persisted.getPhone()).isEqualTo("0721234567");
        assertThat(persisted.getLicenceNumber()).isEqualTo("LIC-003");
        assertThat(persisted.isActive()).isTrue();
        assertThat(persisted.getCreatedAt()).isNotNull();
        assertThat(persisted.getUpdatedAt()).isNotNull();
    }

    @Test
    void create_withActiveSetToFalse_persistsInactiveDriver() {
        // Arrange
        CreateDriverRequest request = new CreateDriverRequest(
                "Inactive Driver",
                "+27841234567",
                "LIC-004",
                false
        );

        Driver savedDriver = Driver.builder()
                .id(4L)
                .fullName("Inactive Driver")
                .phone("+27841234567")
                .licenceNumber("LIC-004")
                .active(false)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        when(driverRepository.save(any(Driver.class))).thenReturn(savedDriver);

        // Act
        DriverResponse response = driverService.create(request);

        // Assert
        assertThat(response.active()).isFalse();
    }

    @Test
    void create_setsCreatedAtAndUpdatedAt() {
        // Arrange
        CreateDriverRequest request = new CreateDriverRequest(
                "Timestamp Driver",
                "+27851234567",
                "LIC-005",
                true
        );

        when(driverRepository.save(any(Driver.class))).thenAnswer(inv -> inv.getArgument(0));

        // Act
        driverService.create(request);

        // Assert: timestamps are set on the entity before save
        ArgumentCaptor<Driver> captor = ArgumentCaptor.forClass(Driver.class);
        verify(driverRepository).save(captor.capture());

        Driver persisted = captor.getValue();
        assertThat(persisted.getCreatedAt()).isNotNull();
        assertThat(persisted.getUpdatedAt()).isNotNull();
        // createdAt and updatedAt should be equal at creation time
        assertThat(persisted.getCreatedAt()).isEqualTo(persisted.getUpdatedAt());
    }

    // -----------------------------------------------------------------------
    // Requirement 25.3 — deactivate: blocked when future assignment exists
    // -----------------------------------------------------------------------

    /**
     * Validates: Requirements 25.3
     *
     * When a driver has at least one future booking assignment, deactivation
     * must be rejected with a ConflictException that includes the booking reference.
     */
    @Test
    void deactivate_withFutureAssignment_throwsConflictException() {
        // Arrange: driver exists and has one future assignment
        Booking futureBooking = Booking.builder()
                .id(100L)
                .bookingReference("MTSA-24001")
                .serviceDate(LocalDate.now().plusDays(3))
                .build();

        BookingAssignment futureAssignment = BookingAssignment.builder()
                .id(200L)
                .booking(futureBooking)
                .driver(activeDriver)
                .assignedAt(Instant.now())
                .build();

        when(driverRepository.findById(DRIVER_ID)).thenReturn(Optional.of(activeDriver));
        when(bookingAssignmentRepository.findFutureAssignmentsByDriver(eq(DRIVER_ID), any(LocalDate.class)))
                .thenReturn(List.of(futureAssignment));

        // Act & Assert
        assertThatThrownBy(() -> driverService.deactivate(DRIVER_ID))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("MTSA-24001");

        // Driver must NOT have been saved as inactive
        verify(driverRepository, never()).save(any());
    }

    @Test
    void deactivate_withMultipleFutureAssignments_conflictMessageContainsAllBookingRefs() {
        // Arrange: driver has two future assignments
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
                .driver(activeDriver)
                .assignedAt(Instant.now())
                .build();

        BookingAssignment assignment2 = BookingAssignment.builder()
                .id(202L)
                .booking(booking2)
                .driver(activeDriver)
                .assignedAt(Instant.now())
                .build();

        when(driverRepository.findById(DRIVER_ID)).thenReturn(Optional.of(activeDriver));
        when(bookingAssignmentRepository.findFutureAssignmentsByDriver(eq(DRIVER_ID), any(LocalDate.class)))
                .thenReturn(List.of(assignment1, assignment2));

        // Act & Assert
        assertThatThrownBy(() -> driverService.deactivate(DRIVER_ID))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("MTSA-24001")
                .hasMessageContaining("MTSA-24002");

        verify(driverRepository, never()).save(any());
    }

    // -----------------------------------------------------------------------
    // Deactivation succeeds when no future assignments exist
    // -----------------------------------------------------------------------

    @Test
    void deactivate_withNoFutureAssignments_setsActiveToFalse() {
        // Arrange: driver exists and has no future assignments
        when(driverRepository.findById(DRIVER_ID)).thenReturn(Optional.of(activeDriver));
        when(bookingAssignmentRepository.findFutureAssignmentsByDriver(eq(DRIVER_ID), any(LocalDate.class)))
                .thenReturn(Collections.emptyList());
        when(driverRepository.save(any(Driver.class))).thenAnswer(inv -> inv.getArgument(0));

        // Act
        driverService.deactivate(DRIVER_ID);

        // Assert: driver was saved with active=false
        verify(driverRepository).save(argThat(d -> !d.isActive()));
        assertThat(activeDriver.isActive()).isFalse();
    }

    @Test
    void deactivate_withNoFutureAssignments_updatesUpdatedAt() {
        // Arrange
        Instant beforeDeactivation = activeDriver.getUpdatedAt();

        when(driverRepository.findById(DRIVER_ID)).thenReturn(Optional.of(activeDriver));
        when(bookingAssignmentRepository.findFutureAssignmentsByDriver(eq(DRIVER_ID), any(LocalDate.class)))
                .thenReturn(Collections.emptyList());
        when(driverRepository.save(any(Driver.class))).thenAnswer(inv -> inv.getArgument(0));

        // Act
        driverService.deactivate(DRIVER_ID);

        // Assert: updatedAt was refreshed
        assertThat(activeDriver.getUpdatedAt()).isAfterOrEqualTo(beforeDeactivation);
    }

    // -----------------------------------------------------------------------
    // Deactivation of non-existent driver
    // -----------------------------------------------------------------------

    @Test
    void deactivate_driverNotFound_throwsBookingNotFoundException() {
        // Arrange: no driver with this ID
        when(driverRepository.findById(DRIVER_ID)).thenReturn(Optional.empty());

        // Act & Assert
        assertThatThrownBy(() -> driverService.deactivate(DRIVER_ID))
                .isInstanceOf(BookingNotFoundException.class)
                .hasMessageContaining(String.valueOf(DRIVER_ID));

        verify(bookingAssignmentRepository, never()).findFutureAssignmentsByDriver(any(), any());
        verify(driverRepository, never()).save(any());
    }

    // -----------------------------------------------------------------------
    // Future assignment check uses today's date as the cutoff
    // -----------------------------------------------------------------------

    @Test
    void deactivate_queriesFutureAssignmentsWithTodayAsCutoff() {
        // Arrange
        when(driverRepository.findById(DRIVER_ID)).thenReturn(Optional.of(activeDriver));
        when(bookingAssignmentRepository.findFutureAssignmentsByDriver(eq(DRIVER_ID), any(LocalDate.class)))
                .thenReturn(Collections.emptyList());
        when(driverRepository.save(any(Driver.class))).thenAnswer(inv -> inv.getArgument(0));

        // Act
        driverService.deactivate(DRIVER_ID);

        // Assert: the date passed to the repository is today
        verify(bookingAssignmentRepository).findFutureAssignmentsByDriver(
                eq(DRIVER_ID),
                argThat(date -> !date.isAfter(LocalDate.now()) && !date.isBefore(LocalDate.now()))
        );
    }
}
