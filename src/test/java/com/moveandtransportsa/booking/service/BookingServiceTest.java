package com.moveandtransportsa.booking.service;

import com.moveandtransportsa.booking.domain.booking.AddressType;
import com.moveandtransportsa.booking.domain.booking.Booking;
import com.moveandtransportsa.booking.domain.booking.BookingAddress;
import com.moveandtransportsa.booking.domain.booking.BookingStatus;
import com.moveandtransportsa.booking.domain.vehicle.VehicleType;
import com.moveandtransportsa.booking.dto.response.PublicBookingResponse;
import com.moveandtransportsa.booking.exception.BookingNotFoundException;
import com.moveandtransportsa.booking.exception.ConflictException;
import com.moveandtransportsa.booking.repository.BookingAddressRepository;
import com.moveandtransportsa.booking.repository.BookingRepository;
import com.moveandtransportsa.booking.repository.VehicleTypeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link BookingService} public operations.
 *
 * All dependencies are mocked — no database or Spring context required.
 *
 * Requirements covered: 3.3, 4.1, 4.2
 */
@ExtendWith(MockitoExtension.class)
class BookingServiceTest {

    @Mock
    private BookingRepository bookingRepository;

    @Mock
    private BookingAddressRepository bookingAddressRepository;

    @Mock
    private VehicleTypeRepository vehicleTypeRepository;

    @Mock
    private BookingReferenceGenerator bookingReferenceGenerator;

    @Mock
    private StatusHistoryService statusHistoryService;

    @Mock
    private NotificationService notificationService;

    private BookingService bookingService;

    // -----------------------------------------------------------------------
    // Shared test fixtures
    // -----------------------------------------------------------------------

    private static final String REFERENCE = "MTSA-24001";
    private static final Long BOOKING_ID = 42L;

    private VehicleType vehicleType;
    private Booking pendingBooking;

    @BeforeEach
    void setUp() {
        bookingService = new BookingService(
                bookingRepository,
                bookingAddressRepository,
                vehicleTypeRepository,
                bookingReferenceGenerator,
                statusHistoryService,
                notificationService
        );

        vehicleType = VehicleType.builder()
                .id(1L)
                .name("Bakkie")
                .description("Small bakkie for light loads")
                .loadCapacity("1 ton")
                .baseFee(new BigDecimal("500.00"))
                .active(true)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        pendingBooking = Booking.builder()
                .id(BOOKING_ID)
                .bookingReference(REFERENCE)
                .status(BookingStatus.PENDING)
                .customerName("John Doe")
                .customerPhone("+27821234567")
                .customerEmail("john@example.com")
                .vehicleType(vehicleType)
                .serviceDate(LocalDate.now().plusDays(3))
                .serviceTime(LocalTime.of(9, 0))
                .helpers((short) 1)
                .stairs(false)
                .estimatedPrice(new BigDecimal("850.00"))
                .finalPrice(null)
                .adminNotes("Some internal admin note")
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
    }

    // -----------------------------------------------------------------------
    // getByReference — correct fields returned (Requirement 3.1)
    // -----------------------------------------------------------------------

    @Test
    void getByReference_existingReference_returnsCorrectFields() {
        // Arrange
        BookingAddress pickup = BookingAddress.builder()
                .id(10L)
                .booking(pendingBooking)
                .addressType(AddressType.PICKUP)
                .addressLine("123 Main St")
                .suburb("Sandton")
                .city("Johannesburg")
                .sortOrder((short) 0)
                .createdAt(Instant.now())
                .build();

        BookingAddress dropoff = BookingAddress.builder()
                .id(11L)
                .booking(pendingBooking)
                .addressType(AddressType.DROPOFF)
                .addressLine("456 Oak Ave")
                .suburb("Soweto")
                .city("Johannesburg")
                .sortOrder((short) 0)
                .createdAt(Instant.now())
                .build();

        when(bookingRepository.findByBookingReference(REFERENCE)).thenReturn(Optional.of(pendingBooking));
        when(bookingAddressRepository.findByBookingIdAndAddressType(BOOKING_ID, AddressType.PICKUP))
                .thenReturn(List.of(pickup));
        when(bookingAddressRepository.findByBookingIdAndAddressType(BOOKING_ID, AddressType.DROPOFF))
                .thenReturn(List.of(dropoff));

        // Act
        PublicBookingResponse response = bookingService.getByReference(REFERENCE);

        // Assert — expected fields are present
        assertThat(response.bookingReference()).isEqualTo(REFERENCE);
        assertThat(response.status()).isEqualTo("PENDING");
        assertThat(response.vehicleType()).isEqualTo("Bakkie");
        assertThat(response.serviceDate()).isEqualTo(pendingBooking.getServiceDate());
        assertThat(response.serviceTime()).isEqualTo(pendingBooking.getServiceTime());
        assertThat(response.estimatedPrice()).isEqualByComparingTo(new BigDecimal("850.00"));
        assertThat(response.finalPrice()).isNull();

        // Assert — addresses are mapped
        assertThat(response.pickupAddresses()).hasSize(1);
        assertThat(response.pickupAddresses().get(0).addressLine()).isEqualTo("123 Main St");
        assertThat(response.pickupAddresses().get(0).suburb()).isEqualTo("Sandton");

        assertThat(response.dropoffAddresses()).hasSize(1);
        assertThat(response.dropoffAddresses().get(0).addressLine()).isEqualTo("456 Oak Ave");
        assertThat(response.dropoffAddresses().get(0).suburb()).isEqualTo("Soweto");
    }

    // -----------------------------------------------------------------------
    // getByReference — sensitive data excluded (Requirement 3.3)
    // -----------------------------------------------------------------------

    @Test
    void getByReference_responseDoesNotExposeInternalId() {
        // Arrange
        when(bookingRepository.findByBookingReference(REFERENCE)).thenReturn(Optional.of(pendingBooking));
        when(bookingAddressRepository.findByBookingIdAndAddressType(anyLong(), any())).thenReturn(List.of());

        // Act
        PublicBookingResponse response = bookingService.getByReference(REFERENCE);

        // Assert — PublicBookingResponse record has no id, adminNotes, or driver fields
        // Verify by checking the record's component names via reflection
        var components = PublicBookingResponse.class.getRecordComponents();
        var componentNames = java.util.Arrays.stream(components)
                .map(java.lang.reflect.RecordComponent::getName)
                .toList();

        assertThat(componentNames).doesNotContain("id");
        assertThat(componentNames).doesNotContain("adminNotes");
        assertThat(componentNames).doesNotContain("driverName");
        assertThat(componentNames).doesNotContain("driverPhone");
        assertThat(componentNames).doesNotContain("driverLicence");
    }

    @Test
    void getByReference_responseDoesNotExposeAdminNotes() {
        // Arrange — booking has admin notes set
        when(bookingRepository.findByBookingReference(REFERENCE)).thenReturn(Optional.of(pendingBooking));
        when(bookingAddressRepository.findByBookingIdAndAddressType(anyLong(), any())).thenReturn(List.of());

        // Act
        PublicBookingResponse response = bookingService.getByReference(REFERENCE);

        // Assert — the response object itself carries no admin notes value
        // (the record simply has no such field)
        assertThat(response).isNotNull();
        // Confirm adminNotes is not part of the response contract
        assertThat(response.toString()).doesNotContain("Some internal admin note");
    }

    // -----------------------------------------------------------------------
    // getByReference — 404 for unknown reference (Requirement 3.2)
    // -----------------------------------------------------------------------

    @Test
    void getByReference_unknownReference_throwsBookingNotFoundException() {
        // Arrange
        when(bookingRepository.findByBookingReference("MTSA-99999")).thenReturn(Optional.empty());

        // Act & Assert
        assertThatThrownBy(() -> bookingService.getByReference("MTSA-99999"))
                .isInstanceOf(BookingNotFoundException.class)
                .hasMessageContaining("MTSA-99999");
    }

    // -----------------------------------------------------------------------
    // cancelByReference — succeeds for PENDING (Requirement 4.1)
    // -----------------------------------------------------------------------

    @Test
    void cancelByReference_pendingBooking_updatesStatusToCancelled() {
        // Arrange
        when(bookingRepository.findByBookingReference(REFERENCE)).thenReturn(Optional.of(pendingBooking));
        when(bookingRepository.save(any(Booking.class))).thenAnswer(inv -> inv.getArgument(0));
        doNothing().when(statusHistoryService).appendStatusChange(anyLong(), any(), any(), anyString(), any());
        doNothing().when(notificationService).dispatch(anyLong(), anyString());

        // Act
        bookingService.cancelByReference(REFERENCE);

        // Assert — status updated to CANCELLED
        assertThat(pendingBooking.getStatus()).isEqualTo(BookingStatus.CANCELLED);
        verify(bookingRepository).save(pendingBooking);
    }

    @Test
    void cancelByReference_pendingBooking_appendsStatusHistoryEntry() {
        // Arrange
        when(bookingRepository.findByBookingReference(REFERENCE)).thenReturn(Optional.of(pendingBooking));
        when(bookingRepository.save(any(Booking.class))).thenAnswer(inv -> inv.getArgument(0));

        // Act
        bookingService.cancelByReference(REFERENCE);

        // Assert — history entry recorded with CUSTOMER actor
        verify(statusHistoryService).appendStatusChange(
                eq(BOOKING_ID),
                eq(BookingStatus.PENDING),
                eq(BookingStatus.CANCELLED),
                eq("CUSTOMER"),
                eq(null)
        );
    }

    @Test
    void cancelByReference_pendingBooking_triggersNotification() {
        // Arrange
        when(bookingRepository.findByBookingReference(REFERENCE)).thenReturn(Optional.of(pendingBooking));
        when(bookingRepository.save(any(Booking.class))).thenAnswer(inv -> inv.getArgument(0));

        // Act
        bookingService.cancelByReference(REFERENCE);

        // Assert — cancellation notification dispatched
        verify(notificationService).dispatch(eq(BOOKING_ID), eq("CANCELLED"));
    }

    // -----------------------------------------------------------------------
    // cancelByReference — succeeds for QUOTED (Requirement 4.1)
    // -----------------------------------------------------------------------

    @Test
    void cancelByReference_quotedBooking_updatesStatusToCancelled() {
        // Arrange
        pendingBooking.setStatus(BookingStatus.QUOTED);
        when(bookingRepository.findByBookingReference(REFERENCE)).thenReturn(Optional.of(pendingBooking));
        when(bookingRepository.save(any(Booking.class))).thenAnswer(inv -> inv.getArgument(0));

        // Act
        bookingService.cancelByReference(REFERENCE);

        // Assert
        assertThat(pendingBooking.getStatus()).isEqualTo(BookingStatus.CANCELLED);
        verify(statusHistoryService).appendStatusChange(
                eq(BOOKING_ID),
                eq(BookingStatus.QUOTED),
                eq(BookingStatus.CANCELLED),
                eq("CUSTOMER"),
                eq(null)
        );
    }

    // -----------------------------------------------------------------------
    // cancelByReference — succeeds for CONFIRMED (Requirement 4.1)
    // -----------------------------------------------------------------------

    @Test
    void cancelByReference_confirmedBooking_updatesStatusToCancelled() {
        // Arrange
        pendingBooking.setStatus(BookingStatus.CONFIRMED);
        when(bookingRepository.findByBookingReference(REFERENCE)).thenReturn(Optional.of(pendingBooking));
        when(bookingRepository.save(any(Booking.class))).thenAnswer(inv -> inv.getArgument(0));

        // Act
        bookingService.cancelByReference(REFERENCE);

        // Assert
        assertThat(pendingBooking.getStatus()).isEqualTo(BookingStatus.CANCELLED);
        verify(statusHistoryService).appendStatusChange(
                eq(BOOKING_ID),
                eq(BookingStatus.CONFIRMED),
                eq(BookingStatus.CANCELLED),
                eq("CUSTOMER"),
                eq(null)
        );
    }

    // -----------------------------------------------------------------------
    // cancelByReference — 409 for ASSIGNED (Requirement 4.2)
    // -----------------------------------------------------------------------

    @Test
    void cancelByReference_assignedBooking_throwsConflictException() {
        // Arrange
        pendingBooking.setStatus(BookingStatus.ASSIGNED);
        when(bookingRepository.findByBookingReference(REFERENCE)).thenReturn(Optional.of(pendingBooking));

        // Act & Assert
        assertThatThrownBy(() -> bookingService.cancelByReference(REFERENCE))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("ASSIGNED");

        // No status change or notification should occur
        verify(bookingRepository, never()).save(any());
        verify(statusHistoryService, never()).appendStatusChange(any(), any(), any(), any(), any());
        verify(notificationService, never()).dispatch(any(), any());
    }

    // -----------------------------------------------------------------------
    // cancelByReference — 409 for IN_PROGRESS (Requirement 4.2)
    // -----------------------------------------------------------------------

    @Test
    void cancelByReference_inProgressBooking_throwsConflictException() {
        // Arrange
        pendingBooking.setStatus(BookingStatus.IN_PROGRESS);
        when(bookingRepository.findByBookingReference(REFERENCE)).thenReturn(Optional.of(pendingBooking));

        // Act & Assert
        assertThatThrownBy(() -> bookingService.cancelByReference(REFERENCE))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("IN_PROGRESS");

        verify(bookingRepository, never()).save(any());
        verify(statusHistoryService, never()).appendStatusChange(any(), any(), any(), any(), any());
        verify(notificationService, never()).dispatch(any(), any());
    }

    // -----------------------------------------------------------------------
    // cancelByReference — 409 for COMPLETED (Requirement 4.2)
    // -----------------------------------------------------------------------

    @Test
    void cancelByReference_completedBooking_throwsConflictException() {
        // Arrange
        pendingBooking.setStatus(BookingStatus.COMPLETED);
        when(bookingRepository.findByBookingReference(REFERENCE)).thenReturn(Optional.of(pendingBooking));

        // Act & Assert
        assertThatThrownBy(() -> bookingService.cancelByReference(REFERENCE))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("COMPLETED");

        verify(bookingRepository, never()).save(any());
        verify(statusHistoryService, never()).appendStatusChange(any(), any(), any(), any(), any());
        verify(notificationService, never()).dispatch(any(), any());
    }

    // -----------------------------------------------------------------------
    // cancelByReference — 409 for CANCELLED (already cancelled)
    // -----------------------------------------------------------------------

    @Test
    void cancelByReference_alreadyCancelledBooking_throwsConflictException() {
        // Arrange
        pendingBooking.setStatus(BookingStatus.CANCELLED);
        when(bookingRepository.findByBookingReference(REFERENCE)).thenReturn(Optional.of(pendingBooking));

        // Act & Assert
        assertThatThrownBy(() -> bookingService.cancelByReference(REFERENCE))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("CANCELLED");
    }

    // -----------------------------------------------------------------------
    // cancelByReference — 404 for unknown reference
    // -----------------------------------------------------------------------

    @Test
    void cancelByReference_unknownReference_throwsBookingNotFoundException() {
        // Arrange
        when(bookingRepository.findByBookingReference("MTSA-99999")).thenReturn(Optional.empty());

        // Act & Assert
        assertThatThrownBy(() -> bookingService.cancelByReference("MTSA-99999"))
                .isInstanceOf(BookingNotFoundException.class)
                .hasMessageContaining("MTSA-99999");
    }

    // -----------------------------------------------------------------------
    // cancelByReference — parameterised: all non-cancellable statuses (Requirement 4.2)
    // -----------------------------------------------------------------------

    @ParameterizedTest
    @EnumSource(value = BookingStatus.class, names = {"ASSIGNED", "IN_PROGRESS", "COMPLETED"})
    void cancelByReference_nonCancellableStatus_throwsConflictException(BookingStatus status) {
        // Arrange
        pendingBooking.setStatus(status);
        when(bookingRepository.findByBookingReference(REFERENCE)).thenReturn(Optional.of(pendingBooking));

        // Act & Assert
        assertThatThrownBy(() -> bookingService.cancelByReference(REFERENCE))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining(status.name());
    }

    // -----------------------------------------------------------------------
    // cancelByReference — parameterised: all cancellable statuses (Requirement 4.1)
    // -----------------------------------------------------------------------

    @ParameterizedTest
    @EnumSource(value = BookingStatus.class, names = {"PENDING", "QUOTED", "CONFIRMED"})
    void cancelByReference_cancellableStatus_succeeds(BookingStatus status) {
        // Arrange
        pendingBooking.setStatus(status);
        when(bookingRepository.findByBookingReference(REFERENCE)).thenReturn(Optional.of(pendingBooking));
        when(bookingRepository.save(any(Booking.class))).thenAnswer(inv -> inv.getArgument(0));

        // Act — should not throw
        bookingService.cancelByReference(REFERENCE);

        // Assert
        assertThat(pendingBooking.getStatus()).isEqualTo(BookingStatus.CANCELLED);
        verify(bookingRepository).save(pendingBooking);
        verify(notificationService).dispatch(eq(BOOKING_ID), eq("CANCELLED"));
    }
}
