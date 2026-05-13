package com.moveandtransportsa.booking.web.admin;

import com.moveandtransportsa.booking.dto.request.CreateDriverRequest;
import com.moveandtransportsa.booking.dto.request.UpdateDriverRequest;
import com.moveandtransportsa.booking.dto.response.DriverResponse;
import com.moveandtransportsa.booking.service.DriverService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/drivers")
@RequiredArgsConstructor
public class DriverController {

    private final DriverService driverService;

    /**
     * GET /api/admin/drivers
     * Returns a paginated list of drivers, optionally filtered by active status.
     * Any authenticated user.
     */
    @GetMapping
    public ResponseEntity<Page<DriverResponse>> listPaginated(
            @RequestParam(required = false) Boolean active,
            Pageable pageable
    ) {
        return ResponseEntity.ok(driverService.listPaginated(active, pageable));
    }

    /**
     * POST /api/admin/drivers
     * Creates a new driver. SUPER_ADMIN only. Returns 201 Created.
     */
    @PostMapping
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<DriverResponse> create(@Valid @RequestBody CreateDriverRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(driverService.create(request));
    }

    /**
     * GET /api/admin/drivers/{id}
     * Returns a single driver by ID. Any authenticated user.
     */
    @GetMapping("/{id}")
    public ResponseEntity<DriverResponse> getById(@PathVariable Long id) {
        return ResponseEntity.ok(driverService.getById(id));
    }

    /**
     * PUT /api/admin/drivers/{id}
     * Partially updates a driver. SUPER_ADMIN only.
     */
    @PutMapping("/{id}")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<DriverResponse> update(
            @PathVariable Long id,
            @RequestBody UpdateDriverRequest request
    ) {
        return ResponseEntity.ok(driverService.update(id, request));
    }

    // No DELETE endpoint — deactivation is done via PUT with active=false
}
