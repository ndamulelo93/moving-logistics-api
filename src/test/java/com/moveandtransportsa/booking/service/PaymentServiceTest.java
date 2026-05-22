package com.moveandtransportsa.booking.service;

import com.moveandtransportsa.booking.domain.auth.AdminRole;
import com.moveandtransportsa.booking.domain.auth.AdminUser;
import com.moveandtransportsa.booking.domain.booking.Booking;
import com.moveandtransportsa.booking.domain.booking.BookingStatus;
import com.moveandtransportsa.booking.domain.payment.Payment;
import com.moveandtransportsa.booking.domain.payment.PaymentMethod;
import com.moveandtransportsa.booking.domain.vehicle.VehicleType;
import com.moveandtransportsa.booking.dto.request.CapturePaymentRequest;
import com.moveandtransportsa.booking.dto.response.PaymentResponse;
import com.moveandtransportsa.booking.exception.BookingNotFoundException;
import com.moveandtransportsa.booking.exception.ConflictException;
import com.moveandtransportsa.booking.repository.AdminUserRepository;
import com.moveandtransportsa.booking.repository.BookingRepository;
import com.moveandtransportsa.booking.repository.PaymentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link PaymentService}.
 *
 * Covers:
 * - Requirement 14.1: Payment captured and totalPaid updated for valid booking statuses.
 * - Requirement 14.2: 409 for PENDING, QUOTED, or CANCELLED booking status.
 * - Requirement 14.3: booking.totalPaid is updated after each payment.
 * - Requirement 14.4: Multiple partial payments accumulate correctly.
 */
@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private BookingRepository bookingRepository;

    @Mock
    private AdminUserRepository adminUserRepository;

    @InjectMocks
    private PaymentService paymentService;

    private static final Long BOOKING_ID = 1L;
    private static final Long ADMIN_ID   = 99L;

    private VehicleType vehicleType;
    private AdminUser adminUser;

    @BeforeEach
    void setUp() {
        vehicleType = VehicleType.builder()
                .id(5L)
                .name("Bakkie")
                .baseFee(BigDecimal.valueOf(500))
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
    // Helper
    // -----------------------------------------------------------------------

    private Booking bookingWithStatus(BookingStatus status) {
        return Booking.builder()
                .id(BOOKING_ID)
                .bookingReference("MTSA-24001")
                .status(status)
                .customerName("Jane Doe")
                .customerPhone("+27821234567")
                .customerEmail("jane@example.com")
                .vehicleType(vehicleType)
                .serviceDate(LocalDate.now().plusDays(2))
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
    }

    // -----------------------------------------------------------------------
    // Requirement 14.1 — payment captured and totalPaid updated
    // -----------------------------------------------------------------------

    /**
     * Validates: Requirements 14.1, 14.3
     *
     * A payment against a CONFIRMED booking is persisted and the booking's
     * totalPaid is incremented by the payment amount.
     */
    @Test
    void capturePayment_confirmedBooking_persistsPaymentAndUpdatesTotalPaid() {
        // Arrange
        Booking booking = bookingWithStatus(BookingStatus.CONFIRMED);
        CapturePaymentRequest req = new CapturePaymentRequest(
                BigDecimal.valueOf(750.00), PaymentMethod.EFT, "EFT-REF-001");

        Payment savedPayment = Payment.builder()
                .id(1L)
                .booking(booking)
                .amount(req.amount())
                .paymentMethod(req.paymentMethod())
                .referenceNumber(req.referenceNumber())
                .capturedBy(adminUser)
                .capturedAt(Instant.now())
                .build();

        when(bookingRepository.findById(BOOKING_ID)).thenReturn(Optional.of(booking));
        when(adminUserRepository.findById(ADMIN_ID)).thenReturn(Optional.of(adminUser));
        when(paymentRepository.save(any(Payment.class))).thenReturn(savedPayment);
        when(bookingRepository.save(any(Booking.class))).thenAnswer(inv -> inv.getArgument(0));

        // Act
        PaymentResponse response = paymentService.capturePayment(BOOKING_ID, req, ADMIN_ID);

        // Assert: payment was persisted with correct fields
        ArgumentCaptor<Payment> paymentCaptor = ArgumentCaptor.forClass(Payment.class);
        verify(paymentRepository).save(paymentCaptor.capture());
        Payment captured = paymentCaptor.getValue();
        assertThat(captured.getAmount()).isEqualByComparingTo(BigDecimal.valueOf(750.00));
        assertThat(captured.getPaymentMethod()).isEqualTo(PaymentMethod.EFT);
        assertThat(captured.getReferenceNumber()).isEqualTo("EFT-REF-001");
        assertThat(captured.getCapturedBy()).isEqualTo(adminUser);
        assertThat(captured.getCapturedAt()).isNotNull();

        // Assert: booking totalPaid was updated
        ArgumentCaptor<Booking> bookingCaptor = ArgumentCaptor.forClass(Booking.class);
        verify(bookingRepository).save(bookingCaptor.capture());
        assertThat(bookingCaptor.getValue().getTotalPaid())
                .isEqualByComparingTo(BigDecimal.valueOf(750.00));

        // Assert: response fields
        assertThat(response.bookingReference()).isEqualTo("MTSA-24001");
        assertThat(response.amount()).isEqualByComparingTo(BigDecimal.valueOf(750.00));
        assertThat(response.paymentMethod()).isEqualTo("EFT");
        assertThat(response.referenceNumber()).isEqualTo("EFT-REF-001");
    }

    /**
     * Validates: Requirements 14.1
     *
     * Payment capture is allowed for all four payable statuses:
     * CONFIRMED, ASSIGNED, IN_PROGRESS, COMPLETED.
     */
    @ParameterizedTest
    @EnumSource(value = BookingStatus.class, names = {"CONFIRMED", "ASSIGNED", "IN_PROGRESS", "COMPLETED"})
    void capturePayment_payableStatus_doesNotThrow(BookingStatus status) {
        // Arrange
        Booking booking = bookingWithStatus(status);
        CapturePaymentRequest req = new CapturePaymentRequest(
                BigDecimal.valueOf(500), PaymentMethod.CASH, null);

        Payment savedPayment = Payment.builder()
                .id(1L)
                .booking(booking)
                .amount(req.amount())
                .paymentMethod(req.paymentMethod())
                .capturedBy(adminUser)
                .capturedAt(Instant.now())
                .build();

        when(bookingRepository.findById(BOOKING_ID)).thenReturn(Optional.of(booking));
        when(adminUserRepository.findById(ADMIN_ID)).thenReturn(Optional.of(adminUser));
        when(paymentRepository.save(any(Payment.class))).thenReturn(savedPayment);
        when(bookingRepository.save(any(Booking.class))).thenAnswer(inv -> inv.getArgument(0));

        // Act & Assert: no exception thrown
        PaymentResponse response = paymentService.capturePayment(BOOKING_ID, req, ADMIN_ID);
        assertThat(response).isNotNull();
    }

    // -----------------------------------------------------------------------
    // Requirement 14.2 — 409 for non-payable statuses
    // -----------------------------------------------------------------------

    /**
     * Validates: Requirements 14.2
     *
     * Payment capture for a PENDING booking must throw ConflictException.
     */
    @Test
    void capturePayment_pendingBooking_throwsConflictException() {
        // Arrange
        Booking booking = bookingWithStatus(BookingStatus.PENDING);
        CapturePaymentRequest req = new CapturePaymentRequest(
                BigDecimal.valueOf(500), PaymentMethod.CASH, null);

        when(bookingRepository.findById(BOOKING_ID)).thenReturn(Optional.of(booking));

        // Act & Assert
        assertThatThrownBy(() -> paymentService.capturePayment(BOOKING_ID, req, ADMIN_ID))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("PENDING");

        verify(paymentRepository, never()).save(any());
        verify(bookingRepository, never()).save(any());
    }

    /**
     * Validates: Requirements 14.2
     *
     * Payment capture for a QUOTED booking must throw ConflictException.
     */
    @Test
    void capturePayment_quotedBooking_throwsConflictException() {
        // Arrange
        Booking booking = bookingWithStatus(BookingStatus.QUOTED);
        CapturePaymentRequest req = new CapturePaymentRequest(
                BigDecimal.valueOf(500), PaymentMethod.EFT, "REF-001");

        when(bookingRepository.findById(BOOKING_ID)).thenReturn(Optional.of(booking));

        // Act & Assert
        assertThatThrownBy(() -> paymentService.capturePayment(BOOKING_ID, req, ADMIN_ID))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("QUOTED");

        verify(paymentRepository, never()).save(any());
        verify(bookingRepository, never()).save(any());
    }

    /**
     * Validates: Requirements 14.2
     *
     * Payment capture for a CANCELLED booking must throw ConflictException.
     */
    @Test
    void capturePayment_cancelledBooking_throwsConflictException() {
        // Arrange
        Booking booking = bookingWithStatus(BookingStatus.CANCELLED);
        CapturePaymentRequest req = new CapturePaymentRequest(
                BigDecimal.valueOf(500), PaymentMethod.CARD, "CARD-001");

        when(bookingRepository.findById(BOOKING_ID)).thenReturn(Optional.of(booking));

        // Act & Assert
        assertThatThrownBy(() -> paymentService.capturePayment(BOOKING_ID, req, ADMIN_ID))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("CANCELLED");

        verify(paymentRepository, never()).save(any());
        verify(bookingRepository, never()).save(any());
    }

    // -----------------------------------------------------------------------
    // Requirement 14.4 — multiple partial payments accumulate correctly
    // -----------------------------------------------------------------------

    /**
     * Validates: Requirements 14.3, 14.4
     *
     * Two partial payments against the same booking accumulate: after the first
     * payment totalPaid equals the first amount; after the second it equals the sum.
     */
    @Test
    void capturePayment_multiplePartialPayments_accumulateTotalPaidCorrectly() {
        // Arrange: booking starts with totalPaid = 0
        Booking booking = bookingWithStatus(BookingStatus.CONFIRMED);

        CapturePaymentRequest firstReq = new CapturePaymentRequest(
                BigDecimal.valueOf(300), PaymentMethod.CASH, null);
        CapturePaymentRequest secondReq = new CapturePaymentRequest(
                BigDecimal.valueOf(450), PaymentMethod.EFT, "EFT-002");

        Payment firstPayment = Payment.builder()
                .id(1L)
                .booking(booking)
                .amount(firstReq.amount())
                .paymentMethod(firstReq.paymentMethod())
                .capturedBy(adminUser)
                .capturedAt(Instant.now())
                .build();

        Payment secondPayment = Payment.builder()
                .id(2L)
                .booking(booking)
                .amount(secondReq.amount())
                .paymentMethod(secondReq.paymentMethod())
                .referenceNumber(secondReq.referenceNumber())
                .capturedBy(adminUser)
                .capturedAt(Instant.now())
                .build();

        when(bookingRepository.findById(BOOKING_ID)).thenReturn(Optional.of(booking));
        when(adminUserRepository.findById(ADMIN_ID)).thenReturn(Optional.of(adminUser));
        when(paymentRepository.save(any(Payment.class)))
                .thenReturn(firstPayment)
                .thenReturn(secondPayment);
        when(bookingRepository.save(any(Booking.class))).thenAnswer(inv -> inv.getArgument(0));

        // Act: first payment
        paymentService.capturePayment(BOOKING_ID, firstReq, ADMIN_ID);
        assertThat(booking.getTotalPaid()).isEqualByComparingTo(BigDecimal.valueOf(300));

        // Act: second payment (booking now has totalPaid = 300 from first call)
        paymentService.capturePayment(BOOKING_ID, secondReq, ADMIN_ID);
        assertThat(booking.getTotalPaid()).isEqualByComparingTo(BigDecimal.valueOf(750));
    }

    // -----------------------------------------------------------------------
    // listPayments
    // -----------------------------------------------------------------------

    /**
     * Validates: Requirements 14.4
     *
     * listPayments returns all payments associated with the booking.
     */
    @Test
    void listPayments_existingBooking_returnsAllPayments() {
        // Arrange
        Booking booking = bookingWithStatus(BookingStatus.COMPLETED);

        Payment p1 = Payment.builder()
                .id(1L)
                .booking(booking)
                .amount(BigDecimal.valueOf(200))
                .paymentMethod(PaymentMethod.CASH)
                .capturedBy(adminUser)
                .capturedAt(Instant.now())
                .build();

        Payment p2 = Payment.builder()
                .id(2L)
                .booking(booking)
                .amount(BigDecimal.valueOf(300))
                .paymentMethod(PaymentMethod.EFT)
                .referenceNumber("EFT-003")
                .capturedBy(adminUser)
                .capturedAt(Instant.now())
                .build();

        when(bookingRepository.findById(BOOKING_ID)).thenReturn(Optional.of(booking));
        when(paymentRepository.findByBookingId(BOOKING_ID)).thenReturn(List.of(p1, p2));

        // Act
        List<PaymentResponse> responses = paymentService.listPayments(BOOKING_ID);

        // Assert
        assertThat(responses).hasSize(2);
        assertThat(responses.get(0).amount()).isEqualByComparingTo(BigDecimal.valueOf(200));
        assertThat(responses.get(1).amount()).isEqualByComparingTo(BigDecimal.valueOf(300));
        assertThat(responses.get(1).referenceNumber()).isEqualTo("EFT-003");
    }

    /**
     * When the booking does not exist, listPayments must throw BookingNotFoundException.
     */
    @Test
    void listPayments_bookingNotFound_throwsBookingNotFoundException() {
        // Arrange
        when(bookingRepository.findById(BOOKING_ID)).thenReturn(Optional.empty());

        // Act & Assert
        assertThatThrownBy(() -> paymentService.listPayments(BOOKING_ID))
                .isInstanceOf(BookingNotFoundException.class);

        verify(paymentRepository, never()).findByBookingId(any());
    }

    // -----------------------------------------------------------------------
    // Booking not found during capture
    // -----------------------------------------------------------------------

    /**
     * When the booking does not exist, capturePayment must throw BookingNotFoundException.
     */
    @Test
    void capturePayment_bookingNotFound_throwsBookingNotFoundException() {
        // Arrange
        CapturePaymentRequest req = new CapturePaymentRequest(
                BigDecimal.valueOf(500), PaymentMethod.CASH, null);

        when(bookingRepository.findById(BOOKING_ID)).thenReturn(Optional.empty());

        // Act & Assert
        assertThatThrownBy(() -> paymentService.capturePayment(BOOKING_ID, req, ADMIN_ID))
                .isInstanceOf(BookingNotFoundException.class);

        verify(paymentRepository, never()).save(any());
        verify(bookingRepository, never()).save(any());
    }
}
