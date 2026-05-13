package com.moveandtransportsa.booking.dto.request;

import com.moveandtransportsa.booking.domain.auth.AdminRole;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Request body for POST /api/admin/users.
 */
public record CreateAdminUserRequest(
        @NotBlank String username,
        @NotBlank String displayName,
        @NotBlank @Size(min = 8) String password,
        @NotNull AdminRole role
) {
}
