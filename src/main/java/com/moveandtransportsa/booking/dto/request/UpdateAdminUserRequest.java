package com.moveandtransportsa.booking.dto.request;

import com.moveandtransportsa.booking.domain.auth.AdminRole;

/**
 * Request body for PUT /api/admin/users/{id}.
 * All fields are optional — only non-null values are applied.
 */
public record UpdateAdminUserRequest(
        String displayName,
        String password,
        AdminRole role,
        Boolean active
) {
}
