package com.moveandtransportsa.booking.service;

import com.moveandtransportsa.booking.domain.booking.BookingAssignment;
import com.moveandtransportsa.booking.domain.driver.Driver;
import com.moveandtransportsa.booking.dto.request.CreateDriverRequest;
import com.moveandtransportsa.booking.dto.request.UpdateDriverRequest;
import com.moveandtransportsa.booking.dto.response.DriverResponse;
import com.moveandtransportsa.booking.exception.BookingNotFoundException;
import com.moveandtransportsa.booking.exception.ConflictException;
import com.moveandtransportsa.booking.repository.BookingAssignmentRepository;
import com.moveandtransportsa.booking.repository.DriverRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DriverService {

    private final DriverRepository driverRepository;
    private final BookingAssignmentRepository bookingAssignmentRepository;

    /**
     * Creates a new driver.
     */
    @Transactional
    public DriverResponse create(CreateDriverRequest request) {
        Instant now = Instant.now();
        Driver driver = Driver.builder()
                .fullName(request.fullName())
                .phone(request.phone())
                .licenceNumber(request.licenceNumber())
                .active(request.active())
                .createdAt(now)
                .updatedAt(now)
                .build();

        Driver saved = driverRepository.save(driver);
        return toResponse(saved);
    }

    /**
     * Partially updates a driver.
     * Only non-null fields in the request are applied.
     * Throws {@link BookingNotFoundException} if the driver does not exist.
     */
    @Transactional
    public DriverResponse update(Long id, UpdateDriverRequest request) {
        Driver driver = driverRepository.findById(id)
                .orElseThrow(() -> new BookingNotFoundException("Driver not found with id: " + id));

        if (request.fullName() != null) {
            driver.setFullName(request.fullName());
        }
        if (request.phone() != null) {
            driver.setPhone(request.phone());
        }
        if (request.licenceNumber() != null) {
            driver.setLicenceNumber(request.licenceNumber());
        }
        if (request.active() != null) {
            driver.setActive(request.active());
        }
        driver.setUpdatedAt(Instant.now());

        Driver saved = driverRepository.save(driver);
        return toResponse(saved);
    }

    /**
     * Soft-deletes a driver by setting active=false.
     * Throws {@link ConflictException} if the driver has future assignments.
     * Throws {@link BookingNotFoundException} if the driver does not exist.
     */
    @Transactional
    public void deactivate(Long id) {
        Driver driver = driverRepository.findById(id)
                .orElseThrow(() -> new BookingNotFoundException("Driver not found with id: " + id));

        List<BookingAssignment> futureAssignments =
                bookingAssignmentRepository.findFutureAssignmentsByDriver(id, LocalDate.now());

        if (!futureAssignments.isEmpty()) {
            String bookingRefs = futureAssignments.stream()
                    .map(a -> a.getBooking().getBookingReference())
                    .collect(Collectors.joining(", "));
            throw new ConflictException(
                    "Driver has future assignments and cannot be deactivated. Booking references: " + bookingRefs);
        }

        driver.setActive(false);
        driver.setUpdatedAt(Instant.now());
        driverRepository.save(driver);
    }

    /**
     * Returns a paginated list of drivers, optionally filtered by active status.
     */
    @Transactional(readOnly = true)
    public Page<DriverResponse> listPaginated(Boolean active, Pageable pageable) {
        if (active != null) {
            Specification<Driver> spec = (root, query, cb) -> cb.equal(root.get("active"), active);
            return driverRepository.findAll(spec, pageable).map(this::toResponse);
        }
        return driverRepository.findAll(pageable).map(this::toResponse);
    }

    /**
     * Returns a single driver by ID.
     * Throws {@link BookingNotFoundException} if not found.
     */
    @Transactional(readOnly = true)
    public DriverResponse getById(Long id) {
        Driver driver = driverRepository.findById(id)
                .orElseThrow(() -> new BookingNotFoundException("Driver not found with id: " + id));
        return toResponse(driver);
    }

    private DriverResponse toResponse(Driver driver) {
        return new DriverResponse(
                driver.getId(),
                driver.getFullName(),
                driver.getPhone(),
                driver.getLicenceNumber(),
                driver.isActive()
        );
    }
}
