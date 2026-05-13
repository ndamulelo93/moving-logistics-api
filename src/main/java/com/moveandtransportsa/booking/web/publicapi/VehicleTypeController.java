package com.moveandtransportsa.booking.web.publicapi;

import com.moveandtransportsa.booking.dto.response.VehicleTypeResponse;
import com.moveandtransportsa.booking.service.VehicleTypeService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Public endpoint — no authentication required.
 */
@RestController
@RequestMapping("/api/vehicle-types")
@RequiredArgsConstructor
public class VehicleTypeController {

    private final VehicleTypeService vehicleTypeService;

    /**
     * GET /api/vehicle-types
     * Returns all active vehicle types. Public, no auth required.
     */
    @GetMapping
    public ResponseEntity<List<VehicleTypeResponse>> listActive() {
        return ResponseEntity.ok(vehicleTypeService.listActive());
    }
}
