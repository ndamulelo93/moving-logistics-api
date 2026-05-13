package com.moveandtransportsa.booking.web.publicapi;

import com.moveandtransportsa.booking.dto.request.CreateBookingRequest;
import com.moveandtransportsa.booking.dto.response.CreateBookingResponse;
import com.moveandtransportsa.booking.dto.response.PublicBookingResponse;
import com.moveandtransportsa.booking.service.BookingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/bookings")
@RequiredArgsConstructor
public class PublicBookingController {

    private final BookingService bookingService;

    @PostMapping
    public ResponseEntity<CreateBookingResponse> createBooking(@Valid @RequestBody CreateBookingRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(bookingService.createBooking(request));
    }

    @GetMapping("/{reference}")
    public ResponseEntity<PublicBookingResponse> getBooking(@PathVariable String reference) {
        return ResponseEntity.ok(bookingService.getByReference(reference));
    }

    @PostMapping("/{reference}/cancel")
    public ResponseEntity<Void> cancelBooking(@PathVariable String reference) {
        bookingService.cancelByReference(reference);
        return ResponseEntity.ok().build();
    }
}
