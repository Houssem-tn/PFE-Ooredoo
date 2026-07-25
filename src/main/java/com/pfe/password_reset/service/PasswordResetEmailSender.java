package com.pfe.password_reset.service;

import com.pfe.MainPackage.Service.EmailService;
import com.pfe.password_reset.config.PasswordResetProperties;
import com.pfe.password_reset.entity.PasswordResetTokenStatus;
import com.pfe.password_reset.repository.PasswordResetTokenRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * Sends the password-reset and confirmation emails on a background thread.
 *
 * <p>Running the send asynchronously keeps the forgot-password endpoint's
 * response time uniform whether or not an account exists (the slow, variable
 * SMTP call never happens on the request thread), which is what prevents timing
 * from leaking account existence. It is a separate bean (not a self-invoked
 * method) so Spring's {@code @Async} proxy actually applies.</p>
 *
 * <p>On send failure the just-created token is revoked so a repeatedly failing
 * mailbox cannot accumulate unlimited valid tokens, and a sanitized audit line
 * is recorded. The raw token, the full reset link and SMTP internals are never
 * logged.</p>
 */
@Component
public class PasswordResetEmailSender {

    private static final Logger log = LoggerFactory.getLogger(PasswordResetEmailSender.class);

    private final EmailService emailService;
    private final PasswordResetTokenRepository tokenRepository;
    private final PasswordResetAuditService auditService;
    private final PasswordResetProperties properties;

    public PasswordResetEmailSender(EmailService emailService,
                                    PasswordResetTokenRepository tokenRepository,
                                    PasswordResetAuditService auditService,
                                    PasswordResetProperties properties) {
        this.emailService = emailService;
        this.tokenRepository = tokenRepository;
        this.auditService = auditService;
        this.properties = properties;
    }

    @Async
    public void sendResetEmail(String toEmail, String resetLink, String tokenId, String userId, String ipHash) {
        String appName = properties.getEmailSenderName();
        long minutes = properties.getTokenExpirationMinutes();

        String subject = appName + " — Password reset request";
        String body = "Hello,\n\n"
                + "We received a request to reset the password for your " + appName + " account.\n\n"
                + "Reset your password using the link below:\n"
                + resetLink + "\n\n"
                + "This link expires in " + minutes + " minutes and can be used only once.\n\n"
                + "If you did not request a password reset, you can safely ignore this email — "
                + "your password will not change.\n\n"
                + "For any security concern, contact your platform administrator / support team.\n\n"
                + "— " + appName;

        try {
            emailService.send(toEmail, subject, body);
            auditService.record(PasswordResetAuditEvent.PASSWORD_RESET_EMAIL_SENT, userId, ipHash);
            log.info("password_reset_email_sent userId={}", userId);
        } catch (Exception e) {
            // Revoke the token we just created so a persistently failing mailbox
            // cannot leave a growing set of still-valid reset tokens behind.
            revokeQuietly(tokenId);
            auditService.record(PasswordResetAuditEvent.PASSWORD_RESET_EMAIL_FAILED, userId, ipHash,
                    "send_failed:" + e.getClass().getSimpleName());
            // Sanitized: class name only, never the link/token/SMTP credentials.
            log.warn("password_reset_email_failed userId={} error={}", userId, e.getClass().getSimpleName());
        }
    }

    @Async
    public void sendConfirmationEmail(String toEmail, String userId) {
        String appName = properties.getEmailSenderName();
        String subject = appName + " — Your password was changed";
        String body = "Hello,\n\n"
                + "This is a confirmation that the password for your " + appName + " account was just changed.\n\n"
                + "If you made this change, no further action is needed.\n\n"
                + "If you did NOT change your password, contact your platform administrator / support team "
                + "immediately — your account may be at risk.\n\n"
                + "— " + appName;

        try {
            emailService.send(toEmail, subject, body);
            log.info("password_reset_confirmation_email_sent userId={}", userId);
        } catch (Exception e) {
            log.warn("password_reset_confirmation_email_failed userId={} error={}", userId, e.getClass().getSimpleName());
        }
    }

    private void revokeQuietly(String tokenId) {
        if (tokenId == null) {
            return;
        }
        try {
            tokenRepository.findById(tokenId).ifPresent(token -> {
                if (token.getStatus() == PasswordResetTokenStatus.ACTIVE) {
                    token.setStatus(PasswordResetTokenStatus.REVOKED);
                    token.setRevokedAt(Instant.now());
                    tokenRepository.save(token);
                }
            });
        } catch (Exception e) {
            log.warn("password_reset_token_revoke_after_email_failure_failed error={}", e.getClass().getSimpleName());
        }
    }
}
