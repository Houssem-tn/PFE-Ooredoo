package com.pfe.activation_analytics.dto;

public record AdminActivationAnalyticsCauseItemResponse(
        String cause,
        int count,
        double percentage
) {
}
