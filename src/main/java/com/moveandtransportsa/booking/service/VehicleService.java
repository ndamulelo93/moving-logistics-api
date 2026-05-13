package com.moveandtransportsa.booking.service;

import com.moveandtransportsa.booking.domain.booking.BookingAssignment;
import com.moveandtransportsa.booking.domain.vehicle.Vehicle;
import com.moveandtransportsa.booking.domain.vehicle.VehicleType;
import com.moveandtransportsa.booking.dto.request.CreateVehicleRequest;
import com.moveandtransportsa.booking.dto.request.UpdateVehicleRequest;
import com.moveandtransportsa.booking.dto.response.VehicleResponse;
import com.moveandtransportsa.booking.dto.response.VehicleTypeResponse;
import com.moveandtransportsa.booking.exception.BookingNotFoundException;
import com.moveandtransportsa.booking.exception.ConflictException;
import com.moveandtransportsa.booking.repository.BookingAssignmentRepository;
import com.moveandtransportsa.booking.repository.VehicleRepository;
import com.moveandtransportsa.booking.repository.VehicleTypeRepository;
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
public class VehicleService {

    private final VehicleRepository vehicleRepository;
    private final VehicleTypeRepository vehicleTypeRepository;
    private final BookingAssignmentRepository bookingAssignmentRepository;

    /**
     * Creates a new vehicle.
     * Throws {@link BookingNotFoundException} if the vehicle type does not exist.
     */
    @Transactional
    public VehicleResponse create(CreateVehicleRequest request) {
        VehicleType vehicleType = vehicleTypeRepository.findById(request.vehicleTypeId())
                .orElseThrow(() -> new BookingNotFoundException("VehicleType not found with id: " + request.vehicleTypeId()));

        Instant now = Instant.now();
        Vehicle vehicle = Vehicle.builder()
                .registrationNumber(request.registrationNumber())
                .make(request.make())
                .model(request.model())
                .year(request.year())
                .vehicleType(vehicleType)
                .active(request.active())
                .createdAt(now)
                .updatedAt(now)
                .build();

        Vehicle saved = vehicleRepository.save(vehicle);
        return toResponse(saved);
    }

    /**
     * Partially updates a vehicle.
     * Only non-null fields in the request are applied.
     * Throws {@link BookingNotFoundException} if the vehicle or vehicle type does not exist.
     */
    @Transactional
    public VehicleResponse update(Long id, UpdateVehicleRequest request) {
        Vehicle vehicle = vehicleRepository.findById(id)
                .orElseThrow(() -> new BookingNotFoundException("Vehicle not found with id: " + id));

        if (request.registrationNumber() != null) {
            vehicle.setRegistrationNumber(request.registrationNumber());
        }
        if (request.make() != null) {
            vehicle.setMake(request.make());
        }
        if (request.model() != null) {
            vehicle.setModel(request.model());
        }
        if (request.year() != null) {
            vehicle.setYear(request.year());
        }
        if (request.vehicleTypeId() != null) {
            VehicleType vehicleType = vehicleTypeRepository.findById(request.vehicleTypeId())
                    .orElseThrow(() -> new BookingNotFoundException("VehicleType not found with id: " + request.vehicleTypeId()));
            vehicle.setVehicleType(vehicleType);
        }
        if (request.active() != null) {
            vehicle.setActive(request.active());
        }
        vehicle.setUpdatedAt(Instant.now());

        Vehicle saved = vehicleRepository.save(vehicle);
        return toResponse(saved);
    }

    /**
     * Soft-deletes a vehicle by setting active=false.
     * Throws {@link ConflictException} if the vehicle has future assignments.
     * Throws {@link BookingNotFoundException} if the vehicle does not exist.
     */
    @Transactional
    public void deactivate(Long id) {
        Vehicle vehicle = vehicleRepository.findById(id)
                .orElseThrow(() -> new BookingNotFoundException("Vehicle not found with id: " + id));

        List<BookingAssignment> futureAssignments =
                bookingAssignmentRepository.findFutureAssignmentsByVehicle(id, LocalDate.now());

        if (!futureAssignments.isEmpty()) {
            String bookingRefs = futureAssignments.stream()
                    .map(a -> a.getBooking().getBookingReference())
                    .collect(Collectors.joining(", "));
            throw new ConflictException(
                    "Vehicle has future assignments and cannot be deactivated. Booking references: " + bookingRefs);
        }

        vehicle.setActive(false);
        vehicle.setUpdatedAt(Instant.now());
        vehicleRepository.save(vehicle);
    }

    /**
     * Returns a paginated list of vehicles, optionally filtered by vehicleTypeId and/or active status.
     */
    @Transactional(readOnly = true)
    public Page<VehicleResponse> listPaginated(Long vehicleTypeId, Boolean active, Pageable pageable) {
        Specification<Vehicle> spec = Specification.where(null);

        if (vehicleTypeId != null) {
            spec = spec.and((root, query, cb) ->
                    cb.equal(root.get("vehicleType").get("id"), vehicleTypeId));
        }
        if (active != null) {
            spec = spec.and((root, query, cb) ->
                    cb.equal(root.get("active"), active));
        }

        return vehicleRepository.findAll(spec, pageable).map(this::toResponse);
    }

    /**
     * Returns a single vehicle by ID.
     * Throws {@link BookingNotFoundException} if not found.
     */
    @Transactional(readOnly = true)
    public VehicleResponse getById(Long id) {
        Vehicle vehicle = vehicleRepository.findById(id)
                .orElseThrow(() -> new BookingNotFoundException("Vehicle not found with id: " + id));
        return toResponse(vehicle);
    }

    private VehicleResponse toResponse(Vehicle vehicle) {
        VehicleType vt = vehicle.getVehicleType();
        VehicleTypeResponse vehicleTypeResponse = new VehicleTypeResponse(
                vt.getId(),
                vt.getName(),
                vt.getDescription(),
                vt.getLoadCapacity(),
                vt.getBaseFee(),
                vt.isActive()
        );
        return new VehicleResponse(
                vehicle.getId(),
                vehicle.getRegistrationNumber(),
                vehicle.getMake(),
                vehicle.getModel(),
                vehicle.getYear(),
                vehicleTypeResponse,
                vehicle.isActive()
        );
    }
}
