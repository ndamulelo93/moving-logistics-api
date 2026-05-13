package com.moveandtransportsa.booking.web.admin;

import com.moveandtransportsa.booking.domain.booking.BookingStatus;
import com.moveandtransportsa.booking.dto.request.AssignmentRequest;
import com.moveandtransportsa.booking.dto.request.CapturePaymentRequest;
import com.moveandtransportsa.booking.dto.request.UpdateBookingRequest;
import com.moveandtransportsa.booking.dto.request.UpdateBookingStatusRequest;
import com.moveandtransportsa.booking.dto.response.*;
import com.moveandtransportsa.booking.service.*;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/admin/bookings")
@RequiredArgsConstructor
public class AdminBookingController {

    private final AdminBookingService adminBookingService;
    private final StatusTransitionService statusTransitionService;
    private final StatusHistoryService statusHistoryService;
    private final AssignmentService assignmentService;
    private final PaymentService paymentService;

    @GetMapping
    public ResponseEntity<Page<BookingListResponse>> listBookings(
            @RequestParam(required = false) BookingStatus status,
            @RequestParam(required = false) Long vehicleTypeId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate serviceDateFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate serviceDateTo,
            @RequestParam(required = false) String customerName,
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(adminBookingService.listBookings(
                status, vehicleTypeId, serviceDateFrom, serviceDateTo, customerName, pageable));
    }

    @GetMapping("/{id}")
    public ResponseEntity<AdminBookingResponse> getBookingDetail(@PathVariable Long id) {
        return ResponseEntity.ok(adminBookingService.getBookingDetail(id));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('SUPER_ADMIN') or hasRole('DISPATCHER')")
    public ResponseEntity<AdminBookingResponse> updateBooking(
            @PathVariable Long id,
            @Valid @RequestBody UpdateBookingRequest request) {
        Long adminUserId = resolveAdminUserId();
        return ResponseEntity.ok(adminBookingService.updateBooking(id, request, adminUserId));
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasRole('SUPER_ADMIN') or hasRole('DISPATCHER')")
    public ResponseEntity<Void> updateStatus(
            @PathVariable Long id,
            @Valid @RequestBody UpdateBookingStatusRequest request) {
        Long adminUserId = resolveAdminUserId();
        statusTransitionService.transition(id, request.status(), adminUserId);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/{id}/history")
    public ResponseEntity<List<StatusHistoryResponse>> getHistory(@PathVariable Long id) {
        return ResponseEntity.ok(statusHistoryService.getHistory(id));
    }

    @PostMapping("/{id}/assign")
    @PreAuthorize("hasRole('SUPER_ADMIN') or hasRole('DISPATCHER')")
    public ResponseEntity<AssignmentResponse> assign(
            @PathVariable Long id,
            @Valid @RequestBody AssignmentRequest request) {
        Long adminUserId = resolveAdminUserId();
        return ResponseEntity.ok(assignmentService.assign(id, request.driverId(), request.vehicleId(), adminUserId));
    }

    @PostMapping("/{id}/payments")
    @PreAuthorize("hasRole('SUPER_ADMIN') or hasRole('DISPATCHER')")
    public ResponseEntity<PaymentResponse> capturePayment(
            @PathVariable Long id,
            @Valid @RequestBody CapturePaymentRequest request) {
        Long adminUserId = resolveAdminUserId();
        return ResponseEntity.ok(paymentService.capturePayment(id, request, adminUserId));
    }

    @GetMapping("/{id}/payments")
    public ResponseEntity<List<PaymentResponse>> listPayments(@PathVariable Long id) {
        return ResponseEntity.ok(paymentService.listPayments(id));
    }

    /**
     * Resolves the admin user ID from the JWT authentication details.
     * The JwtAuthenticationFilter stores the numeric admin user ID in authentication.getDetails().
     */
    private Long resolveAdminUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getDetails() instanceof Long id) {
            return id;
        }
        return null;
    }
}
