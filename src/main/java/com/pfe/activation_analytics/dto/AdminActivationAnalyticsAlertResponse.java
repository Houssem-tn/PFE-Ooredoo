package com.pfe.activation_analytics.dto;

public record AdminActivationAnalyticsAlertResponse(
        String severity,
        String title,
        String message
) {
}
