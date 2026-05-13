package com.moveandtransportsa.booking.service;

import com.moveandtransportsa.booking.domain.auth.AdminUser;
import com.moveandtransportsa.booking.domain.booking.Booking;
import com.moveandtransportsa.booking.domain.booking.BookingAssignment;
import com.moveandtransportsa.booking.domain.booking.BookingStatus;
import com.moveandtransportsa.booking.domain.driver.Driver;
import com.moveandtransportsa.booking.domain.vehicle.Vehicle;
import com.moveandtransportsa.booking.dto.response.AssignmentResponse;
import com.moveandtransportsa.booking.exception.BookingNotFoundException;
import com.moveandtransportsa.booking.exception.ConflictException;
import com.moveandtransportsa.booking.exception.InactiveEntityException;
import com.moveandtransportsa.booking.exception.VehicleTypeMismatchException;
import com.moveandtransportsa.booking.repository.AdminUserRepository;
import com.moveandtransportsa.booking.repository.BookingAssignmentRepository;
import com.moveandtransportsa.booking.repository.BookingRepository;
import com.moveandtransportsa.booking.repository.DriverRepository;
import com.moveandtransportsa.booking.repository.VehicleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
@RequiredArgsConstructor
public class AssignmentService {

    private final BookingRepository bookingRepository;
    private final DriverRepository driverRepository;
    private final VehicleRepository vehicleRepository;
    private final BookingAssignmentRepository bookingAssignmentRepository;
    private final StatusTransitionService statusTransitionService;
    private final AdminUserRepository adminUserRepository;

    /**
     * Assigns a driver and vehicle to a booking.
     * Uses pessimistic locking to prevent concurrent double-booking.
     * Transitions the booking to ASSIGNED status.
     */
    @Transactional
    public AssignmentResponse assign(Long bookingId, Long driverId, Long vehicleId, Long adminUserId) {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new BookingNotFoundException(bookingId));

        if (booking.getStatus() != BookingStatus.CONFIRMED) {
            throw new ConflictException("Booking must be in CONFIRMED status to assign");
        }

        // Acquire pessimistic write locks
        Driver driver = driverRepository.findByIdWithLock(driverId)
                .orElseThrow(() -> new BookingNotFoundException("Driver not found with id: " + driverId));

        Vehicle vehicle = vehicleRepository.findByIdWithLock(vehicleId)
                .orElseThrow(() -> new BookingNotFoundException("Vehicle not found with id: " + vehicleId));

        if (!driver.isActive()) {
            throw new InactiveEntityException("Driver", driverId);
        }
        if (!vehicle.isActive()) {
            throw new InactiveEntityException("Vehicle", vehicleId);
        }

        // Verify vehicle type matches booking
        if (!vehicle.getVehicleType().getId().equals(booking.getVehicleType().getId())) {
            throw new VehicleTypeMismatchException(
                    "Vehicle type " + vehicle.getVehicleType().getName() +
                    " does not match booking vehicle type " + booking.getVehicleType().getName()
            );
        }

        // Check for scheduling conflicts
        if (bookingAssignmentRepository.existsConflictForDriver(driverId, booking.getServiceDate())) {
            throw new ConflictException("Driver already assigned on " + booking.getServiceDate());
        }
        if (bookingAssignmentRepository.existsConflictForVehicle(vehicleId, booking.getServiceDate())) {
            throw new ConflictException("Vehicle already assigned on " + booking.getServiceDate());
        }

        // Resolve the admin user who is performing the assignment
        AdminUser assignedBy = adminUserRepository.findById(adminUserId)
                .orElseThrow(() -> new BookingNotFoundException("AdminUser not found with id: " + adminUserId));

        Instant now = Instant.now();
        BookingAssignment assignment = BookingAssignment.builder()
                .booking(booking)
                .driver(driver)
                .vehicle(vehicle)
                .assignedAt(now)
                .assignedBy(assignedBy)
                .build();

        bookingAssignmentRepository.save(assignment);

        // Transition booking to ASSIGNED
        statusTransitionService.transition(bookingId, BookingStatus.ASSIGNED, adminUserId);

        return new AssignmentResponse(
                booking.getBookingReference(),
                driver.getFullName(),
                vehicle.getRegistrationNumber(),
                now
        );
    }
}
