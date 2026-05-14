package com.pfe.plainte_analytics.dto;

public record AdminPlainteAnalyticsGovernorateItemResponse(
        String gouvernorat,
        int caseCount,
        double percentage,
        Double averageAssignmentSla,
        Double averageRdvDelay,
        Double averageExecutionSla,
        Double averageEndToEndSla,
        double rdvSuccessRate,
        double workCompletionRate,
        double delayedRate,
        int distinctSttCount,
        String topSttName
) {
}
