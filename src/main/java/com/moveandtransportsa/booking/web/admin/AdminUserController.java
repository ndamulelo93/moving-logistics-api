package com.moveandtransportsa.booking.web.admin;

import com.moveandtransportsa.booking.dto.request.CreateAdminUserRequest;
import com.moveandtransportsa.booking.dto.request.UpdateAdminUserRequest;
import com.moveandtransportsa.booking.dto.response.AdminUserResponse;
import com.moveandtransportsa.booking.service.AdminUserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/users")
@PreAuthorize("hasRole('SUPER_ADMIN')")
@RequiredArgsConstructor
public class AdminUserController {

    private final AdminUserService adminUserService;

    /**
     * GET /api/admin/users
     * Returns a paginated list of all admin users.
     */
    @GetMapping
    public ResponseEntity<Page<AdminUserResponse>> listUsers(Pageable pageable) {
        return ResponseEntity.ok(adminUserService.listUsers(pageable));
    }

    /**
     * POST /api/admin/users
     * Creates a new admin user.
     */
    @PostMapping
    public ResponseEntity<AdminUserResponse> createUser(@Valid @RequestBody CreateAdminUserRequest request) {
        return ResponseEntity.ok(adminUserService.createUser(request));
    }

    /**
     * GET /api/admin/users/{id}
     * Returns a single admin user by ID.
     */
    @GetMapping("/{id}")
    public ResponseEntity<AdminUserResponse> getUser(@PathVariable Long id) {
        return ResponseEntity.ok(adminUserService.getUser(id));
    }

    /**
     * PUT /api/admin/users/{id}
     * Updates an existing admin user. Only non-null fields are applied.
     */
    @PutMapping("/{id}")
    public ResponseEntity<AdminUserResponse> updateUser(
            @PathVariable Long id,
            @RequestBody UpdateAdminUserRequest request
    ) {
        return ResponseEntity.ok(adminUserService.updateUser(id, request));
    }

    // No DELETE endpoint — deactivation is done via PUT with active=false
}
