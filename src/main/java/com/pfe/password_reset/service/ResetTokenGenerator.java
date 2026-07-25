package com.pfe.password_reset.service;

import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

/**
 * Generates high-entropy password-reset tokens and derives their storage hash.
 *
 * <p>The raw token is 32 cryptographically-secure random bytes, URL-safe
 * Base64-encoded (no padding) — well above the "at least 32 bytes" bar and safe
 * to place in a URL. Only its SHA-256 hash is ever persisted; the raw token is
 * returned once, used to build the email link, and then discarded. Neither the
 * raw token nor a would-be reverse of the hash is logged anywhere.</p>
 *
 * <p>SHA-256 (not BCrypt) is the correct hash here: the token already has full
 * 256-bit entropy, so it is not brute-forceable and needs no slow/salted KDF; a
 * fast deterministic digest also lets lookup be a single indexed equality query.</p>
 */
@Component
public class ResetTokenGenerator {

    private static final int TOKEN_BYTES = 32;

    private final SecureRandom secureRandom = new SecureRandom();

    /** A freshly generated raw token (never stored) paired with the hash to persist. */
    public record GeneratedToken(String rawToken, String tokenHash) {}

    public GeneratedToken generate() {
        byte[] randomBytes = new byte[TOKEN_BYTES];
        secureRandom.nextBytes(randomBytes);
        String rawToken = Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
        return new GeneratedToken(rawToken, hash(rawToken));
    }

    /** SHA-256 (lower-case hex) of the raw token. Deterministic, so lookup is a hash-then-find. */
    public String hash(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashed = digest.digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hashed);
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 is guaranteed present on every JVM; this is unreachable.
            throw new IllegalStateException("SHA-256 algorithm unavailable", e);
        }
    }
}
