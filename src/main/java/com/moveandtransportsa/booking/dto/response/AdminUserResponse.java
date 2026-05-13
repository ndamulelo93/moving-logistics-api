package com.moveandtransportsa.booking.dto.response;

/**
 * Public-safe representation of an AdminUser.
 * Never exposes the password hash.
 */
public record AdminUserResponse(
        Long id,
        String username,
        String displayName,
        String role
) {
}
