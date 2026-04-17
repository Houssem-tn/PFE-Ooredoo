package com.pfe.activation_analytics.dto;

public record AdminActivationAnalyticsPipelineStageResponse(
        String stageKey,
        String label,
        Double averageBusinessDays,
        int measuredCases
) {
}
