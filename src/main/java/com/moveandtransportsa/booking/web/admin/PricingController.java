package com.moveandtransportsa.booking.web.admin;

import com.moveandtransportsa.booking.dto.request.CreatePricingRuleRequest;
import com.moveandtransportsa.booking.dto.request.UpdatePricingRuleRequest;
import com.moveandtransportsa.booking.dto.response.PricingRuleResponse;
import com.moveandtransportsa.booking.service.PricingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Admin endpoints for managing pricing rules.
 * Write operations are restricted to SUPER_ADMIN.
 */
@RestController
@RequestMapping("/api/admin/pricing-rules")
@RequiredArgsConstructor
public class PricingController {

    private final PricingService pricingService;

    /**
     * GET /api/admin/pricing-rules
     * Returns all pricing rules ordered by vehicle type name then effectiveFrom desc.
     * Any authenticated user.
     */
    @GetMapping
    public ResponseEntity<List<PricingRuleResponse>> listAll() {
        return ResponseEntity.ok(pricingService.listAll());
    }

    /**
     * POST /api/admin/pricing-rules
     * Creates a new pricing rule. SUPER_ADMIN only. Returns 201 Created.
     */
    @PostMapping
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<PricingRuleResponse> create(@Valid @RequestBody CreatePricingRuleRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(pricingService.create(request));
    }

    /**
     * PUT /api/admin/pricing-rules/{id}
     * Creates a new version of the pricing rule (preserves old for history). SUPER_ADMIN only.
     */
    @PutMapping("/{id}")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<PricingRuleResponse> update(
            @PathVariable Long id,
            @RequestBody UpdatePricingRuleRequest request
    ) {
        return ResponseEntity.ok(pricingService.update(id, request));
    }

    // No DELETE endpoint — pricing rules are preserved for historical reference
}
