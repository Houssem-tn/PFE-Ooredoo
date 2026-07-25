package com.pfe.password_reset.service;

import org.springframework.stereotype.Component;

/**
 * Single source of truth for password rules, so registration, password change
 * and password reset validate identically. The bounds mirror the existing
 * registration DTO constraints ({@code @Size(min = 6, max = 120)}) exactly, so
 * introducing this component changes no existing behaviour — it centralises the
 * same rule and lets the reset flow reuse it as the authoritative check.
 *
 * <p>The maximum length is enforced to reject excessively long passwords that
 * would waste CPU when hashed with BCrypt. Password contents are never logged.</p>
 */
@Component
public class PasswordPolicy {

    public static final int MIN_LENGTH = 6;
    public static final int MAX_LENGTH = 120;

    /** Thrown when a candidate password violates the policy. Message is safe to surface to the user. */
    public static class PasswordPolicyException extends RuntimeException {
        public PasswordPolicyException(String message) {
            super(message);
        }
    }

    /**
     * Validates a candidate password. Note it does NOT trim: internal spaces are
     * valid password characters and must be preserved. It only rejects a
     * password that is entirely whitespace (which would otherwise pass a naive
     * length check while being effectively blank).
     *
     * @throws PasswordPolicyException if the password is missing or fails the policy
     */
    public void validate(String rawPassword) {
        if (rawPassword == null || rawPassword.isEmpty()) {
            throw new PasswordPolicyException("Password is required");
        }
        if (rawPassword.trim().isEmpty()) {
            throw new PasswordPolicyException("Password cannot consist only of spaces");
        }
        if (rawPassword.length() < MIN_LENGTH) {
            throw new PasswordPolicyException("Password must be at least " + MIN_LENGTH + " characters");
        }
        if (rawPassword.length() > MAX_LENGTH) {
            throw new PasswordPolicyException("Password must be at most " + MAX_LENGTH + " characters");
        }
    }
}
