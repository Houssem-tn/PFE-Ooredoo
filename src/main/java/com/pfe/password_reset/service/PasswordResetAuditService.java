package com.pfe.password_reset.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Emits password-reset audit events as structured SLF4J log lines under a
 * dedicated {@code AUDIT.PASSWORD_RESET} logger, consistent with how the rest of
 * this codebase records audit trails (structured logs, e.g. the
 * {@code [Notification]} lines) rather than a bespoke audit collection.
 *
 * <p>Deliberately records only non-sensitive metadata: the event, the userId
 * (when known — never the email of an <em>unknown</em> account, to avoid
 * enumeration), a hashed IP reference, an outcome/failure category and a
 * timestamp (added by the logging framework). It NEVER logs the raw token,
 * password, password hash, full reset link, JWT or SMTP credentials.</p>
 */
@Service
public class PasswordResetAuditService {

    private static final Logger AUDIT_LOG = LoggerFactory.getLogger("AUDIT.PASSWORD_RESET");

    /**
     * @param event      the audit category
     * @param userId     owning user id, or {@code null} when unknown (do not pass an email)
     * @param ipHash     hashed source IP reference, or {@code null}
     * @param detail     safe outcome/failure category (never sensitive input), or {@code null}
     */
    public void record(PasswordResetAuditEvent event, String userId, String ipHash, String detail) {
        AUDIT_LOG.info("event={} userId={} ipHash={} detail={}",
                event,
                userId == null ? "-" : userId,
                ipHash == null ? "-" : ipHash,
                detail == null ? "-" : detail);
    }

    public void record(PasswordResetAuditEvent event, String userId, String ipHash) {
        record(event, userId, ipHash, null);
    }
}
