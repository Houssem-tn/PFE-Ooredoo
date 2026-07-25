package com.pfe.password_reset.service;

import org.junit.jupiter.api.Test;

import java.util.Base64;
import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class ResetTokenGeneratorTest {

    private final ResetTokenGenerator generator = new ResetTokenGenerator();

    @Test
    void generate_producesHighEntropyUrlSafeToken() {
        ResetTokenGenerator.GeneratedToken generated = generator.generate();

        // URL-safe Base64 without padding of 32 random bytes ⇒ 43 chars.
        assertThat(generated.rawToken()).hasSize(43);
        assertThat(generated.rawToken()).matches("[A-Za-z0-9_-]+");

        // Decodes back to at least 32 bytes of entropy.
        byte[] decoded = Base64.getUrlDecoder().decode(generated.rawToken());
        assertThat(decoded.length).isGreaterThanOrEqualTo(32);
    }

    @Test
    void generate_hashMatchesRawTokenAndIsNotReversible() {
        ResetTokenGenerator.GeneratedToken generated = generator.generate();

        // Stored hash is exactly SHA-256(raw) and never equals the raw token.
        assertThat(generated.tokenHash()).isEqualTo(generator.hash(generated.rawToken()));
        assertThat(generated.tokenHash()).isNotEqualTo(generated.rawToken());
        assertThat(generated.tokenHash()).hasSize(64); // SHA-256 hex
    }

    @Test
    void generate_producesUniqueTokens() {
        Set<String> raw = new HashSet<>();
        Set<String> hashes = new HashSet<>();
        for (int i = 0; i < 500; i++) {
            ResetTokenGenerator.GeneratedToken g = generator.generate();
            raw.add(g.rawToken());
            hashes.add(g.tokenHash());
        }
        assertThat(raw).hasSize(500);
        assertThat(hashes).hasSize(500);
    }
}
