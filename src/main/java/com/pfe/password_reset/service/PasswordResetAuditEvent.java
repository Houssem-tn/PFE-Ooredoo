package com.pfe.password_reset.service;

/**
 * Audit event categories for the password-reset flow. Emitted as structured
 * SLF4J log lines by {@link PasswordResetAuditService} — matching this
 * codebase's existing "structured logging is the audit trail" convention
 * (there is no central audit-record collection). Audit lines never include the
 * raw token, password, password hash, full reset link, JWT or SMTP credentials.
 */
public enum PasswordResetAuditEvent {
    PASSWORD_RESET_REQUESTED,
    PASSWORD_RESET_EMAIL_SENT,
    PASSWORD_RESET_EMAIL_FAILED,
    PASSWORD_RESET_COMPLETED,
    PASSWORD_RESET_FAILED,
    PASSWORD_RESET_TOKEN_EXPIRED,
    PASSWORD_RESET_TOKEN_REUSED,
    PASSWORD_RESET_RATE_LIMITED
}
