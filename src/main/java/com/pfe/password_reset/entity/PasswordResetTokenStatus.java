package com.pfe.password_reset.entity;

/**
 * Lifecycle state of a {@link PasswordResetToken}. A token is created ACTIVE and
 * moves exactly once to a terminal state:
 * <ul>
 *   <li>{@code USED}    — consumed by a successful password reset (single use).</li>
 *   <li>{@code REVOKED} — invalidated without being used (superseded by a newer
 *       request, or explicitly revoked when a reset completes/email fails).</li>
 * </ul>
 * Expiry is <em>not</em> a status: it is derived from {@code expiresAt} and always
 * re-checked in the service, because the TTL cleanup that removes expired
 * documents is not immediate.
 */
public enum PasswordResetTokenStatus {
    ACTIVE,
    USED,
    REVOKED
}
