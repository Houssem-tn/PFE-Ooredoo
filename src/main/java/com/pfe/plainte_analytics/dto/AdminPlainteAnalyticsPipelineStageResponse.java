package com.pfe.plainte_analytics.dto;

public record AdminPlainteAnalyticsPipelineStageResponse(
        String stageKey,
        String label,
        Double averageBusinessDays,
        int measuredCases
) {
}
