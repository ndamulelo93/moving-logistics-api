package com.moveandtransportsa.booking.dto.request;

import jakarta.validation.constraints.NotBlank;

/**
 * Request body for POST /api/auth/refresh.
 */
public record RefreshTokenRequest(
        @NotBlank String refreshToken
) {
}
