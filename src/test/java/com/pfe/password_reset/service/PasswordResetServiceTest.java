package com.pfe.password_reset.service;

import com.pfe.MainPackage.Entity.AccountStatus;
import com.pfe.MainPackage.Entity.AppUser;
import com.pfe.MainPackage.Entity.UserRole;
import com.pfe.MainPackage.Repository.UserRepository;
import com.pfe.MainPackage.Service.NotificationEventService;
import com.pfe.password_reset.config.PasswordResetProperties;
import com.pfe.password_reset.dto.ResetPasswordRequest;
import com.pfe.password_reset.entity.PasswordResetToken;
import com.pfe.password_reset.entity.PasswordResetTokenStatus;
import com.pfe.password_reset.repository.PasswordResetTokenRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link PasswordResetService}. Uses a real {@link ResetTokenGenerator}
 * and {@link PasswordPolicy} (so hashing / policy are exercised for real) and mocks
 * the persistence, rate-limiter, email and notification collaborators.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PasswordResetServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private PasswordResetTokenRepository tokenRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private PasswordResetRateLimiter rateLimiter;
    @Mock private PasswordResetAuditService auditService;
    @Mock private PasswordResetEmailSender emailSender;
    @Mock private NotificationEventService notificationEventService;
    @Mock private MongoTemplate mongoTemplate;

    private final ResetTokenGenerator tokenGenerator = new ResetTokenGenerator();
    private final PasswordPolicy passwordPolicy = new PasswordPolicy();
    private final PasswordResetProperties properties = new PasswordResetProperties();

    private static final String FRONTEND_URL = "https://app.example.com";
    private static final String CLIENT_IP = "203.0.113.7";

    private PasswordResetService service;

    @BeforeEach
    void setUp() {
        service = new PasswordResetService(
                userRepository, tokenRepository, tokenGenerator, passwordEncoder, passwordPolicy,
                rateLimiter, auditService, emailSender, notificationEventService, properties,
                mongoTemplate, FRONTEND_URL);

        when(rateLimiter.allowForgotPassword(anyString(), any())).thenReturn(true);
        when(rateLimiter.allowResetAttempt(any())).thenReturn(true);
        when(passwordEncoder.encode(anyString())).thenAnswer(inv -> "ENC(" + inv.getArgument(0) + ")");
        when(tokenRepository.save(any(PasswordResetToken.class))).thenAnswer(inv -> {
            PasswordResetToken t = inv.getArgument(0);
            if (t.getId() == null) {
                t.setId("token-1");
            }
            return t;
        });
    }

    private AppUser eligibleUser() {
        return AppUser.builder()
                .id("user-1")
                .email("user@example.com")
                .password("OLD-HASH")
                .role(UserRole.ROLE_STT)
                .status(AccountStatus.APPROVED)
                .enabled(true)
                .tokenVersion(2L)
                .build();
    }

    // ── forgot-password ──────────────────────────────────────────────────────

    @Test
    void forgotPassword_existingEligibleUser_createsHashedTokenAndSendsEmail() {
        AppUser user = eligibleUser();
        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(user));
        when(tokenRepository.findByUserIdAndStatus("user-1", PasswordResetTokenStatus.ACTIVE))
                .thenReturn(List.of());

        service.forgotPassword("  User@Example.com  ", CLIENT_IP, "JUnit-UA");

        ArgumentCaptor<PasswordResetToken> tokenCaptor = ArgumentCaptor.forClass(PasswordResetToken.class);
        verify(tokenRepository).save(tokenCaptor.capture());
        PasswordResetToken saved = tokenCaptor.getValue();

        ArgumentCaptor<String> linkCaptor = ArgumentCaptor.forClass(String.class);
        verify(emailSender).sendResetEmail(eq("user@example.com"), linkCaptor.capture(),
                anyString(), eq("user-1"), any());

        // (5) The raw token is never persisted — the stored field is a hash, and
        // it does not equal the raw token embedded in the email link.
        String resetLink = linkCaptor.getValue();
        assertThat(resetLink).startsWith(FRONTEND_URL + "/reset-password?token=");
        String rawToken = resetLink.substring(resetLink.indexOf("token=") + "token=".length());
        assertThat(saved.getTokenHash()).isNotEqualTo(rawToken);

        // (6) The stored hash is exactly SHA-256 of the raw token in the link.
        assertThat(saved.getTokenHash()).isEqualTo(tokenGenerator.hash(rawToken));

        assertThat(saved.getStatus()).isEqualTo(PasswordResetTokenStatus.ACTIVE);
        assertThat(saved.getUserId()).isEqualTo("user-1");
        // (7) Expiry sits inside the 15–30 minute policy band.
        long minutesToExpiry = Duration.between(saved.getCreatedAt(), saved.getExpiresAt()).toMinutes();
        assertThat(minutesToExpiry).isBetween(15L, 30L);
        // Raw IP is never stored — only a hash reference.
        assertThat(saved.getRequestIpHash()).isNotNull().isNotEqualTo(CLIENT_IP);
    }

    @Test
    void forgotPassword_unknownEmail_doesNotCreateTokenOrSendEmail() {
        when(userRepository.findByEmail("ghost@example.com")).thenReturn(Optional.empty());

        service.forgotPassword("ghost@example.com", CLIENT_IP, "UA");

        verify(tokenRepository, never()).save(any());
        verify(emailSender, never()).sendResetEmail(anyString(), anyString(), any(), any(), any());
    }

    @Test
    void forgotPassword_disabledAccount_doesNotSendEmail() {
        AppUser user = eligibleUser();
        user.setEnabled(false);
        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(user));

        service.forgotPassword("user@example.com", CLIENT_IP, "UA");

        verify(tokenRepository, never()).save(any());
        verify(emailSender, never()).sendResetEmail(anyString(), anyString(), any(), any(), any());
    }

    @Test
    void forgotPassword_unapprovedAccount_doesNotSendEmail() {
        AppUser user = eligibleUser();
        user.setStatus(AccountStatus.PENDING);
        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(user));

        service.forgotPassword("user@example.com", CLIENT_IP, "UA");

        verify(tokenRepository, never()).save(any());
        verify(emailSender, never()).sendResetEmail(anyString(), anyString(), any(), any(), any());
    }

    @Test
    void forgotPassword_rateLimited_doesNotSendEmail_andStaysGeneric() {
        when(rateLimiter.allowForgotPassword(anyString(), any())).thenReturn(false);

        // No throw — the controller still returns the identical generic message.
        service.forgotPassword("user@example.com", CLIENT_IP, "UA");

        verify(userRepository, never()).findByEmail(anyString());
        verify(tokenRepository, never()).save(any());
        verify(auditService).record(eq(PasswordResetAuditEvent.PASSWORD_RESET_RATE_LIMITED), any(), any(), anyString());
    }

    @Test
    void forgotPassword_revokesPreviousUnusedTokens() {
        AppUser user = eligibleUser();
        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(user));
        PasswordResetToken old = PasswordResetToken.builder()
                .id("old-token").userId("user-1").tokenHash("oldhash")
                .status(PasswordResetTokenStatus.ACTIVE)
                .createdAt(Instant.now()).expiresAt(Instant.now().plusSeconds(600))
                .build();
        when(tokenRepository.findByUserIdAndStatus("user-1", PasswordResetTokenStatus.ACTIVE))
                .thenReturn(List.of(old));

        service.forgotPassword("user@example.com", CLIENT_IP, "UA");

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<PasswordResetToken>> captor = ArgumentCaptor.forClass(List.class);
        verify(tokenRepository).saveAll(captor.capture());
        assertThat(captor.getValue()).allSatisfy(t ->
                assertThat(t.getStatus()).isEqualTo(PasswordResetTokenStatus.REVOKED));
        assertThat(old.getRevokedAt()).isNotNull();
    }

    // ── reset-password ───────────────────────────────────────────────────────

    private PasswordResetToken activeTokenFor(String rawToken, String userId) {
        return PasswordResetToken.builder()
                .id("token-1")
                .userId(userId)
                .tokenHash(tokenGenerator.hash(rawToken))
                .status(PasswordResetTokenStatus.ACTIVE)
                .createdAt(Instant.now())
                .expiresAt(Instant.now().plus(Duration.ofMinutes(15)))
                .build();
    }

    private void stubSuccessfulClaim(PasswordResetToken token) {
        when(mongoTemplate.findAndModify(any(Query.class), any(Update.class),
                any(FindAndModifyOptions.class), eq(PasswordResetToken.class)))
                .thenAnswer(inv -> {
                    token.setStatus(PasswordResetTokenStatus.USED);
                    token.setUsedAt(Instant.now());
                    return token;
                });
    }

    @Test
    void resetPassword_validToken_encodesPasswordBumpsVersionAndKeepsAccountFlags() {
        String rawToken = "valid-raw-token-value-1234567890";
        PasswordResetToken token = activeTokenFor(rawToken, "user-1");
        AppUser user = eligibleUser();

        when(tokenRepository.findByTokenHash(token.getTokenHash())).thenReturn(Optional.of(token));
        stubSuccessfulClaim(token);
        when(userRepository.findById("user-1")).thenReturn(Optional.of(user));
        when(tokenRepository.findByUserIdAndStatus("user-1", PasswordResetTokenStatus.ACTIVE))
                .thenReturn(List.of());

        service.resetPassword(new ResetPasswordRequest(rawToken, "NewSecurePass123!", "NewSecurePass123!"), CLIENT_IP);

        ArgumentCaptor<AppUser> userCaptor = ArgumentCaptor.forClass(AppUser.class);
        verify(userRepository).save(userCaptor.capture());
        AppUser saved = userCaptor.getValue();

        // (19) Password is encoded, never stored in clear text.
        assertThat(saved.getPassword()).isEqualTo("ENC(NewSecurePass123!)");
        assertThat(saved.getPassword()).isNotEqualTo("NewSecurePass123!");
        assertThat(saved.getPasswordChangedAt()).isNotNull();
        // (23) Old JWTs invalidated: tokenVersion incremented.
        assertThat(saved.getTokenVersion()).isEqualTo(3L);
        // (20-22) role / enabled / approval untouched.
        assertThat(saved.getRole()).isEqualTo(UserRole.ROLE_STT);
        assertThat(saved.isEnabled()).isTrue();
        assertThat(saved.getStatus()).isEqualTo(AccountStatus.APPROVED);

        // (25) confirmation email requested; security notification emitted.
        verify(emailSender).sendConfirmationEmail("user@example.com", "user-1");
        verify(notificationEventService).onPasswordChanged(user);
        verify(auditService).record(eq(PasswordResetAuditEvent.PASSWORD_RESET_COMPLETED), eq("user-1"), any());
    }

    @Test
    void resetPassword_usedToken_isRejectedAsInvalidLink() {
        String rawToken = "used-raw-token-value-1234567890";
        PasswordResetToken token = activeTokenFor(rawToken, "user-1");
        token.setStatus(PasswordResetTokenStatus.USED);
        when(tokenRepository.findByTokenHash(token.getTokenHash())).thenReturn(Optional.of(token));

        assertThatThrownBy(() -> service.resetPassword(
                new ResetPasswordRequest(rawToken, "NewSecurePass123!", "NewSecurePass123!"), CLIENT_IP))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Invalid or expired reset link");

        verify(userRepository, never()).save(any());
        verify(auditService).record(eq(PasswordResetAuditEvent.PASSWORD_RESET_TOKEN_REUSED), eq("user-1"), any());
    }

    @Test
    void resetPassword_expiredToken_isRejected() {
        String rawToken = "expired-raw-token-value-1234567890";
        PasswordResetToken token = activeTokenFor(rawToken, "user-1");
        token.setExpiresAt(Instant.now().minusSeconds(1));
        when(tokenRepository.findByTokenHash(token.getTokenHash())).thenReturn(Optional.of(token));

        assertThatThrownBy(() -> service.resetPassword(
                new ResetPasswordRequest(rawToken, "NewSecurePass123!", "NewSecurePass123!"), CLIENT_IP))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Invalid or expired reset link");

        verify(userRepository, never()).save(any());
        verify(auditService).record(eq(PasswordResetAuditEvent.PASSWORD_RESET_TOKEN_EXPIRED), eq("user-1"), any());
    }

    @Test
    void resetPassword_revokedToken_isRejected() {
        String rawToken = "revoked-raw-token-value-1234567890";
        PasswordResetToken token = activeTokenFor(rawToken, "user-1");
        token.setStatus(PasswordResetTokenStatus.REVOKED);
        when(tokenRepository.findByTokenHash(token.getTokenHash())).thenReturn(Optional.of(token));

        assertThatThrownBy(() -> service.resetPassword(
                new ResetPasswordRequest(rawToken, "NewSecurePass123!", "NewSecurePass123!"), CLIENT_IP))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Invalid or expired reset link");

        verify(userRepository, never()).save(any());
    }

    @Test
    void resetPassword_unknownToken_isRejected() {
        when(tokenRepository.findByTokenHash(anyString())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.resetPassword(
                new ResetPasswordRequest("some-random-token-value-1234567890", "NewSecurePass123!", "NewSecurePass123!"),
                CLIENT_IP))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Invalid or expired reset link");

        verify(userRepository, never()).save(any());
    }

    @Test
    void resetPassword_lostRaceOnClaim_isTreatedAsReuse() {
        String rawToken = "raced-raw-token-value-1234567890";
        PasswordResetToken token = activeTokenFor(rawToken, "user-1");
        when(tokenRepository.findByTokenHash(token.getTokenHash())).thenReturn(Optional.of(token));
        when(mongoTemplate.findAndModify(any(Query.class), any(Update.class),
                any(FindAndModifyOptions.class), eq(PasswordResetToken.class))).thenReturn(null);

        assertThatThrownBy(() -> service.resetPassword(
                new ResetPasswordRequest(rawToken, "NewSecurePass123!", "NewSecurePass123!"), CLIENT_IP))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Invalid or expired reset link");

        verify(userRepository, never()).save(any());
        verify(auditService).record(eq(PasswordResetAuditEvent.PASSWORD_RESET_TOKEN_REUSED), eq("user-1"), any());
    }

    @Test
    void resetPassword_passwordMismatch_isRejected() {
        assertThatThrownBy(() -> service.resetPassword(
                new ResetPasswordRequest("raw-token-value-1234567890", "NewSecurePass123!", "Different123!"), CLIENT_IP))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Password confirmation does not match");

        verify(tokenRepository, never()).findByTokenHash(anyString());
    }

    @Test
    void resetPassword_weakPassword_isRejected() {
        assertThatThrownBy(() -> service.resetPassword(
                new ResetPasswordRequest("raw-token-value-1234567890", "123", "123"), CLIENT_IP))
                .isInstanceOf(IllegalArgumentException.class);

        verify(userRepository, never()).save(any());
    }

    @Test
    void resetPassword_excessivelyLongPassword_isRejected() {
        String longPassword = "A1".repeat(80); // 160 chars > 120 max
        assertThatThrownBy(() -> service.resetPassword(
                new ResetPasswordRequest("raw-token-value-1234567890", longPassword, longPassword), CLIENT_IP))
                .isInstanceOf(IllegalArgumentException.class);

        verify(userRepository, never()).save(any());
    }

    @Test
    void resetPassword_rateLimited_throwsTooManyRequests() {
        when(rateLimiter.allowResetAttempt(any())).thenReturn(false);

        assertThatThrownBy(() -> service.resetPassword(
                new ResetPasswordRequest("raw-token-value-1234567890", "NewSecurePass123!", "NewSecurePass123!"),
                CLIENT_IP))
                .isInstanceOf(ResponseStatusException.class);

        verify(userRepository, never()).save(any());
    }

    @Test
    void resetPassword_accountDisabledAfterIssue_isRejectedAndNotModified() {
        String rawToken = "raw-token-value-disabled-1234567890";
        PasswordResetToken token = activeTokenFor(rawToken, "user-1");
        AppUser user = eligibleUser();
        user.setEnabled(false); // banned after the link was issued

        when(tokenRepository.findByTokenHash(token.getTokenHash())).thenReturn(Optional.of(token));
        stubSuccessfulClaim(token);
        when(userRepository.findById("user-1")).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> service.resetPassword(
                new ResetPasswordRequest(rawToken, "NewSecurePass123!", "NewSecurePass123!"), CLIENT_IP))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Invalid or expired reset link");

        verify(userRepository, never()).save(any());
    }
}
