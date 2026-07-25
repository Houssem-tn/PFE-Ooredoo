package com.pfe.password_reset.service;

import com.pfe.password_reset.config.PasswordResetProperties;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PasswordResetRateLimiterTest {

    private PasswordResetProperties props(int cooldown, int perEmail, int perIp, int resetPerIp) {
        PasswordResetProperties p = new PasswordResetProperties();
        p.setRequestCooldownSeconds(cooldown);
        p.setMaxRequestsPerEmailPerHour(perEmail);
        p.setMaxRequestsPerIpPerHour(perIp);
        p.setMaxResetAttemptsPerIpPerHour(resetPerIp);
        return p;
    }

    @Test
    void forgotPassword_cooldownBlocksImmediateSecondRequest() {
        PasswordResetRateLimiter limiter = new PasswordResetRateLimiter(props(60, 5, 20, 30));

        assertThat(limiter.allowForgotPassword("user@example.com", "ip-a")).isTrue();
        // Second request for the same email within the cooldown is blocked.
        assertThat(limiter.allowForgotPassword("user@example.com", "ip-a")).isFalse();
    }

    @Test
    void forgotPassword_perEmailHourlyCapEnforced() {
        // No cooldown so we can exercise the hourly cap directly.
        PasswordResetRateLimiter limiter = new PasswordResetRateLimiter(props(0, 3, 100, 30));

        assertThat(limiter.allowForgotPassword("a@example.com", "ip-1")).isTrue();
        assertThat(limiter.allowForgotPassword("a@example.com", "ip-2")).isTrue();
        assertThat(limiter.allowForgotPassword("a@example.com", "ip-3")).isTrue();
        assertThat(limiter.allowForgotPassword("a@example.com", "ip-4")).isFalse();
    }

    @Test
    void forgotPassword_perIpHourlyCapEnforcedAcrossEmails() {
        PasswordResetRateLimiter limiter = new PasswordResetRateLimiter(props(0, 100, 2, 30));

        assertThat(limiter.allowForgotPassword("a@example.com", "shared-ip")).isTrue();
        assertThat(limiter.allowForgotPassword("b@example.com", "shared-ip")).isTrue();
        assertThat(limiter.allowForgotPassword("c@example.com", "shared-ip")).isFalse();
    }

    @Test
    void resetAttempt_perIpCapEnforced() {
        PasswordResetRateLimiter limiter = new PasswordResetRateLimiter(props(0, 100, 100, 2));

        assertThat(limiter.allowResetAttempt("ip-x")).isTrue();
        assertThat(limiter.allowResetAttempt("ip-x")).isTrue();
        assertThat(limiter.allowResetAttempt("ip-x")).isFalse();
    }

    @Test
    void resetAttempt_nullIpIsAllowed() {
        PasswordResetRateLimiter limiter = new PasswordResetRateLimiter(props(0, 100, 100, 1));
        assertThat(limiter.allowResetAttempt(null)).isTrue();
    }
}
