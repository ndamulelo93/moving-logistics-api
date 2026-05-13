package com.moveandtransportsa.booking.dto.response;

/**
 * Returned on successful login or token rotation.
 *
 * @param accessToken  Signed JWT (short-lived)
 * @param refreshToken Opaque refresh token UUID (long-lived)
 * @param expiresIn    Access token lifetime in seconds
 * @param role         The admin role of the authenticated user
 */
public record TokenPair(
        String accessToken,
        String refreshToken,
        int expiresIn,
        String role
) {
}
