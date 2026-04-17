package com.pfe.activation_analytics.dto;

public record AdminActivationAnalyticsKpiResponse(
        int totalCases,
        double completedCasesPercentage,
        Double averageAssignmentSla,
        Double averageRdvDelay,
        Double averageExecutionSla,
        Double averageEndToEndSla,
        double delayedCasesPercentage,
        double blockedCasesPercentage,
        double injoignableRate,
        double abandonRate,
        int sttCount,
        int governorateCount
) {
}
