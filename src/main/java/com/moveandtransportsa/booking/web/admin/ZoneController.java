package com.moveandtransportsa.booking.web.admin;

import com.moveandtransportsa.booking.dto.request.CreateSuburbZoneRequest;
import com.moveandtransportsa.booking.dto.request.CreateZoneDistanceRequest;
import com.moveandtransportsa.booking.dto.request.UpdateSuburbZoneRequest;
import com.moveandtransportsa.booking.dto.request.UpdateZoneDistanceRequest;
import com.moveandtransportsa.booking.dto.response.SuburbZoneResponse;
import com.moveandtransportsa.booking.dto.response.ZoneDistanceResponse;
import com.moveandtransportsa.booking.service.ZoneService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Admin endpoints for managing suburb zones and zone distances.
 * Write operations are restricted to SUPER_ADMIN.
 */
@RestController
@RequestMapping("/api/admin/zones")
@RequiredArgsConstructor
public class ZoneController {

    private final ZoneService zoneService;

    // -------------------------------------------------------------------------
    // Suburb zone endpoints
    // -------------------------------------------------------------------------

    /**
     * GET /api/admin/zones/suburbs
     * Returns all suburb zones. Any authenticated user.
     */
    @GetMapping("/suburbs")
    public ResponseEntity<List<SuburbZoneResponse>> listSuburbs() {
        return ResponseEntity.ok(zoneService.listSuburbs());
    }

    /**
     * POST /api/admin/zones/suburbs
     * Adds a new suburb zone. SUPER_ADMIN only. Returns 201 Created.
     */
    @PostMapping("/suburbs")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<SuburbZoneResponse> addSuburb(@Valid @RequestBody CreateSuburbZoneRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(zoneService.addSuburb(request));
    }

    /**
     * PUT /api/admin/zones/suburbs/{id}
     * Partially updates a suburb zone. SUPER_ADMIN only.
     */
    @PutMapping("/suburbs/{id}")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<SuburbZoneResponse> updateSuburb(
            @PathVariable Long id,
            @RequestBody UpdateSuburbZoneRequest request
    ) {
        return ResponseEntity.ok(zoneService.updateSuburb(id, request));
    }

    // -------------------------------------------------------------------------
    // Zone distance endpoints
    // -------------------------------------------------------------------------

    /**
     * GET /api/admin/zones/distances
     * Returns all zone distances. Any authenticated user.
     */
    @GetMapping("/distances")
    public ResponseEntity<List<ZoneDistanceResponse>> listZoneDistances() {
        return ResponseEntity.ok(zoneService.listZoneDistances());
    }

    /**
     * POST /api/admin/zones/distances
     * Adds a new zone distance. SUPER_ADMIN only. Returns 201 Created.
     */
    @PostMapping("/distances")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<ZoneDistanceResponse> addZoneDistance(
            @Valid @RequestBody CreateZoneDistanceRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(zoneService.addZoneDistance(request));
    }

    /**
     * PUT /api/admin/zones/distances/{id}
     * Partially updates a zone distance. SUPER_ADMIN only.
     */
    @PutMapping("/distances/{id}")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<ZoneDistanceResponse> updateZoneDistance(
            @PathVariable Long id,
            @RequestBody UpdateZoneDistanceRequest request
    ) {
        return ResponseEntity.ok(zoneService.updateZoneDistance(id, request));
    }
}
