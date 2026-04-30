package com.pfe.plainte_analytics.dto;

public record AdminPlainteAnalyticsKpiResponse(
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
