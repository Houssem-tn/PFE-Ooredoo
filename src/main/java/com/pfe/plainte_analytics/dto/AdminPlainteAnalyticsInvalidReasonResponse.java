package com.pfe.plainte_analytics.dto;

public record AdminPlainteAnalyticsInvalidReasonResponse(
        String reason,
        long count
) {
}
