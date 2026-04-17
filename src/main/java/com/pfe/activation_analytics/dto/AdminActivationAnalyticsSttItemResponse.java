package com.pfe.activation_analytics.dto;

public record AdminActivationAnalyticsSttItemResponse(
        String sttName,
        int caseCount,
        double percentage,
        Double averageAssignmentSla,
        Double averageRdvDelay,
        Double averageExecutionSla,
        Double averageEndToEndSla,
        double rdvSuccessRate,
        double workCompletionRate,
        double abandonRate,
        double injoignableRate,
        double delayedRate
) {
}
