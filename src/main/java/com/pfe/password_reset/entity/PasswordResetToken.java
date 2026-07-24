package com.pfe.password_reset.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

/**
 * A single-use, short-lived password-reset grant.
 *
 * <p>Only the SHA-256 hash of the high-entropy raw token is ever persisted
 * ({@link #tokenHash}); the raw token exists only in memory long enough to build
 * the email link and is never stored or logged. Lookup is therefore always
 * "hash the presented raw token, then find by hash".</p>
 *
 * <p>Indexes (created programmatically in {@code PasswordResetIndexConfig}):
 * unique on {@code tokenHash}, plus {@code userId}, {@code status} and a TTL on
 * {@code expiresAt} so expired documents are reaped automatically. Expiry is
 * still validated explicitly in the service because TTL cleanup is not
 * immediate.</p>
 */
@Document(collection = "password_reset_tokens")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PasswordResetToken {

    @Id
    private String id;

    /** Owning {@code AppUser.id}. Never the email, to avoid leaking identity in this collection. */
    private String userId;

    /** SHA-256 (hex) of the raw token. The raw token itself is never stored. */
    private String tokenHash;

    private Instant createdAt;

    private Instant expiresAt;

    /** Set when the token is consumed by a successful reset. */
    private Instant usedAt;

    /** Set when the token is invalidated without being used. */
    private Instant revokedAt;

    @Builder.Default
    private PasswordResetTokenStatus status = PasswordResetTokenStatus.ACTIVE;

    /**
     * SHA-256 (hex) of the requesting IP, for safe abuse forensics without
     * storing the raw address. Never the raw IP.
     */
    private String requestIpHash;

    /** Truncated User-Agent summary (best-effort, non-sensitive) for audit context. */
    private String userAgentSummary;

    /** Whether this token was created by an automated/system flow rather than a direct user action. */
    @Builder.Default
    private boolean createdBySystem = false;

    public boolean isActive() {
        return status == PasswordResetTokenStatus.ACTIVE;
    }

    public boolean isExpired(Instant now) {
        return expiresAt == null || !expiresAt.isAfter(now);
    }
}
