package com.moveandtransportsa.booking.service;

import com.moveandtransportsa.booking.domain.booking.Booking;
import com.moveandtransportsa.booking.domain.booking.BookingStatus;
import com.moveandtransportsa.booking.exception.BookingNotFoundException;
import com.moveandtransportsa.booking.exception.InvalidStatusTransitionException;
import com.moveandtransportsa.booking.repository.BookingRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link StatusTransitionService}.
 *
 * Covers:
 * - Requirement 10.1: Only allowed status transitions succeed.
 * - Requirement 10.2: Invalid transitions throw InvalidStatusTransitionException.
 * - Requirement 10.3: Status history is recorded on every transition.
 * - Requirement 10.4: Notification dispatched when booking transitions to QUOTED.
 * - Requirement 10.5: Notification dispatched when booking transitions to CONFIRMED.
 * - Requirement 10.6: Notification dispatched when booking transitions to ASSIGNED.
 * - Requirement 10.7: Notification dispatched when booking transitions to COMPLETED.
 */
@ExtendWith(MockitoExtension.class)
class StatusTransitionServiceTest {

    @Mock
    private BookingRepository bookingRepository;

    @Mock
    private StatusHistoryService statusHistoryService;

    @Mock
    private NotificationService notificationService;

    @InjectMocks
    private StatusTransitionService statusTransitionService;

    private static final Long BOOKING_ID = 1L;
    private static final Long ADMIN_USER_ID = 42L;

    private Booking buildBooking(BookingStatus status) {
        return Booking.builder()
                .id(BOOKING_ID)
                .bookingReference("MTSA-24001")
                .status(status)
                .customerName("Test Customer")
                .customerPhone("+27821234567")
                .customerEmail("test@example.com")
                .serviceDate(LocalDate.now().plusDays(3))
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
    }

    // -----------------------------------------------------------------------
    // Requirement 10.1 — valid transitions succeed
    // -----------------------------------------------------------------------

    /**
     * Validates: Requirements 10.1
     *
     * Provides all valid transitions defined by the booking lifecycle state machine.
     */
    static Stream<Arguments> validTransitions() {
        return Stream.of(
                Arguments.of(BookingStatus.PENDING,     BookingStatus.QUOTED),
                Arguments.of(BookingStatus.PENDING,     BookingStatus.CANCELLED),
                Arguments.of(BookingStatus.QUOTED,      BookingStatus.CONFIRMED),
                Arguments.of(BookingStatus.QUOTED,      BookingStatus.CANCELLED),
                Arguments.of(BookingStatus.CONFIRMED,   BookingStatus.ASSIGNED),
                Arguments.of(BookingStatus.CONFIRMED,   BookingStatus.CANCELLED),
                Arguments.of(BookingStatus.ASSIGNED,    BookingStatus.IN_PROGRESS),
                Arguments.of(BookingStatus.ASSIGNED,    BookingStatus.CANCELLED),
                Arguments.of(BookingStatus.IN_PROGRESS, BookingStatus.COMPLETED),
                Arguments.of(BookingStatus.IN_PROGRESS, BookingStatus.CANCELLED)
        );
    }

    @ParameterizedTest(name = "{0} -> {1} is a valid transition")
    @MethodSource("validTransitions")
    void transition_validTransition_updatesBookingStatus(BookingStatus from, BookingStatus to) {
        // Arrange
        Booking booking = buildBooking(from);
        when(bookingRepository.findById(BOOKING_ID)).thenReturn(Optional.of(booking));
        when(bookingRepository.save(any(Booking.class))).thenAnswer(inv -> inv.getArgument(0));

        // Act
        statusTransitionService.transition(BOOKING_ID, to, ADMIN_USER_ID);

        // Assert: booking status was updated and saved
        ArgumentCaptor<Booking> captor = ArgumentCaptor.forClass(Booking.class);
        verify(bookingRepository).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(to);
    }

    @ParameterizedTest(name = "{0} -> {1} records status history")
    @MethodSource("validTransitions")
    void transition_validTransition_appendsStatusHistory(BookingStatus from, BookingStatus to) {
        // Arrange
        Booking booking = buildBooking(from);
        when(bookingRepository.findById(BOOKING_ID)).thenReturn(Optional.of(booking));
        when(bookingRepository.save(any(Booking.class))).thenAnswer(inv -> inv.getArgument(0));

        // Act
        statusTransitionService.transition(BOOKING_ID, to, ADMIN_USER_ID);

        // Assert: history entry recorded with correct previous/new status and actor
        verify(statusHistoryService).appendStatusChange(
                eq(BOOKING_ID),
                eq(from),
                eq(to),
                eq("ADMIN"),
                eq(ADMIN_USER_ID)
        );
    }

    // -----------------------------------------------------------------------
    // Requirement 10.2 — invalid transitions throw InvalidStatusTransitionException
    // -----------------------------------------------------------------------

    /**
     * Validates: Requirements 10.2
     *
     * Provides representative invalid transitions that must be rejected.
     */
    static Stream<Arguments> invalidTransitions() {
        return Stream.of(
                // Cannot skip steps forward
                Arguments.of(BookingStatus.PENDING,     BookingStatus.CONFIRMED),
                Arguments.of(BookingStatus.PENDING,     BookingStatus.ASSIGNED),
                Arguments.of(BookingStatus.PENDING,     BookingStatus.IN_PROGRESS),
                Arguments.of(BookingStatus.PENDING,     BookingStatus.COMPLETED),
                Arguments.of(BookingStatus.QUOTED,      BookingStatus.ASSIGNED),
                Arguments.of(BookingStatus.QUOTED,      BookingStatus.IN_PROGRESS),
                Arguments.of(BookingStatus.QUOTED,      BookingStatus.COMPLETED),
                Arguments.of(BookingStatus.CONFIRMED,   BookingStatus.IN_PROGRESS),
                Arguments.of(BookingStatus.CONFIRMED,   BookingStatus.COMPLETED),
                Arguments.of(BookingStatus.ASSIGNED,    BookingStatus.COMPLETED),
                // Cannot go backwards
                Arguments.of(BookingStatus.QUOTED,      BookingStatus.PENDING),
                Arguments.of(BookingStatus.CONFIRMED,   BookingStatus.PENDING),
                Arguments.of(BookingStatus.CONFIRMED,   BookingStatus.QUOTED),
                Arguments.of(BookingStatus.IN_PROGRESS, BookingStatus.PENDING),
                Arguments.of(BookingStatus.IN_PROGRESS, BookingStatus.QUOTED),
                Arguments.of(BookingStatus.IN_PROGRESS, BookingStatus.CONFIRMED),
                Arguments.of(BookingStatus.IN_PROGRESS, BookingStatus.ASSIGNED),
                // Terminal states cannot transition to anything
                Arguments.of(BookingStatus.COMPLETED,   BookingStatus.CANCELLED),
                Arguments.of(BookingStatus.COMPLETED,   BookingStatus.PENDING),
                Arguments.of(BookingStatus.CANCELLED,   BookingStatus.PENDING),
                Arguments.of(BookingStatus.CANCELLED,   BookingStatus.QUOTED)
        );
    }

    @ParameterizedTest(name = "{0} -> {1} is an invalid transition")
    @MethodSource("invalidTransitions")
    void transition_invalidTransition_throwsInvalidStatusTransitionException(BookingStatus from, BookingStatus to) {
        // Arrange
        Booking booking = buildBooking(from);
        when(bookingRepository.findById(BOOKING_ID)).thenReturn(Optional.of(booking));

        // Act & Assert
        assertThatThrownBy(() -> statusTransitionService.transition(BOOKING_ID, to, ADMIN_USER_ID))
                .isInstanceOf(InvalidStatusTransitionException.class)
                .hasMessageContaining(from.name())
                .hasMessageContaining(to.name());
    }

    @ParameterizedTest(name = "invalid {0} -> {1}: booking is not saved")
    @MethodSource("invalidTransitions")
    void transition_invalidTransition_doesNotSaveBooking(BookingStatus from, BookingStatus to) {
        // Arrange
        Booking booking = buildBooking(from);
        when(bookingRepository.findById(BOOKING_ID)).thenReturn(Optional.of(booking));

        // Act
        try {
            statusTransitionService.transition(BOOKING_ID, to, ADMIN_USER_ID);
        } catch (InvalidStatusTransitionException ignored) {
            // expected
        }

        // Assert: booking was never persisted
        verify(bookingRepository, never()).save(any());
    }

    @ParameterizedTest(name = "invalid {0} -> {1}: history is not recorded")
    @MethodSource("invalidTransitions")
    void transition_invalidTransition_doesNotAppendHistory(BookingStatus from, BookingStatus to) {
        // Arrange
        Booking booking = buildBooking(from);
        when(bookingRepository.findById(BOOKING_ID)).thenReturn(Optional.of(booking));

        // Act
        try {
            statusTransitionService.transition(BOOKING_ID, to, ADMIN_USER_ID);
        } catch (InvalidStatusTransitionException ignored) {
            // expected
        }

        // Assert: no history entry was written
        verify(statusHistoryService, never()).appendStatusChange(any(), any(), any(), any(), any());
    }

    // -----------------------------------------------------------------------
    // Requirements 10.4–10.7 — notifications triggered for key statuses
    // -----------------------------------------------------------------------

    /**
     * Validates: Requirements 10.4
     *
     * When a booking transitions to QUOTED, a notification must be dispatched.
     */
    @Test
    void transition_toQuoted_dispatchesNotification() {
        // Arrange
        Booking booking = buildBooking(BookingStatus.PENDING);
        when(bookingRepository.findById(BOOKING_ID)).thenReturn(Optional.of(booking));
        when(bookingRepository.save(any(Booking.class))).thenAnswer(inv -> inv.getArgument(0));

        // Act
        statusTransitionService.transition(BOOKING_ID, BookingStatus.QUOTED, ADMIN_USER_ID);

        // Assert
        verify(notificationService).dispatch(BOOKING_ID, BookingStatus.QUOTED.name());
    }

    /**
     * Validates: Requirements 10.5
     *
     * When a booking transitions to CONFIRMED, a notification must be dispatched.
     */
    @Test
    void transition_toConfirmed_dispatchesNotification() {
        // Arrange
        Booking booking = buildBooking(BookingStatus.QUOTED);
        when(bookingRepository.findById(BOOKING_ID)).thenReturn(Optional.of(booking));
        when(bookingRepository.save(any(Booking.class))).thenAnswer(inv -> inv.getArgument(0));

        // Act
        statusTransitionService.transition(BOOKING_ID, BookingStatus.CONFIRMED, ADMIN_USER_ID);

        // Assert
        verify(notificationService).dispatch(BOOKING_ID, BookingStatus.CONFIRMED.name());
    }

    /**
     * Validates: Requirements 10.6
     *
     * When a booking transitions to ASSIGNED, a notification must be dispatched.
     */
    @Test
    void transition_toAssigned_dispatchesNotification() {
        // Arrange
        Booking booking = buildBooking(BookingStatus.CONFIRMED);
        when(bookingRepository.findById(BOOKING_ID)).thenReturn(Optional.of(booking));
        when(bookingRepository.save(any(Booking.class))).thenAnswer(inv -> inv.getArgument(0));

        // Act
        statusTransitionService.transition(BOOKING_ID, BookingStatus.ASSIGNED, ADMIN_USER_ID);

        // Assert
        verify(notificationService).dispatch(BOOKING_ID, BookingStatus.ASSIGNED.name());
    }

    /**
     * Validates: Requirements 10.7
     *
     * When a booking transitions to COMPLETED, a notification must be dispatched.
     */
    @Test
    void transition_toCompleted_dispatchesNotification() {
        // Arrange
        Booking booking = buildBooking(BookingStatus.IN_PROGRESS);
        when(bookingRepository.findById(BOOKING_ID)).thenReturn(Optional.of(booking));
        when(bookingRepository.save(any(Booking.class))).thenAnswer(inv -> inv.getArgument(0));

        // Act
        statusTransitionService.transition(BOOKING_ID, BookingStatus.COMPLETED, ADMIN_USER_ID);

        // Assert
        verify(notificationService).dispatch(BOOKING_ID, BookingStatus.COMPLETED.name());
    }

    /**
     * Validates: Requirements 10.1
     *
     * When a booking transitions to CANCELLED, no notification should be dispatched
     * (CANCELLED is not in the notification trigger set).
     */
    @Test
    void transition_toCancelled_doesNotDispatchNotification() {
        // Arrange
        Booking booking = buildBooking(BookingStatus.PENDING);
        when(bookingRepository.findById(BOOKING_ID)).thenReturn(Optional.of(booking));
        when(bookingRepository.save(any(Booking.class))).thenAnswer(inv -> inv.getArgument(0));

        // Act
        statusTransitionService.transition(BOOKING_ID, BookingStatus.CANCELLED, ADMIN_USER_ID);

        // Assert: no notification for CANCELLED
        verify(notificationService, never()).dispatch(any(), any());
    }

    /**
     * Validates: Requirements 10.1
     *
     * When a booking transitions to IN_PROGRESS, no notification should be dispatched
     * (IN_PROGRESS is not in the notification trigger set).
     */
    @Test
    void transition_toInProgress_doesNotDispatchNotification() {
        // Arrange
        Booking booking = buildBooking(BookingStatus.ASSIGNED);
        when(bookingRepository.findById(BOOKING_ID)).thenReturn(Optional.of(booking));
        when(bookingRepository.save(any(Booking.class))).thenAnswer(inv -> inv.getArgument(0));

        // Act
        statusTransitionService.transition(BOOKING_ID, BookingStatus.IN_PROGRESS, ADMIN_USER_ID);

        // Assert: no notification for IN_PROGRESS
        verify(notificationService, never()).dispatch(any(), any());
    }

    // -----------------------------------------------------------------------
    // Booking not found
    // -----------------------------------------------------------------------

    @Test
    void transition_bookingNotFound_throwsBookingNotFoundException() {
        // Arrange
        when(bookingRepository.findById(BOOKING_ID)).thenReturn(Optional.empty());

        // Act & Assert
        assertThatThrownBy(() ->
                statusTransitionService.transition(BOOKING_ID, BookingStatus.QUOTED, ADMIN_USER_ID))
                .isInstanceOf(BookingNotFoundException.class)
                .hasMessageContaining(String.valueOf(BOOKING_ID));

        verify(bookingRepository, never()).save(any());
        verify(statusHistoryService, never()).appendStatusChange(any(), any(), any(), any(), any());
        verify(notificationService, never()).dispatch(any(), any());
    }

    // -----------------------------------------------------------------------
    // updatedAt is refreshed on successful transition
    // -----------------------------------------------------------------------

    @Test
    void transition_validTransition_refreshesUpdatedAt() {
        // Arrange
        Booking booking = buildBooking(BookingStatus.PENDING);
        Instant originalUpdatedAt = booking.getUpdatedAt();
        when(bookingRepository.findById(BOOKING_ID)).thenReturn(Optional.of(booking));
        when(bookingRepository.save(any(Booking.class))).thenAnswer(inv -> inv.getArgument(0));

        // Act
        statusTransitionService.transition(BOOKING_ID, BookingStatus.QUOTED, ADMIN_USER_ID);

        // Assert: updatedAt was set to a time >= the original
        assertThat(booking.getUpdatedAt()).isAfterOrEqualTo(originalUpdatedAt);
    }
}
