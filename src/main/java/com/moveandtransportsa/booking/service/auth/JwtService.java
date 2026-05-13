package com.moveandtransportsa.booking.service.auth;

import com.moveandtransportsa.booking.config.JwtConfig;
import com.moveandtransportsa.booking.domain.auth.AdminUser;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class JwtService {

    private final JwtConfig jwtConfig;

    /**
     * Generates a signed HS256 JWT for the given admin user.
     * Claims: sub=username, role=role name, jti=random UUID.
     */
    public String generateToken(AdminUser user) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + (long) jwtConfig.getExpirySeconds() * 1000);

        return Jwts.builder()
                .subject(user.getUsername())
                .claim("role", user.getRole().name())
                .id(UUID.randomUUID().toString())
                .issuedAt(now)
                .expiration(expiry)
                .signWith(getSigningKey())
                .compact();
    }

    /**
     * Parses and validates the JWT, returning its claims.
     * Throws AuthenticationException on any failure (expired, invalid signature, malformed).
     */
    public Claims validateToken(String token) {
        try {
            return Jwts.parser()
                    .verifyWith(getSigningKey())
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (Exception e) {
            throw new BadCredentialsException("Invalid or expired JWT token", e);
        }
    }

    /**
     * Extracts the username (subject) from the token.
     */
    public String extractUsername(String token) {
        return validateToken(token).getSubject();
    }

    /**
     * Extracts the role claim from the token.
     */
    public String extractRole(String token) {
        return validateToken(token).get("role", String.class);
    }

    /**
     * Extracts the JWT ID (jti) from the token.
     */
    public String extractJti(String token) {
        return validateToken(token).getId();
    }

    /**
     * Returns the configured access token lifetime in seconds.
     */
    public int getExpirySeconds() {
        return jwtConfig.getExpirySeconds();
    }

    private SecretKey getSigningKey() {
        return Keys.hmacShaKeyFor(jwtConfig.getSecret().getBytes(StandardCharsets.UTF_8));
    }
}
