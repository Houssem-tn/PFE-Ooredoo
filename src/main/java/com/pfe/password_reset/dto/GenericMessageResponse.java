package com.pfe.password_reset.dto;

/**
 * Uniform {@code {"message": "..."}} response for the password-reset endpoints.
 * Deliberately carries nothing else — no status codes, no account details — so
 * the forgot-password response is byte-for-byte identical whether or not the
 * account exists.
 */
public record GenericMessageResponse(String message) {}
