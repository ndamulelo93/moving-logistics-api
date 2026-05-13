package com.moveandtransportsa.booking.web.admin;

import com.moveandtransportsa.booking.dto.request.LoginRequest;
import com.moveandtransportsa.booking.dto.request.RefreshTokenRequest;
import com.moveandtransportsa.booking.dto.response.AdminUserResponse;
import com.moveandtransportsa.booking.dto.response.TokenPair;
import com.moveandtransportsa.booking.service.auth.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    /**
     * POST /api/auth/login
     * Authenticates with username + password and returns a token pair.
     */
    @PostMapping("/login")
    public ResponseEntity<TokenPair> login(@Valid @RequestBody LoginRequest request) {
        TokenPair tokenPair = authService.login(request.username(), request.password());
        return ResponseEntity.ok(tokenPair);
    }

    /**
     * POST /api/auth/refresh
     * Rotates a refresh token and returns a new token pair.
     */
    @PostMapping("/refresh")
    public ResponseEntity<TokenPair> refresh(@Valid @RequestBody RefreshTokenRequest request) {
        TokenPair tokenPair = authService.refresh(request.refreshToken());
        return ResponseEntity.ok(tokenPair);
    }

    /**
     * POST /api/auth/logout
     * Invalidates all refresh tokens for the currently authenticated user.
     */
    @PostMapping("/logout")
    public ResponseEntity<Void> logout() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        Long adminUserId = (Long) auth.getDetails();
        authService.logout(adminUserId);
        return ResponseEntity.noContent().build();
    }

    /**
     * GET /api/auth/me
     * Returns the profile of the currently authenticated user.
     */
    @GetMapping("/me")
    public ResponseEntity<AdminUserResponse> me() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String username = auth.getName();
        return ResponseEntity.ok(authService.me(username));
    }
}
