package com.moveandtransportsa.booking.web.admin;

import com.moveandtransportsa.booking.dto.request.CreateVehicleRequest;
import com.moveandtransportsa.booking.dto.request.UpdateVehicleRequest;
import com.moveandtransportsa.booking.dto.response.VehicleResponse;
import com.moveandtransportsa.booking.service.VehicleService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/vehicles")
@RequiredArgsConstructor
public class VehicleController {

    private final VehicleService vehicleService;

    /**
     * GET /api/admin/vehicles
     * Returns a paginated list of vehicles, optionally filtered by vehicleTypeId and/or active.
     * Any authenticated user.
     */
    @GetMapping
    public ResponseEntity<Page<VehicleResponse>> listPaginated(
            @RequestParam(required = false) Long vehicleTypeId,
            @RequestParam(required = false) Boolean active,
            Pageable pageable
    ) {
        return ResponseEntity.ok(vehicleService.listPaginated(vehicleTypeId, active, pageable));
    }

    /**
     * POST /api/admin/vehicles
     * Creates a new vehicle. SUPER_ADMIN only. Returns 201 Created.
     */
    @PostMapping
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<VehicleResponse> create(@Valid @RequestBody CreateVehicleRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(vehicleService.create(request));
    }

    /**
     * GET /api/admin/vehicles/{id}
     * Returns a single vehicle by ID. Any authenticated user.
     */
    @GetMapping("/{id}")
    public ResponseEntity<VehicleResponse> getById(@PathVariable Long id) {
        return ResponseEntity.ok(vehicleService.getById(id));
    }

    /**
     * PUT /api/admin/vehicles/{id}
     * Partially updates a vehicle. SUPER_ADMIN only.
     */
    @PutMapping("/{id}")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<VehicleResponse> update(
            @PathVariable Long id,
            @RequestBody UpdateVehicleRequest request
    ) {
        return ResponseEntity.ok(vehicleService.update(id, request));
    }

    // No DELETE endpoint — deactivation is done via PUT with active=false
}
