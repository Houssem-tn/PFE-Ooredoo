package com.pfe.plainte_analytics.dto;

public record AdminPlainteAnalyticsCauseItemResponse(
        String cause,
        int count,
        double percentage
) {
}
