package com.moveandtransportsa.booking.service;

import com.moveandtransportsa.booking.domain.vehicle.VehicleType;
import com.moveandtransportsa.booking.dto.request.CreateVehicleTypeRequest;
import com.moveandtransportsa.booking.dto.request.UpdateVehicleTypeRequest;
import com.moveandtransportsa.booking.dto.response.VehicleTypeResponse;
import com.moveandtransportsa.booking.exception.BookingNotFoundException;
import com.moveandtransportsa.booking.repository.BookingAssignmentRepository;
import com.moveandtransportsa.booking.repository.VehicleTypeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
public class VehicleTypeService {

    private final VehicleTypeRepository vehicleTypeRepository;
    private final BookingAssignmentRepository bookingAssignmentRepository;

    /**
     * Returns all active vehicle types (public endpoint).
     */
    @Transactional(readOnly = true)
    public List<VehicleTypeResponse> listActive() {
        return vehicleTypeRepository.findAllByActiveTrue()
                .stream()
                .map(this::toResponse)
                .toList();
    }

    /**
     * Returns a paginated list of all vehicle types (admin endpoint).
     */
    @Transactional(readOnly = true)
    public Page<VehicleTypeResponse> listAll(Pageable pageable) {
        return vehicleTypeRepository.findAll(pageable).map(this::toResponse);
    }

    /**
     * Returns a single vehicle type by ID.
     * Throws {@link BookingNotFoundException} if not found.
     */
    @Transactional(readOnly = true)
    public VehicleTypeResponse getById(Long id) {
        VehicleType vehicleType = vehicleTypeRepository.findById(id)
                .orElseThrow(() -> new BookingNotFoundException("VehicleType not found with id: " + id));
        return toResponse(vehicleType);
    }

    /**
     * Creates a new vehicle type.
     */
    @Transactional
    public VehicleTypeResponse create(CreateVehicleTypeRequest request) {
        Instant now = Instant.now();
        VehicleType vehicleType = VehicleType.builder()
                .name(request.name())
                .description(request.description())
                .loadCapacity(request.loadCapacity())
                .baseFee(request.baseFee())
                .active(true)
                .createdAt(now)
                .updatedAt(now)
                .build();

        VehicleType saved = vehicleTypeRepository.save(vehicleType);
        return toResponse(saved);
    }

    /**
     * Partially updates a vehicle type.
     * Only non-null fields in the request are applied.
     * Throws {@link BookingNotFoundException} if not found.
     */
    @Transactional
    public VehicleTypeResponse update(Long id, UpdateVehicleTypeRequest request) {
        VehicleType vehicleType = vehicleTypeRepository.findById(id)
                .orElseThrow(() -> new BookingNotFoundException("VehicleType not found with id: " + id));

        if (request.name() != null) {
            vehicleType.setName(request.name());
        }
        if (request.description() != null) {
            vehicleType.setDescription(request.description());
        }
        if (request.loadCapacity() != null) {
            vehicleType.setLoadCapacity(request.loadCapacity());
        }
        if (request.baseFee() != null) {
            vehicleType.setBaseFee(request.baseFee());
        }
        if (request.active() != null) {
            vehicleType.setActive(request.active());
        }
        vehicleType.setUpdatedAt(Instant.now());

        VehicleType saved = vehicleTypeRepository.save(vehicleType);
        return toResponse(saved);
    }

    /**
     * Soft-deletes a vehicle type by setting active=false.
     * No future assignment check is required for vehicle types in MVP.
     * Throws {@link BookingNotFoundException} if not found.
     */
    @Transactional
    public void deactivate(Long id) {
        VehicleType vehicleType = vehicleTypeRepository.findById(id)
                .orElseThrow(() -> new BookingNotFoundException("VehicleType not found with id: " + id));
        vehicleType.setActive(false);
        vehicleType.setUpdatedAt(Instant.now());
        vehicleTypeRepository.save(vehicleType);
    }

    private VehicleTypeResponse toResponse(VehicleType vehicleType) {
        return new VehicleTypeResponse(
                vehicleType.getId(),
                vehicleType.getName(),
                vehicleType.getDescription(),
                vehicleType.getLoadCapacity(),
                vehicleType.getBaseFee(),
                vehicleType.isActive()
        );
    }
}
