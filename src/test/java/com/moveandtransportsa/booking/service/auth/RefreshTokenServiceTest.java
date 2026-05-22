package com.moveandtransportsa.booking.service.auth;

import com.moveandtransportsa.booking.config.JwtConfig;
import com.moveandtransportsa.booking.domain.auth.AdminRole;
import com.moveandtransportsa.booking.domain.auth.AdminUser;
import com.moveandtransportsa.booking.domain.auth.RefreshToken;
import com.moveandtransportsa.booking.dto.response.TokenPair;
import com.moveandtransportsa.booking.repository.RefreshTokenRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.authentication.BadCredentialsException;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link RefreshTokenService}.
 *
 * Requirements covered: 7.3, 7.4
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RefreshTokenServiceTest {

    private static final String SECRET = "test-secret-key-that-is-32-chars!";
    private static final int EXPIRY_SECONDS = 900;
    private static final int REFRESH_EXPIRY_DAYS = 7;

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    @Mock
    private JwtConfig jwtConfig;

    @Mock
    private JwtService jwtService;

    @InjectMocks
    private RefreshTokenService refreshTokenService;

    private AdminUser adminUser;

    @BeforeEach
    void setUp() {
        when(jwtConfig.getRefreshExpiryDays()).thenReturn(REFRESH_EXPIRY_DAYS);

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
    // issue — creates a new refresh token with a new family
    // -----------------------------------------------------------------------

    @Test
    void issue_savesTokenWithNewFamilyId() {
        ArgumentCaptor<RefreshToken> captor = ArgumentCaptor.forClass(RefreshToken.class);
        when(refreshTokenRepository.save(captor.capture())).thenAnswer(inv -> inv.getArgument(0));

        refreshTokenService.issue(adminUser);

        RefreshToken saved = captor.getValue();
        assertThat(saved.getFamilyId()).isNotNull().isNotBlank();
        assertThat(saved.getToken()).isNotNull().isNotBlank();
    }

    @Test
    void issue_tokenIsNotUsed() {
        ArgumentCaptor<RefreshToken> captor = ArgumentCaptor.forClass(RefreshToken.class);
        when(refreshTokenRepository.save(captor.capture())).thenAnswer(inv -> inv.getArgument(0));

        refreshTokenService.issue(adminUser);

        assertThat(captor.getValue().isUsed()).isFalse();
    }

    @Test
    void issue_expiryIsApproximatelySevenDaysFromNow() {
        ArgumentCaptor<RefreshToken> captor = ArgumentCaptor.forClass(RefreshToken.class);
        when(refreshTokenRepository.save(captor.capture())).thenAnswer(inv -> inv.getArgument(0));

        Instant before = Instant.now();
        refreshTokenService.issue(adminUser);
        Instant after = Instant.now();

        Instant expiry = captor.getValue().getExpiresAt();
        assertThat(expiry).isBetween(
                before.plus(REFRESH_EXPIRY_DAYS, ChronoUnit.DAYS).minusSeconds(2),
                after.plus(REFRESH_EXPIRY_DAYS, ChronoUnit.DAYS).plusSeconds(2)
        );
    }

    @Test
    void issue_associatesTokenWithCorrectUser() {
        ArgumentCaptor<RefreshToken> captor = ArgumentCaptor.forClass(RefreshToken.class);
        when(refreshTokenRepository.save(captor.capture())).thenAnswer(inv -> inv.getArgument(0));

        refreshTokenService.issue(adminUser);

        assertThat(captor.getValue().getAdminUser()).isSameAs(adminUser);
    }

    @Test
    void issue_twoCallsProduceDifferentTokenValues() {
        when(refreshTokenRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        RefreshToken first = refreshTokenService.issue(adminUser);
        RefreshToken second = refreshTokenService.issue(adminUser);

        assertThat(first.getToken()).isNotEqualTo(second.getToken());
    }

    @Test
    void issue_twoCallsProduceDifferentFamilyIds() {
        when(refreshTokenRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        RefreshToken first = refreshTokenService.issue(adminUser);
        RefreshToken second = refreshTokenService.issue(adminUser);

        assertThat(first.getFamilyId()).isNotEqualTo(second.getFamilyId());
    }

    // -----------------------------------------------------------------------
    // rotate — valid rotation
    // -----------------------------------------------------------------------

    @Test
    void rotate_validToken_returnsNewTokenPair() {
        String rawToken = UUID.randomUUID().toString();
        String familyId = UUID.randomUUID().toString();
        RefreshToken existing = buildToken(rawToken, familyId, false, Instant.now().plus(7, ChronoUnit.DAYS));

        when(refreshTokenRepository.findByToken(rawToken)).thenReturn(Optional.of(existing));
        when(refreshTokenRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(jwtService.generateToken(adminUser)).thenReturn("new.jwt.token");
        when(jwtConfig.getExpirySeconds()).thenReturn(EXPIRY_SECONDS);

        TokenPair result = refreshTokenService.rotate(rawToken);

        assertThat(result.accessToken()).isEqualTo("new.jwt.token");
        assertThat(result.refreshToken()).isNotNull().isNotBlank();
        assertThat(result.refreshToken()).isNotEqualTo(rawToken);
    }

    @Test
    void rotate_validToken_marksOldTokenAsUsed() {
        String rawToken = UUID.randomUUID().toString();
        String familyId = UUID.randomUUID().toString();
        RefreshToken existing = buildToken(rawToken, familyId, false, Instant.now().plus(7, ChronoUnit.DAYS));

        when(refreshTokenRepository.findByToken(rawToken)).thenReturn(Optional.of(existing));
        when(refreshTokenRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(jwtService.generateToken(adminUser)).thenReturn("new.jwt.token");
        when(jwtConfig.getExpirySeconds()).thenReturn(EXPIRY_SECONDS);

        refreshTokenService.rotate(rawToken);

        assertThat(existing.isUsed()).isTrue();
    }

    @Test
    void rotate_validToken_newTokenIsInSameFamily() {
        String rawToken = UUID.randomUUID().toString();
        String familyId = UUID.randomUUID().toString();
        RefreshToken existing = buildToken(rawToken, familyId, false, Instant.now().plus(7, ChronoUnit.DAYS));

        ArgumentCaptor<RefreshToken> captor = ArgumentCaptor.forClass(RefreshToken.class);
        when(refreshTokenRepository.findByToken(rawToken)).thenReturn(Optional.of(existing));
        when(refreshTokenRepository.save(captor.capture())).thenAnswer(inv -> inv.getArgument(0));
        when(jwtService.generateToken(adminUser)).thenReturn("new.jwt.token");
        when(jwtConfig.getExpirySeconds()).thenReturn(EXPIRY_SECONDS);

        refreshTokenService.rotate(rawToken);

        // The second save call is the new token; find it by checking used=false and different token value
        RefreshToken newToken = captor.getAllValues().stream()
                .filter(t -> !t.isUsed())
                .findFirst()
                .orElseThrow();
        assertThat(newToken.getFamilyId()).isEqualTo(familyId);
    }

    @Test
    void rotate_validToken_responseContainsCorrectRole() {
        String rawToken = UUID.randomUUID().toString();
        RefreshToken existing = buildToken(rawToken, UUID.randomUUID().toString(), false,
                Instant.now().plus(7, ChronoUnit.DAYS));

        when(refreshTokenRepository.findByToken(rawToken)).thenReturn(Optional.of(existing));
        when(refreshTokenRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(jwtService.generateToken(adminUser)).thenReturn("new.jwt.token");
        when(jwtConfig.getExpirySeconds()).thenReturn(EXPIRY_SECONDS);

        TokenPair result = refreshTokenService.rotate(rawToken);

        assertThat(result.role()).isEqualTo("DISPATCHER");
        assertThat(result.expiresIn()).isEqualTo(EXPIRY_SECONDS);
    }

    // -----------------------------------------------------------------------
    // rotate — token not found
    // -----------------------------------------------------------------------

    @Test
    void rotate_unknownToken_throwsBadCredentialsException() {
        when(refreshTokenRepository.findByToken("unknown-token")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> refreshTokenService.rotate("unknown-token"))
                .isInstanceOf(BadCredentialsException.class)
                .hasMessageContaining("Invalid refresh token");
    }

    // -----------------------------------------------------------------------
    // rotate — expired token rejection
    // -----------------------------------------------------------------------

    @Test
    void rotate_expiredToken_throwsBadCredentialsException() {
        String rawToken = UUID.randomUUID().toString();
        // Token expired 1 second ago
        RefreshToken expired = buildToken(rawToken, UUID.randomUUID().toString(), false,
                Instant.now().minus(1, ChronoUnit.SECONDS));

        when(refreshTokenRepository.findByToken(rawToken)).thenReturn(Optional.of(expired));

        assertThatThrownBy(() -> refreshTokenService.rotate(rawToken))
                .isInstanceOf(BadCredentialsException.class)
                .hasMessageContaining("expired");
    }

    @Test
    void rotate_expiredToken_doesNotDeleteFamily() {
        String rawToken = UUID.randomUUID().toString();
        String familyId = UUID.randomUUID().toString();
        RefreshToken expired = buildToken(rawToken, familyId, false,
                Instant.now().minus(1, ChronoUnit.SECONDS));

        when(refreshTokenRepository.findByToken(rawToken)).thenReturn(Optional.of(expired));

        assertThatThrownBy(() -> refreshTokenService.rotate(rawToken))
                .isInstanceOf(BadCredentialsException.class);

        // Family should NOT be deleted for a simple expiry (only reuse triggers family deletion)
        verify(refreshTokenRepository, never()).deleteByFamilyId(any());
    }

    // -----------------------------------------------------------------------
    // rotate — reuse detection and family invalidation
    // -----------------------------------------------------------------------

    @Test
    void rotate_alreadyUsedToken_throwsBadCredentialsException() {
        String rawToken = UUID.randomUUID().toString();
        // Token is marked as used (reuse scenario)
        RefreshToken used = buildToken(rawToken, UUID.randomUUID().toString(), true,
                Instant.now().plus(7, ChronoUnit.DAYS));

        when(refreshTokenRepository.findByToken(rawToken)).thenReturn(Optional.of(used));

        assertThatThrownBy(() -> refreshTokenService.rotate(rawToken))
                .isInstanceOf(BadCredentialsException.class)
                .hasMessageContaining("reuse");
    }

    @Test
    void rotate_alreadyUsedToken_deletesEntireFamily() {
        String rawToken = UUID.randomUUID().toString();
        String familyId = UUID.randomUUID().toString();
        RefreshToken used = buildToken(rawToken, familyId, true,
                Instant.now().plus(7, ChronoUnit.DAYS));

        when(refreshTokenRepository.findByToken(rawToken)).thenReturn(Optional.of(used));

        assertThatThrownBy(() -> refreshTokenService.rotate(rawToken))
                .isInstanceOf(BadCredentialsException.class);

        verify(refreshTokenRepository).deleteByFamilyId(familyId);
    }

    @Test
    void rotate_alreadyUsedToken_doesNotIssueNewToken() {
        String rawToken = UUID.randomUUID().toString();
        RefreshToken used = buildToken(rawToken, UUID.randomUUID().toString(), true,
                Instant.now().plus(7, ChronoUnit.DAYS));

        when(refreshTokenRepository.findByToken(rawToken)).thenReturn(Optional.of(used));

        assertThatThrownBy(() -> refreshTokenService.rotate(rawToken))
                .isInstanceOf(BadCredentialsException.class);

        // No new token should be saved
        verify(refreshTokenRepository, never()).save(any());
    }

    // -----------------------------------------------------------------------
    // invalidateForUser
    // -----------------------------------------------------------------------

    @Test
    void invalidateForUser_deletesAllTokensForUser() {
        refreshTokenService.invalidateForUser(1L);
        verify(refreshTokenRepository).deleteByAdminUserId(1L);
    }

    @Test
    void invalidateForUser_doesNotDeleteOtherUsersTokens() {
        refreshTokenService.invalidateForUser(1L);
        verify(refreshTokenRepository, never()).deleteByAdminUserId(2L);
    }

    // -----------------------------------------------------------------------
    // Helper
    // -----------------------------------------------------------------------

    private RefreshToken buildToken(String token, String familyId, boolean used, Instant expiresAt) {
        return RefreshToken.builder()
                .id(1L)
                .token(token)
                .familyId(familyId)
                .adminUser(adminUser)
                .used(used)
                .expiresAt(expiresAt)
                .build();
    }
}
