package com.pfe.password_reset.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Externalised, environment-overridable tuning for the password-reset flow.
 * Bound from {@code app.password-reset.*}. All values have safe development
 * defaults; production can override them via environment variables (see the
 * property wiring in {@code application.properties}).
 */
@Component
@ConfigurationProperties(prefix = "app.password-reset")
public class PasswordResetProperties {

    /**
     * Token lifetime in minutes. Kept within the recommended 15–30 minute band;
     * clamped in {@link #getTokenExpirationMinutes()} so a misconfiguration can
     * never widen the window beyond policy.
     */
    private long tokenExpirationMinutes = 20;

    /** Maximum simultaneously-active (unused, unexpired) tokens per user. */
    private int maxActiveTokensPerUser = 3;

    /** Minimum seconds between two forgot-password requests for the same email. */
    private long requestCooldownSeconds = 60;

    /** Maximum forgot-password requests per email within the rolling window. */
    private int maxRequestsPerEmailPerHour = 5;

    /** Maximum forgot-password requests per client IP within the rolling window. */
    private int maxRequestsPerIpPerHour = 20;

    /** Maximum reset (token-submit) attempts per client IP within the rolling window. */
    private int maxResetAttemptsPerIpPerHour = 30;

    /** Sender display name used in reset emails. */
    private String emailSenderName = "Ooredoo Field Service Platform";

    public long getTokenExpirationMinutes() {
        // Enforce the 15–30 minute policy band regardless of configuration.
        if (tokenExpirationMinutes < 15) {
            return 15;
        }
        return Math.min(tokenExpirationMinutes, 30);
    }

    public void setTokenExpirationMinutes(long tokenExpirationMinutes) {
        this.tokenExpirationMinutes = tokenExpirationMinutes;
    }

    public int getMaxActiveTokensPerUser() {
        return maxActiveTokensPerUser;
    }

    public void setMaxActiveTokensPerUser(int maxActiveTokensPerUser) {
        this.maxActiveTokensPerUser = maxActiveTokensPerUser;
    }

    public long getRequestCooldownSeconds() {
        return requestCooldownSeconds;
    }

    public void setRequestCooldownSeconds(long requestCooldownSeconds) {
        this.requestCooldownSeconds = requestCooldownSeconds;
    }

    public int getMaxRequestsPerEmailPerHour() {
        return maxRequestsPerEmailPerHour;
    }

    public void setMaxRequestsPerEmailPerHour(int maxRequestsPerEmailPerHour) {
        this.maxRequestsPerEmailPerHour = maxRequestsPerEmailPerHour;
    }

    public int getMaxRequestsPerIpPerHour() {
        return maxRequestsPerIpPerHour;
    }

    public void setMaxRequestsPerIpPerHour(int maxRequestsPerIpPerHour) {
        this.maxRequestsPerIpPerHour = maxRequestsPerIpPerHour;
    }

    public int getMaxResetAttemptsPerIpPerHour() {
        return maxResetAttemptsPerIpPerHour;
    }

    public void setMaxResetAttemptsPerIpPerHour(int maxResetAttemptsPerIpPerHour) {
        this.maxResetAttemptsPerIpPerHour = maxResetAttemptsPerIpPerHour;
    }

    public String getEmailSenderName() {
        return emailSenderName;
    }

    public void setEmailSenderName(String emailSenderName) {
        this.emailSenderName = emailSenderName;
    }
}
