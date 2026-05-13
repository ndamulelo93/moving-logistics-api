package com.moveandtransportsa.booking.service;

import com.moveandtransportsa.booking.domain.booking.*;
import com.moveandtransportsa.booking.domain.vehicle.VehicleType;
import com.moveandtransportsa.booking.dto.request.BookingAddressRequest;
import com.moveandtransportsa.booking.dto.request.UpdateBookingRequest;
import com.moveandtransportsa.booking.dto.response.AdminBookingResponse;
import com.moveandtransportsa.booking.dto.response.BookingAddressResponse;
import com.moveandtransportsa.booking.dto.response.BookingListResponse;
import com.moveandtransportsa.booking.exception.BookingNotFoundException;
import com.moveandtransportsa.booking.exception.ConflictException;
import com.moveandtransportsa.booking.repository.BookingAddressRepository;
import com.moveandtransportsa.booking.repository.BookingRepository;
import com.moveandtransportsa.booking.repository.VehicleTypeRepository;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class AdminBookingService {

    private static final Set<BookingStatus> IMMUTABLE_STATUSES =
            Set.of(BookingStatus.IN_PROGRESS, BookingStatus.COMPLETED);

    private final BookingRepository bookingRepository;
    private final BookingAddressRepository bookingAddressRepository;
    private final VehicleTypeRepository vehicleTypeRepository;
    private final StatusHistoryService statusHistoryService;

    /**
     * Returns a paginated, filtered list of bookings.
     * Enforces a maximum page size of 100.
     */
    @Transactional(readOnly = true)
    public Page<BookingListResponse> listBookings(BookingStatus status, Long vehicleTypeId,
                                                   LocalDate serviceDateFrom, LocalDate serviceDateTo,
                                                   String customerName, Pageable pageable) {
        // Enforce max page size
        if (pageable.getPageSize() > 100) {
            pageable = PageRequest.of(pageable.getPageNumber(), 100, pageable.getSort());
        }

        Specification<Booking> spec = buildSpec(status, vehicleTypeId, serviceDateFrom, serviceDateTo, customerName);
        return bookingRepository.findAll(spec, pageable).map(this::toListResponse);
    }

    /**
     * Returns the full booking detail including addresses and admin notes.
     */
    @Transactional(readOnly = true)
    public AdminBookingResponse getBookingDetail(Long id) {
        Booking booking = bookingRepository.findById(id)
                .orElseThrow(() -> new BookingNotFoundException(id));
        return toAdminResponse(booking);
    }

    /**
     * Updates allowed fields on a booking. Rejects updates when booking is IN_PROGRESS or COMPLETED.
     * Records a price adjustment history entry if finalPrice changes.
     */
    @Transactional
    public AdminBookingResponse updateBooking(Long id, UpdateBookingRequest req, Long adminUserId) {
        Booking booking = bookingRepository.findById(id)
                .orElseThrow(() -> new BookingNotFoundException(id));

        if (IMMUTABLE_STATUSES.contains(booking.getStatus())) {
            throw new ConflictException("Booking cannot be updated in status " + booking.getStatus());
        }

        if (req.customerName() != null) booking.setCustomerName(req.customerName());
        if (req.customerPhone() != null) booking.setCustomerPhone(req.customerPhone());
        if (req.customerEmail() != null) booking.setCustomerEmail(req.customerEmail());
        if (req.serviceDate() != null) booking.setServiceDate(req.serviceDate());
        if (req.helpers() != null) booking.setHelpers(req.helpers().shortValue());
        if (req.adminNotes() != null) booking.setAdminNotes(req.adminNotes());

        if (req.vehicleTypeId() != null) {
            VehicleType vehicleType = vehicleTypeRepository.findById(req.vehicleTypeId())
                    .orElseThrow(() -> new BookingNotFoundException("VehicleType not found with id: " + req.vehicleTypeId()));
            booking.setVehicleType(vehicleType);
        }

        // Handle finalPrice change — record price adjustment history
        if (req.finalPrice() != null) {
            BigDecimal oldPrice = booking.getFinalPrice();
            BigDecimal newPrice = req.finalPrice();
            if (!newPrice.equals(oldPrice)) {
                booking.setFinalPrice(newPrice);
                statusHistoryService.appendPriceAdjustment(id, oldPrice, newPrice, adminUserId);
            }
        }

        // Replace addresses if provided
        if (req.pickupAddresses() != null && !req.pickupAddresses().isEmpty()) {
            replaceAddresses(booking, req.pickupAddresses(), AddressType.PICKUP);
        }
        if (req.dropoffAddresses() != null && !req.dropoffAddresses().isEmpty()) {
            replaceAddresses(booking, req.dropoffAddresses(), AddressType.DROPOFF);
        }

        booking.setUpdatedAt(Instant.now());
        bookingRepository.save(booking);

        return toAdminResponse(booking);
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    private void replaceAddresses(Booking booking, List<BookingAddressRequest> requests, AddressType type) {
        List<BookingAddress> existing = bookingAddressRepository
                .findByBookingIdAndAddressType(booking.getId(), type);
        bookingAddressRepository.deleteAll(existing);

        Instant now = Instant.now();
        List<BookingAddress> newAddresses = new ArrayList<>();
        for (int i = 0; i < requests.size(); i++) {
            BookingAddressRequest req = requests.get(i);
            newAddresses.add(BookingAddress.builder()
                    .booking(booking)
                    .addressType(type)
                    .addressLine(req.addressLine())
                    .suburb(req.suburb())
                    .city(req.city())
                    .sortOrder((short) i)
                    .createdAt(now)
                    .build());
        }
        bookingAddressRepository.saveAll(newAddresses);
    }

    private Specification<Booking> buildSpec(BookingStatus status, Long vehicleTypeId,
                                              LocalDate serviceDateFrom, LocalDate serviceDateTo,
                                              String customerName) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            if (vehicleTypeId != null) {
                predicates.add(cb.equal(root.get("vehicleType").get("id"), vehicleTypeId));
            }
            if (serviceDateFrom != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("serviceDate"), serviceDateFrom));
            }
            if (serviceDateTo != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("serviceDate"), serviceDateTo));
            }
            if (customerName != null && !customerName.isBlank()) {
                predicates.add(cb.like(
                        cb.lower(root.get("customerName")),
                        "%" + customerName.toLowerCase() + "%"
                ));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    private BookingListResponse toListResponse(Booking booking) {
        return new BookingListResponse(
                booking.getId(),
                booking.getBookingReference(),
                booking.getStatus().name(),
                booking.getCustomerName(),
                booking.getVehicleType().getName(),
                booking.getServiceDate(),
                booking.getEstimatedPrice(),
                booking.getFinalPrice(),
                booking.getCreatedAt()
        );
    }

    private AdminBookingResponse toAdminResponse(Booking booking) {
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

        return new AdminBookingResponse(
                booking.getId(),
                booking.getBookingReference(),
                booking.getStatus().name(),
                booking.getCustomerName(),
                booking.getCustomerPhone(),
                booking.getCustomerEmail(),
                booking.getVehicleType().getName(),
                booking.getServiceDate(),
                booking.getServiceTime(),
                booking.getHelpers() != null ? booking.getHelpers() : 0,
                booking.isStairs(),
                booking.getEstimatedPrice(),
                booking.getFinalPrice(),
                booking.getTotalPaid(),
                booking.getAdminNotes(),
                pickupAddresses,
                dropoffAddresses,
                booking.getCreatedAt(),
                booking.getUpdatedAt()
        );
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
