package com.moveandtransportsa.booking.filter;

import com.moveandtransportsa.booking.repository.AdminUserRepository;
import com.moveandtransportsa.booking.service.auth.JwtService;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtService jwtService;
    private final AdminUserRepository adminUserRepository;

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain chain
    ) throws ServletException, IOException {

        String authHeader = request.getHeader("Authorization");

        if (authHeader == null || !authHeader.startsWith(BEARER_PREFIX)) {
            log.debug("JWT filter: no Bearer token on {} {}", request.getMethod(), request.getRequestURI());
            chain.doFilter(request, response);
            return;
        }

        String token = authHeader.substring(BEARER_PREFIX.length());

        try {
            Claims claims = jwtService.validateToken(token);
            String username = claims.getSubject();
            String role = claims.get("role", String.class);

            log.debug("JWT filter: username={}, role={}, uri={}", username, role, request.getRequestURI());

            // Look up the user to get the numeric ID for logout support
            Long adminUserId = adminUserRepository.findByUsername(username)
                    .map(u -> u.getId())
                    .orElse(null);

            // Spring Security expects roles prefixed with ROLE_
            String authority = "ROLE_" + role;
            log.debug("JWT filter: granting authority={}", authority);

            UsernamePasswordAuthenticationToken authentication =
                    new UsernamePasswordAuthenticationToken(username, null, List.of(new SimpleGrantedAuthority(authority)));

            // Store the admin user ID in details so controllers can retrieve it
            authentication.setDetails(adminUserId);

            SecurityContextHolder.getContext().setAuthentication(authentication);
            log.debug("JWT filter: authentication set successfully for user={}", username);
        } catch (Exception e) {
            log.warn("JWT filter: token validation failed — {} — uri={}", e.getMessage(), request.getRequestURI());
            // Invalid token — clear context and let Spring Security return 401
            SecurityContextHolder.clearContext();
        }

        chain.doFilter(request, response);
    }
}
