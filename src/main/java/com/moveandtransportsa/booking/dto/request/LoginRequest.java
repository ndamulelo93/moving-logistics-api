package com.moveandtransportsa.booking.dto.request;

import jakarta.validation.constraints.NotBlank;

/**
 * Request body for POST /api/auth/login.
 */
public record LoginRequest(
        @NotBlank String username,
        @NotBlank String password
) {
}
