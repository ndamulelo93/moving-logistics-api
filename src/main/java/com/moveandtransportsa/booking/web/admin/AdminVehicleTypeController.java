package com.moveandtransportsa.booking.web.admin;

import com.moveandtransportsa.booking.dto.request.CreateVehicleTypeRequest;
import com.moveandtransportsa.booking.dto.request.UpdateVehicleTypeRequest;
import com.moveandtransportsa.booking.dto.response.VehicleTypeResponse;
import com.moveandtransportsa.booking.service.VehicleTypeService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/vehicle-types")
@RequiredArgsConstructor
public class AdminVehicleTypeController {

    private final VehicleTypeService vehicleTypeService;

    /**
     * GET /api/admin/vehicle-types
     * Returns a paginated list of all vehicle types. Any authenticated user.
     */
    @GetMapping
    public ResponseEntity<Page<VehicleTypeResponse>> listAll(Pageable pageable) {
        return ResponseEntity.ok(vehicleTypeService.listAll(pageable));
    }

    /**
     * POST /api/admin/vehicle-types
     * Creates a new vehicle type. SUPER_ADMIN only.
     */
    @PostMapping
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<VehicleTypeResponse> create(@Valid @RequestBody CreateVehicleTypeRequest request) {
        return ResponseEntity.ok(vehicleTypeService.create(request));
    }

    /**
     * GET /api/admin/vehicle-types/{id}
     * Returns a single vehicle type by ID. Any authenticated user.
     */
    @GetMapping("/{id}")
    public ResponseEntity<VehicleTypeResponse> getById(@PathVariable Long id) {
        return ResponseEntity.ok(vehicleTypeService.getById(id));
    }

    /**
     * PUT /api/admin/vehicle-types/{id}
     * Partially updates a vehicle type. SUPER_ADMIN only.
     */
    @PutMapping("/{id}")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<VehicleTypeResponse> update(
            @PathVariable Long id,
            @RequestBody UpdateVehicleTypeRequest request
    ) {
        return ResponseEntity.ok(vehicleTypeService.update(id, request));
    }

    // No DELETE endpoint — deactivation is done via PUT with active=false
}
