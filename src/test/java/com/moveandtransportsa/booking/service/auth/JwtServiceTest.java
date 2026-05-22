package com.moveandtransportsa.booking.service.auth;

import com.moveandtransportsa.booking.config.JwtConfig;
import com.moveandtransportsa.booking.domain.auth.AdminRole;
import com.moveandtransportsa.booking.domain.auth.AdminUser;
import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.authentication.BadCredentialsException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import java.util.Date;

/**
 * Unit tests for {@link JwtService}.
 *
 * Requirements covered: 7.1
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class JwtServiceTest {

    // A 256-bit (32-char) secret — minimum required for HS256
    private static final String SECRET = "test-secret-key-that-is-32-chars!";
    private static final int EXPIRY_SECONDS = 900; // 15 minutes

    @Mock
    private JwtConfig jwtConfig;

    @InjectMocks
    private JwtService jwtService;

    private AdminUser adminUser;

    @BeforeEach
    void setUp() {
        when(jwtConfig.getSecret()).thenReturn(SECRET);
        when(jwtConfig.getExpirySeconds()).thenReturn(EXPIRY_SECONDS);

        adminUser = AdminUser.builder()
                .id(1L)
                .username("dispatcher1")
                .displayName("Dispatcher One")
                .passwordHash("$2a$12$hashed")
                .role(AdminRole.DISPATCHER)
                .active(true)
                .build();
    }

    // -----------------------------------------------------------------------
    // generateToken — format and claims
    // -----------------------------------------------------------------------

    @Test
    void generateToken_returnsNonNullToken() {
        String token = jwtService.generateToken(adminUser);
        assertThat(token).isNotNull().isNotBlank();
    }

    @Test
    void generateToken_tokenHasThreeParts() {
        // A compact JWT has exactly three Base64URL segments separated by dots
        String token = jwtService.generateToken(adminUser);
        assertThat(token.split("\\.")).hasSize(3);
    }

    @Test
    void generateToken_subjectClaimEqualsUsername() {
        String token = jwtService.generateToken(adminUser);
        Claims claims = jwtService.validateToken(token);
        assertThat(claims.getSubject()).isEqualTo("dispatcher1");
    }

    @Test
    void generateToken_roleClaimMatchesUserRole() {
        String token = jwtService.generateToken(adminUser);
        Claims claims = jwtService.validateToken(token);
        assertThat(claims.get("role", String.class)).isEqualTo("DISPATCHER");
    }

    @Test
    void generateToken_jtiClaimIsPresent() {
        String token = jwtService.generateToken(adminUser);
        Claims claims = jwtService.validateToken(token);
        assertThat(claims.getId()).isNotNull().isNotBlank();
    }

    @Test
    void generateToken_twoTokensHaveDifferentJti() {
        String token1 = jwtService.generateToken(adminUser);
        String token2 = jwtService.generateToken(adminUser);
        assertThat(jwtService.extractJti(token1)).isNotEqualTo(jwtService.extractJti(token2));
    }

    @Test
    void generateToken_expiryIsApproximatelyNowPlusExpirySeconds() {
        long beforeMs = System.currentTimeMillis();
        String token = jwtService.generateToken(adminUser);
        long afterMs = System.currentTimeMillis();

        Claims claims = jwtService.validateToken(token);
        Date expiration = claims.getExpiration();

        long expectedExpiryMs = beforeMs + (long) EXPIRY_SECONDS * 1000;
        // Allow 2-second tolerance for test execution time
        assertThat(expiration.getTime())
                .isBetween(expectedExpiryMs - 2000, afterMs + (long) EXPIRY_SECONDS * 1000 + 2000);
    }

    @Test
    void generateToken_superAdminRoleEncodedCorrectly() {
        AdminUser superAdmin = AdminUser.builder()
                .id(2L)
                .username("superadmin")
                .displayName("Super Admin")
                .passwordHash("$2a$12$hashed")
                .role(AdminRole.SUPER_ADMIN)
                .active(true)
                .build();

        String token = jwtService.generateToken(superAdmin);
        assertThat(jwtService.extractRole(token)).isEqualTo("SUPER_ADMIN");
    }

    @Test
    void generateToken_viewOnlyRoleEncodedCorrectly() {
        AdminUser viewOnly = AdminUser.builder()
                .id(3L)
                .username("viewer")
                .displayName("Viewer")
                .passwordHash("$2a$12$hashed")
                .role(AdminRole.VIEW_ONLY)
                .active(true)
                .build();

        String token = jwtService.generateToken(viewOnly);
        assertThat(jwtService.extractRole(token)).isEqualTo("VIEW_ONLY");
    }

    // -----------------------------------------------------------------------
    // validateToken — valid token
    // -----------------------------------------------------------------------

    @Test
    void validateToken_validToken_returnsClaims() {
        String token = jwtService.generateToken(adminUser);
        Claims claims = jwtService.validateToken(token);
        assertThat(claims).isNotNull();
        assertThat(claims.getSubject()).isEqualTo("dispatcher1");
    }

    // -----------------------------------------------------------------------
    // validateToken — invalid / tampered tokens
    // -----------------------------------------------------------------------

    @Test
    void validateToken_tamperedToken_throwsBadCredentialsException() {
        String token = jwtService.generateToken(adminUser);
        // Corrupt the signature segment
        String tampered = token.substring(0, token.lastIndexOf('.') + 1) + "invalidsignature";

        assertThatThrownBy(() -> jwtService.validateToken(tampered))
                .isInstanceOf(BadCredentialsException.class)
                .hasMessageContaining("Invalid or expired JWT token");
    }

    @Test
    void validateToken_randomString_throwsBadCredentialsException() {
        assertThatThrownBy(() -> jwtService.validateToken("not.a.jwt"))
                .isInstanceOf(BadCredentialsException.class);
    }

    @Test
    void validateToken_emptyString_throwsBadCredentialsException() {
        assertThatThrownBy(() -> jwtService.validateToken(""))
                .isInstanceOf(BadCredentialsException.class);
    }

    @Test
    void validateToken_tokenSignedWithDifferentSecret_throwsBadCredentialsException() {
        // Build a second JwtService with a different secret
        JwtConfig otherConfig = new JwtConfig();
        otherConfig.setSecret("different-secret-key-32-chars-xx!");
        otherConfig.setExpirySeconds(900);
        JwtService otherService = new JwtService(otherConfig);

        String tokenFromOtherService = otherService.generateToken(adminUser);

        // Our service should reject it
        assertThatThrownBy(() -> jwtService.validateToken(tokenFromOtherService))
                .isInstanceOf(BadCredentialsException.class);
    }

    // -----------------------------------------------------------------------
    // extractUsername / extractRole / extractJti
    // -----------------------------------------------------------------------

    @Test
    void extractUsername_returnsCorrectUsername() {
        String token = jwtService.generateToken(adminUser);
        assertThat(jwtService.extractUsername(token)).isEqualTo("dispatcher1");
    }

    @Test
    void extractRole_returnsCorrectRole() {
        String token = jwtService.generateToken(adminUser);
        assertThat(jwtService.extractRole(token)).isEqualTo("DISPATCHER");
    }

    @Test
    void extractJti_returnsNonBlankUuid() {
        String token = jwtService.generateToken(adminUser);
        String jti = jwtService.extractJti(token);
        assertThat(jti).isNotBlank();
        // Should be a valid UUID format
        assertThat(jti).matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");
    }

    // -----------------------------------------------------------------------
    // getExpirySeconds
    // -----------------------------------------------------------------------

    @Test
    void getExpirySeconds_returnsConfiguredValue() {
        assertThat(jwtService.getExpirySeconds()).isEqualTo(EXPIRY_SECONDS);
    }
}
