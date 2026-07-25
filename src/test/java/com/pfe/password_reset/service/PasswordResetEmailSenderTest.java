package com.pfe.password_reset.service;

import com.pfe.MainPackage.Service.EmailService;
import com.pfe.password_reset.config.PasswordResetProperties;
import com.pfe.password_reset.entity.PasswordResetToken;
import com.pfe.password_reset.entity.PasswordResetTokenStatus;
import com.pfe.password_reset.repository.PasswordResetTokenRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PasswordResetEmailSenderTest {

    @Mock private EmailService emailService;
    @Mock private PasswordResetTokenRepository tokenRepository;
    @Mock private PasswordResetAuditService auditService;

    private final PasswordResetProperties properties = new PasswordResetProperties();

    private PasswordResetEmailSender sender() {
        return new PasswordResetEmailSender(emailService, tokenRepository, auditService, properties);
    }

    @Test
    void sendResetEmail_success_recordsEmailSentAudit_andDoesNotLeakToken() {
        sender().sendResetEmail("user@example.com",
                "https://app.example.com/reset-password?token=RAWTOKEN", "token-1", "user-1", "iphash");

        ArgumentCaptor<String> body = ArgumentCaptor.forClass(String.class);
        verify(emailService).send(anyString(), anyString(), body.capture());
        // The email body carries the reset LINK (needed by the user) but never a
        // password or password hash.
        assertThat(body.getValue()).contains("reset-password?token=RAWTOKEN");
        assertThat(body.getValue().toLowerCase()).doesNotContain("password hash");

        verify(auditService).record(PasswordResetAuditEvent.PASSWORD_RESET_EMAIL_SENT, "user-1", "iphash");
    }

    @Test
    void sendResetEmail_failure_revokesTokenAndDoesNotThrow() {
        doThrow(new RuntimeException("SMTP down")).when(emailService).send(anyString(), anyString(), anyString());

        PasswordResetToken token = PasswordResetToken.builder()
                .id("token-1").userId("user-1").tokenHash("h")
                .status(PasswordResetTokenStatus.ACTIVE)
                .createdAt(Instant.now()).expiresAt(Instant.now().plusSeconds(600))
                .build();
        when(tokenRepository.findById("token-1")).thenReturn(Optional.of(token));

        // Email failure must NOT propagate (would otherwise reveal account existence)…
        assertThatCode(() -> sender().sendResetEmail("user@example.com",
                "https://app.example.com/reset-password?token=RAW", "token-1", "user-1", "iphash"))
                .doesNotThrowAnyException();

        // …and the unusable token is revoked so failures can't pile up valid tokens.
        verify(tokenRepository).save(token);
        assertThat(token.getStatus()).isEqualTo(PasswordResetTokenStatus.REVOKED);
        verify(auditService).record(org.mockito.ArgumentMatchers.eq(PasswordResetAuditEvent.PASSWORD_RESET_EMAIL_FAILED),
                org.mockito.ArgumentMatchers.eq("user-1"), org.mockito.ArgumentMatchers.eq("iphash"),
                anyString());
    }
}
