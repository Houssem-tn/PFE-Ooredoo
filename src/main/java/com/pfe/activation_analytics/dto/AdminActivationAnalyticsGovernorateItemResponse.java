package com.pfe.activation_analytics.dto;

public record AdminActivationAnalyticsGovernorateItemResponse(
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
