package com.moveandtransportsa.booking.service;

import com.moveandtransportsa.booking.domain.booking.*;
import com.moveandtransportsa.booking.domain.vehicle.VehicleType;
import com.moveandtransportsa.booking.dto.request.BookingAddressRequest;
import com.moveandtransportsa.booking.dto.request.CreateBookingRequest;
import com.moveandtransportsa.booking.dto.response.BookingAddressResponse;
import com.moveandtransportsa.booking.dto.response.CreateBookingResponse;
import com.moveandtransportsa.booking.dto.response.PublicBookingResponse;
import com.moveandtransportsa.booking.exception.BookingNotFoundException;
import com.moveandtransportsa.booking.exception.ConflictException;
import com.moveandtransportsa.booking.repository.BookingAddressRepository;
import com.moveandtransportsa.booking.repository.BookingRepository;
import com.moveandtransportsa.booking.repository.VehicleTypeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Service
public class BookingService {

    private final BookingRepository bookingRepository;
    private final BookingAddressRepository bookingAddressRepository;
    private final VehicleTypeRepository vehicleTypeRepository;
    private final BookingReferenceGenerator bookingReferenceGenerator;
    private final StatusHistoryService statusHistoryService;
    private final NotificationService notificationService;

    public BookingService(BookingRepository bookingRepository,
                          BookingAddressRepository bookingAddressRepository,
                          VehicleTypeRepository vehicleTypeRepository,
                          BookingReferenceGenerator bookingReferenceGenerator,
                          StatusHistoryService statusHistoryService,
                          @Lazy NotificationService notificationService) {
        this.bookingRepository = bookingRepository;
        this.bookingAddressRepository = bookingAddressRepository;
        this.vehicleTypeRepository = vehicleTypeRepository;
        this.bookingReferenceGenerator = bookingReferenceGenerator;
        this.statusHistoryService = statusHistoryService;
        this.notificationService = notificationService;
    }

    private static final Set<BookingStatus> CANCELLABLE_STATUSES =
            Set.of(BookingStatus.PENDING, BookingStatus.QUOTED, BookingStatus.CONFIRMED);

    /**
     * Creates a new booking with PENDING status, persists addresses, generates reference,
     * and records the initial status history entry.
     */
    @Transactional
    public CreateBookingResponse createBooking(CreateBookingRequest req) {
        VehicleType vehicleType = vehicleTypeRepository.findById(req.vehicleTypeId())
                .orElseThrow(() -> new BookingNotFoundException("VehicleType not found with id: " + req.vehicleTypeId()));

        Instant now = Instant.now();

        // Generate reference before saving to avoid null constraint violation
        String reference = bookingReferenceGenerator.generate(LocalDate.now().getYear());

        Booking booking = Booking.builder()
                .bookingReference(reference)
                .status(BookingStatus.PENDING)
                .customerName(req.customerName())
                .customerPhone(req.customerPhone())
                .customerEmail(req.customerEmail())
                .vehicleType(vehicleType)
                .serviceDate(req.serviceDate())
                .serviceTime(req.serviceTime())
                .helpers((short) req.helpers())
                .stairs(req.stairs())
                .estimatedPrice(req.estimatedPrice())
                .createdAt(now)
                .updatedAt(now)
                .build();

        booking = bookingRepository.save(booking);

        // Persist pickup addresses
        List<BookingAddress> addresses = new ArrayList<>();
        for (int i = 0; i < req.pickupAddresses().size(); i++) {
            BookingAddressRequest addr = req.pickupAddresses().get(i);
            addresses.add(BookingAddress.builder()
                    .booking(booking)
                    .addressType(AddressType.PICKUP)
                    .addressLine(addr.addressLine())
                    .suburb(addr.suburb())
                    .city(addr.city())
                    .sortOrder((short) i)
                    .createdAt(now)
                    .build());
        }

        // Persist dropoff addresses
        for (int i = 0; i < req.dropoffAddresses().size(); i++) {
            BookingAddressRequest addr = req.dropoffAddresses().get(i);
            addresses.add(BookingAddress.builder()
                    .booking(booking)
                    .addressType(AddressType.DROPOFF)
                    .addressLine(addr.addressLine())
                    .suburb(addr.suburb())
                    .city(addr.city())
                    .sortOrder((short) i)
                    .createdAt(now)
                    .build());
        }
        bookingAddressRepository.saveAll(addresses);

        // Record initial status history
        statusHistoryService.appendStatusChange(booking.getId(), null, BookingStatus.PENDING, "CUSTOMER", null);

        return new CreateBookingResponse(
                booking.getBookingReference(),
                booking.getStatus().name(),
                booking.getEstimatedPrice(),
                booking.getCreatedAt()
        );
    }

    /**
     * Retrieves a booking by reference and maps it to a public-safe response.
     * Excludes id, adminNotes, and driver details.
     */
    @Transactional(readOnly = true)
    public PublicBookingResponse getByReference(String reference) {
        Booking booking = bookingRepository.findByBookingReference(reference)
                .orElseThrow(() -> new BookingNotFoundException("bookingReference", reference));

        List<BookingAddressResponse> pickupAddresses = bookingAddressRepository
                .findByBookingIdAndAddressType(booking.getId(), AddressType.PICKUP)
                .stream()
                .map(this::toAddressResponse)
                .toList();

        List<BookingAddressResponse> dropoffAddresses = bookingAddressRepository
                .findByBookingIdAndAddressType(booking.getId(), AddressType.DROPOFF)
                .stream()
                .map(this::toAddressResponse)
                .toList();

        return new PublicBookingResponse(
                booking.getBookingReference(),
                booking.getStatus().name(),
                booking.getVehicleType().getName(),
                booking.getServiceDate(),
                booking.getServiceTime(),
                pickupAddresses,
                dropoffAddresses,
                booking.getEstimatedPrice(),
                booking.getFinalPrice()
        );
    }

    /**
     * Cancels a booking by reference. Only PENDING, QUOTED, or CONFIRMED bookings can be cancelled.
     */
    @Transactional
    public void cancelByReference(String reference) {
        Booking booking = bookingRepository.findByBookingReference(reference)
                .orElseThrow(() -> new BookingNotFoundException("bookingReference", reference));

        BookingStatus currentStatus = booking.getStatus();
        if (!CANCELLABLE_STATUSES.contains(currentStatus)) {
            throw new ConflictException("Booking cannot be cancelled from status " + currentStatus);
        }

        booking.setStatus(BookingStatus.CANCELLED);
        booking.setUpdatedAt(Instant.now());
        bookingRepository.save(booking);

        statusHistoryService.appendStatusChange(booking.getId(), currentStatus, BookingStatus.CANCELLED, "CUSTOMER", null);
        notificationService.dispatch(booking.getId(), "CANCELLED");
    }

    private BookingAddressResponse toAddressResponse(BookingAddress addr) {
        return new BookingAddressResponse(
                addr.getId(),
                addr.getAddressType().name(),
                addr.getAddressLine(),
                addr.getSuburb(),
                addr.getCity()
        );
    }
}
