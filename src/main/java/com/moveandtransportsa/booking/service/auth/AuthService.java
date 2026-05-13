package com.moveandtransportsa.booking.service.auth;

import com.moveandtransportsa.booking.domain.auth.AdminUser;
import com.moveandtransportsa.booking.domain.auth.RefreshToken;
import com.moveandtransportsa.booking.dto.response.AdminUserResponse;
import com.moveandtransportsa.booking.dto.response.TokenPair;
import com.moveandtransportsa.booking.repository.AdminUserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final AdminUserRepository adminUserRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final RefreshTokenService refreshTokenService;

    /**
     * Authenticates a user by username and password.
     * Returns a {@link TokenPair} on success.
     * Throws a generic {@link BadCredentialsException} on any failure to avoid
     * leaking information about which field was wrong.
     */
    @Transactional
    public TokenPair login(String username, String password) {
        AdminUser user = adminUserRepository.findByUsername(username)
                .orElseThrow(() -> new BadCredentialsException("Invalid credentials"));

        if (!passwordEncoder.matches(password, user.getPasswordHash())) {
            throw new BadCredentialsException("Invalid credentials");
        }

        if (!user.isActive()) {
            throw new BadCredentialsException("Invalid credentials");
        }

        String jwt = jwtService.generateToken(user);
        RefreshToken refreshToken = refreshTokenService.issue(user);

        return new TokenPair(jwt, refreshToken.getToken(), jwtService.getExpirySeconds(), user.getRole().name());
    }

    /**
     * Returns the profile of the currently authenticated user.
     */
    @Transactional(readOnly = true)
    public AdminUserResponse me(String username) {
        AdminUser user = adminUserRepository.findByUsername(username)
                .orElseThrow(() -> new BadCredentialsException("User not found"));
        return toResponse(user);
    }

    /**
     * Rotates a refresh token and returns a new {@link TokenPair}.
     */
    @Transactional
    public TokenPair refresh(String rawToken) {
        return refreshTokenService.rotate(rawToken);
    }

    /**
     * Logs out the user by invalidating all their refresh tokens.
     */
    @Transactional
    public void logout(Long adminUserId) {
        refreshTokenService.invalidateForUser(adminUserId);
    }

    private AdminUserResponse toResponse(AdminUser user) {
        return new AdminUserResponse(user.getId(), user.getUsername(), user.getDisplayName(), user.getRole().name());
    }
}
