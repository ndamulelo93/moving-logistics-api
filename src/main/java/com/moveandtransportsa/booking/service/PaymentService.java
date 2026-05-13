package com.moveandtransportsa.booking.service;

import com.moveandtransportsa.booking.domain.auth.AdminUser;
import com.moveandtransportsa.booking.domain.booking.Booking;
import com.moveandtransportsa.booking.domain.booking.BookingStatus;
import com.moveandtransportsa.booking.domain.payment.Payment;
import com.moveandtransportsa.booking.dto.request.CapturePaymentRequest;
import com.moveandtransportsa.booking.dto.response.PaymentResponse;
import com.moveandtransportsa.booking.exception.BookingNotFoundException;
import com.moveandtransportsa.booking.exception.ConflictException;
import com.moveandtransportsa.booking.repository.AdminUserRepository;
import com.moveandtransportsa.booking.repository.BookingRepository;
import com.moveandtransportsa.booking.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class PaymentService {

    private static final Set<BookingStatus> PAYABLE_STATUSES =
            Set.of(BookingStatus.CONFIRMED, BookingStatus.ASSIGNED, BookingStatus.IN_PROGRESS, BookingStatus.COMPLETED);

    private final PaymentRepository paymentRepository;
    private final BookingRepository bookingRepository;
    private final AdminUserRepository adminUserRepository;

    /**
     * Captures a payment for the given booking.
     * Updates booking.totalPaid by adding the new payment amount.
     */
    @Transactional
    public PaymentResponse capturePayment(Long bookingId, CapturePaymentRequest req, Long adminUserId) {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new BookingNotFoundException(bookingId));

        if (!PAYABLE_STATUSES.contains(booking.getStatus())) {
            throw new ConflictException("Payment cannot be captured for booking in status " + booking.getStatus());
        }

        AdminUser adminUser = adminUserRepository.findById(adminUserId)
                .orElseThrow(() -> new BookingNotFoundException("AdminUser not found with id: " + adminUserId));

        Payment payment = Payment.builder()
                .booking(booking)
                .amount(req.amount())
                .paymentMethod(req.paymentMethod())
                .referenceNumber(req.referenceNumber())
                .capturedBy(adminUser)
                .capturedAt(Instant.now())
                .build();

        payment = paymentRepository.save(payment);

        booking.setTotalPaid(booking.getTotalPaid().add(req.amount()));
        booking.setUpdatedAt(Instant.now());
        bookingRepository.save(booking);

        return toResponse(payment, booking);
    }

    /**
     * Returns all payments for the given booking.
     */
    @Transactional(readOnly = true)
    public List<PaymentResponse> listPayments(Long bookingId) {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new BookingNotFoundException(bookingId));

        return paymentRepository.findByBookingId(bookingId)
                .stream()
                .map(p -> toResponse(p, booking))
                .toList();
    }

    private PaymentResponse toResponse(Payment payment, Booking booking) {
        return new PaymentResponse(
                payment.getId(),
                booking.getBookingReference(),
                payment.getAmount(),
                payment.getPaymentMethod().name(),
                payment.getReferenceNumber(),
                booking.getTotalPaid(),
                payment.getCapturedAt()
        );
    }
}
