package com.pfe.plainte_analytics.dto;

public record AdminPlainteAnalyticsAlertResponse(
        String severity,
        String title,
        String message
) {
}
