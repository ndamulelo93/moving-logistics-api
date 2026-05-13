package com.moveandtransportsa.booking.service.auth;

import com.moveandtransportsa.booking.config.JwtConfig;
import com.moveandtransportsa.booking.domain.auth.AdminUser;
import com.moveandtransportsa.booking.domain.auth.RefreshToken;
import com.moveandtransportsa.booking.dto.response.TokenPair;
import com.moveandtransportsa.booking.repository.RefreshTokenRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class RefreshTokenService {

    private final RefreshTokenRepository refreshTokenRepository;
    private final JwtConfig jwtConfig;
    private final JwtService jwtService;

    /**
     * Issues a brand-new refresh token for the given user.
     * A new family ID is created, meaning this is the root of a new token family.
     */
    @Transactional
    public RefreshToken issue(AdminUser user) {
        RefreshToken token = RefreshToken.builder()
                .token(UUID.randomUUID().toString())
                .familyId(UUID.randomUUID().toString())
                .adminUser(user)
                .used(false)
                .expiresAt(Instant.now().plus(jwtConfig.getRefreshExpiryDays(), ChronoUnit.DAYS))
                .build();
        return refreshTokenRepository.save(token);
    }

    /**
     * Rotates a refresh token:
     * <ol>
     *   <li>Looks up the token — 401 if not found.</li>
     *   <li>Checks expiry — 401 if expired.</li>
     *   <li>Checks reuse — if already used, deletes the entire family and throws 401.</li>
     *   <li>Marks the current token as used, issues a new one in the same family.</li>
     *   <li>Returns a new {@link TokenPair} with a fresh JWT and the new refresh token.</li>
     * </ol>
     */
    @Transactional
    public TokenPair rotate(String rawToken) {
        RefreshToken token = refreshTokenRepository.findByToken(rawToken)
                .orElseThrow(() -> new BadCredentialsException("Invalid refresh token"));

        if (token.isExpired()) {
            throw new BadCredentialsException("Refresh token expired");
        }

        if (token.isUsed()) {
            // Reuse detected — invalidate the entire family to protect against token theft
            refreshTokenRepository.deleteByFamilyId(token.getFamilyId());
            throw new BadCredentialsException("Refresh token reuse detected");
        }

        // Mark current token as used
        token.setUsed(true);
        refreshTokenRepository.save(token);

        // Issue a new token in the same family
        AdminUser user = token.getAdminUser();
        RefreshToken newToken = RefreshToken.builder()
                .token(UUID.randomUUID().toString())
                .familyId(token.getFamilyId())
                .adminUser(user)
                .used(false)
                .expiresAt(Instant.now().plus(jwtConfig.getRefreshExpiryDays(), ChronoUnit.DAYS))
                .build();
        refreshTokenRepository.save(newToken);

        String newJwt = jwtService.generateToken(user);
        return new TokenPair(newJwt, newToken.getToken(), jwtConfig.getExpirySeconds(), user.getRole().name());
    }

    /**
     * Invalidates all refresh tokens for the given user (logout).
     */
    @Transactional
    public void invalidateForUser(Long adminUserId) {
        refreshTokenRepository.deleteByAdminUserId(adminUserId);
    }
}
