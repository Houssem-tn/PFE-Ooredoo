package com.pfe.activation_analytics.dto;

public record AdminActivationAnalyticsInvalidReasonResponse(
        String reason,
        long count
) {
}
