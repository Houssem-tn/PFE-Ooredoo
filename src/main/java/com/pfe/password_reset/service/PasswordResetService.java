package com.pfe.password_reset.service;

import com.pfe.MainPackage.Entity.AccountStatus;
import com.pfe.MainPackage.Entity.AppUser;
import com.pfe.MainPackage.Repository.UserRepository;
import com.pfe.MainPackage.Service.NotificationEventService;
import com.pfe.password_reset.config.PasswordResetProperties;
import com.pfe.password_reset.dto.ResetPasswordRequest;
import com.pfe.password_reset.entity.PasswordResetToken;
import com.pfe.password_reset.entity.PasswordResetTokenStatus;
import com.pfe.password_reset.repository.PasswordResetTokenRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Orchestrates the forgot-password and reset-password flows.
 *
 * <h2>Account-existence privacy</h2>
 * {@link #forgotPassword} performs the same externally-observable work
 * (returns {@code void}; the caller always sends the identical generic message)
 * regardless of whether the account exists, is disabled, is unapproved or is
 * missing. The slow, variable part — the SMTP send — is delegated to an async
 * sender so response timing does not leak existence either.
 *
 * <h2>Account controls are never bypassed</h2>
 * A reset only ever changes {@code password}, {@code passwordChangedAt} and
 * {@code tokenVersion}. It never touches {@code role}, {@code status},
 * {@code enabled} or any assignment. Only APPROVED + enabled accounts may
 * receive or complete a reset (chosen business rule); everyone else silently
 * gets the generic response.
 *
 * <h2>Atomicity without transactions</h2>
 * The deployment is not assumed to be a transaction-capable replica set, so this
 * uses a single atomic {@code findAndModify} to claim the token (ACTIVE → USED)
 * as the linearization point that guarantees single use even under concurrency,
 * followed by safe-ordered writes. See {@link #resetPassword} for the ordering
 * and its compensation reasoning.
 */
@Service
public class PasswordResetService {

    private static final Logger log = LoggerFactory.getLogger(PasswordResetService.class);
    private static final String GENERIC_INVALID_LINK = "Invalid or expired reset link";

    private final UserRepository userRepository;
    private final PasswordResetTokenRepository tokenRepository;
    private final ResetTokenGenerator tokenGenerator;
    private final PasswordEncoder passwordEncoder;
    private final PasswordPolicy passwordPolicy;
    private final PasswordResetRateLimiter rateLimiter;
    private final PasswordResetAuditService auditService;
    private final PasswordResetEmailSender emailSender;
    private final NotificationEventService notificationEventService;
    private final PasswordResetProperties properties;
    private final MongoTemplate mongoTemplate;

    private final String frontendBaseUrl;

    public PasswordResetService(UserRepository userRepository,
                                PasswordResetTokenRepository tokenRepository,
                                ResetTokenGenerator tokenGenerator,
                                PasswordEncoder passwordEncoder,
                                PasswordPolicy passwordPolicy,
                                PasswordResetRateLimiter rateLimiter,
                                PasswordResetAuditService auditService,
                                PasswordResetEmailSender emailSender,
                                NotificationEventService notificationEventService,
                                PasswordResetProperties properties,
                                MongoTemplate mongoTemplate,
                                @Value("${app.frontend-url}") String frontendBaseUrl) {
        this.userRepository = userRepository;
        this.tokenRepository = tokenRepository;
        this.tokenGenerator = tokenGenerator;
        this.passwordEncoder = passwordEncoder;
        this.passwordPolicy = passwordPolicy;
        this.rateLimiter = rateLimiter;
        this.auditService = auditService;
        this.emailSender = emailSender;
        this.notificationEventService = notificationEventService;
        this.properties = properties;
        this.mongoTemplate = mongoTemplate;
        this.frontendBaseUrl = frontendBaseUrl;
    }

    // ── Forgot password ────────────────────────────────────────────────────────

    /**
     * Processes a forgot-password request. Never throws for account-state reasons
     * and never returns anything that could reveal whether the account exists —
     * the controller always responds with the same generic message.
     */
    public void forgotPassword(String rawEmail, String clientIp, String userAgent) {
        String email = normalizeEmail(rawEmail);
        String ipHash = hashIp(clientIp);

        if (!rateLimiter.allowForgotPassword(email, ipHash)) {
            auditService.record(PasswordResetAuditEvent.PASSWORD_RESET_RATE_LIMITED, null, ipHash, "forgot_password");
            log.info("password_reset_forgot_rate_limited ipHash={}", ipHash);
            return;
        }

        log.info("password_reset_forgot_request_received ipHash={}", ipHash);

        Optional<AppUser> maybeUser = userRepository.findByEmail(email);
        if (maybeUser.isEmpty()) {
            // Unknown email: record only a hashed IP + category, never the email,
            // so ordinary operators cannot enumerate accounts from audit output.
            auditService.record(PasswordResetAuditEvent.PASSWORD_RESET_REQUESTED, null, ipHash, "unknown_email");
            return;
        }

        AppUser user = maybeUser.get();
        auditService.record(PasswordResetAuditEvent.PASSWORD_RESET_REQUESTED, user.getId(), ipHash);

        if (!isEligibleForReset(user)) {
            // Disabled / unapproved / role-less: no email, but the external
            // response stays identical. The account is not modified in any way.
            auditService.record(PasswordResetAuditEvent.PASSWORD_RESET_FAILED, user.getId(), ipHash,
                    "ineligible_account_state");
            return;
        }

        // Older unused tokens are revoked so at most one reset link is ever live.
        revokeActiveTokens(user.getId());

        ResetTokenGenerator.GeneratedToken generated = tokenGenerator.generate();
        Instant now = Instant.now();

        PasswordResetToken token = PasswordResetToken.builder()
                .userId(user.getId())
                .tokenHash(generated.tokenHash())
                .createdAt(now)
                .expiresAt(now.plus(Duration.ofMinutes(properties.getTokenExpirationMinutes())))
                .status(PasswordResetTokenStatus.ACTIVE)
                .requestIpHash(ipHash)
                .userAgentSummary(summarizeUserAgent(userAgent))
                .createdBySystem(false)
                .build();

        PasswordResetToken saved = tokenRepository.save(token);

        // Built from the configured frontend URL — never from a request Host
        // header — so a Host-header injection cannot rewrite the reset link.
        String resetLink = buildResetLink(generated.rawToken());

        // Async send keeps timing uniform and, on failure, revokes this token.
        emailSender.sendResetEmail(user.getEmail(), resetLink, saved.getId(), user.getId(), ipHash);
        // generated.rawToken() now falls out of scope — never stored, never logged.
    }

    // ── Reset password ──────────────────────────────────────────────────────────

    /**
     * Completes a password reset. Throws {@link ResponseStatusException}/
     * {@link IllegalArgumentException} with safe, generic messages on any failure;
     * token failures are deliberately indistinguishable (all "invalid or expired
     * reset link"). Does not authenticate the user afterwards.
     */
    public void resetPassword(ResetPasswordRequest request, String clientIp) {
        String ipHash = hashIp(clientIp);

        if (!rateLimiter.allowResetAttempt(ipHash)) {
            auditService.record(PasswordResetAuditEvent.PASSWORD_RESET_RATE_LIMITED, null, ipHash, "reset_password");
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                    "Too many attempts. Please try again later.");
        }

        // Password-shape checks first: they reveal nothing about accounts or token
        // validity (password rules are public) and give the user precise feedback.
        if (!request.newPassword().equals(request.confirmPassword())) {
            throw new IllegalArgumentException("Password confirmation does not match");
        }
        try {
            passwordPolicy.validate(request.newPassword());
        } catch (PasswordPolicy.PasswordPolicyException e) {
            throw new IllegalArgumentException(e.getMessage());
        }

        String tokenHash = tokenGenerator.hash(request.token());
        Optional<PasswordResetToken> maybeToken = tokenRepository.findByTokenHash(tokenHash);
        Instant now = Instant.now();

        if (maybeToken.isEmpty()) {
            auditService.record(PasswordResetAuditEvent.PASSWORD_RESET_FAILED, null, ipHash, "unknown_token");
            throw invalidLink();
        }

        PasswordResetToken token = maybeToken.get();

        if (token.getStatus() == PasswordResetTokenStatus.USED) {
            auditService.record(PasswordResetAuditEvent.PASSWORD_RESET_TOKEN_REUSED, token.getUserId(), ipHash);
            throw invalidLink();
        }
        if (token.getStatus() == PasswordResetTokenStatus.REVOKED) {
            auditService.record(PasswordResetAuditEvent.PASSWORD_RESET_FAILED, token.getUserId(), ipHash, "revoked_token");
            throw invalidLink();
        }
        if (token.isExpired(now)) {
            auditService.record(PasswordResetAuditEvent.PASSWORD_RESET_TOKEN_EXPIRED, token.getUserId(), ipHash);
            throw invalidLink();
        }

        // Atomic single-use claim: only one request can flip ACTIVE (and not yet
        // expired) → USED. A lost race returns null and is treated as reuse.
        PasswordResetToken claimed = claimToken(tokenHash, now);
        if (claimed == null) {
            auditService.record(PasswordResetAuditEvent.PASSWORD_RESET_TOKEN_REUSED, token.getUserId(), ipHash);
            throw invalidLink();
        }

        Optional<AppUser> maybeUser = userRepository.findById(claimed.getUserId());
        if (maybeUser.isEmpty()) {
            auditService.record(PasswordResetAuditEvent.PASSWORD_RESET_FAILED, claimed.getUserId(), ipHash, "user_missing");
            throw invalidLink();
        }

        AppUser user = maybeUser.get();
        if (!isEligibleForReset(user)) {
            // e.g. account was disabled after the link was issued: refuse, and do
            // not change enabled/approved/role. Response stays generic.
            auditService.record(PasswordResetAuditEvent.PASSWORD_RESET_FAILED, user.getId(), ipHash,
                    "ineligible_account_state");
            throw invalidLink();
        }

        // Safe-ordered writes (no multi-doc transaction assumed). The token is
        // already consumed above, so the update is idempotent-safe: the
        // security-critical change (new password + tokenVersion bump) happens
        // atomically on the single user document; a crash after this point at
        // worst leaves other stale tokens un-revoked, which the same
        // findAndModify guard would still reject on use.
        user.setPassword(passwordEncoder.encode(request.newPassword()));
        user.setPasswordChangedAt(now);
        // Single invalidation mechanism: bumping tokenVersion rejects every
        // previously issued access token on the next REST request AND every old
        // STOMP CONNECT — no separate refresh-token store exists to revoke.
        user.setTokenVersion(user.getTokenVersion() + 1);
        userRepository.save(user);

        // Revoke any other still-active tokens for this user (the consumed one is
        // already USED and therefore untouched).
        revokeActiveTokens(user.getId());

        auditService.record(PasswordResetAuditEvent.PASSWORD_RESET_COMPLETED, user.getId(), ipHash);
        log.info("password_reset_completed userId={}", user.getId());

        // Confirmation email + in-app security notification. Neither must be able
        // to fail the reset that already succeeded.
        emailSender.sendConfirmationEmail(user.getEmail(), user.getId());
        safeNotify(user);
    }

    // ── Internals ────────────────────────────────────────────────────────────────

    private PasswordResetToken claimToken(String tokenHash, Instant now) {
        Query query = new Query(Criteria.where("tokenHash").is(tokenHash)
                .and("status").is(PasswordResetTokenStatus.ACTIVE)
                .and("expiresAt").gt(now));
        Update update = new Update()
                .set("status", PasswordResetTokenStatus.USED)
                .set("usedAt", now);
        return mongoTemplate.findAndModify(
                query, update,
                FindAndModifyOptions.options().returnNew(true),
                PasswordResetToken.class);
    }

    private void revokeActiveTokens(String userId) {
        List<PasswordResetToken> active =
                tokenRepository.findByUserIdAndStatus(userId, PasswordResetTokenStatus.ACTIVE);
        if (active.isEmpty()) {
            return;
        }
        Instant now = Instant.now();
        for (PasswordResetToken token : active) {
            token.setStatus(PasswordResetTokenStatus.REVOKED);
            token.setRevokedAt(now);
        }
        tokenRepository.saveAll(active);
    }

    private boolean isEligibleForReset(AppUser user) {
        // Chosen business rule: only fully active accounts (approved + enabled)
        // may receive or complete a reset. Reset never flips these flags.
        return user.getStatus() == AccountStatus.APPROVED && user.isEnabled();
    }

    private void safeNotify(AppUser user) {
        try {
            notificationEventService.onPasswordChanged(user);
        } catch (Exception e) {
            log.warn("password_reset_notification_failed userId={} error={}", user.getId(), e.getClass().getSimpleName());
        }
    }

    private IllegalArgumentException invalidLink() {
        return new IllegalArgumentException(GENERIC_INVALID_LINK);
    }

    private String normalizeEmail(String rawEmail) {
        return rawEmail == null ? "" : rawEmail.trim().toLowerCase();
    }

    private String hashIp(String clientIp) {
        if (clientIp == null || clientIp.isBlank()) {
            return null;
        }
        return tokenGenerator.hash(clientIp.trim());
    }

    private String summarizeUserAgent(String userAgent) {
        if (userAgent == null || userAgent.isBlank()) {
            return null;
        }
        String cleaned = userAgent.replaceAll("[\\r\\n]", " ").trim();
        return cleaned.length() > 120 ? cleaned.substring(0, 120) : cleaned;
    }

    private String buildResetLink(String rawToken) {
        String base = frontendBaseUrl == null ? "" : frontendBaseUrl.trim();
        if (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        return base + "/reset-password?token=" + rawToken;
    }
}
